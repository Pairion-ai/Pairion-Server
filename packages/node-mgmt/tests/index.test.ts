import { describe, it, expect } from 'vitest';
import type { NodeTier, OfflineStrategy, NodeCapabilities } from '../src/index.js';

describe('@pairion/node-mgmt', () => {
  it('exports NodeTier type', () => {
    const tier: NodeTier = 'dumb';
    expect(tier).toBe('dumb');
  });

  it('exports OfflineStrategy type', () => {
    const strategy: OfflineStrategy = 'cached-error';
    expect(strategy).toBe('cached-error');
  });

  it('NodeCapabilities is structurally valid', () => {
    const caps: NodeCapabilities = {
      audioIn: true, audioOut: true, localWakeWord: true, localVad: true,
      localStt: false, localLlmSmall: false, localTtsCache: true,
      aiAccelerator: 'none', dedicatedNpuRamGb: 0,
    };
    expect(caps.audioIn).toBe(true);
    expect(caps.aiAccelerator).toBe('none');
  });
});
