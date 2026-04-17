/**
 * @pairion/node-mgmt — Node pairing, capability manifest, arbitration.
 *
 * @remarks
 * M0 scaffolding only. No runtime logic. Manages Node lifecycle: pairing flow,
 * capability manifest storage, multi-Node arbitration algorithm, offline policy
 * management, and LED command issuance.
 *
 * @packageDocumentation
 */

/** Node hardware tier, derived from capabilities manifest. */
export type NodeTier = 'dumb' | 'smart';

/** Node offline policy strategy. */
export type OfflineStrategy = 'silent' | 'cached-error' | 'degraded-smart';

/** Node capabilities manifest per Charter §6.5.2. */
export interface NodeCapabilities {
  /** Whether the node has audio input. */
  readonly audioIn: boolean;
  /** Whether the node has audio output. */
  readonly audioOut: boolean;
  /** Whether local wake word detection is available. */
  readonly localWakeWord: boolean;
  /** Whether local VAD is available. */
  readonly localVad: boolean;
  /** Whether local STT is available (Smart tier only). */
  readonly localStt: boolean;
  /** Whether a small local LLM is available (Smart tier only). */
  readonly localLlmSmall: boolean;
  /** Whether cached TTS is available. */
  readonly localTtsCache: boolean;
  /** AI accelerator type. */
  readonly aiAccelerator: 'none' | 'hailo-10h' | 'hailo-8' | 'hailo-8l' | 'other';
  /** Dedicated NPU RAM in GB. */
  readonly dedicatedNpuRamGb: number;
}
