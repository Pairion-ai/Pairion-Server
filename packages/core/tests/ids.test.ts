import { describe, it, expect } from 'vitest';
import { createId } from '../src/index.js';
import type { UserId, SessionId } from '../src/index.js';

describe('branded identifiers', () => {
  it('creates a branded id from a string', () => {
    const id = createId<UserId>('user-123');
    expect(id).toBe('user-123');
  });

  it('preserves string semantics at runtime', () => {
    const userId = createId<UserId>('u1');
    const sessionId = createId<SessionId>('s1');
    expect(typeof userId).toBe('string');
    expect(typeof sessionId).toBe('string');
    expect(userId).not.toBe(sessionId);
  });
});
