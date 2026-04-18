package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server-to-client message confirming a session has been established.
 *
 * @param type the message type discriminator, always {@code "SessionOpened"}
 * @param sessionId unique identifier for the established session
 * @param serverVersion semantic version of the server
 */
public record SessionOpened(
        @JsonProperty("type") String type,
        @JsonProperty("sessionId") String sessionId,
        @JsonProperty("serverVersion") String serverVersion)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "SessionOpened";
}
