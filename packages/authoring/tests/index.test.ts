import { describe, it, expect } from 'vitest';
import type { AuthoringState, AuthoringIntent } from '../src/index.js';

describe('@pairion/authoring', () => {
  it('exports AuthoringState type', () => {
    const state: AuthoringState = 'clarifying';
    expect(state).toBe('clarifying');
  });

  it('exports AuthoringIntent type', () => {
    const intent: AuthoringIntent = 'clarifying-question';
    expect(intent).toBe('clarifying-question');
  });
});
