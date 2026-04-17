import { describe, it, expect } from 'vitest';
import { DEFAULT_VOICE_ID_THRESHOLDS } from '../src/index.js';
import type { SpeakerIdResult, VoiceIdThresholds } from '../src/index.js';

describe('@pairion/speaker-id', () => {
  it('exports SpeakerIdResult type', () => {
    const result: SpeakerIdResult = 'identified';
    expect(result).toBe('identified');
  });

  it('exports default thresholds per Charter §6.4', () => {
    expect(DEFAULT_VOICE_ID_THRESHOLDS.identifyThreshold).toBe(0.85);
    expect(DEFAULT_VOICE_ID_THRESHOLDS.ambiguityMargin).toBe(0.05);
    expect(DEFAULT_VOICE_ID_THRESHOLDS.guestThreshold).toBe(0.70);
  });

  it('VoiceIdThresholds is structurally valid', () => {
    const t: VoiceIdThresholds = { identifyThreshold: 0.9, ambiguityMargin: 0.1, guestThreshold: 0.6 };
    expect(t.identifyThreshold).toBe(0.9);
  });
});
