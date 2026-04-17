import { describe, it, expect, vi } from 'vitest';
import { runMigrations } from '../src/index.js';
import type { Migration, MigrationState } from '../src/index.js';

describe('runMigrations', () => {
  it('runs all pending migrations in order', async () => {
    const order: string[] = [];
    const migrations: Migration[] = [
      { id: '001', up: () => { order.push('001'); } },
      { id: '002', up: () => { order.push('002'); } },
    ];
    const state: MigrationState = { applied: [] };

    const result = await runMigrations(migrations, state);
    expect(result).toEqual(['001', '002']);
    expect(order).toEqual(['001', '002']);
  });

  it('skips already-applied migrations', async () => {
    const up = vi.fn();
    const migrations: Migration[] = [
      { id: '001', up },
      { id: '002', up },
    ];
    const state: MigrationState = { applied: ['001'] };

    const result = await runMigrations(migrations, state);
    expect(result).toEqual(['002']);
    expect(up).toHaveBeenCalledOnce();
  });

  it('returns empty array when all migrations are applied', async () => {
    const migrations: Migration[] = [
      { id: '001', up: vi.fn() },
    ];
    const state: MigrationState = { applied: ['001'] };

    const result = await runMigrations(migrations, state);
    expect(result).toEqual([]);
  });

  it('supports async migration functions', async () => {
    const migrations: Migration[] = [
      { id: '001', up: async () => { await Promise.resolve(); } },
    ];
    const state: MigrationState = { applied: [] };

    const result = await runMigrations(migrations, state);
    expect(result).toEqual(['001']);
  });
});
