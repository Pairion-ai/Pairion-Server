package com.pairion.adapters.stt.spi;

/**
 * Service provider interface for Speech-to-Text adapters.
 *
 * <p>Implementations wrap STT engines (whisper.cpp, etc.) and expose a uniform transcription
 * interface.
 */
public interface SttAdapter {

    /**
     * Returns the human-readable name of this adapter.
     *
     * @return the adapter name
     */
    String name();
}
