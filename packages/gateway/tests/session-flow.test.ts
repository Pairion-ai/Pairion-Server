import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { createServer } from '../src/server.js';
import type { ServerInstance } from '../src/server.js';
import { WebSocketSessionEmitter } from '../src/ws-session-emitter.js';
import { SessionManager, DEFAULT_AGENT_CONFIG } from '@pairion/agent';
import { AdapterRegistry } from '@pairion/adapters';
import { SkillRegistry, SkillInvoker } from '@pairion/skills';
import { EventBus } from '@pairion/core';
import WebSocket from 'ws';

const DEV_TOKEN = 'session-test-token';
const PORT = 19891;

function createMockAdapters() {
  return {
    llm: {
      providerId: 'anthropic',
      capabilities: [],
      generate: async () => (async function* () { yield 'test'; })(),
      chat: async function* () {
        yield { type: 'text_delta' as const, text: 'It is sunny today.' };
        yield { type: 'done' as const };
      },
    },
    stt: {
      providerId: 'whisper-mlx',
      capabilities: [],
      transcribe: async function* () {
        yield { isFinal: false, text: 'What', confidence: 0.8 };
        yield { isFinal: true, text: 'What is the weather?', confidence: 0.95 };
      },
    },
    tts: {
      providerId: 'kokoro-mlx',
      capabilities: [],
      synthesize: async () => (async function* () {
        yield new Uint8Array([1, 2, 3, 4]);
      })(),
    },
  };
}

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

function collectMessages(ws: WebSocket, count: number, timeoutMs: number = 5000): Promise<Array<Record<string, unknown> | Buffer>> {
  return new Promise((resolve) => {
    const messages: Array<Record<string, unknown> | Buffer> = [];
    const handler = (data: Buffer) => {
      try {
        messages.push(JSON.parse(data.toString()) as Record<string, unknown>);
      } catch {
        messages.push(data);
      }
      if (messages.length >= count) {
        ws.off('message', handler);
        resolve(messages);
      }
    };
    ws.on('message', handler);
    setTimeout(() => {
      ws.off('message', handler);
      resolve(messages);
    }, timeoutMs);
  });
}

