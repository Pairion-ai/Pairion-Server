package com.pairion.core.llm;

import java.util.List;
import java.util.Map;

/**
 * Request to generate a streaming LLM completion.
 *
 * @param systemPrompt the system prompt (SOUL + memory context)
 * @param userMessage the user's message (typically the final transcript)
 * @param toolDefinitions tool definitions available for tool-use (may be empty)
 * @param model the model identifier to use, or null for the adapter default
 * @param toolCallHistory completed tool call pairs from prior turns in this conversation (may be
 *     empty)
 */
public record LlmRequest(
        String systemPrompt,
        String userMessage,
        List<ToolDefinition> toolDefinitions,
        String model,
        List<ToolCallPair> toolCallHistory) {

    /**
     * Creates a simple request with no tools and the default model.
     *
     * @param systemPrompt the system prompt
     * @param userMessage the user's message
     * @return a new LlmRequest
     */
    public static LlmRequest simple(String systemPrompt, String userMessage) {
        return new LlmRequest(systemPrompt, userMessage, List.of(), null, List.of());
    }

    /**
     * A completed tool call pair, representing one round of tool invocation in a multi-turn
     * conversation.
     *
     * @param toolCallId the unique ID assigned by the LLM to this tool call
     * @param toolName the name of the tool that was invoked
     * @param toolInput the input parameters the LLM passed to the tool
     * @param toolOutput the result returned by the tool executor
     */
    public record ToolCallPair(
            String toolCallId,
            String toolName,
            Map<String, Object> toolInput,
            Map<String, Object> toolOutput) {}
}
