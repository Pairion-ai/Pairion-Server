package com.pairion.adapters.vad.spi;

/**
 * Service provider interface for Voice Activity Detection adapters.
 *
 * <p>Implementations wrap VAD engines (Silero, etc.) and expose a uniform detection interface.
 */
public interface VadAdapter {

    /**
     * Returns the human-readable name of this adapter.
     *
     * @return the adapter name
     */
    String name();
}
