package com.pairion.core.tts;

/**
 * Describes the capabilities of a TTS adapter.
 *
 * @param available whether the adapter is ready to synthesize speech
 * @param supportsStreaming whether the adapter supports low-latency chunked output
 */
public record TtsCapabilities(boolean available, boolean supportsStreaming) {

    /**
     * Returns capabilities indicating the adapter is unavailable.
     *
     * @return unavailable capabilities
     */
    public static TtsCapabilities unavailable() {
        return new TtsCapabilities(false, false);
    }
}