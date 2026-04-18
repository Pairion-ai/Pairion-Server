package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server-to-client message indicating the session has been closed.
 *
 * @param type the message type discriminator, always {@code "SessionClosed"}
 * @param reason human-readable reason for the session closure
 */
public record SessionClosed(
        @JsonProperty("type") String type, @JsonProperty("reason") String reason)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "SessionClosed";
}
