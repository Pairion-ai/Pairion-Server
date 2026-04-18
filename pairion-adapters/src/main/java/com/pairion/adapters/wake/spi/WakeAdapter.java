package com.pairion.adapters.wake.spi;

/**
 * Service provider interface for wake-word detection adapters.
 *
 * <p>Implementations wrap wake-word engines (openWakeWord, etc.) and expose a uniform detection
 * interface.
 */
public interface WakeAdapter {

    /**
     * Returns the human-readable name of this adapter.
     *
     * @return the adapter name
     */
    String name();
}
