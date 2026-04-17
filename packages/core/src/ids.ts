/**
 * Branded identifier types for type-safe entity references throughout the monorepo.
 *
 * @remarks
 * Each id type is a branded string — structurally a plain string at runtime
 * but a distinct type at compile time. This prevents accidentally passing a
 * `UserId` where a `SessionId` is expected.
 */

/** Symbol used for nominal branding. */
declare const brand: unique symbol;

/** A branded string type. The `Brand` generic parameter prevents cross-type assignment. */
type Branded<Brand extends string> = string & { readonly [brand]: Brand };

/** Unique identifier for a household User. */
export type UserId = Branded<'UserId'>;

/** Unique identifier for a conversation Session. */
export type SessionId = Branded<'SessionId'>;

/** Unique identifier for a Pi Node. */
export type NodeId = Branded<'NodeId'>;

/** Unique identifier for a paired Client Device. */
export type DeviceId = Branded<'DeviceId'>;

/** Unique identifier for a Skill. */
export type SkillId = Branded<'SkillId'>;

/** Unique identifier for an Adapter instance. */
export type AdapterId = Branded<'AdapterId'>;

/** Unique identifier for a ProactiveRule. */
export type RuleId = Branded<'RuleId'>;

/** Unique identifier for a computer-use Action. */
export type ActionId = Branded<'ActionId'>;

/**
 * Creates a branded identifier from a plain string.
 *
 * @typeParam T - The branded id type to produce.
 * @param value - The raw string value.
 * @returns The branded identifier.
 */
export function createId<T extends Branded<string>>(value: string): T {
  return value as T;
}
