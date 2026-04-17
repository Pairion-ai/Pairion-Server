import { describe, it, expect } from 'vitest';
import type { ActionKind, ActionStatus } from '../src/index.js';

describe('@pairion/actions', () => {
  it('exports ActionKind type', () => {
    const kind: ActionKind = 'click';
    expect(kind).toBe('click');
  });

  it('exports ActionStatus type', () => {
    const status: ActionStatus = 'pending';
    expect(status).toBe('pending');
  });
});
