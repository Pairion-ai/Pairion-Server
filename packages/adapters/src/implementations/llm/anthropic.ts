/**
 * Anthropic Claude LLM adapter.
 *
 * @remarks
 * Implements `LLMProvider` using the Anthropic SDK. Supports streaming
 * token output and tool-use for MCP skill invocation. The API key is
 * passed at construction — resolved from SecretsStore or environment
 * by the bootstrap code.
 *
 * This is the only file in the monorepo that may import `@anthropic-ai/sdk`.
 */

import Anthropic from '@anthropic-ai/sdk';
import type {
  LLMProvider,
  LLMGenerateOptions,
  LLMMessage,
  ToolDefinition,
  LLMStreamEvent,
  CapabilityDescriptor,
} from '../../interfaces.js';
import { createSubsystemLogger } from '@pairion/core';

const log = createSubsystemLogger('adapter-anthropic');

/** Configuration for the Anthropic adapter. */
export interface AnthropicConfig {
  /** Anthropic API key. */
  readonly apiKey: string;
  /** Model to use (default: claude-sonnet-4-20250514). */
  readonly model?: string | undefined;
  /** Max tokens for generation (default: 4096). */
  readonly maxTokens?: number | undefined;
}

/**
 * Anthropic Claude LLM provider implementation.
 */
export class AnthropicLLMProvider implements LLMProvider {
  readonly providerId = 'anthropic';
  readonly capabilities: readonly CapabilityDescriptor[] = [
    { id: 'tool-use', label: 'Tool Use' },
    { id: 'streaming', label: 'Streaming' },
    { id: 'vision', label: 'Vision' },
  ];

  private readonly client: Anthropic;
  private readonly model: string;
  private readonly defaultMaxTokens: number;

  /**
   * Creates an Anthropic LLM provider.
   *
   * @param config - Anthropic configuration including API key.
   */
  constructor(config: AnthropicConfig) {
    this.client = new Anthropic({ apiKey: config.apiKey });
    this.model = config.model ?? 'claude-sonnet-4-20250514';
    this.defaultMaxTokens = config.maxTokens ?? 4096;
    log.info({ model: this.model }, 'Anthropic adapter initialized');
  }

  /** @inheritdoc */
  async generate(prompt: string, options?: LLMGenerateOptions): Promise<AsyncIterable<string>> {
    const events = this.chat([{ role: 'user', content: prompt }], undefined, options);
    return (async function* () {
      for await (const event of events) {
        if (event.type === 'text_delta' && event.text) {
          yield event.text;
        }
      }
    })();
  }

  /** @inheritdoc */
  async *chat(
    messages: LLMMessage[],
    tools?: ToolDefinition[],
    options?: LLMGenerateOptions,
  ): AsyncIterable<LLMStreamEvent> {
    const systemMessages = messages.filter((m) => m.role === 'system');
    const nonSystemMessages = messages.filter((m) => m.role !== 'system');

    const anthropicMessages: Anthropic.MessageParam[] = nonSystemMessages.map((m) => {
      if (m.role === 'tool') {
        return {
          role: 'user' as const,
          content: [
            {
              type: 'tool_result' as const,
              tool_use_id: m.toolCallId ?? '',
              content: m.content,
            },
          ],
        };
      }
      return {
        role: m.role as 'user' | 'assistant',
        content: m.content,
      };
    });

    const anthropicTools: Anthropic.Tool[] | undefined = tools?.map((t) => ({
      name: t.name,
      description: t.description,
      input_schema: t.inputSchema as Anthropic.Tool.InputSchema,
    }));

    const stream = this.client.messages.stream({
      model: this.model,
      max_tokens: options?.maxTokens ?? this.defaultMaxTokens,
      ...(options?.temperature !== undefined ? { temperature: options.temperature } : {}),
      ...(systemMessages.length > 0
        ? { system: systemMessages.map((m) => m.content).join('\n') }
        : {}),
      messages: anthropicMessages,
      ...(anthropicTools?.length ? { tools: anthropicTools } : {}),
    });

    let currentToolId = '';
    let currentToolName = '';
    let currentToolArgs = '';

    for await (const event of stream) {
      if (event.type === 'content_block_start') {
        if (event.content_block.type === 'tool_use') {
          currentToolId = event.content_block.id;
          currentToolName = event.content_block.name;
          currentToolArgs = '';
          yield {
            type: 'tool_call_start',
            toolCall: { id: currentToolId, name: currentToolName, arguments: '' },
          };
        }
      } else if (event.type === 'content_block_delta') {
        if (event.delta.type === 'text_delta') {
          yield { type: 'text_delta', text: event.delta.text };
        } else if (event.delta.type === 'input_json_delta') {
          currentToolArgs += event.delta.partial_json;
          yield {
            type: 'tool_call_delta',
            toolCall: { id: currentToolId, name: currentToolName, arguments: currentToolArgs },
          };
        }
      } else if (event.type === 'content_block_stop') {
        if (currentToolId) {
          yield {
            type: 'tool_call_end',
            toolCall: { id: currentToolId, name: currentToolName, arguments: currentToolArgs },
          };
          currentToolId = '';
          currentToolName = '';
          currentToolArgs = '';
        }
      } else if (event.type === 'message_stop') {
        yield { type: 'done' };
      }
    }
  }
}
