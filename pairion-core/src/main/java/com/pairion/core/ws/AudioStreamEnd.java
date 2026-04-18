package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Bidirectional message indicating the end of an audio stream.
 *
 * <p>Used for both client-to-server and server-to-client audio stream termination.
 *
 * @param type the message type discriminator, always {@code "AudioStreamEnd"}
 * @param streamId identifier of the audio stream that ended
 * @param reason reason for stream termination: normal, interrupted, or error
 */
public record AudioStreamEnd(
        @JsonProperty("type") String type,
        @JsonProperty("streamId") String streamId,
        @JsonProperty("reason") String reason)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "AudioStreamEnd";
}
