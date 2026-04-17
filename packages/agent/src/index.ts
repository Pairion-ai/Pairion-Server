/**
 * @pairion/agent — Session orchestration, turn loop, sub-agent spawning.
 *
 * @remarks
 * M0 scaffolding only. No runtime logic. The agent orchestrator owns session
 * state, the turn loop (STT → reason → tool-calls → TTS), sub-agent spawning,
 * under-breath cadence logic, and visible-intelligence event emission.
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
