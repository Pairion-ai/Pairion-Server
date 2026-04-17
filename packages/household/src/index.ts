/**
 * @pairion/household — Users, roles, policy engine, per-user partitioning.
 *
 * @remarks
 * M0 scaffolding only. No runtime logic. The household package owns Users,
 * roles, the policy engine, cross-user messaging, and the per-user
 * partitioning middleware.
 *
 * @packageDocumentation
 */

/** Household member roles per Charter §6.2. */
export type UserRole = 'owner' | 'member' | 'minor' | 'guest';

/** Household metadata. */
export interface Household {
  /** Display name for the household. */
  readonly name: string;
  /** IANA timezone identifier. */
  readonly timezone: string;
}

/** Default household configuration. */
export const DEFAULT_HOUSEHOLD: Household = {
  name: 'My Household',
  timezone: 'America/New_York',
};
