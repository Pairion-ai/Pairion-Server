package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Client-to-server notification that the wake word was detected.
 *
 * @param type the message type discriminator, always {@code "WakeWordDetected"}
 * @param timestamp ISO-8601 timestamp of detection
 * @param confidence detection confidence score, may be null
 */
public record WakeWordDetected(
        @JsonProperty("type") String type,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("confidence") Double confidence)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "WakeWordDetected";
}
