/**
 * @pairion/adapters — Pluggable backend interfaces and adapter registry.
 *
 * @remarks
 * Defines the seven adapter interfaces (LLM, TTS, STT, WakeWord, VoiceId,
 * Embedding, VectorStore) and the runtime registry. No vendor SDKs are
 * imported by any other package — all vendor interaction happens through
 * these interfaces.
 *
 * In M0, only interfaces and the registry exist. No implementations
 * are registered.
 *
 * @packageDocumentation
 */

export type {
  CapabilityDescriptor,
  LLMProvider,
  LLMGenerateOptions,
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
