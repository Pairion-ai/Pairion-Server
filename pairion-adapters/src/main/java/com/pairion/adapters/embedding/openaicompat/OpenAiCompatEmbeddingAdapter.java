package com.pairion.adapters.embedding.openaicompat;

import com.pairion.adapters.embedding.spi.EmbeddingAdapter;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Embedding adapter backed by any OpenAI-compatible embeddings API.
 *
 * <p>Activated when {@code pairion.adapters.embedding.enabled=true} (default). Compatible with
 * Ollama (nomic-embed-text), OpenAI, and any OpenAI-compatible embeddings endpoint.
 *
 * <p>Configuration properties:
 *
 * <ul>
 *   <li>{@code pairion.adapters.embedding.baseUrl} — the API base URL (default: {@code
 *       http://localhost:11434/v1})
 *   <li>{@code pairion.adapters.embedding.model} — the embedding model identifier (default: {@code
 *       nomic-embed-text})
 * </ul>
 *
 * <p>All HTTP errors are swallowed — the adapter returns {@link Optional#empty()} and logs a
 * warning. The voice pipeline continues without embedding if the service is unavailable.
 */
@Component
@ConditionalOnProperty(
        name = "pairion.adapters.embedding.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OpenAiCompatEmbeddingAdapter implements EmbeddingAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatEmbeddingAdapter.class);

    private final OpenAiCompatEmbeddingClient embeddingClient;
    private final String baseUrl;
    private final AtomicBoolean unavailableLogged = new AtomicBoolean(false);

    /**
     * Spring-managed constructor. Receives the {@link OpenAiCompatEmbeddingClient} implementation
     * and the configured base URL via dependency injection.
     *
     * @param embeddingClient the HTTP boundary that performs real embedding calls
     * @param baseUrl the base URL of the OpenAI-compatible embeddings endpoint, used for log
     *     messages
     */
    public OpenAiCompatEmbeddingAdapter(
            OpenAiCompatEmbeddingClient embeddingClient,
            @Value("${pairion.adapters.embedding.baseUrl:http://localhost:11434/v1}")
                    String baseUrl) {
        this.embeddingClient = embeddingClient;
        this.baseUrl = baseUrl;
        log.info("OpenAI-compatible embedding adapter initialized: baseUrl={}", baseUrl);
    }

    /**
     * Returns the adapter name.
     *
     * @return {@code "openaicompat-embedding"}
     */
    @Override
    public String name() {
        return "openaicompat-embedding";
    }

    /**
     * Returns {@code true} if the embedding service endpoint is reachable.
     *
     * <p>Delegates to the {@link OpenAiCompatEmbeddingClient#checkAvailability()} boundary so that
     * the availability check can be unit-tested without a real HTTP server. Logs a single warning
     * the first time the service is found unavailable; resets the flag once it recovers.
     *
     * @return true if the endpoint responds with status below 500; false otherwise
     */
    @Override
    public boolean isAvailable() {
        boolean reachable = embeddingClient.checkAvailability();
        if (reachable) {
            unavailableLogged.set(false);
        } else if (unavailableLogged.compareAndSet(false, true)) {
            log.warn("embedding.unavailable: endpoint is not reachable at {}", baseUrl);
        }
        return reachable;
    }

    /**
     * Embeds the given text via the configured OpenAI-compatible embeddings endpoint.
     *
     * <p>Returns {@link Optional#empty()} on any error (connection refused, timeout, non-200
     * response, parse failure). Never throws.
     *
     * @param text the text to embed
     * @return the embedding vector, or empty if the service is unavailable or errored
     */
    @Override
    public Optional<float[]> embed(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Optional<float[]> result = embeddingClient.fetchEmbedding(text);
        if (result.isEmpty()) {
            log.warn("embedding.empty: could not embed text of length={}", text.length());
        }
        return result;
    }

    /**
     * Returns the base URL configured for this adapter (used in tests).
     *
     * @return the base URL
     */
    String getBaseUrl() {
        return baseUrl;
    }
}
