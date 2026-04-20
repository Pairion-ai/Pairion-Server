package com.pairion.adapters.llm.openaicompat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stateful parser for OpenAI-compatible Server-Sent Events (SSE) streams.
 *
 * <p>Processes {@code data:} prefixed JSON lines from the Chat Completions streaming API.
 * Accumulates tool call input JSON across multiple delta chunks (keyed by tool call index), and
 * dispatches complete tool calls when {@code finish_reason: "tool_calls"} is received. Extracts
 * output token count from the {@code usage} field when {@code stream_options.include_usage} was
 * set on the request.
 *
 * <p>Extracted from {@link DefaultOpenAiCompatClientWrapper} to enable full unit test coverage
 * without live HTTP connections.
 */
class OpenAiCompatSseParser {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatSseParser.class);
    private static final String DATA_PREFIX = "data: ";
    private static final String DONE_SENTINEL = "data: [DONE]";

    private final ObjectMapper objectMapper;
    private final Map<Integer, ToolCallAccumulator> toolCallAccumulators = new HashMap<>();
    private int outputTokens = 0;

    /**
     * Constructs the parser with the given Jackson ObjectMapper.
     *
     * @param objectMapper the Jackson ObjectMapper for JSON deserialization
     */
    OpenAiCompatSseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Processes a single SSE line and dispatches events to the callback.
     *
     * <p>Empty lines, lines starting with {@code :} (SSE comments), and non-{@code data:} lines
     * are silently ignored. The {@code [DONE]} sentinel causes {@link Callback#onDone(int)} to be
     * invoked with the accumulated output token count.
     *
     * @param line the raw SSE line
     * @param callback the event callback
     */
    void parseLine(String line, Callback callback) {
        if (line == null || line.isBlank() || line.startsWith(":")) {
            return;
        }
        if (DONE_SENTINEL.equals(line)) {
            callback.onDone(outputTokens);
            return;
        }
        if (!line.startsWith(DATA_PREFIX)) {
            return;
        }

        String json = line.substring(DATA_PREFIX.length());
        try {
            JsonNode root = objectMapper.readTree(json);

            // Extract usage if present (appears in the last data chunk when include_usage=true)
            JsonNode usageNode = root.get("usage");
            if (usageNode != null && usageNode.has("completion_tokens")) {
                outputTokens = usageNode.get("completion_tokens").asInt();
            }

            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) {
                return;
            }

            JsonNode choice = choices.get(0);
            JsonNode delta = choice.get("delta");
            if (delta == null) {
                return;
            }

            // Text token delta
            JsonNode contentNode = delta.get("content");
            if (contentNode != null && !contentNode.isNull()) {
                String content = contentNode.asText();
                if (!content.isEmpty()) {
                    callback.onToken(content);
                }
            }

            // Tool call deltas — accumulate by index across multiple SSE chunks
            JsonNode toolCallsNode = delta.get("tool_calls");
            if (toolCallsNode != null && toolCallsNode.isArray()) {
                for (JsonNode tcNode : toolCallsNode) {
                    int index = tcNode.has("index") ? tcNode.get("index").asInt() : 0;
                    JsonNode fnNodeForLambda = tcNode.get("function");
                    ToolCallAccumulator acc =
                            toolCallAccumulators.computeIfAbsent(
                                    index,
                                    i -> {
                                        String id =
                                                tcNode.has("id")
                                                        ? tcNode.get("id").asText("")
                                                        : "";
                                        String name = "";
                                        if (fnNodeForLambda != null
                                                && fnNodeForLambda.has("name")) {
                                            name = fnNodeForLambda.get("name").asText("");
                                        }
                                        return new ToolCallAccumulator(id, name);
                                    });

                    // Update id or name if they arrive in a later chunk
                    if (tcNode.has("id") && acc.getId().isEmpty()) {
                        acc.setId(tcNode.get("id").asText(""));
                    }
                    JsonNode fnNode = tcNode.get("function");
                    if (fnNode != null) {
                        if (fnNode.has("name") && acc.getName().isEmpty()) {
                            acc.setName(fnNode.get("name").asText(""));
                        }
                        if (fnNode.has("arguments")) {
                            acc.appendJson(fnNode.get("arguments").asText(""));
                        }
                    }
                }
            }

            // Dispatch accumulated tool calls when finish_reason indicates tool use
            JsonNode finishReasonNode = choice.get("finish_reason");
            if (finishReasonNode != null && !finishReasonNode.isNull()) {
                if ("tool_calls".equals(finishReasonNode.asText())) {
                    dispatchAllToolCalls(callback);
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse SSE line: json={}, error={}", json, e.getMessage());
        }
    }

    /**
     * Dispatches all accumulated tool calls to the callback, then clears the accumulator map.
     *
     * @param callback the event callback
     */
    private void dispatchAllToolCalls(Callback callback) {
        for (ToolCallAccumulator acc : toolCallAccumulators.values()) {
            try {
                String json = acc.getJson();
                @SuppressWarnings("unchecked")
                Map<String, Object> input =
                        json.isBlank()
                                ? Map.of()
                                : (Map<String, Object>) objectMapper.readValue(json, Map.class);
                callback.onToolCallRequest(acc.getId(), acc.getName(), input);
            } catch (Exception e) {
                log.error(
                        "Failed to parse tool call input: id={}, json={}, error={}",
                        acc.getId(),
                        acc.getJson(),
                        e.getMessage());
                callback.onToolCallRequest(acc.getId(), acc.getName(), Map.of());
            }
        }
        toolCallAccumulators.clear();
    }

    /** Callback interface for parsed SSE events. */
    interface Callback {

        /**
         * Called for each text token delta.
         *
         * @param delta the token text
         */
        void onToken(String delta);

        /**
         * Called when a complete tool call request is available.
         *
         * @param toolCallId the unique tool call ID
         * @param toolName the tool name
         * @param input the parsed input parameters
         */
        void onToolCallRequest(String toolCallId, String toolName, Map<String, Object> input);

        /**
         * Called when the stream ends (the {@code [DONE]} sentinel is received).
         *
         * @param outputTokens the total output token count (0 if usage was not reported)
         */
        void onDone(int outputTokens);
    }

    /** Accumulates tool call ID, name, and streamed input JSON fragments for a single tool call. */
    private static final class ToolCallAccumulator {

        private String id;
        private String name;
        private final StringBuilder jsonBuffer = new StringBuilder();

        ToolCallAccumulator(String id, String name) {
            this.id = id;
            this.name = name;
        }

        /** Appends a partial JSON fragment to the accumulated input JSON buffer. */
        void appendJson(String partial) {
            jsonBuffer.append(partial);
        }

        /**
         * Returns the accumulated tool call ID.
         *
         * @return tool call ID
         */
        String getId() {
            return id;
        }

        /**
         * Sets the tool call ID (used when ID arrives in a later SSE chunk).
         *
         * @param id the tool call ID
         */
        void setId(String id) {
            this.id = id;
        }

        /**
         * Returns the accumulated tool name.
         *
         * @return tool name
         */
        String getName() {
            return name;
        }

        /**
         * Sets the tool name (used when name arrives in a later SSE chunk).
         *
         * @param name the tool name
         */
        void setName(String name) {
            this.name = name;
        }

        /**
         * Returns the fully accumulated input JSON string.
         *
         * @return accumulated JSON
         */
        String getJson() {
            return jsonBuffer.toString();
        }
    }
}
