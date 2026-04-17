/**
 * @pairion/skills — MCP skill registry, lifecycle, and invocation dispatch.
 *
 * @remarks
 * M0 scaffolding only. No runtime logic. Skill config is per-user — a User's
 * credentials for a skill are never shared with another User.
 *
 * @packageDocumentation
 */

/** Skill source types. */
export type SkillSourceKind = 'bundled' | 'mcp-url' | 'mcp-stdio' | 'local-authored';

/** Skill source descriptor. */
export interface SkillSource {
  /** The kind of skill source. */
  readonly kind: SkillSourceKind;
  /** MCP server URL (for mcp-url kind). */
  readonly url?: string;
  /** Command to start the skill (for mcp-stdio kind). */
  readonly command?: string;
}
