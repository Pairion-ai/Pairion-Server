package com.pairion.adapters.voiceid.spi;

/**
 * Service provider interface for Voice Identification adapters.
 *
 * <p>Implementations wrap voice-ID models (SpeechBrain ECAPA-TDNN, etc.) and expose a uniform
 * speaker identification interface.
 */
public interface VoiceIdAdapter {

    /**
     * Returns the human-readable name of this adapter.
     *
     * @return the adapter name
     */
    String name();
}
