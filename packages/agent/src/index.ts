/**
 * @pairion/agent — Session orchestration, turn loop, and visible-intelligence
 * event emission.
 *
 * @remarks
 * The agent owns session state, the turn loop (STT → LLM → tool-calls → TTS),
 * and emits AsyncAPI events via the SessionEmitter interface. In M1 it
 * processes single-turn voice interactions end-to-end.
 *
 * @packageDocumentation
 */

/** Agent state during a session turn. */
export type AgentState = 'idle' | 'listening' | 'identifying' | 'thinking' | 'speaking' | 'handoff';

/** Configuration for the agent orchestrator. */
export interface AgentConfig {
  /** Under-breath acknowledgment delay in milliseconds. */
  readonly underBreathDelayMs: number;
  /** Session inactivity timeout in milliseconds. */
  readonly sessionTimeoutMs: number;
}

/** Default agent configuration. */
export const DEFAULT_AGENT_CONFIG: AgentConfig = {
  underBreathDelayMs: 2000,
  sessionTimeoutMs: 300_000,
};

export type { SessionEmitter } from './session-emitter.js';
export { SessionManager, type Session } from './session-manager.js';
export { processTurn, type TurnSummary } from './turn-loop.js';
export { loadSystemPrompt } from './soul.js';
