package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Bidirectional message indicating the start of an audio stream.
 *
 * <p>Used for both client-to-server (microphone capture) and server-to-client (TTS output) audio
 * streams.
 *
 * @param type the message type discriminator, always {@code "AudioStreamStart"}
 * @param streamId unique identifier for this audio stream
 * @param codec audio codec, always {@code "opus"}
 * @param sampleRate audio sample rate in Hz
 */
public record AudioStreamStart(
        @JsonProperty("type") String type,
        @JsonProperty("streamId") String streamId,
        @JsonProperty("codec") String codec,
        @JsonProperty("sampleRate") int sampleRate)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "AudioStreamStart";
}
