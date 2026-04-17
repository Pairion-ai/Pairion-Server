/**
 * @pairion/memory — Episodic, semantic, and preference memory stores.
 *
 * @remarks
 * M0 scaffolding only. No runtime logic. All stores are per-user partitioned
 * at the data layer. The Memory facade always takes a userId as the first
 * argument.
 *
 * @packageDocumentation
 */

/** Memory store types. */
export type MemoryStore = 'episodic' | 'semantic' | 'preferences';

/** A memory search hit. */
export interface MemoryHit {
  /** Which store this hit came from. */
  readonly store: MemoryStore;
  /** Relevance score (0–1). */
  readonly score: number;
  /** Human-readable summary. */
  readonly summary: string;
}
