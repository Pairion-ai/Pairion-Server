/**
 * Skill invoker for dispatching LLM tool calls to registered skills.
 */

import type { SkillRegistry } from './registry.js';
import { createSubsystemLogger } from '@pairion/core';

const log = createSubsystemLogger('skill-invoker');

/**
 * Dispatches tool calls from the LLM to the correct skill.
 */
export class SkillInvoker {
  private readonly registry: SkillRegistry;

  /**
   * Creates a skill invoker.
   *
   * @param registry - The skill registry to dispatch against.
   */
  constructor(registry: SkillRegistry) {
    this.registry = registry;
  }

  /**
   * Invoke a skill by tool name.
   *
   * @param toolName - The name of the tool/skill to invoke.
   * @param args - The arguments from the LLM's tool call.
   * @returns The skill's text result.
   * @throws If the skill is not found in the registry.
   */
  async invoke(toolName: string, args: Record<string, unknown>): Promise<string> {
    const skill = this.registry.get(toolName);
    if (!skill) {
      log.warn({ toolName }, 'Unknown tool called');
      return `Error: Unknown tool "${toolName}"`;
    }

    const startTime = Date.now();
    try {
      const result = await skill.invoke(args);
      const elapsed = Date.now() - startTime;
      log.info({ toolName, elapsed }, 'Skill invocation completed');
      return result;
    } catch (err) {
      const elapsed = Date.now() - startTime;
      log.error({ toolName, elapsed, err }, 'Skill invocation failed');
      return `Error invoking ${toolName}: ${err instanceof Error ? err.message : String(err)}`;
    }
  }
}
