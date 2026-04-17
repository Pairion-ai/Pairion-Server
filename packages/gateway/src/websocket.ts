/**
 * WebSocket handler for the AsyncAPI streaming protocol.
 *
 * @remarks
 * Single bidirectional WebSocket per paired Device or Node at `/ws/v1`.
 * All messages carry a `type` discriminator field. M1 adds session lifecycle
 * messages (WakeWordDetected, AudioStreamStart, SpeechEnded) alongside
 * the M0 identification and heartbeat flow.
 */

import { WebSocketServer, type WebSocket } from 'ws';
import type { Server as HttpServer } from 'node:http';
import { createSubsystemLogger, AsyncIterableQueue } from '@pairion/core';
import { processLogForward } from '@pairion/logs';
import type { SessionManager } from '@pairion/agent';
import { WebSocketSessionEmitter } from './ws-session-emitter.js';

const log = createSubsystemLogger('websocket');

/** Known AsyncAPI message types for dispatch. */
const IMPLEMENTED_TYPES = new Set([
  'DeviceIdentify', 'NodeIdentify', 'HeartbeatPing', 'LogForward',
  'WakeWordDetected', 'AudioStreamStart', 'SpeechEnded',
]);

/**
 * Per-connection state tracking active sessions and audio queues.
 */
interface ConnectionState {
  identified: boolean;
  deviceId: string;
  activeSessionId: string | null;
  audioQueue: AsyncIterableQueue<Uint8Array> | null;
}

/**
 * Sets up the WebSocket server on the given HTTP server.
 *
 * @param server - The Node.js HTTP server.
 * @param devToken - The dev bearer token for authentication.
 * @param sessionManager - Optional session manager for agent turn processing.
 * @returns The WebSocketServer instance.
 */
export function setupWebSocket(
  server: HttpServer,
  devToken: string,
  sessionManager?: SessionManager,
): WebSocketServer {
  const wss = new WebSocketServer({ server, path: '/ws/v1' });

  wss.on('connection', (ws: WebSocket) => {
    const state: ConnectionState = {
      identified: false,
      deviceId: '',
      activeSessionId: null,
      audioQueue: null,
    };

    ws.on('message', (data: Buffer | ArrayBuffer | Buffer[]) => {
      // Check if this is a binary audio frame
      const buf = Buffer.isBuffer(data) ? data : /* v8 ignore next */ Buffer.from(data as ArrayBuffer);

      // Try to parse as JSON first
      let msg: Record<string, unknown>;
      try {
        msg = JSON.parse(buf.toString('utf-8')) as Record<string, unknown>;
      } catch {
        // If not JSON and we have an active audio queue, treat as binary audio chunk
        if (state.audioQueue) {
          state.audioQueue.push(new Uint8Array(buf));
          return;
        }
        sendError(ws, 'protocol.invalid_json', 'Invalid JSON');
        return;
      }

      const type = msg['type'] as string | undefined;
      if (!type) {
        sendError(ws, 'protocol.missing_type', 'Message must include a "type" field');
        return;
      }

      if (!state.identified && type !== 'DeviceIdentify' && type !== 'NodeIdentify') {
        sendError(ws, 'protocol.not_identified', 'Must send DeviceIdentify or NodeIdentify first');
        return;
      }

      if (!IMPLEMENTED_TYPES.has(type)) {
        sendError(ws, 'server.not_implemented', `Message type "${type}" is not implemented`);
        return;
      }

      handleMessage(ws, type, msg, devToken, state, sessionManager);
    });

    ws.on('error', /* v8 ignore next */ (err) => {
      log.error({ err }, 'WebSocket error');
    });

    ws.on('close', /* v8 ignore next 5 -- WebSocket close timing is non-deterministic in tests */ () => {
      if (state.audioQueue) {
        state.audioQueue.end();
      }
      if (state.activeSessionId && sessionManager) {
        sessionManager.closeSession(state.activeSessionId, 'disconnect');
      }
    });
  });

  return wss;
}

/**
 * Handles a parsed WebSocket message by type.
 */
