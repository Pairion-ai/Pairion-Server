import { describe, it, expect, beforeEach } from 'vitest';
import {
  DEFAULT_AGENT_CONFIG, SessionManager, processTurn, loadSystemPrompt,
} from '../src/index.js';
import type { AgentState, AgentConfig, SessionEmitter } from '../src/index.js';
import { EventBus } from '@pairion/core';
import { AdapterRegistry } from '@pairion/adapters';
import { SkillRegistry, SkillInvoker } from '@pairion/skills';

function createMockEmitter(): SessionEmitter & { calls: Array<{ method: string; args: unknown[] }> } {
  const calls: Array<{ method: string; args: unknown[] }> = [];
  const record = (method: string) => (...args: unknown[]) => { calls.push({ method, args }); };
  return {
    calls,
    sendAgentState: record('sendAgentState'),
    sendTranscript: record('sendTranscript'),
    sendLlmToken: record('sendLlmToken'),
    sendToolCallStarted: record('sendToolCallStarted'),
    sendToolCallCompleted: record('sendToolCallCompleted'),
    sendAudioStreamStart: record('sendAudioStreamStart'),
    sendAudioChunk: record('sendAudioChunk'),
    sendAudioStreamEnd: record('sendAudioStreamEnd'),
    sendSessionOpened: record('sendSessionOpened'),
    sendSessionClosed: record('sendSessionClosed'),
  };
}

function createMockAdapters() {
  return {
    llm: {
      providerId: 'anthropic',
      capabilities: [],
      generate: async () => (async function* () { yield 'Hello'; })(),
      chat: async function* () {
        yield { type: 'text_delta' as const, text: 'The weather is sunny.' };
        yield { type: 'done' as const };
      },
    },
    stt: {
      providerId: 'whisper-mlx',
      capabilities: [],
      transcribe: async function* () {
        yield { isFinal: false, text: 'What is the', confidence: 0.8 };
        yield { isFinal: true, text: 'What is the weather?', confidence: 0.95 };
      },
    },
    tts: {
      providerId: 'kokoro-mlx',
      capabilities: [],
      synthesize: async () => (async function* () {
        yield new Uint8Array([1, 2, 3]);
        yield new Uint8Array([4, 5, 6]);
      })(),
    },
  };
}

describe('AgentConfig', () => {
  it('exports default agent config', () => {
    expect(DEFAULT_AGENT_CONFIG.underBreathDelayMs).toBe(2000);
    expect(DEFAULT_AGENT_CONFIG.sessionTimeoutMs).toBe(300_000);
  });

  it('AgentState type is valid', () => {
    const state: AgentState = 'idle';
    expect(state).toBe('idle');
  });

  it('AgentConfig is structurally valid', () => {
    const config: AgentConfig = { underBreathDelayMs: 1000, sessionTimeoutMs: 60000 };
    expect(config.underBreathDelayMs).toBe(1000);
  });
});

describe('loadSystemPrompt', () => {
  it('loads a system prompt from file', () => {
    const prompt = loadSystemPrompt();
    expect(prompt.length).toBeGreaterThan(0);
    expect(prompt).toContain('Pairion');
  });

  it('contains the expected persona content', () => {
    const prompt = loadSystemPrompt();
    expect(prompt).toContain('weather');
  });
});

