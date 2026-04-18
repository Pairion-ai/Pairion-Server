package com.pairion.core.llm;

import java.util.Map;

/**
 * Sealed interface representing events emitted during streaming LLM generation.
 *
 * <p>The event stream follows: zero or more {@link TokenDelta} events, optionally interspersed with
 * {@link ToolCallRequest} and {@link ToolCallResult} pairs, ending with a single {@link Stop}.
 */
public sealed interface LlmEvent
        permits LlmEvent.TokenDelta,
                LlmEvent.ToolCallRequest,
                LlmEvent.ToolCallResult,
                LlmEvent.Stop {

    /**
     * An incremental text token from the LLM response.
     *
     * @param delta the token text
     */
    record TokenDelta(String delta) implements LlmEvent {}

    /**
     * The LLM is requesting a tool invocation.
     *
     * @param toolCallId unique identifier for this tool call
     * @param toolName the name of the tool to invoke
     * @param input the input arguments for the tool
     */
    record ToolCallRequest(String toolCallId, String toolName, Map<String, Object> input)
            implements LlmEvent {}

    /**
     * The result of a tool invocation, to be fed back to the LLM.
     *
     * @param toolCallId the tool call this result corresponds to
     * @param output the tool's output
     */
    record ToolCallResult(String toolCallId, Map<String, Object> output) implements LlmEvent {}

    /**
     * The LLM has finished generating.
     *
     * @param outputTokens total number of output tokens generated
     */
    record Stop(int outputTokens) implements LlmEvent {}
}
