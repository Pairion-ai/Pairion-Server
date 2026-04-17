/**
 * @pairion/adapters — Pluggable backend interfaces and adapter registry.
 *
 * @remarks
 * Defines the seven adapter interfaces and ships reference implementations.
 * No vendor SDKs are imported by any other package — all vendor interaction
 * happens through these interfaces.
 *
 * @packageDocumentation
 */

export type {
  CapabilityDescriptor,
  LLMProvider,
  LLMGenerateOptions,
  LLMMessage,
  ToolDefinition,
  LLMStreamEvent,
  TTSProvider,
  TTSSynthesizeOptions,
  STTProvider,
  TranscriptEvent,
  WakeWordProvider,
  VoiceIdProvider,
  VoiceIdCandidate,
  EmbeddingProvider,
  VectorStore,
  VectorSearchResult,
} from './interfaces.js';

export { AdapterRegistry, type AdapterKind, type AnyProvider } from './registry.js';

export {
  type SecretsStore,
  FileSecretsStore,
  ensureDevToken,
} from './secrets-store.js';

export { KeychainSecretsStore } from './keychain-secrets-store.js';

export { AnthropicLLMProvider, type AnthropicConfig } from './implementations/llm/anthropic.js';
export { KokoroMLXTTSProvider, type KokoroMLXConfig } from './implementations/tts/kokoro-mlx.js';
export { WhisperMLXSTTProvider, type WhisperMLXConfig } from './implementations/stt/whisper-mlx.js';
export { OpenWakeWordProvider, type OpenWakeWordConfig } from './implementations/wake-word/openwakeword.js';
