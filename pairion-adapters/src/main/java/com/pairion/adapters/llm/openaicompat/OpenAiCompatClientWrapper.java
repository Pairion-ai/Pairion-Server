package com.pairion.adapters.llm.openaicompat;

import com.pairion.core.llm.LlmRequest;
import com.pairion.core.llm.ToolDefinition;
import java.util.List;
import java.util.Map;

/**
 * Internal abstraction boundary over the OpenAI-compatible HTTP client.
 *
 * <p>The production implementation ({@link DefaultOpenAiCompatClientWrapper}) makes real HTTP calls
 * to the configured endpoint. Test implementations return canned responses. This boundary enables
 * 100% coverage of {@link OpenAiCompatLlmAdapter} without real API calls.
 */
public interface OpenAiCompatClientWrapper {

    /**
     * Whether the adapter is available (base URL configured and reachable).
     *
     * @return true if the adapter can accept requests
     */
    boolean isAvailable();

    /**
     * Streams a completion from an OpenAI-compatible Chat Completions endpoint.
     *
     * @param baseUrl the base URL of the API endpoint (e.g., {@code http://localhost:1234/v1})
     * @param apiKey the API key, or empty string if not required
     * @param model the model identifier
     * @param systemPrompt the system prompt
     * @param userMessage the user's message
     * @param tools tool definitions available for tool-use (may be empty)
     * @param toolCallHistory completed tool call pairs from prior turns (may be empty)
     * @param callback callback receiving streaming events
     */
    void streamCompletion(
            String baseUrl,
            String apiKey,
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
         * Called when the LLM requests a tool call. Input JSON has been fully accumulated and
         * parsed before this is called.
         *
         * @param toolCallId the unique ID for this tool call
         * @param toolName the tool name requested
         * @param input the parsed input parameters
         */
        void onToolCallRequest(String toolCallId, String toolName, Map<String, Object> input);

        /**
         * Called when generation completes successfully.
         *
         * @param outputTokens the total number of output tokens generated
         */
        void onComplete(int outputTokens);

        /**
         * Called when an error occurs during generation.
         *
         * @param e the error
         */
        void onError(Exception e);
    }
}
