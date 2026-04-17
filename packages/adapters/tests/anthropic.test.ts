import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AnthropicLLMProvider } from '../src/implementations/llm/anthropic.js';

// Mock the Anthropic SDK
vi.mock('@anthropic-ai/sdk', () => {
  return {
    default: class MockAnthropic {
      messages = {
        stream: vi.fn(() => {
          const events = [
            { type: 'content_block_start', content_block: { type: 'text', text: '' }, index: 0 },
            { type: 'content_block_delta', delta: { type: 'text_delta', text: 'Hello' }, index: 0 },
            { type: 'content_block_delta', delta: { type: 'text_delta', text: ' world' }, index: 0 },
            { type: 'content_block_stop', index: 0 },
            { type: 'message_stop' },
          ];
          return {
            [Symbol.asyncIterator]: async function* () {
              for (const e of events) yield e;
            },
          };
        }),
      };
    },
  };
});

describe('AnthropicLLMProvider', () => {
  let provider: AnthropicLLMProvider;

  beforeEach(() => {
    provider = new AnthropicLLMProvider({ apiKey: 'test-key' });
  });

  it('has providerId "anthropic"', () => {
    expect(provider.providerId).toBe('anthropic');
  });

  it('has tool-use and streaming capabilities', () => {
    const caps = provider.capabilities.map((c) => c.id);
    expect(caps).toContain('tool-use');
    expect(caps).toContain('streaming');
  });

  it('generate() yields text tokens', async () => {
    const iterable = await provider.generate('Hello');
    const tokens: string[] = [];
    for await (const token of iterable) {
      tokens.push(token);
    }
    expect(tokens).toEqual(['Hello', ' world']);
  });

  it('chat() yields LLMStreamEvents', async () => {
    const events = [];
    for await (const event of provider.chat([{ role: 'user', content: 'Hi' }])) {
      events.push(event);
    }
    expect(events.some((e) => e.type === 'text_delta')).toBe(true);
    expect(events[events.length - 1]?.type).toBe('done');
  });

  it('chat() handles tool_use content blocks', async () => {
    const { default: MockAnthropic } = await import('@anthropic-ai/sdk');
    const mockInstance = new MockAnthropic() as any;
    mockInstance.messages.stream.mockReturnValue({
      [Symbol.asyncIterator]: async function* () {
        yield { type: 'content_block_start', content_block: { type: 'tool_use', id: 'call_1', name: 'get_weather' }, index: 0 };
        yield { type: 'content_block_delta', delta: { type: 'input_json_delta', partial_json: '{"lat":' }, index: 0 };
        yield { type: 'content_block_delta', delta: { type: 'input_json_delta', partial_json: '37}' }, index: 0 };
        yield { type: 'content_block_stop', index: 0 };
        yield { type: 'message_stop' };
      },
    });

    // Create provider with mocked client by replacing internal client
    const p = new AnthropicLLMProvider({ apiKey: 'test-key' });
    (p as any).client = mockInstance;

    const events = [];
    for await (const event of p.chat(
      [{ role: 'user', content: 'weather?' }],
      [{ name: 'get_weather', description: 'Get weather', inputSchema: {} }],
    )) {
      events.push(event);
    }

    const toolStart = events.find((e) => e.type === 'tool_call_start');
    expect(toolStart?.toolCall?.name).toBe('get_weather');

    const toolEnd = events.find((e) => e.type === 'tool_call_end');
    expect(toolEnd?.toolCall?.arguments).toContain('37');
  });

  it('chat() passes system messages correctly', async () => {
    const events = [];
    for await (const event of provider.chat([
      { role: 'system', content: 'You are Pairion' },
      { role: 'user', content: 'Hello' },
    ])) {
      events.push(event);
    }
    expect(events.length).toBeGreaterThan(0);
  });

  it('chat() handles tool result messages', async () => {
    const events = [];
    for await (const event of provider.chat([
      { role: 'user', content: 'weather?' },
      { role: 'tool', content: 'Sunny, 72F', toolCallId: 'call_1' },
    ])) {
      events.push(event);
    }
    expect(events.length).toBeGreaterThan(0);
  });
});
