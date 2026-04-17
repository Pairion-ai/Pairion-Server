import { describe, it, expect } from 'vitest';
import { AdapterRegistry } from '../src/index.js';
import type { LLMProvider, LLMGenerateOptions } from '../src/index.js';

function createMockLLM(id: string): LLMProvider {
  return {
    providerId: id,
    capabilities: [{ id: 'text-gen', label: 'Text Generation' }],
    generate: async (_prompt: string, _options?: LLMGenerateOptions) =>
      (async function* () { yield 'hello'; })(),
    chat: async function* () { yield { type: 'done' as const }; },
  };
}

describe('AdapterRegistry', () => {
  it('registers and retrieves an adapter', () => {
    const registry = new AdapterRegistry();
    const mock = createMockLLM('test-llm');
    registry.register('llm', mock);
    expect(registry.get('llm', 'test-llm')).toBe(mock);
  });

  it('returns undefined for unregistered adapter', () => {
    const registry = new AdapterRegistry();
    expect(registry.get('llm', 'nonexistent')).toBeUndefined();
  });

  it('returns undefined for unregistered kind', () => {
    const registry = new AdapterRegistry();
    expect(registry.get('tts', 'any')).toBeUndefined();
  });

  it('lists adapters by kind', () => {
    const registry = new AdapterRegistry();
    const mock1 = createMockLLM('llm-a');
    const mock2 = createMockLLM('llm-b');
    registry.register('llm', mock1);
    registry.register('llm', mock2);
    expect(registry.list('llm')).toHaveLength(2);
  });

  it('returns empty array for unregistered kind', () => {
    const registry = new AdapterRegistry();
    expect(registry.list('stt')).toEqual([]);
  });

  it('lists all registered adapters', () => {
    const registry = new AdapterRegistry();
    registry.register('llm', createMockLLM('a'));
    registry.register('tts', {
      providerId: 'tts-test',
      capabilities: [],
      synthesize: async () => (async function* () { yield new Uint8Array(); })(),
    });
    const all = registry.listAll();
    expect(all.size).toBe(2);
    expect(all.get('llm')).toHaveLength(1);
    expect(all.get('tts')).toHaveLength(1);
  });
});
