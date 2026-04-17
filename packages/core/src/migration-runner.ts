/**
 * Lightweight TypeScript migration runner for development.
 *
 * @remarks
 * Each package owns its own migration directory. The runner applies migrations
 * in dependency order at startup. In M0 the runner exists and is exercised by
 * tests; no migrations themselves are shipped.
 *
 * Production hardening uses Flyway (post-v1). During development, Flyway's
 * lifecycle creates delays during restart-heavy cycles, so this lightweight
 * runner is used instead.
 */

/**
 * A single migration definition.
 */
export interface Migration {
  /** Unique migration id (e.g. `001_create_users`). */
  readonly id: string;
  /** The SQL or programmatic migration to apply. */
  readonly up: () => void | Promise<void>;
  /** The rollback logic (optional in development). */
  readonly down?: () => void | Promise<void>;
}

/**
 * Tracks applied migration state.
 */
export interface MigrationState {
  /** List of migration ids that have been applied. */
  readonly applied: readonly string[];
}

/**
 * Runs pending migrations in order, skipping those already applied.
 *
 * @param migrations - Ordered list of migrations to consider.
 * @param state - Current state of applied migrations.
 * @returns The ids of newly applied migrations.
 */
export async function runMigrations(
  migrations: readonly Migration[],
  state: MigrationState,
): Promise<string[]> {
  const applied = new Set(state.applied);
  const newlyApplied: string[] = [];

  for (const migration of migrations) {
    if (!applied.has(migration.id)) {
      await migration.up();
      newlyApplied.push(migration.id);
    }
  }

  return newlyApplied;
}
