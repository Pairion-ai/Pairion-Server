import { describe, it, expect } from 'vitest';
import { DEFAULT_AGENT_CONFIG } from '../src/index.js';
import type { AgentState, AgentConfig } from '../src/index.js';

describe('@pairion/agent', () => {
  it('exports AgentState type', () => {
    const state: AgentState = 'idle';
    expect(state).toBe('idle');
  });

  it('exports default agent config', () => {
    expect(DEFAULT_AGENT_CONFIG.underBreathDelayMs).toBe(2000);
    expect(DEFAULT_AGENT_CONFIG.sessionTimeoutMs).toBe(300_000);
  });

  it('AgentConfig is structurally valid', () => {
    const config: AgentConfig = { underBreathDelayMs: 1000, sessionTimeoutMs: 60000 };
    expect(config.underBreathDelayMs).toBe(1000);
  });
});
