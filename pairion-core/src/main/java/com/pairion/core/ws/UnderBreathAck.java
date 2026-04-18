package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server-to-client under-breath acknowledgement during barge-in.
 *
 * @param type the message type discriminator, always {@code "UnderBreathAck"}
 * @param acknowledgementType the style of acknowledgement: mm, sure, alright, or okay; may be null
 */
public record UnderBreathAck(
        @JsonProperty("type") String type,
        @JsonProperty("acknowledgementType") String acknowledgementType)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "UnderBreathAck";
}
