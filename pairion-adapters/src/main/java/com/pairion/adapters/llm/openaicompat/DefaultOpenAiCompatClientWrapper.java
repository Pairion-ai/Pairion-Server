package com.pairion.adapters.llm.openaicompat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairion.core.llm.LlmRequest;
import com.pairion.core.llm.ToolDefinition;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Production implementation of {@link OpenAiCompatClientWrapper} using {@link
 * java.net.http.HttpClient}.
 *
 * <p>This is the only class in this package that makes real HTTP calls to the OpenAI-compatible
 * endpoint. {@link OpenAiCompatSseParser} handles SSE line parsing and is fully unit-tested
 * independently.
 *
 * <p>The {@code Authorization: Bearer} header is omitted when {@code apiKey} is blank, enabling
 * use with local deployments (LM Studio, Ollama) that do not require authentication.
 *
 * <p>This class is excluded from JaCoCo coverage because it wraps real network I/O. It is tested
 * via {@code OpenAiCompatContractTest} using an in-process {@code com.sun.net.httpserver.HttpServer}.
 */
@Component
public class DefaultOpenAiCompatClientWrapper implements OpenAiCompatClientWrapper {

    private static final Logger log =
            LoggerFactory.getLogger(DefaultOpenAiCompatClientWrapper.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /** Constructs the wrapper with a redirect-following HTTP client and a fresh ObjectMapper. */
    public DefaultOpenAiCompatClientWrapper() {
        this(
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build(),
                new ObjectMapper());
    }

    /**
     * Package-private constructor for testing via the contract test with an injected HTTP client.
     *
     * @param httpClient the HTTP client to use
     * @param objectMapper the Jackson ObjectMapper to use
     */
    DefaultOpenAiCompatClientWrapper(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns whether the adapter is available. The OpenAI-compatible wrapper is always considered
     * available; URL misconfiguration surfaces as an error at generation time.
     *
     * @return true always
     */
    @Override
    public boolean isAvailable() {
        return true;
    }

    /**
     * Streams a chat completion from the OpenAI-compatible endpoint via Server-Sent Events.
     *
     * <p>Builds the JSON request (system → user → interleaved tool history messages), sends a POST
     * to {@code {baseUrl}/chat/completions}, and reads the SSE response line-by-line using
     * {@link OpenAiCompatSseParser}.
     *
     * @param baseUrl the base URL of the API endpoint
     * @param apiKey the API key (empty string if not required)
     * @param model the model identifier
     * @param systemPrompt the system prompt
     * @param userMessage the user's message
     * @param tools tool definitions (may be empty)
     * @param toolCallHistory completed tool call pairs from prior turns (may be empty)
     * @param callback callback receiving streaming events
     */
    @Override
    public void streamCompletion(
            String baseUrl,
            String apiKey,
            String model,
            String systemPrompt,
            String userMessage,
            List<ToolDefinition> tools,
            List<LlmRequest.ToolCallPair> toolCallHistory,
            StreamCallback callback) {
        try {
            String requestBody =
                    buildRequestJson(model, systemPrompt, userMessage, tools, toolCallHistory);

            HttpRequest.Builder requestBuilder =
                    HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/chat/completions"))
                            .header("Content-Type", "application/json")
                            .header("Accept", "text/event-stream")
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            requestBody, StandardCharsets.UTF_8));

            if (apiKey != null && !apiKey.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            HttpResponse<java.io.InputStream> response =
                    httpClient.send(
                            requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorBody =
                        new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalStateException(
                        "OpenAI-compatible API returned HTTP "
                                + response.statusCode()
                                + ": "
                                + errorBody);
            }

            OpenAiCompatSseParser parser = new OpenAiCompatSseParser(objectMapper);
            OpenAiCompatSseParser.Callback sseCallback =
                    new OpenAiCompatSseParser.Callback() {
                        @Override
                        public void onToken(String delta) {
                            callback.onToken(delta);
                        }

                        @Override
                        public void onToolCallRequest(
                                String toolCallId,
                                String toolName,
                                Map<String, Object> input) {
                            callback.onToolCallRequest(toolCallId, toolName, input);
                        }

                        @Override
                        public void onDone(int outputTokens) {
                            callback.onComplete(outputTokens);
                        }
                    };

            try (BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parser.parseLine(line, sseCallback);
                }
            }

        } catch (Exception e) {
            log.error("OpenAI-compatible streaming failed: {}", e.getMessage());
            callback.onError(e);
        }
    }

    /**
     * Builds the JSON request body for the Chat Completions API.
     *
     * <p>Message order: system → user → (assistant with tool_calls + tool result)*. Tool call
     * history is replayed as interleaved assistant and tool messages per the OpenAI multi-turn
     * format. Tool definitions are wired as {@code tools} with {@code type: "function"}.
     *
     * @param model the model identifier
     * @param systemPrompt the system prompt
     * @param userMessage the user's message
     * @param tools tool definitions
     * @param toolCallHistory prior tool call pairs to replay
     * @return the serialized JSON request body
     */
    private String buildRequestJson(
            String model,
            String systemPrompt,
            String userMessage,
            List<ToolDefinition> tools,
            List<LlmRequest.ToolCallPair> toolCallHistory)
            throws Exception {

        List<Map<String, Object>> messages = new ArrayList<>();

        // System message
        messages.add(Map.of("role", "system", "content", systemPrompt));

        // User message
        messages.add(Map.of("role", "user", "content", userMessage));

        // Replay prior tool call history as interleaved assistant + tool messages
        for (LlmRequest.ToolCallPair pair : toolCallHistory) {
            Map<String, Object> functionObj = new HashMap<>();
            functionObj.put("name", pair.toolName());
            functionObj.put("arguments", objectMapper.writeValueAsString(pair.toolInput()));

            Map<String, Object> toolCallObj = new HashMap<>();
            toolCallObj.put("id", pair.toolCallId());
            toolCallObj.put("type", "function");
            toolCallObj.put("function", functionObj);

            Map<String, Object> assistantMsg = new HashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", null);
            assistantMsg.put("tool_calls", List.of(toolCallObj));
            messages.add(assistantMsg);

            Map<String, Object> toolMsg = new HashMap<>();
            toolMsg.put("role", "tool");
            toolMsg.put("tool_call_id", pair.toolCallId());
            toolMsg.put("content", objectMapper.writeValueAsString(pair.toolOutput()));
            messages.add(toolMsg);
        }

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("model", model);
        requestMap.put("messages", messages);
        requestMap.put("stream", true);
        requestMap.put("stream_options", Map.of("include_usage", true));

        if (!tools.isEmpty()) {
            List<Map<String, Object>> toolsJson = new ArrayList<>();
            for (ToolDefinition td : tools) {
                Map<String, Object> functionDef = new HashMap<>();
                functionDef.put("name", td.name());
                functionDef.put("description", td.description());
                functionDef.put("parameters", td.inputSchema());

                Map<String, Object> toolDef = new HashMap<>();
                toolDef.put("type", "function");
                toolDef.put("function", functionDef);
                toolsJson.add(toolDef);
            }
            requestMap.put("tools", toolsJson);
        }

        return objectMapper.writeValueAsString(requestMap);
    }
}