describe('SessionManager', () => {
  let manager: SessionManager;
  let emitter: ReturnType<typeof createMockEmitter>;
  let registry: AdapterRegistry;

  beforeEach(() => {
    registry = new AdapterRegistry();
    const mocks = createMockAdapters();
    registry.register('llm', mocks.llm);
    registry.register('stt', mocks.stt);
    registry.register('tts', mocks.tts);

    const skillRegistry = new SkillRegistry();
    const skillInvoker = new SkillInvoker(skillRegistry);
    const eventBus = new EventBus();

    manager = new SessionManager({
      registry,
      skillRegistry,
      skillInvoker,
      eventBus,
      config: DEFAULT_AGENT_CONFIG,
      systemPrompt: 'You are Pairion.',
    });

    emitter = createMockEmitter();
  });

  it('creates a session', () => {
    const sessionId = manager.createSession('dev-1', emitter);
    expect(sessionId).toBeDefined();
    expect(manager.activeCount).toBe(1);
    expect(emitter.calls.some((c) => c.method === 'sendSessionOpened')).toBe(true);
  });

  it('gets a session', () => {
    const sessionId = manager.createSession('dev-1', emitter);
    const session = manager.getSession(sessionId);
    expect(session?.id).toBe(sessionId);
    expect(session?.deviceId).toBe('dev-1');
  });

  it('closes a session', () => {
    const sessionId = manager.createSession('dev-1', emitter);
    manager.closeSession(sessionId);
    expect(manager.activeCount).toBe(0);
    expect(manager.getSession(sessionId)).toBeUndefined();
  });

  it('closes a nonexistent session without error', () => {
    expect(() => manager.closeSession('nonexistent')).not.toThrow();
  });

  it('processes a turn', async () => {
    const sessionId = manager.createSession('dev-1', emitter);
    async function* audio() { yield new Uint8Array([0, 0]); }

    const summary = await manager.processTurn(sessionId, audio(), emitter);
    expect(summary).toBeDefined();
    expect(summary?.sttLatencyMs).toBeGreaterThanOrEqual(0);
  });

  it('returns undefined for turn on missing session', async () => {
    async function* audio() { yield new Uint8Array([0]); }
    const summary = await manager.processTurn('missing', audio(), emitter);
    expect(summary).toBeUndefined();
  });

  it('returns undefined when adapters are not registered', async () => {
    const m = new SessionManager({
      registry: new AdapterRegistry(),
      skillRegistry: new SkillRegistry(),
      skillInvoker: new SkillInvoker(new SkillRegistry()),
      eventBus: new EventBus(),
      config: DEFAULT_AGENT_CONFIG,
      systemPrompt: 'test',
    });
    const sessionId = m.createSession('dev-1', emitter);
    async function* audio() { yield new Uint8Array([0]); }
    const summary = await m.processTurn(sessionId, audio(), emitter);
    expect(summary).toBeUndefined();
  });
});

