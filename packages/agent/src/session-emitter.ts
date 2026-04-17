/**
 * SessionEmitter interface — abstraction for sending messages back
 * to the connected Client during a session.
 *
 * @remarks
 * Decouples the agent turn loop from WebSocket transport. The gateway
 * provides a concrete implementation wrapping the WebSocket; tests
 * provide a mock that records calls.
 */

import type { AgentState } from './index.js';

/**
 * Interface for emitting session events back to the Client.
 */
export interface SessionEmitter {
  /** Emit an agent state change event. */
  sendAgentState(sessionId: string, state: AgentState): void;
  /** Emit a transcript event (partial or final). */
  sendTranscript(sessionId: string, text: string, isFinal: boolean, confidence?: number): void;
  /** Emit an LLM token stream event. */
  sendLlmToken(sessionId: string, token: string, done: boolean): void;
  /** Emit a tool call started event. */
  sendToolCallStarted(sessionId: string, callId: string, toolId: string, args?: Record<string, unknown>): void;
  /** Emit a tool call completed event. */
  sendToolCallCompleted(sessionId: string, callId: string, success: boolean, resultSummary?: string, latencyMs?: number): void;
  /** Send an outbound audio stream start envelope. */
  sendAudioStreamStart(sessionId: string, streamId: string): void;
  /** Send an outbound audio chunk. */
  sendAudioChunk(data: Uint8Array): void;
  /** Send an outbound audio stream end. */
  sendAudioStreamEnd(sessionId: string, streamId: string, reason: string): void;
  /** Send a session opened event. */
  sendSessionOpened(sessionId: string, userId: string): void;
  /** Send a session closed event. */
  sendSessionClosed(sessionId: string, reason: string): void;
}
