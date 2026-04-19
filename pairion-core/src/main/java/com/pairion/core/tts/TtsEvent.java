package com.pairion.core.tts;

/**
 * Sealed interface representing events emitted during streaming text-to-speech.
 *
 * <p>Zero or more {@link Chunk} events are emitted as synthesis produces audio, ending with exactly
 * one {@link Completed} event.
 */
public sealed interface TtsEvent permits TtsEvent.Chunk, TtsEvent.Completed {

    /**
     * An audio chunk (PCM or Opus bytes).
     *
     * @param audio the audio data
     * @param isOpus whether the audio is Opus-encoded
     */
    record Chunk(byte[] audio, boolean isOpus) implements TtsEvent {}

    /**
     * Synthesis completed.
     *
     * @param totalDurationMs total synthesized duration in milliseconds
     */
    record Completed(long totalDurationMs) implements TtsEvent {}
}