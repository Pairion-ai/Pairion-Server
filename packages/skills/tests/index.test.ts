import { describe, it, expect, vi } from 'vitest';
import { SkillRegistry, SkillInvoker, weatherSkill, getWeather } from '../src/index.js';
import type { SkillDefinition, SkillSource, SkillSourceKind } from '../src/index.js';

describe('SkillRegistry', () => {
  it('registers and retrieves a skill', () => {
    const registry = new SkillRegistry();
    const skill: SkillDefinition = {
      name: 'test_skill',
      description: 'A test skill',
      inputSchema: { type: 'object', properties: {} },
      invoke: async () => 'result',
    };
    registry.register(skill);
    expect(registry.get('test_skill')).toBe(skill);
  });

  it('returns undefined for unregistered skill', () => {
    const registry = new SkillRegistry();
    expect(registry.get('missing')).toBeUndefined();
  });

  it('lists tool definitions for LLM', () => {
    const registry = new SkillRegistry();
    registry.register(weatherSkill);
    const tools = registry.listToolDefinitions();
    expect(tools).toHaveLength(1);
    expect(tools[0]?.name).toBe('get_current_weather');
    expect(tools[0]?.inputSchema).toBeDefined();
  });

  it('reports correct size', () => {
    const registry = new SkillRegistry();
    expect(registry.size).toBe(0);
    registry.register(weatherSkill);
    expect(registry.size).toBe(1);
  });
});

describe('SkillInvoker', () => {
  it('dispatches to registered skill', async () => {
    const registry = new SkillRegistry();
    registry.register({
      name: 'echo',
      description: 'Echoes input',
      inputSchema: {},
      invoke: async (args) => `Echo: ${args['text']}`,
    });
    const invoker = new SkillInvoker(registry);
    const result = await invoker.invoke('echo', { text: 'hello' });
    expect(result).toBe('Echo: hello');
  });

  it('returns error for unknown tool', async () => {
    const registry = new SkillRegistry();
    const invoker = new SkillInvoker(registry);
    const result = await invoker.invoke('missing_tool', {});
    expect(result).toContain('Unknown tool');
  });

  it('handles skill invocation errors', async () => {
    const registry = new SkillRegistry();
    registry.register({
      name: 'failing',
      description: 'Always fails',
      inputSchema: {},
      invoke: async () => { throw new Error('skill broke'); },
    });
    const invoker = new SkillInvoker(registry);
    const result = await invoker.invoke('failing', {});
    expect(result).toContain('Error invoking failing');
  });
});

describe('weatherSkill', () => {
  it('has correct name and schema', () => {
    expect(weatherSkill.name).toBe('get_current_weather');
    expect(weatherSkill.inputSchema).toBeDefined();
  });

  it('invoke delegates to getWeather', async () => {
    // Mock global fetch for this test
    const originalFetch = globalThis.fetch;
    globalThis.fetch = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          results: [{ latitude: 0, longitude: 0, name: 'Test', country: 'XX' }],
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          current_weather: { temperature: 70, weathercode: 0, windspeed: 5 },
        }),
      }) as unknown as typeof fetch;

    try {
      const result = await weatherSkill.invoke({ location: 'Test' });
      expect(result).toContain('70°F');
    } finally {
      globalThis.fetch = originalFetch;
    }
  });
});

describe('getWeather', () => {
  it('returns formatted weather for a valid location', async () => {
    const mockFetch = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          results: [{ latitude: 37.77, longitude: -122.42, name: 'San Francisco', country: 'US' }],
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          current_weather: { temperature: 72, weathercode: 0, windspeed: 12 },
        }),
      });

    const result = await getWeather({ location: 'San Francisco' }, mockFetch as unknown as typeof fetch);
    expect(result).toContain('72°F');
    expect(result).toContain('clear sky');
    expect(result).toContain('San Francisco');
  });

  it('handles unknown location', async () => {
    const mockFetch = vi.fn().mockResolvedValueOnce({
      ok: true,
      json: async () => ({ results: [] }),
    });

    const result = await getWeather({ location: 'Nonexistent' }, mockFetch as unknown as typeof fetch);
    expect(result).toContain('Could not find');
  });

  it('handles geocoding API failure', async () => {
    const mockFetch = vi.fn().mockResolvedValueOnce({ ok: false });
    const result = await getWeather({ location: 'Bad' }, mockFetch as unknown as typeof fetch);
    expect(result).toContain('Could not find');
  });

  it('handles weather API failure', async () => {
    const mockFetch = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          results: [{ latitude: 0, longitude: 0, name: 'Test', country: 'XX' }],
        }),
      })
      .mockResolvedValueOnce({ ok: false });

    const result = await getWeather({ location: 'Test' }, mockFetch as unknown as typeof fetch);
    expect(result).toContain('Could not fetch');
  });

  it('supports metric units', async () => {
    const mockFetch = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          results: [{ latitude: 51.5, longitude: -0.12, name: 'London', country: 'GB' }],
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          current_weather: { temperature: 15, weathercode: 2, windspeed: 20 },
        }),
      });

    const result = await getWeather({ location: 'London', units: 'metric' }, mockFetch as unknown as typeof fetch);
    expect(result).toContain('15°C');
    expect(result).toContain('partly cloudy');
  });

  it('handles unknown weather code', async () => {
    const mockFetch = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          results: [{ latitude: 0, longitude: 0, name: 'Test', country: 'XX' }],
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          current_weather: { temperature: 20, weathercode: 999, windspeed: 5 },
        }),
      });

    const result = await getWeather({ location: 'Test' }, mockFetch as unknown as typeof fetch);
    expect(result).toContain('unknown conditions');
  });
});

describe('SkillSource types', () => {
  it('SkillSourceKind values are valid', () => {
    const kind: SkillSourceKind = 'bundled';
    expect(kind).toBe('bundled');
  });

  it('SkillSource interface is valid', () => {
    const source: SkillSource = { kind: 'mcp-url', url: 'http://example.com' };
    expect(source.kind).toBe('mcp-url');
  });
});
