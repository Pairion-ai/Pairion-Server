/**
 * Adapter registry for runtime adapter management.
 *
 * @remarks
 * Accepts adapter implementations at runtime. In M0 no implementations
 * are registered; the registry infrastructure exists and is tested.
 */

import type {
  LLMProvider,
  TTSProvider,
  STTProvider,
  WakeWordProvider,
  VoiceIdProvider,
  EmbeddingProvider,
  VectorStore,
} from './interfaces.js';

/** The seven adapter kinds. */
export type AdapterKind = 'llm' | 'tts' | 'stt' | 'wake-word' | 'voice-id' | 'embedding' | 'vector-store';

/** Union of all adapter provider types. */
export type AnyProvider =
  | LLMProvider
  | TTSProvider
  | STTProvider
  | WakeWordProvider
  | VoiceIdProvider
  | EmbeddingProvider
  | VectorStore;

/**
 * Registry that holds adapter implementations, keyed by kind and provider id.
 */
export class AdapterRegistry {
  private readonly adapters = new Map<AdapterKind, Map<string, AnyProvider>>();

  /**
   * Register an adapter implementation.
   *
   * @param kind - The adapter kind.
   * @param provider - The adapter implementation.
   */
  register(kind: AdapterKind, provider: AnyProvider): void {
    let kindMap = this.adapters.get(kind);
    if (!kindMap) {
      kindMap = new Map();
      this.adapters.set(kind, kindMap);
    }
    kindMap.set(provider.providerId, provider);
  }

  /**
   * Get a registered adapter by kind and provider id.
   *
   * @param kind - The adapter kind.
   * @param providerId - The provider identifier.
   * @returns The adapter, or undefined if not registered.
   */
  get(kind: AdapterKind, providerId: string): AnyProvider | undefined {
    return this.adapters.get(kind)?.get(providerId);
  }

  /**
   * List all registered adapters of a given kind.
   *
   * @param kind - The adapter kind.
   * @returns Array of registered providers for the kind.
   */
  list(kind: AdapterKind): AnyProvider[] {
    const kindMap = this.adapters.get(kind);
    return kindMap ? [...kindMap.values()] : [];
  }

  /**
   * List all registered adapter kinds and their providers.
   *
   * @returns Map of kind to provider arrays.
   */
  listAll(): Map<AdapterKind, AnyProvider[]> {
    const result = new Map<AdapterKind, AnyProvider[]>();
    for (const [kind, providers] of this.adapters) {
      result.set(kind, [...providers.values()]);
    }
    return result;
  }
}
