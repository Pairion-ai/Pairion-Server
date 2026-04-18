package com.pairion.adapters.tts.spi;

/**
 * Service provider interface for Text-to-Speech adapters.
 *
 * <p>Implementations wrap TTS engines (Piper, etc.) and expose a uniform synthesis interface.
 */
public interface TtsAdapter {

    /**
     * Returns the human-readable name of this adapter.
     *
     * @return the adapter name
     */
    String name();
}
