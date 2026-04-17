import { describe, it, expect } from 'vitest';
import type {
  LLMProvider,
  TTSProvider,
  STTProvider,
  WakeWordProvider,
  VoiceIdProvider,
  EmbeddingProvider,
  VectorStore,
  CapabilityDescriptor,
} from '../src/index.js';

describe('adapter interface contract tests', () => {
  it('LLMProvider shape is correct', () => {
    const mock: LLMProvider = {
      providerId: 'test',
      capabilities: [],
      generate: async () => (async function* () { yield 'token'; })(),
      chat: async function* () { yield { type: 'text_delta' as const, text: 'hello' }; },
    };
    expect(mock.providerId).toBe('test');
    expect(typeof mock.generate).toBe('function');
    expect(typeof mock.chat).toBe('function');
  });

  it('TTSProvider shape is correct', () => {
    const mock: TTSProvider = {
      providerId: 'test',
      capabilities: [],
      synthesize: async () => (async function* () { yield new Uint8Array(); })(),
    };
    expect(mock.providerId).toBe('test');
    expect(typeof mock.synthesize).toBe('function');
  });

  it('STTProvider shape is correct', () => {
    const mock: STTProvider = {
      providerId: 'test',
      capabilities: [],
      transcribe: async function* () { yield { isFinal: true, text: 'hello' }; },
    };
    expect(typeof mock.transcribe).toBe('function');
  });

  it('WakeWordProvider shape is correct', () => {
    const mock: WakeWordProvider = {
      providerId: 'test',
      capabilities: [],
      getModelPath: () => '/path/to/model',
      getThreshold: () => 0.5,
    };
    expect(mock.getModelPath()).toBe('/path/to/model');
    expect(mock.getThreshold()).toBe(0.5);
  });

  it('VoiceIdProvider shape is correct', () => {
    const mock: VoiceIdProvider = {
      providerId: 'test',
      capabilities: [],
      enroll: async () => new Uint8Array(),
      identify: async () => [{ userId: 'u1', confidence: 0.9 }],
    };
    expect(typeof mock.enroll).toBe('function');
    expect(typeof mock.identify).toBe('function');
  });

  it('EmbeddingProvider shape is correct', () => {
    const mock: EmbeddingProvider = {
      providerId: 'test',
      capabilities: [],
      embed: async () => [0.1, 0.2, 0.3],
      dimensions: 3,
    };
    expect(mock.dimensions).toBe(3);
  });

  it('VectorStore shape is correct', () => {
    const mock: VectorStore = {
      providerId: 'test',
      capabilities: [],
      upsert: async () => {},
      query: async () => [{ id: 'doc1', score: 0.9, metadata: {} }],
      delete: async () => {},
    };
    expect(typeof mock.upsert).toBe('function');
    expect(typeof mock.query).toBe('function');
    expect(typeof mock.delete).toBe('function');
  });

  it('CapabilityDescriptor shape is correct', () => {
    const cap: CapabilityDescriptor = { id: 'streaming', label: 'Streaming output' };
    expect(cap.id).toBe('streaming');
    expect(cap.label).toBe('Streaming output');
  });
});
