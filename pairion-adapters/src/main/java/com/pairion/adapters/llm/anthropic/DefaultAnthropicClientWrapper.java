package com.pairion.adapters.llm.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.RawContentBlockDelta;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.TextDelta;
import com.pairion.core.llm.ToolDefinition;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Production implementation of {@link AnthropicClientWrapper} using the official Anthropic Java
 * SDK.
 *
 * <p>This is the <strong>only</strong> class in the codebase that directly imports {@code
 * com.anthropic.*} types. All Anthropic SDK usage is isolated here.
 */
@Component
public class DefaultAnthropicClientWrapper implements AnthropicClientWrapper {

    private static final Logger log = LoggerFactory.getLogger(DefaultAnthropicClientWrapper.class);

    private final AnthropicClient client;
    private final boolean available;

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
     * Streams a completion from the Anthropic Messages API.
     *
     * @param model the model identifier
     * @param systemPrompt the system prompt
     * @param userMessage the user's message
     * @param tools tool definitions (unused in PS-002 — scaffolded for PS-003)
     * @param callback callback receiving streaming events
     */
    @Override
    public void streamCompletion(
            String model,
            String systemPrompt,
            String userMessage,
            List<ToolDefinition> tools,
            StreamCallback callback) {
        if (!available) {
            callback.onError(new IllegalStateException("Anthropic API key not configured"));
            return;
        }

        try {
            MessageCreateParams params =
                    MessageCreateParams.builder()
                            .model(Model.of(model))
                            .maxTokens(1024)
                            .system(systemPrompt)
                            .addUserMessage(userMessage)
                            .build();

            try (StreamResponse<RawMessageStreamEvent> stream =
                    client.messages().createStreaming(params)) {
                stream.stream()
                        .forEach(
                                event -> {
                                    if (event.isContentBlockDelta()) {
                                        RawContentBlockDelta delta =
                                                event.asContentBlockDelta().delta();
                                        Optional<TextDelta> textDelta = delta.text();
                                        textDelta.ifPresent(td -> callback.onToken(td.text()));
                                    }
                                });
            }
            callback.onComplete();
        } catch (Exception e) {
            log.error("Anthropic streaming failed: {}", e.getMessage());
            callback.onError(e);
        }
    }
}
