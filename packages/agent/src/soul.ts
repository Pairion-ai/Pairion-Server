/**
 * Loads the default SOUL.md system prompt for the agent.
 *
 * @remarks
 * The SOUL.md file defines Pairion's personality, conversational style,
 * and instructions for tool use. In M1 this is a minimal persona;
 * expanded at M3 when memory and personality come online.
 */

import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/** Default system prompt if the SOUL.md file cannot be read. */
const FALLBACK_PROMPT = `You are Pairion, an ambient AI assistant. You speak with a calm, helpful tone.
When the user asks about the weather, use the get_current_weather tool.
Keep responses concise and conversational.`;

/**
 * Loads the system prompt from the default SOUL.md file.
 *
 * @returns The system prompt string.
 */
export function loadSystemPrompt(): string {
  try {
    const soulPath = join(import.meta.dirname, '../soul/default-soul.md');
    return readFileSync(soulPath, 'utf-8').trim();
  } catch /* v8 ignore next */ {
    return FALLBACK_PROMPT;
  }
}
