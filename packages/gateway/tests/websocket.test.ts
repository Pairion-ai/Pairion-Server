import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { createServer } from '../src/server.js';
import type { ServerInstance } from '../src/server.js';
import WebSocket from 'ws';

const DEV_TOKEN = 'ws-test-token';
const PORT = 19789;

function connectWs(): Promise<WebSocket> {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`ws://127.0.0.1:${PORT}/ws/v1`);
    ws.on('open', () => resolve(ws));
    ws.on('error', reject);
  });
}

function sendAndReceive(ws: WebSocket, msg: Record<string, unknown>): Promise<Record<string, unknown>> {
  return new Promise((resolve) => {
    ws.once('message', (data: Buffer) => {
      resolve(JSON.parse(data.toString()) as Record<string, unknown>);
    });
    ws.send(JSON.stringify(msg));
  });
}

describe('WebSocket protocol', () => {
  let server: ServerInstance;

  beforeAll(async () => {
    server = createServer({ devToken: DEV_TOKEN, port: PORT, host: '127.0.0.1' });
    await server.app.listen({ port: PORT, host: '127.0.0.1' });
  });

  afterAll(async () => {
    server.wss.close();
    await server.app.close();
  });

  it('DeviceIdentify with valid token returns IdentifyAck accepted', async () => {
    const ws = await connectWs();
    try {
      const ack = await sendAndReceive(ws, {
        type: 'DeviceIdentify',
        deviceId: 'dev-1',
        token: DEV_TOKEN,
        clientVersion: '1.0.0',
        timestamp: new Date().toISOString(),
      });
      expect(ack['type']).toBe('IdentifyAck');
      expect(ack['accepted']).toBe(true);
      expect(ack['serverVersion']).toBe('1.0.0-alpha.1');
    } finally {
      ws.close();
    }
  });

  it('DeviceIdentify with invalid token is rejected and closed', async () => {
    const ws = await connectWs();
    const closePromise = new Promise<number>((resolve) => {
      ws.on('close', (code: number) => resolve(code));
    });
    const ack = await sendAndReceive(ws, {
      type: 'DeviceIdentify',
      deviceId: 'dev-1',
      token: 'bad-token',
      clientVersion: '1.0.0',
      timestamp: new Date().toISOString(),
    });
    expect(ack['type']).toBe('IdentifyAck');
    expect(ack['accepted']).toBe(false);
    const code = await closePromise;
    expect(code).toBe(4001);
  });

  it('HeartbeatPing returns HeartbeatPong after identify', async () => {
    const ws = await connectWs();
    try {
      // First identify
      await sendAndReceive(ws, {
        type: 'DeviceIdentify',
        deviceId: 'dev-1',
        token: DEV_TOKEN,
        clientVersion: '1.0.0',
        timestamp: new Date().toISOString(),
      });
      // Then heartbeat
      const pong = await sendAndReceive(ws, {
        type: 'HeartbeatPing',
        timestamp: new Date().toISOString(),
      });
      expect(pong['type']).toBe('HeartbeatPong');
      expect(pong['timestamp']).toBeDefined();
      expect(typeof pong['latencyMs']).toBe('number');
    } finally {
      ws.close();
    }
  });

  it('message before identify returns error', async () => {
    const ws = await connectWs();
    try {
      const err = await sendAndReceive(ws, {
        type: 'HeartbeatPing',
        timestamp: new Date().toISOString(),
      });
      expect(err['type']).toBe('Error');
      expect(err['code']).toBe('protocol.not_identified');
    } finally {
      ws.close();
    }
  });

  it('unknown message type returns Error not_implemented', async () => {
    const ws = await connectWs();
    try {
      await sendAndReceive(ws, {
        type: 'DeviceIdentify',
        deviceId: 'dev-1',
        token: DEV_TOKEN,
        clientVersion: '1.0.0',
        timestamp: new Date().toISOString(),
      });
      const err = await sendAndReceive(ws, {
        type: 'InterruptRequest',
        sessionId: '00000000-0000-0000-0000-000000000000',
        timestamp: new Date().toISOString(),
      });
      expect(err['type']).toBe('Error');
      expect(err['code']).toBe('server.not_implemented');
    } finally {
      ws.close();
    }
  });

  it('NodeIdentify with valid token returns IdentifyAck accepted', async () => {
    const ws = await connectWs();
    try {
      const ack = await sendAndReceive(ws, {
        type: 'NodeIdentify',
        nodeId: 'node-kitchen',
        token: DEV_TOKEN,
        firmwareVersion: '1.0.0',
        capabilities: {
          audioIn: true, audioOut: true, localWakeWord: true, localVad: true,
          localStt: false, localLlmSmall: false, localTtsCache: true,
          aiAccelerator: 'none', dedicatedNpuRamGb: 0,
        },
        timestamp: new Date().toISOString(),
      });
      expect(ack['type']).toBe('IdentifyAck');
      expect(ack['accepted']).toBe(true);
    } finally {
      ws.close();
    }
  });

  it('invalid JSON returns error', async () => {
    const ws = await connectWs();
    try {
      const err = await new Promise<Record<string, unknown>>((resolve) => {
        ws.once('message', (data: Buffer) => {
          resolve(JSON.parse(data.toString()) as Record<string, unknown>);
        });
        ws.send('not valid json{{{');
      });
      expect(err['type']).toBe('Error');
      expect(err['code']).toBe('protocol.invalid_json');
    } finally {
      ws.close();
    }
  });

  it('message without type field returns error', async () => {
    const ws = await connectWs();
    try {
      const err = await sendAndReceive(ws, { noType: true });
      expect(err['type']).toBe('Error');
      expect(err['code']).toBe('protocol.missing_type');
    } finally {
      ws.close();
    }
  });

  it('LogForward with minimal entries is processed', async () => {
    const ws = await connectWs();
    try {
      await sendAndReceive(ws, {
        type: 'DeviceIdentify',
        deviceId: 'dev-1',
        token: DEV_TOKEN,
        clientVersion: '1.0.0',
        timestamp: new Date().toISOString(),
      });
      // LogForward with entries missing all optional fields — exercises every ?? fallback
      ws.send(JSON.stringify({
        type: 'LogForward',
        sourceNodeId: 'node-1',
        entries: [{}],
        timestamp: new Date().toISOString(),
      }));
      await new Promise((resolve) => setTimeout(resolve, 50));
      // Also send one with all fields present
      ws.send(JSON.stringify({
        type: 'LogForward',
        sourceNodeId: 'node-1',
        entries: [{
          timestamp: new Date().toISOString(),
          level: 'warn',
          subsystem: 'test-sub',
          message: 'full entry',
          context: { key: 'val' },
          requestId: 'r1',
          sessionId: 's1',
        }],
        timestamp: new Date().toISOString(),
      }));
      await new Promise((resolve) => setTimeout(resolve, 50));
      const pong = await sendAndReceive(ws, {
        type: 'HeartbeatPing',
        timestamp: new Date().toISOString(),
      });
      expect(pong['type']).toBe('HeartbeatPong');
    } finally {
      ws.close();
    }
  });

  it('LogForward with no entries array is handled', async () => {
    const ws = await connectWs();
    try {
      await sendAndReceive(ws, {
        type: 'DeviceIdentify',
        deviceId: 'dev-1',
        token: DEV_TOKEN,
        clientVersion: '1.0.0',
        timestamp: new Date().toISOString(),
      });
      ws.send(JSON.stringify({
        type: 'LogForward',
        timestamp: new Date().toISOString(),
      }));
      await new Promise((resolve) => setTimeout(resolve, 50));
      const pong = await sendAndReceive(ws, {
        type: 'HeartbeatPing',
        timestamp: new Date().toISOString(),
      });
      expect(pong['type']).toBe('HeartbeatPong');
    } finally {
      ws.close();
    }
  });

  it('HeartbeatPing without timestamp computes latency as 0', async () => {
    const ws = await connectWs();
    try {
      await sendAndReceive(ws, {
        type: 'DeviceIdentify',
        deviceId: 'dev-1',
        token: DEV_TOKEN,
        clientVersion: '1.0.0',
        timestamp: new Date().toISOString(),
      });
      const pong = await sendAndReceive(ws, {
        type: 'HeartbeatPing',
      });
      expect(pong['type']).toBe('HeartbeatPong');
      expect(pong['latencyMs']).toBe(0);
    } finally {
      ws.close();
    }
  });

  it('LogForward is accepted after identify', async () => {
    const ws = await connectWs();
    try {
      await sendAndReceive(ws, {
        type: 'DeviceIdentify',
        deviceId: 'dev-1',
        token: DEV_TOKEN,
        clientVersion: '1.0.0',
        timestamp: new Date().toISOString(),
      });
      // LogForward does not send a response, so we just verify no error
      ws.send(JSON.stringify({
        type: 'LogForward',
        sourceDeviceId: 'dev-1',
        entries: [{
          timestamp: new Date().toISOString(),
          level: 'info',
          subsystem: 'test',
          message: 'test log',
        }],
        timestamp: new Date().toISOString(),
      }));
      // Wait briefly then send a heartbeat to confirm connection is still alive
      await new Promise((resolve) => setTimeout(resolve, 50));
      const pong = await sendAndReceive(ws, {
        type: 'HeartbeatPing',
        timestamp: new Date().toISOString(),
      });
      expect(pong['type']).toBe('HeartbeatPong');
    } finally {
      ws.close();
    }
  });
});
