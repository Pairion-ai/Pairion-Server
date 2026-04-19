package com.pairion.adapters.llm.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.RawContentBlockDelta;
import com.anthropic.models.messages.RawContentBlockStartEvent;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUnion;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairion.core.llm.LlmRequest;
import com.pairion.core.llm.ToolDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Production implementation of {@link AnthropicClientWrapper} using the official Anthropic Java
 * SDK.
 *
 * <p>This is the <strong>only</strong> class in the codebase that directly imports {@code
 * com.anthropic.*} types. All Anthropic SDK usage is isolated here.
 *
 * <p>Supports multi-turn tool use: tool definitions are wired into {@link MessageCreateParams}, tool
 * input is accumulated from streaming {@code input_json_delta} events, and completed tool calls are
 * delivered via {@link StreamCallback#onToolCallRequest}. Prior-turn tool call history is replayed
 * as interleaved assistant + user messages.
 */
@Component
public class DefaultAnthropicClientWrapper implements AnthropicClientWrapper {

    private static final Logger log = LoggerFactory.getLogger(DefaultAnthropicClientWrapper.class);

    private final AnthropicClient client;
    private final boolean available;
    private final ObjectMapper objectMapper;

    /** Constructs the wrapper, reading the API key from the environment. */
    public DefaultAnthropicClientWrapper() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
            this.available = true;
            log.info("Anthropic SDK client initialized");
        } else {
            this.client = null;
            this.available = false;
            log.warn("ANTHROPIC_API_KEY not set — Anthropic LLM adapter unavailable");
        }
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Returns whether the API key is configured.
     *
     * @return true if API key is present
     */
    @Override
    public boolean isAvailable() {
        return available;
    }

    /**
     * Streams a completion from the Anthropic Messages API with full tool support.
     *
     * <p>Tool definitions are wired in as {@link ToolUnion} entries. Prior tool call pairs in {@code
     * toolCallHistory} are replayed as interleaved assistant (tool_use) and user (tool_result)
     * message turns. Tool input JSON is accumulated from streaming deltas; when a tool_use content
     * block completes, {@link StreamCallback#onToolCallRequest} is called.
     *
     * @param model the model identifier
     * @param systemPrompt the system prompt
     * @param userMessage the user's message
     * @param tools tool definitions (may be empty)
     * @param toolCallHistory completed tool call pairs from prior turns (may be empty)
     * @param callback callback receiving streaming events
     */
    @Override
    public void streamCompletion(
            String model,
            String systemPrompt,
            String userMessage,
            List<ToolDefinition> tools,
            List<LlmRequest.ToolCallPair> toolCallHistory,
            StreamCallback callback) {
        if (!available) {
            callback.onError(new IllegalStateException("Anthropic API key not configured"));
            return;
        }

        try {
            MessageCreateParams.Builder paramsBuilder =
                    MessageCreateParams.builder()
                            .model(Model.of(model))
                            .maxTokens(1024)
                            .system(systemPrompt)
                            .addUserMessage(userMessage);

            // Wire tool definitions
            if (!tools.isEmpty()) {
                List<ToolUnion> toolUnions = new ArrayList<>();
                for (ToolDefinition td : tools) {
                    toolUnions.add(ToolUnion.ofTool(buildTool(td)));
                }
                paramsBuilder.tools(toolUnions);
            }

            // Replay prior tool call history as interleaved assistant + user messages
            for (LlmRequest.ToolCallPair pair : toolCallHistory) {
                // Assistant message: tool_use block
                ToolUseBlockParam toolUseParam =
                        ToolUseBlockParam.builder()
                                .id(pair.toolCallId())
                                .name(pair.toolName())
                                .input(buildToolUseInput(pair.toolInput()))
                                .build();
                paramsBuilder.addAssistantMessageOfBlockParams(
                        List.of(ContentBlockParam.ofToolUse(toolUseParam)));

                // User message: tool_result block
                String resultJson = objectMapper.writeValueAsString(pair.toolOutput());
                ToolResultBlockParam toolResultParam =
                        ToolResultBlockParam.builder()
                                .toolUseId(pair.toolCallId())
                                .content(resultJson)
                                .build();
                paramsBuilder.addUserMessageOfBlockParams(
                        List.of(ContentBlockParam.ofToolResult(toolResultParam)));
            }

            MessageCreateParams params = paramsBuilder.build();

            // Per-stream state for accumulating tool call input JSON
            Map<Long, ToolCallAccumulator> toolCallAccumulators = new HashMap<>();

            try (StreamResponse<RawMessageStreamEvent> stream =
                    client.messages().createStreaming(params)) {
                stream.stream()
                        .forEach(
                                event -> {
                                    if (event.isContentBlockStart()) {
                                        handleBlockStart(
                                                event.asContentBlockStart(), toolCallAccumulators);
                                    } else if (event.isContentBlockDelta()) {
                                        RawContentBlockDelta delta =
                                                event.asContentBlockDelta().delta();
                                        long index = event.asContentBlockDelta().index();
                                        if (delta.isText()) {
                                            callback.onToken(delta.asText().text());
                                        } else if (delta.isInputJson()) {
                                            ToolCallAccumulator acc =
                                                    toolCallAccumulators.get(index);
                                            if (acc != null) {
                                                acc.appendJson(delta.asInputJson().partialJson());
                                            }
                                        }
                                    } else if (event.isContentBlockStop()) {
                                        long index = event.asContentBlockStop().index();
                                        ToolCallAccumulator acc = toolCallAccumulators.get(index);
                                        if (acc != null) {
                                            dispatchToolCallRequest(acc, callback);
                                            toolCallAccumulators.remove(index);
                                        }
                                    }
                                });
            }
            callback.onComplete();
        } catch (Exception e) {
            log.error("Anthropic streaming failed: {}", e.getMessage());
            callback.onError(e);
        }
    }

    /**
     * Handles a content_block_start event, registering tool_use blocks in the accumulator map.
     *
     * @param startEvent the start event
     * @param accumulators map from block index to accumulator
     */
    private void handleBlockStart(
            RawContentBlockStartEvent startEvent,
            Map<Long, ToolCallAccumulator> accumulators) {
        RawContentBlockStartEvent.ContentBlock contentBlock = startEvent.contentBlock();
        if (contentBlock.isToolUse()) {
            ToolUseBlock toolUseBlock = contentBlock.asToolUse();
            accumulators.put(
                    startEvent.index(),
                    new ToolCallAccumulator(toolUseBlock.id(), toolUseBlock.name()));
        }
    }

    /**
     * Parses accumulated tool input JSON and delivers the tool call request to the callback.
     *
     * @param acc the accumulator with complete JSON
     * @param callback the stream callback
     */
    @SuppressWarnings("unchecked")
    private void dispatchToolCallRequest(ToolCallAccumulator acc, StreamCallback callback) {
        try {
            String json = acc.getJson();
            Map<String, Object> input =
                    json.isBlank()
                            ? Map.of()
                            : objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            callback.onToolCallRequest(acc.getId(), acc.getName(), input);
        } catch (Exception e) {
            log.error(
                    "Failed to parse tool call input JSON: id={}, json={}, error={}",
                    acc.getId(),
                    acc.getJson(),
                    e.getMessage());
            callback.onToolCallRequest(acc.getId(), acc.getName(), Map.of());
        }
    }

    /**
     * Builds an Anthropic {@link Tool} from a {@link ToolDefinition}.
     *
     * @param td the tool definition
     * @return the Anthropic Tool
     */
    private Tool buildTool(ToolDefinition td) {
        Tool.InputSchema.Builder schemaBuilder = Tool.InputSchema.builder();

        // Extract "required" from inputSchema if present
        Object required = td.inputSchema().get("required");
        if (required instanceof List<?> reqList) {
            List<String> requiredFields = new ArrayList<>();
            for (Object item : reqList) {
                if (item instanceof String s) {
                    requiredFields.add(s);
                }
            }
            schemaBuilder.required(requiredFields);
        }

        // Pass the full inputSchema as additional properties so the SDK serializes it correctly
        try {
            String schemaJson = objectMapper.writeValueAsString(td.inputSchema());
            Map<String, JsonValue> additionalProps =
                    objectMapper.readValue(schemaJson, new TypeReference<Map<String, Object>>() {})
                            .entrySet().stream()
                            .collect(
                                    java.util.stream.Collectors.toMap(
                                            Map.Entry::getKey,
                                            e -> JsonValue.from(e.getValue())));
            for (Map.Entry<String, JsonValue> entry : additionalProps.entrySet()) {
                schemaBuilder.putAdditionalProperty(entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            log.error("Failed to serialize tool input schema: tool={}, error={}", td.name(), e.getMessage());
        }

        return Tool.builder()
                .name(td.name())
                .description(td.description())
                .inputSchema(schemaBuilder.build())
                .build();
    }

    /**
     * Builds a {@link ToolUseBlockParam.Input} from a raw input map.
     *
     * @param inputMap the raw input map
     * @return the SDK input type
     */
    private ToolUseBlockParam.Input buildToolUseInput(Map<String, Object> inputMap) {
        ToolUseBlockParam.Input.Builder builder = ToolUseBlockParam.Input.builder();
        for (Map.Entry<String, Object> entry : inputMap.entrySet()) {
            builder.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
        }
        return builder.build();
    }

    /** Accumulates tool call ID, name, and streamed input JSON for a single tool_use block. */
    private static final class ToolCallAccumulator {

        private final String id;
        private final String name;
        private final StringBuilder jsonBuffer = new StringBuilder();

        ToolCallAccumulator(String id, String name) {
            this.id = id;
            this.name = name;
        }

        void appendJson(String partial) {
            jsonBuffer.append(partial);
        }

        String getId() {
            return id;
        }

        String getName() {
            return name;
        }

        String getJson() {
            return jsonBuffer.toString();
        }
    }
}
