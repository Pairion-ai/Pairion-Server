package com.pairion.core.stt;

/**
 * Sealed interface representing events emitted during streaming speech-to-text.
 *
 * <p>Zero or more {@link Partial} events are emitted as transcription proceeds, ending with exactly
 * one {@link Final} event when the audio stream is finalized.
 */
public sealed interface SttEvent permits SttEvent.Partial, SttEvent.Final {

    /**
     * An interim partial transcript.
     *
     * @param text the partial transcript text
     */
    record Partial(String text) implements SttEvent {}

    /**
     * The final transcript after the audio stream is complete.
     *
     * @param text the final transcript text
     * @param durationMs total audio duration in milliseconds
     */
    record Final(String text, long durationMs) implements SttEvent {}
}
