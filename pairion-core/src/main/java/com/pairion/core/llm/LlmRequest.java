package com.pairion.core.llm;

import java.util.List;

/**
 * Request to generate a streaming LLM completion.
 *
 * @param systemPrompt the system prompt (SOUL + memory context)
 * @param userMessage the user's message (typically the final transcript)
 * @param toolDefinitions tool definitions available for tool-use (may be empty)
 * @param model the model identifier to use, or null for the adapter default
 */
public record LlmRequest(
        String systemPrompt,
        String userMessage,
        List<ToolDefinition> toolDefinitions,
        String model) {

    /**
     * Creates a simple request with no tools and the default model.
     *
     * @param systemPrompt the system prompt
     * @param userMessage the user's message
     * @return a new LlmRequest
     */
    public static LlmRequest simple(String systemPrompt, String userMessage) {
        return new LlmRequest(systemPrompt, userMessage, List.of(), null);
    }
}
