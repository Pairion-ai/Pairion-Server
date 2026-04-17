import { describe, it, expect } from 'vitest';
import { DEFAULT_HOUSEHOLD } from '../src/index.js';
import type { UserRole, Household } from '../src/index.js';

describe('@pairion/household', () => {
  it('exports UserRole type', () => {
    const role: UserRole = 'owner';
    expect(role).toBe('owner');
  });

  it('exports default household config', () => {
    expect(DEFAULT_HOUSEHOLD.name).toBe('My Household');
    expect(DEFAULT_HOUSEHOLD.timezone).toBe('America/New_York');
  });

  it('Household interface is structurally valid', () => {
    const h: Household = { name: 'Test', timezone: 'UTC' };
    expect(h.name).toBe('Test');
  });
});
