package com.pairion.core.stt;

/**
 * Describes the capabilities of an STT adapter.
 *
 * @param available whether the adapter is ready to accept audio input
 * @param supportsStreaming whether the adapter supports real-time streaming transcription
 */
public record SttCapabilities(boolean available, boolean supportsStreaming) {

    /**
     * Returns capabilities indicating the adapter is unavailable.
     *
     * @return unavailable capabilities
     */
    public static SttCapabilities unavailable() {
        return new SttCapabilities(false, false);
    }
}
