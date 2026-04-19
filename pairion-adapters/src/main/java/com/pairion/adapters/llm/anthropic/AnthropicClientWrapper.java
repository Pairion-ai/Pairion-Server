package com.pairion.adapters.llm.anthropic;

import com.pairion.core.llm.LlmRequest;
import com.pairion.core.llm.ToolDefinition;
import java.util.List;
import java.util.Map;

/**
 * Internal abstraction boundary over the Anthropic Java SDK client.
 *
 * <p>The production implementation ({@link DefaultAnthropicClientWrapper}) calls the real Anthropic
 * API via the official SDK. Test implementations return canned responses. This boundary enables
 * 100% coverage of {@link AnthropicLlmAdapter} without real API calls.
 */
public interface AnthropicClientWrapper {

    /**
     * Whether the Anthropic API is available (API key configured).
     *
     * @return true if the adapter can accept requests
     */
    boolean isAvailable();

    /**
     * Streams a completion from the Anthropic Messages API.
     *
     * @param model the model identifier
     * @param systemPrompt the system prompt
     * @param userMessage the user's message
     * @param tools tool definitions (may be empty)
     * @param toolCallHistory completed tool call pairs from prior turns (may be empty)
     * @param callback callback receiving streaming events
     */
    void streamCompletion(
            String model,
            String systemPrompt,
            String userMessage,
            List<ToolDefinition> tools,
            List<LlmRequest.ToolCallPair> toolCallHistory,
            StreamCallback callback);

    /** Callback interface for streaming completion events. */
    interface StreamCallback {

        /**
         * Called for each text token delta.
         *
         * @param delta the token text
         */
        void onToken(String delta);

        /**
         * Called when the LLM requests a tool call. The input JSON has been fully accumulated and
         * parsed before this is called.
         *
         * @param toolCallId the unique ID for this tool call
         * @param toolName the tool name requested
         * @param input the parsed input parameters
         */
        void onToolCallRequest(String toolCallId, String toolName, Map<String, Object> input);

        /** Called when generation completes successfully (including after tool_use turns). */
        void onComplete();

        /**
         * Called when an error occurs during generation.
         *
         * @param e the error
         */
        void onError(Exception e);
    }
}