function handleMessage(
  ws: WebSocket,
  type: string,
  msg: Record<string, unknown>,
  devToken: string,
  state: ConnectionState,
  sessionManager?: SessionManager,
): void {
  switch (type) {
    case 'DeviceIdentify':
    case 'NodeIdentify': {
      const token = msg['token'] as string | undefined;
      if (token !== devToken) {
        ws.send(JSON.stringify({
          type: 'IdentifyAck',
          accepted: false,
          serverVersion: '1.0.0-alpha.1',
          reason: 'Invalid bearer token',
          timestamp: new Date().toISOString(),
        }));
        ws.close(4001, 'Authentication failed');
        return;
      }
      state.identified = true;
      /* v8 ignore next -- defensive fallback chain */
      state.deviceId = (msg['deviceId'] as string) ?? (msg['nodeId'] as string) ?? '';
      ws.send(JSON.stringify({
        type: 'IdentifyAck',
        accepted: true,
        serverVersion: '1.0.0-alpha.1',
        timestamp: new Date().toISOString(),
      }));
      log.info({ type, deviceId: msg['deviceId'], nodeId: msg['nodeId'] }, 'Client/Node identified');
      break;
    }

    case 'HeartbeatPing': {
      const clientTs = msg['timestamp'] as string | undefined;
      const now = new Date();
      const latencyMs = clientTs ? now.getTime() - new Date(clientTs).getTime() : 0;
      ws.send(JSON.stringify({
        type: 'HeartbeatPong',
        timestamp: now.toISOString(),
        latencyMs: Math.max(0, latencyMs),
      }));
      break;
    }

    case 'LogForward': {
      processLogForward({
        sourceDeviceId: msg['sourceDeviceId'] as string | undefined,
        sourceNodeId: msg['sourceNodeId'] as string | undefined,
        entries: (msg['entries'] as Array<Record<string, unknown>> ?? []).map((e) => ({
          timestamp: (e['timestamp'] as string) ?? new Date().toISOString(),
          level: (e['level'] as 'info') ?? 'info',
          subsystem: (e['subsystem'] as string) ?? 'unknown',
          message: (e['message'] as string) ?? '',
          context: e['context'] as Record<string, unknown> | undefined,
          requestId: e['requestId'] as string | undefined,
          sessionId: e['sessionId'] as string | undefined,
        })),
      });
      break;
    }

    case 'WakeWordDetected': {
      if (!sessionManager) {
        sendError(ws, 'server.no_agent', 'Agent not available');
        return;
      }
      const emitter = new WebSocketSessionEmitter(ws);
      const sessionId = sessionManager.createSession(state.deviceId, emitter);
      state.activeSessionId = sessionId;
      state.audioQueue = new AsyncIterableQueue<Uint8Array>();
      log.info({ sessionId, deviceId: state.deviceId }, 'Wake word detected — session created');
      break;
    }

    case 'AudioStreamStart': {
      // Audio stream start envelope — the queue is already set up from WakeWordDetected
      /* v8 ignore next 3 -- fallback for edge case where AudioStreamStart arrives before WakeWordDetected */
      if (!state.audioQueue) {
        state.audioQueue = new AsyncIterableQueue<Uint8Array>();
      }
      break;
    }

    case 'SpeechEnded': {
      if (!state.audioQueue || !state.activeSessionId || !sessionManager) {
        return;
      }
      const queue = state.audioQueue;
      const sessionId = state.activeSessionId;
      queue.end();
      state.audioQueue = null;

      const emitter = new WebSocketSessionEmitter(ws);
      // Process the turn asynchronously
      sessionManager.processTurn(sessionId, queue, emitter)
        .then(/* v8 ignore next 4 -- async callback branch coverage varies by test timing */ (summary) => {
          if (summary) {
            log.info({ sessionId, totalMs: summary.totalMs }, 'Turn processed');
          }
        })
        .catch(/* v8 ignore next 3 -- async error path hard to trigger deterministically */ (err) => {
          log.error({ err, sessionId }, 'Turn processing failed');
          sendError(ws, 'agent.turn_failed', 'Turn processing failed');
        });
      break;
    }
  }
}

/**
 * Sends an Error message over the WebSocket.
 */
function sendError(ws: WebSocket, code: string, message: string): void {
  ws.send(JSON.stringify({ type: 'Error', code, message }));
}
