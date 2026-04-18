package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Client-to-server notification that the user has stopped speaking.
 *
 * @param type the message type discriminator, always {@code "SpeechEnded"}
 * @param streamId identifier of the audio stream that ended
 */
public record SpeechEnded(
        @JsonProperty("type") String type, @JsonProperty("streamId") String streamId)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "SpeechEnded";
}
