package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server-to-client notification of agent state transition.
 *
 * @param type the message type discriminator, always {@code "AgentStateChange"}
 * @param state the new agent state: idle, listening, thinking, or speaking
 */
public record AgentStateChange(
        @JsonProperty("type") String type, @JsonProperty("state") String state)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "AgentStateChange";
}
