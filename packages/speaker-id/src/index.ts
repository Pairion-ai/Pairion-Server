/**
 * @pairion/speaker-id — Voice enrollment and real-time speaker identification.
 *
 * @remarks
 * M0 scaffolding only. No runtime logic. Owns the enrollment flow, real-time
 * identification on every utterance, embedding management, and the CI eval
 * harness for the 99% precision gate.
 *
 * @packageDocumentation
 */

/** Voice identification result classification. */
export type SpeakerIdResult = 'identified' | 'ambiguous' | 'unknown';

/** Voice-ID threshold configuration. */
export interface VoiceIdThresholds {
  /** Minimum confidence to identify a speaker (default 0.85). */
  readonly identifyThreshold: number;
  /** Margin within which two candidates are considered ambiguous. */
  readonly ambiguityMargin: number;
  /** Minimum confidence for guest classification (default 0.70). */
  readonly guestThreshold: number;
}

/** Default voice-ID thresholds per Charter §6.4. */
export const DEFAULT_VOICE_ID_THRESHOLDS: VoiceIdThresholds = {
  identifyThreshold: 0.85,
  ambiguityMargin: 0.05,
  guestThreshold: 0.70,
};
