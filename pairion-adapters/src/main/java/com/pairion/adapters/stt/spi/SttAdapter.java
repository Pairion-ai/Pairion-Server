package com.pairion.adapters.stt.spi;

import com.pairion.core.stt.SttCapabilities;
import com.pairion.core.stt.SttEvent;
import java.util.function.Consumer;

/**
 * Service provider interface for Speech-to-Text adapters.
 *
 * <p>Implementations wrap STT engines (whisper.cpp, etc.) and expose a uniform streaming
 * transcription interface.
 */
public interface SttAdapter {

    /**
     * Returns the human-readable name of this adapter.
     *
     * @return the adapter name
     */
    String name();

    /**
     * Returns the current capabilities of this adapter.
     *
     * @return capability descriptor
     */
    SttCapabilities capabilities();

    /**
     * Creates a new streaming transcription session.
     *
     * @param eventConsumer callback receiving partial and final transcript events
     * @return a session handle for feeding audio and finalizing
     */
    SttSession createSession(Consumer<SttEvent> eventConsumer);

    /** A streaming transcription session that accepts PCM audio data incrementally. */
    interface SttSession {

        /**
         * Feeds a chunk of 16 kHz mono signed 16-bit little-endian PCM audio data.
         *
         * @param pcmData the PCM audio bytes
         */
        void feedAudio(byte[] pcmData);

        /**
         * Signals the end of the audio stream and produces the final transcript.
         *
         * <p>After calling this method, the session is no longer usable.
         */
        void finalizeStream();
    }
}
