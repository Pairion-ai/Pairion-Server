/**
 * @pairion/skills — MCP skill registry, lifecycle, and invocation dispatch.
 *
 * @remarks
 * Skills expose tools that the LLM can invoke during conversations.
 * The registry manages skill installation and bridges skill definitions
 * to LLM `ToolDefinition` format. In M1, one bundled skill (weather)
 * is registered at startup.
 *
 * @packageDocumentation
 */

export { SkillRegistry, type SkillDefinition } from './registry.js';
export { SkillInvoker } from './invoker.js';
export { weatherSkill, getWeather } from './bundled/weather.js';

/** Skill source types. */
export type SkillSourceKind = 'bundled' | 'mcp-url' | 'mcp-stdio' | 'local-authored';

/** Skill source descriptor. */
export interface SkillSource {
  /** The kind of skill source. */
  readonly kind: SkillSourceKind;
  /** MCP server URL (for mcp-url kind). */
  readonly url?: string | undefined;
  /** Command to start the skill (for mcp-stdio kind). */
  readonly command?: string | undefined;
}
