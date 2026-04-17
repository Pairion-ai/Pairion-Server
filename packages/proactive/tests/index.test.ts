import { describe, it, expect } from 'vitest';
import type { ProactiveTriggerKind, ProactiveActionKind } from '../src/index.js';

describe('@pairion/proactive', () => {
  it('exports ProactiveTriggerKind type', () => {
    const kind: ProactiveTriggerKind = 'calendar';
    expect(kind).toBe('calendar');
  });

  it('exports ProactiveActionKind type', () => {
    const kind: ProactiveActionKind = 'speak';
    expect(kind).toBe('speak');
  });
});
