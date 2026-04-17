/**
 * Skill registry for managing MCP-compatible skills.
 *
 * @remarks
 * Skills expose tools that the LLM can invoke during a conversation.
 * The registry bridges skill definitions to LLM `ToolDefinition` format.
 */

import type { ToolDefinition } from '@pairion/adapters';
import { createSubsystemLogger } from '@pairion/core';

const log = createSubsystemLogger('skills');

/**
 * A skill definition that exposes one or more tools to the LLM.
 */
export interface SkillDefinition {
  /** Unique skill name (matches the tool name the LLM calls). */
  readonly name: string;
  /** Human-readable description of what the skill does. */
  readonly description: string;
  /** JSON Schema describing the skill's input parameters. */
  readonly inputSchema: Record<string, unknown>;
  /**
   * Invoke the skill with the given arguments.
   *
   * @param args - The arguments from the LLM's tool call.
   * @returns A string result to feed back to the LLM.
   */
  invoke(args: Record<string, unknown>): Promise<string>;
}

/**
 * Registry for managing installed skills.
 */
export class SkillRegistry {
  private readonly skills = new Map<string, SkillDefinition>();

  /**
   * Register a skill.
   *
   * @param skill - The skill definition to register.
   */
  register(skill: SkillDefinition): void {
    this.skills.set(skill.name, skill);
    log.info({ skill: skill.name }, 'Skill registered');
  }

  /**
   * Get a skill by name.
   *
   * @param name - The skill/tool name.
   * @returns The skill definition, or undefined if not found.
   */
  get(name: string): SkillDefinition | undefined {
    return this.skills.get(name);
  }

  /**
   * List all registered skills as ToolDefinitions for the LLM.
   *
   * @returns Array of ToolDefinition objects compatible with LLMProvider.chat().
   */
  listToolDefinitions(): ToolDefinition[] {
    return [...this.skills.values()].map((skill) => ({
      name: skill.name,
      description: skill.description,
      inputSchema: skill.inputSchema,
    }));
  }

  /** Returns the number of registered skills. */
  get size(): number {
    return this.skills.size;
  }
}
