package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server-to-client heartbeat pong response.
 *
 * @param type the message type discriminator, always {@code "HeartbeatPong"}
 * @param timestamp ISO-8601 timestamp of when the pong was generated
 */
public record HeartbeatPong(
        @JsonProperty("type") String type, @JsonProperty("timestamp") String timestamp)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "HeartbeatPong";
}
