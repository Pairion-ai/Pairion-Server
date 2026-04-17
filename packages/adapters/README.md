# @pairion/adapters

The plugin boundary. Defines the seven adapter interfaces and ships the runtime registry. No other package imports a vendor SDK directly — all vendor interaction happens through adapters.

## Public Surface

- **Interfaces** — `LLMProvider`, `TTSProvider`, `STTProvider`, `WakeWordProvider`, `VoiceIdProvider`, `EmbeddingProvider`, `VectorStore`
- **Registry** — `AdapterRegistry` for runtime adapter registration and lookup
- **SecretsStore** — `SecretsStore` interface, `FileSecretsStore` implementation, and `ensureDevToken()` for dev-mode bearer tokens
- **Types** — `AdapterKind`, `CapabilityDescriptor`, `TranscriptEvent`, `VoiceIdCandidate`, `VectorSearchResult`

## Status

M0: Interfaces only. No adapter implementations are registered.
