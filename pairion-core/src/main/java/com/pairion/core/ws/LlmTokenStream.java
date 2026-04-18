package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server-to-client streaming LLM token output.
 *
 * @param type the message type discriminator, always {@code "LlmTokenStream"}
 * @param delta the incremental token text
 */
public record LlmTokenStream(@JsonProperty("type") String type, @JsonProperty("delta") String delta)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "LlmTokenStream";
}
