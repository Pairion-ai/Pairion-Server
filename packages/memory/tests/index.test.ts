import { describe, it, expect } from 'vitest';
import type { MemoryStore, MemoryHit } from '../src/index.js';

describe('@pairion/memory', () => {
  it('exports MemoryStore type', () => {
    const store: MemoryStore = 'episodic';
    expect(store).toBe('episodic');
  });

  it('MemoryHit is structurally valid', () => {
    const hit: MemoryHit = { store: 'semantic', score: 0.95, summary: 'test' };
    expect(hit.score).toBe(0.95);
  });
});
