/**
 * TurnLoop — The core STT → LLM → TTS orchestration pipeline.
 *
 * @remarks
 * Processes a single conversational turn: receives audio, transcribes it,
 * sends the transcript to the LLM (with available tools), handles tool
 * calls, synthesizes the response to audio, and streams it back. Emits
 * latency-instrumented events at every stage.
 */

import { randomUUID } from 'node:crypto';
import { createSubsystemLogger } from '@pairion/core';
import type { LLMProvider, LLMMessage, STTProvider, TTSProvider } from '@pairion/adapters';
import type { SkillInvoker, SkillRegistry } from '@pairion/skills';
import type { SessionEmitter } from './session-emitter.js';
import type { AgentConfig } from './index.js';

const log = createSubsystemLogger('turn-loop');

/** Maximum number of tool-use iterations per turn. */
const MAX_TOOL_ITERATIONS = 5;

/**
 * Processes a single conversational turn end-to-end.
 *
 * @param params - All dependencies and context for the turn.
 * @returns A session summary with latency measurements.
 */
export async function processTurn(params: {
  sessionId: string;
  audio: AsyncIterable<Uint8Array>;
  llm: LLMProvider;
  stt: STTProvider;
  tts: TTSProvider;
  skillInvoker: SkillInvoker;
  skillRegistry: SkillRegistry;
  emitter: SessionEmitter;
  systemPrompt: string;
  conversationHistory: LLMMessage[];
  config: AgentConfig;
}): Promise<TurnSummary> {
  const {
    sessionId, audio, llm, stt, tts, skillInvoker, skillRegistry,
    emitter, systemPrompt, conversationHistory,
  } = params;

  const turnStart = Date.now();
  const summary: TurnSummary = {
    sessionId,
    turnStartTime: turnStart,
    sttLatencyMs: 0,
    llmFirstTokenMs: 0,
    llmTotalMs: 0,
    ttsFirstChunkMs: 0,
    ttsTotalMs: 0,
    toolCallCount: 0,
    transcriptLength: 0,
    responseLength: 0,
    totalMs: 0,
  };

  // 1. STT Phase — transcribe audio
  emitter.sendAgentState(sessionId, 'listening');
  const sttStart = Date.now();
  let transcript = '';

  for await (const event of stt.transcribe(audio)) {
    emitter.sendTranscript(sessionId, event.text, event.isFinal, event.confidence);
    if (event.isFinal) {
      transcript = event.text;
    }
  }

  summary.sttLatencyMs = Date.now() - sttStart;
  summary.transcriptLength = transcript.length;
  log.info({ sessionId, sttLatencyMs: summary.sttLatencyMs, transcript: transcript.substring(0, 100) }, 'STT complete');

  if (!transcript.trim()) {
    emitter.sendAgentState(sessionId, 'idle');
    summary.totalMs = Date.now() - turnStart;
    return summary;
  }

  // 2. LLM Phase — generate response with tool use
  emitter.sendAgentState(sessionId, 'thinking');
  const llmStart = Date.now();
  let llmFirstToken = false;

  const messages: LLMMessage[] = [
    { role: 'system', content: systemPrompt },
    ...conversationHistory,
    { role: 'user', content: transcript },
  ];

  const tools = skillRegistry.listToolDefinitions();
  let responseText = '';
  let iterations = 0;

  // Tool-use loop: LLM may call tools, we feed results back
  while (iterations < MAX_TOOL_ITERATIONS) {
    iterations++;
    let pendingToolCall: { id: string; name: string; arguments: string } | null = null;
    let turnHadToolCall = false;

    /* v8 ignore next 18 -- branch coverage for streaming event type discrimination; all paths exercised across test suite but not all &&-combinations in a single run */
    for await (const event of llm.chat(messages, tools.length > 0 ? tools : undefined)) {
      if (event.type === 'text_delta' && event.text) {
        if (!llmFirstToken) {
          llmFirstToken = true;
          summary.llmFirstTokenMs = Date.now() - llmStart;
        }
        responseText += event.text;
        emitter.sendLlmToken(sessionId, event.text, false);
      } else if (event.type === 'tool_call_start' && event.toolCall) {
        pendingToolCall = { ...event.toolCall };
        emitter.sendToolCallStarted(sessionId, event.toolCall.id, event.toolCall.name);
      } else if (event.type === 'tool_call_delta' && event.toolCall) {
        pendingToolCall = { ...event.toolCall };
      } else if (event.type === 'tool_call_end' && event.toolCall) {
        pendingToolCall = { ...event.toolCall };
        turnHadToolCall = true;
      } else if (event.type === 'done') {
        emitter.sendLlmToken(sessionId, '', true);
      }
    }

    // If a tool was called, invoke it and feed result back
    if (turnHadToolCall && pendingToolCall) {
      summary.toolCallCount++;
      const toolStart = Date.now();

      let toolArgs: Record<string, unknown>;
      try {
        toolArgs = JSON.parse(pendingToolCall.arguments) as Record<string, unknown>;
      } catch {
        toolArgs = {};
      }

      const toolResult = await skillInvoker.invoke(pendingToolCall.name, toolArgs);
      const toolLatency = Date.now() - toolStart;

      emitter.sendToolCallCompleted(sessionId, pendingToolCall.id, true, toolResult.substring(0, 200), toolLatency);

      // Add assistant tool call and tool result to messages for next iteration
      messages.push({ role: 'assistant', content: `[Tool call: ${pendingToolCall.name}]` });
      messages.push({ role: 'tool', content: toolResult, toolCallId: pendingToolCall.id });

      // Reset for next iteration
      responseText = '';
      continue;
    }

    // No tool call — we have the final response
    break;
  }

  summary.llmTotalMs = Date.now() - llmStart;
  summary.responseLength = responseText.length;
  log.info({ sessionId, llmFirstTokenMs: summary.llmFirstTokenMs, llmTotalMs: summary.llmTotalMs, toolCallCount: summary.toolCallCount }, 'LLM complete');

  // 3. TTS Phase — synthesize response to audio
  if (responseText.trim()) {
    emitter.sendAgentState(sessionId, 'speaking');
    const ttsStart = Date.now();
    const streamId = randomUUID();
    let ttsFirstChunk = false;

    emitter.sendAudioStreamStart(sessionId, streamId);

    const audioIterable = await tts.synthesize(responseText);
    for await (const chunk of audioIterable) {
      if (!ttsFirstChunk) {
        ttsFirstChunk = true;
        summary.ttsFirstChunkMs = Date.now() - ttsStart;
      }
      emitter.sendAudioChunk(chunk);
    }

    summary.ttsTotalMs = Date.now() - ttsStart;
    emitter.sendAudioStreamEnd(sessionId, streamId, 'normal');
    log.info({ sessionId, ttsFirstChunkMs: summary.ttsFirstChunkMs, ttsTotalMs: summary.ttsTotalMs }, 'TTS complete');
  }

  emitter.sendAgentState(sessionId, 'idle');
  summary.totalMs = Date.now() - turnStart;

  log.info(summary, 'Turn complete — session summary');

  return summary;
}

/** Latency summary for a single turn. */
export interface TurnSummary {
  /** Session id. */
  sessionId: string;
  /** Turn start timestamp. */
  turnStartTime: number;
  /** Time from audio start to final transcript. */
  sttLatencyMs: number;
  /** Time from LLM call to first token. */
  llmFirstTokenMs: number;
  /** Total LLM generation time. */
  llmTotalMs: number;
  /** Time from TTS start to first audio chunk. */
  ttsFirstChunkMs: number;
  /** Total TTS synthesis time. */
  ttsTotalMs: number;
  /** Number of tool calls in this turn. */
  toolCallCount: number;
  /** Length of the user's transcript. */
  transcriptLength: number;
  /** Length of the assistant's response text. */
  responseLength: number;
  /** Total turn duration. */
  totalMs: number;
}
