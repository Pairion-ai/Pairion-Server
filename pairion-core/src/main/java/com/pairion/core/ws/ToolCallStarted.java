package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Server-to-client notification that a tool call has been initiated.
 *
 * @param type the message type discriminator, always {@code "ToolCallStarted"}
 * @param toolCallId unique identifier for this tool call
 * @param toolName name of the tool being invoked
 * @param input the input parameters passed to the tool
 */
public record ToolCallStarted(
        @JsonProperty("type") String type,
        @JsonProperty("toolCallId") String toolCallId,
        @JsonProperty("toolName") String toolName,
        @JsonProperty("input") Map<String, Object> input)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "ToolCallStarted";
}
