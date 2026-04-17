/**
 * WebSocket-backed SessionEmitter implementation.
 *
 * @remarks
 * Translates agent session events into AsyncAPI-compliant WebSocket messages.
 * Each method sends a JSON message with the correct `type` discriminator.
 */

import type { WebSocket } from 'ws';
import type { SessionEmitter } from '@pairion/agent';

/**
 * Emits session events over a WebSocket connection.
 */
export class WebSocketSessionEmitter implements SessionEmitter {
  private readonly ws: WebSocket;

  /**
   * Creates a WebSocket session emitter.
   *
   * @param ws - The WebSocket connection to emit on.
   */
  constructor(ws: WebSocket) {
    this.ws = ws;
  }

  /** @inheritdoc */
  sendAgentState(sessionId: string, state: string): void {
    this.send({ type: 'AgentStateChange', sessionId, state, timestamp: now() });
  }

  /** @inheritdoc */
  sendTranscript(sessionId: string, text: string, isFinal: boolean, confidence?: number): void {
    const type = isFinal ? 'TranscriptFinal' : 'TranscriptPartial';
    this.send({ type, sessionId, text, ...(confidence !== undefined ? { confidence } : {}), timestamp: now() });
  }

  /** @inheritdoc */
  sendLlmToken(sessionId: string, token: string, done: boolean): void {
    this.send({ type: 'LlmTokenStream', sessionId, token, done, timestamp: now() });
  }

  /** @inheritdoc */
  sendToolCallStarted(sessionId: string, callId: string, toolId: string, args?: Record<string, unknown>): void {
    this.send({ type: 'ToolCallStarted', sessionId, callId, toolId, ...(args ? { arguments: args } : {}), timestamp: now() });
  }

  /** @inheritdoc */
  sendToolCallCompleted(sessionId: string, callId: string, success: boolean, resultSummary?: string, latencyMs?: number): void {
    this.send({
      type: 'ToolCallCompleted', sessionId, callId, success,
      ...(resultSummary !== undefined ? { resultSummary } : {}),
      ...(latencyMs !== undefined ? { latencyMs } : {}),
      timestamp: now(),
    });
  }

  /** @inheritdoc */
  sendAudioStreamStart(sessionId: string, streamId: string): void {
    this.send({
      type: 'AudioStreamStart', sessionId, streamId, direction: 'out',
      codec: 'pcm-s16le', sampleRate: 24000, channels: 1, timestamp: now(),
    });
  }

  /** @inheritdoc */
  sendAudioChunk(data: Uint8Array): void {
    this.ws.send(data);
  }

  /** @inheritdoc */
  sendAudioStreamEnd(sessionId: string, streamId: string, reason: string): void {
    this.send({ type: 'AudioStreamEnd', sessionId, streamId, reason, timestamp: now() });
  }

  /** @inheritdoc */
  sendSessionOpened(sessionId: string, userId: string): void {
    this.send({ type: 'SessionOpened', sessionId, userId, timestamp: now() });
  }

  /** @inheritdoc */
  sendSessionClosed(sessionId: string, reason: string): void {
    this.send({ type: 'SessionClosed', sessionId, reason, timestamp: now() });
  }

  /** Sends a JSON message over the WebSocket. */
  private send(msg: Record<string, unknown>): void {
    this.ws.send(JSON.stringify(msg));
  }
}

/** Returns an ISO timestamp string. */
function now(): string {
  return new Date().toISOString();
}
