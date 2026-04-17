/**
 * Adapter interfaces for all pluggable backends.
 *
 * @remarks
 * Seven adapter interfaces cover every external model integration.
 * No other package in the monorepo may import a vendor SDK directly —
 * all vendor interaction happens through these interfaces.
 */

/**
 * Capability descriptor declared by an adapter on registration.
 * Skills declare what they require; the system warns on incompatible combinations.
 */
export interface CapabilityDescriptor {
  /** Unique capability identifier (e.g. `streaming-tts`, `vision`, `tool-use`). */
  readonly id: string;
  /** Human-readable label. */
  readonly label: string;
}

/**
 * LLM provider adapter interface.
 *
 * @remarks
 * Supports streaming generation, tool use, and optional vision.
 */
export interface LLMProvider {
  /** Unique provider id (e.g. `anthropic`, `openai`, `ollama`). */
  readonly providerId: string;
  /** Capabilities this provider supports. */
  readonly capabilities: readonly CapabilityDescriptor[];
  /** Generate a response from a simple string prompt. */
  generate(prompt: string, options?: LLMGenerateOptions): Promise<AsyncIterable<string>>;
  /** Chat with messages and optional tool definitions (streaming with tool-use). */
  chat(
    messages: LLMMessage[],
    tools?: ToolDefinition[],
    options?: LLMGenerateOptions,
  ): AsyncIterable<LLMStreamEvent>;
}

/** Options for LLM generation. */
export interface LLMGenerateOptions {
  /** Maximum tokens to generate. */
  readonly maxTokens?: number | undefined;
  /** Temperature for sampling. */
  readonly temperature?: number | undefined;
  /** Whether to enable streaming. */
  readonly stream?: boolean | undefined;
}

/** A message in a conversation for the chat interface. */
export interface LLMMessage {
  /** The role of the message author. */
  readonly role: 'system' | 'user' | 'assistant' | 'tool';
  /** The message text content. */
  readonly content: string;
  /** Tool call id when role is 'tool' (the result of a tool invocation). */
  readonly toolCallId?: string | undefined;
}

/** A tool definition passed to the LLM for function calling. */
export interface ToolDefinition {
  /** Tool name (must match the skill name). */
  readonly name: string;
  /** Human-readable description of what the tool does. */
  readonly description: string;
  /** JSON Schema describing the tool's input parameters. */
  readonly inputSchema: Record<string, unknown>;
}

/** An event emitted during LLM streaming with tool-use support. */
export interface LLMStreamEvent {
  /** The type of stream event. */
  readonly type: 'text_delta' | 'tool_call_start' | 'tool_call_delta' | 'tool_call_end' | 'done';
  /** Text content (present for text_delta). */
  readonly text?: string | undefined;
  /** Tool call data (present for tool_call_* events). */
  readonly toolCall?: {
    readonly id: string;
    readonly name: string;
    readonly arguments: string;
  } | undefined;
}

/**
 * Text-to-speech provider adapter interface.
 *
 * @remarks
 * Returns an async iterable of audio frames (Opus by default).
 */
export interface TTSProvider {
  /** Unique provider id. */
  readonly providerId: string;
  /** Capabilities this provider supports. */
  readonly capabilities: readonly CapabilityDescriptor[];
  /** Synthesize text to audio frames. */
  synthesize(text: string, options?: TTSSynthesizeOptions): Promise<AsyncIterable<Uint8Array>>;
}

/** Options for TTS synthesis. */
export interface TTSSynthesizeOptions {
  /** Voice identifier. */
  readonly voiceId?: string;
  /** Speech rate multiplier (0.5–2.0). */
  readonly speechRate?: number;
}

/**
 * Speech-to-text provider adapter interface.
 *
 * @remarks
 * Consumes an async iterable of audio chunks, produces transcript events.
 */
export interface STTProvider {
  /** Unique provider id. */
  readonly providerId: string;
  /** Capabilities this provider supports. */
  readonly capabilities: readonly CapabilityDescriptor[];
  /** Transcribe audio to text. */
  transcribe(audio: AsyncIterable<Uint8Array>): AsyncIterable<TranscriptEvent>;
}

/** A transcript event from the STT provider. */
export interface TranscriptEvent {
  /** Whether this is a final or partial transcript. */
  readonly isFinal: boolean;
  /** The transcribed text. */
  readonly text: string;
  /** Confidence score (0–1). */
  readonly confidence?: number;
}

/**
 * Wake-word detection provider adapter interface.
 *
 * @remarks
 * Used client-side by Clients and Nodes. Server provides model files
 * and threshold configuration.
 */
export interface WakeWordProvider {
  /** Unique provider id. */
  readonly providerId: string;
  /** Capabilities this provider supports. */
  readonly capabilities: readonly CapabilityDescriptor[];
  /** Get the path to the wake-word model file. */
  getModelPath(): string;
  /** Get the detection confidence threshold. */
  getThreshold(): number;
}

/**
 * Voice identification provider adapter interface.
 *
 * @remarks
 * Handles voice enrollment (producing embeddings) and real-time
 * identification (scoring against enrolled embeddings).
 */
export interface VoiceIdProvider {
  /** Unique provider id. */
  readonly providerId: string;
  /** Capabilities this provider supports. */
  readonly capabilities: readonly CapabilityDescriptor[];
  /** Enroll a user's voice from audio samples, producing an embedding. */
  enroll(userId: string, audio: AsyncIterable<Uint8Array>): Promise<Uint8Array>;
  /** Identify a speaker from an audio sample against enrolled embeddings. */
  identify(audio: Uint8Array): Promise<VoiceIdCandidate[]>;
}

/** A voice identification candidate with confidence score. */
export interface VoiceIdCandidate {
  /** The matched user id. */
  readonly userId: string;
  /** Confidence score (0–1). */
  readonly confidence: number;
}

/**
 * Embedding provider adapter interface.
 *
 * @remarks
 * Generates vector embeddings from text for semantic search.
 */
export interface EmbeddingProvider {
  /** Unique provider id. */
  readonly providerId: string;
  /** Capabilities this provider supports. */
  readonly capabilities: readonly CapabilityDescriptor[];
  /** Generate an embedding vector from text. */
  embed(text: string): Promise<number[]>;
  /** The dimensionality of the output vectors. */
  readonly dimensions: number;
}

/**
 * Vector store adapter interface.
 *
 * @remarks
 * Provides vector similarity search with mandatory user-id filtering.
 */
export interface VectorStore {
  /** Unique provider id. */
  readonly providerId: string;
  /** Capabilities this provider supports. */
  readonly capabilities: readonly CapabilityDescriptor[];
  /** Upsert a document with its embedding vector. */
  upsert(id: string, vector: number[], metadata: Record<string, unknown>): Promise<void>;
  /** Query for similar vectors with an optional filter. */
  query(
    vector: number[],
    topK: number,
    filter?: Record<string, unknown>,
  ): Promise<VectorSearchResult[]>;
  /** Delete a document by id. */
  delete(id: string): Promise<void>;
}

/** A vector search result. */
export interface VectorSearchResult {
  /** Document id. */
  readonly id: string;
  /** Similarity score. */
  readonly score: number;
  /** Document metadata. */
  readonly metadata: Record<string, unknown>;
}