describe('Session flow via WebSocket', () => {
  let server: ServerInstance;

  beforeAll(async () => {
    const registry = new AdapterRegistry();
    const mocks = createMockAdapters();
    registry.register('llm', mocks.llm);
    registry.register('stt', mocks.stt);
    registry.register('tts', mocks.tts);

    const skillRegistry = new SkillRegistry();
    const skillInvoker = new SkillInvoker(skillRegistry);
    const eventBus = new EventBus();

    const sessionManager = new SessionManager({
      registry,
      skillRegistry,
      skillInvoker,
      eventBus,
      config: DEFAULT_AGENT_CONFIG,
      systemPrompt: 'You are Pairion.',
    });

    server = createServer({ devToken: DEV_TOKEN, port: PORT, host: '127.0.0.1', sessionManager });
    await server.app.listen({ port: PORT, host: '127.0.0.1' });
  });

  afterAll(async () => {
    server.wss.close();
    await server.app.close();
  });

  it('WakeWordDetected creates a session and sends SessionOpened', async () => {
    const ws = await connectWs();
    try {
      // Identify first
      await sendAndReceive(ws, {
        type: 'DeviceIdentify',
        deviceId: 'dev-1',
        token: DEV_TOKEN,
        clientVersion: '1.0.0',
        timestamp: new Date().toISOString(),
      });

      // Send WakeWordDetected — should get SessionOpened back
      const sessionOpened = await sendAndReceive(ws, {
        type: 'WakeWordDetected',
        confidence: 0.95,
        timestamp: new Date().toISOString(),
      });
      expect(sessionOpened['type']).toBe('SessionOpened');
      expect(sessionOpened['sessionId']).toBeDefined();
      expect(sessionOpened['userId']).toBe('__dev_user__');
    } finally {
      ws.close();
    }
  });

  it('Full turn: WakeWordDetected → audio → SpeechEnded → response', async () => {
    const ws = await connectWs();
    try {
      // Identify
      await sendAndReceive(ws, {
        type: 'DeviceIdentify',
        deviceId: 'dev-2',
        token: DEV_TOKEN,
        clientVersion: '1.0.0',
        timestamp: new Date().toISOString(),
      });

      // Wake word — get SessionOpened
      const sessionOpened = await sendAndReceive(ws, {
        type: 'WakeWordDetected',
        confidence: 0.95,
        timestamp: new Date().toISOString(),
      });
      expect(sessionOpened['type']).toBe('SessionOpened');

      // Send AudioStreamStart
      ws.send(JSON.stringify({
        type: 'AudioStreamStart',
        sessionId: sessionOpened['sessionId'],
        streamId: 'stream-1',
        direction: 'in',
        codec: 'pcm-s16le',
        sampleRate: 16000,
        channels: 1,
        timestamp: new Date().toISOString(),
      }));

      // Send some audio data
      ws.send(JSON.stringify({ type: 'AudioChunkIn', data: 'AAAA' }));

      // Send SpeechEnded — this triggers the turn loop
      // Collect all the response messages
      const responseCollect = collectMessages(ws, 10, 3000);

      ws.send(JSON.stringify({
        type: 'SpeechEnded',
        sessionId: sessionOpened['sessionId'],
        streamId: 'stream-1',
        timestamp: new Date().toISOString(),
      }));

      const responses = await responseCollect;
      const jsonResponses = responses.filter((r): r is Record<string, unknown> => typeof r === 'object' && !Buffer.isBuffer(r));
      const types = jsonResponses.map((r) => r['type'] as string);

      // Should contain agent state changes, transcript events, LLM tokens, and audio
      expect(types).toContain('AgentStateChange');
      expect(types.some((t) => t === 'TranscriptPartial' || t === 'TranscriptFinal')).toBe(true);
    } finally {
      ws.close();
    }
  });

  it('WakeWordDetected without agent returns no_agent error when server has no SessionManager', async () => {
    // Create a server without SessionManager
    const noAgentServer = createServer({ devToken: DEV_TOKEN, port: PORT + 1, host: '127.0.0.1' });
    await noAgentServer.app.listen({ port: PORT + 1, host: '127.0.0.1' });

    try {
      const ws = new WebSocket(`ws://127.0.0.1:${PORT + 1}/ws/v1`);
      await new Promise<void>((resolve) => ws.on('open', resolve));

      await sendAndReceive(ws, {
        type: 'DeviceIdentify',
        deviceId: 'dev-1',
        token: DEV_TOKEN,
        clientVersion: '1.0.0',
        timestamp: new Date().toISOString(),
      });

      const err = await sendAndReceive(ws, {
        type: 'WakeWordDetected',
        confidence: 0.95,
        timestamp: new Date().toISOString(),
      });
      expect(err['type']).toBe('Error');
      expect(err['code']).toBe('server.no_agent');

      ws.close();
    } finally {
      noAgentServer.wss.close();
      await noAgentServer.app.close();
    }
  });

  it('SpeechEnded without active session is ignored', async () => {
    const ws = await connectWs();
    try {
      await sendAndReceive(ws, {
        type: 'DeviceIdentify',
        deviceId: 'dev-1',
        token: DEV_TOKEN,
        clientVersion: '1.0.0',
        timestamp: new Date().toISOString(),
      });

      // Send SpeechEnded without a WakeWordDetected first — should be silently ignored
      ws.send(JSON.stringify({
        type: 'SpeechEnded',
        sessionId: 'nonexistent',
        streamId: 's1',
        timestamp: new Date().toISOString(),
      }));

      // Verify connection is still alive
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

  it('Binary audio data is pushed to active queue', async () => {
    const ws = await connectWs();
    try {
      await sendAndReceive(ws, {
        type: 'DeviceIdentify',
        deviceId: 'dev-1',
        token: DEV_TOKEN,
        clientVersion: '1.0.0',
        timestamp: new Date().toISOString(),
      });

      // Create a session
      await sendAndReceive(ws, {
        type: 'WakeWordDetected',
        confidence: 0.95,
        timestamp: new Date().toISOString(),
      });

      // Send binary data directly — should be accepted as audio chunk
      ws.send(Buffer.from([0, 0, 0, 0, 0, 0]));

      await new Promise((resolve) => setTimeout(resolve, 50));

      // End the speech to trigger turn processing
      const collect = collectMessages(ws, 5, 3000);
      ws.send(JSON.stringify({
        type: 'SpeechEnded',
        sessionId: 'any',
        streamId: 's1',
        timestamp: new Date().toISOString(),
      }));

      const responses = await collect;
      expect(responses.length).toBeGreaterThan(0);
    } finally {
      ws.close();
    }
  });
});

describe('WebSocketSessionEmitter', () => {
  it('sends all event types as JSON', async () => {
    const messages: string[] = [];
    const mockWs = {
      send: (data: unknown) => {
        if (typeof data === 'string') {
          messages.push(data);
        }
      },
    } as unknown as WebSocket;

    const emitter = new WebSocketSessionEmitter(mockWs);

    emitter.sendAgentState('s1', 'thinking');
    emitter.sendTranscript('s1', 'hello', false, 0.9);
    emitter.sendTranscript('s1', 'hello world', true);
    emitter.sendLlmToken('s1', 'token', false);
    emitter.sendLlmToken('s1', '', true);
    emitter.sendToolCallStarted('s1', 'c1', 'weather', { location: 'SF' });
    emitter.sendToolCallStarted('s1', 'c2', 'calc');
    emitter.sendToolCallCompleted('s1', 'c1', true, 'Sunny', 100);
    emitter.sendToolCallCompleted('s1', 'c2', false);
    emitter.sendAudioStreamStart('s1', 'stream-1');
    emitter.sendAudioStreamEnd('s1', 'stream-1', 'normal');
    emitter.sendSessionOpened('s1', 'user-1');
    emitter.sendSessionClosed('s1', 'normal');

    expect(messages.length).toBe(13);

    const parsed = messages.map((m) => JSON.parse(m) as Record<string, unknown>);
    expect(parsed[0]?.['type']).toBe('AgentStateChange');
    expect(parsed[1]?.['type']).toBe('TranscriptPartial');
    expect(parsed[2]?.['type']).toBe('TranscriptFinal');
    expect(parsed[3]?.['type']).toBe('LlmTokenStream');
    expect(parsed[5]?.['type']).toBe('ToolCallStarted');
    expect(parsed[9]?.['type']).toBe('AudioStreamStart');
    expect(parsed[11]?.['type']).toBe('SessionOpened');
    expect(parsed[12]?.['type']).toBe('SessionClosed');
  });

  it('sends binary audio chunks', () => {
    let sentBinary = false;
    const mockWs = {
      send: (data: unknown) => {
        if (data instanceof Uint8Array) {
          sentBinary = true;
        }
      },
    } as unknown as WebSocket;

    const emitter = new WebSocketSessionEmitter(mockWs);
    emitter.sendAudioChunk(new Uint8Array([1, 2, 3]));
    expect(sentBinary).toBe(true);
  });
});
