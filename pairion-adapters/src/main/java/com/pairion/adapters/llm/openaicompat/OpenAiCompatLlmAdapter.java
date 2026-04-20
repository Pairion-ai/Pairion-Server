package com.pairion.adapters.llm.openaicompat;

import com.pairion.adapters.llm.spi.LlmAdapter;
import com.pairion.core.llm.LlmCapabilities;
import com.pairion.core.llm.LlmEvent;
import com.pairion.core.llm.LlmRequest;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * LLM adapter backed by any OpenAI-compatible Chat Completions API.
 *
 * <p>Activated when {@code pairion.adapters.llm=openaicompat}. Compatible with LM Studio, Ollama,
 * OpenAI, xAI (Grok), Groq, DeepSeek, vLLM, and any other OpenAI-compatible backend.
 *
 * <p>Configuration properties:
 * <ul>
 *   <li>{@code pairion.adapters.llm.openaicompat.baseUrl} — the API base URL (default:
 *       {@code http://localhost:1234/v1})
 *   <li>{@code pairion.adapters.llm.openaicompat.model} — the default model identifier (default:
 *       {@code gpt-4o-mini})
 *   <li>{@code pairion.adapters.llm.openaicompat.apiKey} — the API key, empty for local
 *       deployments (default: empty string)
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "pairion.adapters.llm", havingValue = "openaicompat")
public class OpenAiCompatLlmAdapter implements LlmAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatLlmAdapter.class);

    private final OpenAiCompatClientWrapper clientWrapper;
    private final String baseUrl;
    private final String defaultModel;
    private final String apiKey;

    /**
     * Constructs the adapter with the given client wrapper and configuration values.
     *
     * @param clientWrapper abstraction over the OpenAI-compatible HTTP client
     * @param baseUrl the base URL of the OpenAI-compatible endpoint
     * @param defaultModel the default model identifier used when the request does not specify one
     * @param apiKey the API key; may be empty for local deployments that require no authentication
     */
    public OpenAiCompatLlmAdapter(
            OpenAiCompatClientWrapper clientWrapper,
            @Value("${pairion.adapters.llm.openaicompat.baseUrl:http://localhost:1234/v1}")
                    String baseUrl,
            @Value("${pairion.adapters.llm.openaicompat.model:gpt-4o-mini}") String defaultModel,
            @Value("${pairion.adapters.llm.openaicompat.apiKey:}") String apiKey) {
        this.clientWrapper = clientWrapper;
        this.baseUrl = baseUrl;
        this.defaultModel = defaultModel;
        this.apiKey = apiKey;
        log.info(
                "OpenAI-compatible LLM adapter initialized: baseUrl={}, model={}",
                baseUrl,
                defaultModel);
    }

    /**
     * Returns the adapter name.
     *
     * @return "openaicompat"
     */
    @Override
    public String name() {
        return "openaicompat";
    }

    /**
     * Returns the current capabilities of this adapter.
     *
     * @return capabilities reflecting base URL availability
     */
    @Override
    public LlmCapabilities capabilities() {
        return clientWrapper.isAvailable()
                ? new LlmCapabilities(true, true, true)
                : LlmCapabilities.unavailable();
    }

    /**
     * Generates a streaming completion by calling the OpenAI-compatible Chat Completions API.
     *
     * <p>Uses the model from the request if specified; otherwise falls back to the configured
     * default model. Logs first-token latency and total generation time.
     *
     * @param request the generation request
     * @param eventConsumer callback receiving streaming events
     */
    @Override
    public void generate(LlmRequest request, Consumer<LlmEvent> eventConsumer) {
        String model = request.model() != null ? request.model() : defaultModel;
        String sessionId = MDC.get("sessionId");

        log.info("LLM generation starting: model={}, sessionId={}", model, sessionId);
        long startTime = System.currentTimeMillis();

        clientWrapper.streamCompletion(
                baseUrl,
                apiKey,
                model,
                request.systemPrompt(),
                request.userMessage(),
                request.toolDefinitions(),
                request.toolCallHistory(),
                new OpenAiCompatClientWrapper.StreamCallback() {
                    private boolean firstTokenLogged = false;
                    private int tokenCount = 0;

                    @Override
                    public void onToken(String delta) {
                        if (!firstTokenLogged) {
                            long firstTokenMs = System.currentTimeMillis() - startTime;
                            log.info(
                                    "llm.first_token_ms={}, sessionId={}", firstTokenMs, sessionId);
                            firstTokenLogged = true;
                        }
                        tokenCount++;
                        eventConsumer.accept(new LlmEvent.TokenDelta(delta));
                    }

                    @Override
                    public void onToolCallRequest(
                            String toolCallId, String toolName, Map<String, Object> input) {
                        log.info(
                                "llm.tool_call: id={}, tool={}, sessionId={}",
                                toolCallId,
                                toolName,
                                sessionId);
                        eventConsumer.accept(
                                new LlmEvent.ToolCallRequest(toolCallId, toolName, input));
                    }

                    @Override
                    public void onComplete(int outputTokens) {
                        long totalMs = System.currentTimeMillis() - startTime;
                        log.info(
                                "llm.total_ms={}, llm.output_tokens={}, sessionId={}",
                                totalMs,
                                outputTokens,
                                sessionId);
                        eventConsumer.accept(new LlmEvent.Stop(outputTokens));
                    }

                    @Override
                    public void onError(Exception e) {
                        log.error(
                                "LLM generation failed: sessionId={}, error={}",
                                sessionId,
                                e.getMessage());
                        eventConsumer.accept(new LlmEvent.Stop(tokenCount));
                    }
                });
    }
}
