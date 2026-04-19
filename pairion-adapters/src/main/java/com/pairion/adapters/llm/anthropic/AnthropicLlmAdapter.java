package com.pairion.adapters.llm.anthropic;

import com.pairion.adapters.llm.spi.LlmAdapter;
import com.pairion.core.llm.LlmCapabilities;
import com.pairion.core.llm.LlmEvent;
import com.pairion.core.llm.LlmRequest;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * LLM adapter backed by the Anthropic Claude API via the official Java SDK.
 *
 * <p>Activated when {@code pairion.adapters.llm=anthropic} (the default). Reads the API key from
 * the {@code ANTHROPIC_API_KEY} environment variable. If the key is absent, the adapter reports
 * itself as unavailable but does not prevent server startup.
 */
@Component
@ConditionalOnProperty(
        name = "pairion.adapters.llm",
        havingValue = "anthropic",
        matchIfMissing = true)
public class AnthropicLlmAdapter implements LlmAdapter {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmAdapter.class);

    private final AnthropicClientWrapper clientWrapper;
    private final String defaultModel;

    /**
     * Constructs the adapter with the given client wrapper and default model.
     *
     * @param clientWrapper abstraction over the Anthropic SDK client
     * @param defaultModel the default model identifier
     */
    public AnthropicLlmAdapter(
            AnthropicClientWrapper clientWrapper,
            @Value("${pairion.adapters.llm.anthropic.model:claude-sonnet-4-6}")
                    String defaultModel) {
        this.clientWrapper = clientWrapper;
        this.defaultModel = defaultModel;
        log.info("Anthropic LLM adapter initialized with default model: {}", defaultModel);
    }

    /**
     * Returns the adapter name.
     *
     * @return "anthropic"
     */
    @Override
    public String name() {
        return "anthropic";
    }

    /**
     * Returns the current capabilities.
     *
     * @return capabilities reflecting API key availability
     */
    @Override
    public LlmCapabilities capabilities() {
        return clientWrapper.isAvailable()
                ? new LlmCapabilities(true, true, true)
                : LlmCapabilities.unavailable();
    }

    /**
     * Generates a streaming completion by calling the Anthropic Messages API.
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
                model,
                request.systemPrompt(),
                request.userMessage(),
                request.toolDefinitions(),
                request.toolCallHistory(),
                new AnthropicClientWrapper.StreamCallback() {
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
                            String toolCallId,
                            String toolName,
                            java.util.Map<String, Object> input) {
                        log.info(
                                "llm.tool_call: id={}, tool={}, sessionId={}",
                                toolCallId,
                                toolName,
                                sessionId);
                        eventConsumer.accept(new LlmEvent.ToolCallRequest(toolCallId, toolName, input));
                    }

                    @Override
                    public void onComplete() {
                        long totalMs = System.currentTimeMillis() - startTime;
                        log.info(
                                "llm.total_ms={}, llm.output_tokens={}, sessionId={}",
                                totalMs,
                                tokenCount,
                                sessionId);
                        eventConsumer.accept(new LlmEvent.Stop(tokenCount));
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
