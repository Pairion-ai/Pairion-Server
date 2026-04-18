package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Server-to-client notification that a tool call has completed.
 *
 * @param type the message type discriminator, always {@code "ToolCallCompleted"}
 * @param toolCallId unique identifier for the completed tool call
 * @param output the output produced by the tool
 */
public record ToolCallCompleted(
        @JsonProperty("type") String type,
        @JsonProperty("toolCallId") String toolCallId,
        @JsonProperty("output") Map<String, Object> output)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "ToolCallCompleted";
}
