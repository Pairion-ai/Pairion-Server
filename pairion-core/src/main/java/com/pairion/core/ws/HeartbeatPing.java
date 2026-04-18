package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Client-to-server heartbeat ping.
 *
 * @param type the message type discriminator, always {@code "HeartbeatPing"}
 * @param timestamp ISO-8601 timestamp of when the ping was sent
 */
public record HeartbeatPing(
        @JsonProperty("type") String type, @JsonProperty("timestamp") String timestamp)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "HeartbeatPing";
}
