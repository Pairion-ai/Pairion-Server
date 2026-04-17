/**
 * WebSocket handler for the AsyncAPI streaming protocol.
 *
 * @remarks
 * Single bidirectional WebSocket per paired Device or Node at `/ws/v1`.
 * All messages carry a `type` discriminator field. In M0:
 * - `DeviceIdentify` / `NodeIdentify` → `IdentifyAck`
 * - `HeartbeatPing` → `HeartbeatPong`
 * - `LogForward` → processed by @pairion/logs
 * - Any other type → `Error` with `server.not_implemented`
 */

import { WebSocketServer, type WebSocket } from 'ws';
import type { Server as HttpServer } from 'node:http';
import { createSubsystemLogger } from '@pairion/core';
import { processLogForward } from '@pairion/logs';

const log = createSubsystemLogger('websocket');

/** Known AsyncAPI message types for dispatch. */
const IMPLEMENTED_TYPES = new Set(['DeviceIdentify', 'NodeIdentify', 'HeartbeatPing', 'LogForward']);

/**
 * Sets up the WebSocket server on the given HTTP server.
 *
 * @param server - The Node.js HTTP server.
 * @param devToken - The dev bearer token for authentication.
 * @returns The WebSocketServer instance.
 */
export function setupWebSocket(server: HttpServer, devToken: string): WebSocketServer {
  const wss = new WebSocketServer({ server, path: '/ws/v1' });

  wss.on('connection', (ws: WebSocket) => {
    let identified = false;

    ws.on('message', (data: Buffer | ArrayBuffer | Buffer[]) => {
      /* v8 ignore next 4 -- ws library always delivers Buffer in Node.js; ArrayBuffer/Buffer[] are theoretical */
      const raw = Buffer.isBuffer(data)
        ? data.toString('utf-8')
        : Array.isArray(data)
          ? Buffer.concat(data).toString('utf-8')
          : Buffer.from(data).toString('utf-8');

      let msg: Record<string, unknown>;
      try {
        msg = JSON.parse(raw) as Record<string, unknown>;
      } catch {
        sendError(ws, 'protocol.invalid_json', 'Invalid JSON');
        return;
      }

      const type = msg['type'] as string | undefined;
      if (!type) {
        sendError(ws, 'protocol.missing_type', 'Message must include a "type" field');
        return;
      }

      if (!identified && type !== 'DeviceIdentify' && type !== 'NodeIdentify') {
        sendError(ws, 'protocol.not_identified', 'Must send DeviceIdentify or NodeIdentify first');
        return;
      }

      if (!IMPLEMENTED_TYPES.has(type)) {
        sendError(ws, 'server.not_implemented', `Message type "${type}" is not implemented`);
        return;
      }

      handleMessage(ws, type, msg, devToken, () => { identified = true; });
    });

    ws.on('error', /* v8 ignore next */ (err) => {
      log.error({ err }, 'WebSocket error');
    });
  });

  return wss;
}

/**
 * Handles a parsed WebSocket message by type.
 *
 * @param ws - The WebSocket connection.
 * @param type - The message type discriminator.
 * @param msg - The parsed message payload.
 * @param devToken - The expected bearer token.
 * @param onIdentified - Callback to mark the connection as identified.
 */
function handleMessage(
  ws: WebSocket,
  type: string,
  msg: Record<string, unknown>,
  devToken: string,
  onIdentified: () => void,
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
      onIdentified();
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
  }
}

/**
 * Sends an Error message over the WebSocket.
 *
 * @param ws - The WebSocket connection.
 * @param code - Machine-readable error code.
 * @param message - Human-readable error message.
 */
function sendError(ws: WebSocket, code: string, message: string): void {
  ws.send(JSON.stringify({ type: 'Error', code, message }));
}