describe('processTurn', () => {
  it('runs the full STT → LLM → TTS pipeline', async () => {
    const emitter = createMockEmitter();
    const mocks = createMockAdapters();
    const skillRegistry = new SkillRegistry();

    async function* audio() { yield new Uint8Array([0, 0]); }

    const summary = await processTurn({
      sessionId: 'test-session',
      audio: audio(),
      llm: mocks.llm,
      stt: mocks.stt,
      tts: mocks.tts,
      skillInvoker: new SkillInvoker(skillRegistry),
      skillRegistry,
      emitter,
      systemPrompt: 'You are Pairion.',
      conversationHistory: [],
      config: DEFAULT_AGENT_CONFIG,
    });

    expect(summary.sessionId).toBe('test-session');
    expect(summary.transcriptLength).toBeGreaterThan(0);
    expect(summary.responseLength).toBeGreaterThan(0);

    const methods = emitter.calls.map((c) => c.method);
    expect(methods).toContain('sendAgentState');
    expect(methods).toContain('sendTranscript');
    expect(methods).toContain('sendLlmToken');
    expect(methods).toContain('sendAudioStreamStart');
    expect(methods).toContain('sendAudioChunk');
    expect(methods).toContain('sendAudioStreamEnd');
  });

  it('handles empty transcript', async () => {
    const emitter = createMockEmitter();
    const mocks = createMockAdapters();
    mocks.stt.transcribe = async function* () {
      yield { isFinal: true, text: '', confidence: 0 };
    };

    async function* audio() { yield new Uint8Array([0]); }

    const summary = await processTurn({
      sessionId: 's1',
      audio: audio(),
      llm: mocks.llm,
      stt: mocks.stt,
      tts: mocks.tts,
      skillInvoker: new SkillInvoker(new SkillRegistry()),
      skillRegistry: new SkillRegistry(),
      emitter,
      systemPrompt: 'test',
      conversationHistory: [],
      config: DEFAULT_AGENT_CONFIG,
    });

    expect(summary.transcriptLength).toBe(0);
    expect(summary.responseLength).toBe(0);
  });

  it('handles tool calls in the LLM response', async () => {
    const emitter = createMockEmitter();
    const mocks = createMockAdapters();

    let callCount = 0;
    mocks.llm.chat = async function* () {
      callCount++;
      if (callCount === 1) {
        yield { type: 'tool_call_start' as const, toolCall: { id: 'c1', name: 'get_weather', arguments: '' } };
        yield { type: 'tool_call_delta' as const, toolCall: { id: 'c1', name: 'get_weather', arguments: '{"location":' } };
        yield { type: 'tool_call_end' as const, toolCall: { id: 'c1', name: 'get_weather', arguments: '{"location":"SF"}' } };
        yield { type: 'done' as const };
      } else {
        yield { type: 'text_delta' as const, text: 'Sunny in SF!' };
        yield { type: 'done' as const };
      }
    };

    const skillRegistry = new SkillRegistry();
    skillRegistry.register({
      name: 'get_weather',
      description: 'Weather',
      inputSchema: {},
      invoke: async () => 'Sunny, 72F',
    });

    async function* audio() { yield new Uint8Array([0]); }

    const summary = await processTurn({
      sessionId: 's1',
      audio: audio(),
      llm: mocks.llm,
      stt: mocks.stt,
      tts: mocks.tts,
      skillInvoker: new SkillInvoker(skillRegistry),
      skillRegistry,
      emitter,
      systemPrompt: 'test',
      conversationHistory: [],
      config: DEFAULT_AGENT_CONFIG,
    });

    expect(summary.toolCallCount).toBe(1);
    expect(emitter.calls.filter((c) => c.method === 'sendToolCallStarted').length).toBe(1);
    expect(emitter.calls.filter((c) => c.method === 'sendToolCallCompleted').length).toBe(1);
  });

  it('handles empty LLM response (no TTS phase)', async () => {
    const emitter = createMockEmitter();
    const mocks = createMockAdapters();
    mocks.llm.chat = async function* () {
      yield { type: 'text_delta' as const, text: '   ' };
      yield { type: 'done' as const };
    };

    async function* audio() { yield new Uint8Array([0]); }

    const summary = await processTurn({
      sessionId: 's1',
      audio: audio(),
      llm: mocks.llm,
      stt: mocks.stt,
      tts: mocks.tts,
      skillInvoker: new SkillInvoker(new SkillRegistry()),
      skillRegistry: new SkillRegistry(),
      emitter,
      systemPrompt: 'test',
      conversationHistory: [],
      config: DEFAULT_AGENT_CONFIG,
    });

    // Should not have any TTS events
    const audioMethods = emitter.calls.filter((c) => c.method === 'sendAudioStreamStart');
    expect(audioMethods.length).toBe(0);
    expect(summary.ttsTotalMs).toBe(0);
  });

  it('handles tool calls with invalid JSON arguments', async () => {
    const emitter = createMockEmitter();
    const mocks = createMockAdapters();

    let callCount = 0;
    mocks.llm.chat = async function* () {
      callCount++;
      if (callCount === 1) {
        yield { type: 'tool_call_start' as const, toolCall: { id: 'c1', name: 'echo', arguments: '' } };
        yield { type: 'tool_call_end' as const, toolCall: { id: 'c1', name: 'echo', arguments: 'not valid json{{{' } };
        yield { type: 'done' as const };
      } else {
        yield { type: 'text_delta' as const, text: 'Done.' };
        yield { type: 'done' as const };
      }
    };

    const skillRegistry = new SkillRegistry();
    skillRegistry.register({
      name: 'echo',
      description: 'Echo',
      inputSchema: {},
      invoke: async () => 'echoed',
    });

    async function* audio() { yield new Uint8Array([0]); }

    const summary = await processTurn({
      sessionId: 's1',
      audio: audio(),
      llm: mocks.llm,
      stt: mocks.stt,
      tts: mocks.tts,
      skillInvoker: new SkillInvoker(skillRegistry),
      skillRegistry,
      emitter,
      systemPrompt: 'test',
      conversationHistory: [],
      config: DEFAULT_AGENT_CONFIG,
    });

    expect(summary.toolCallCount).toBe(1);
  });
});
