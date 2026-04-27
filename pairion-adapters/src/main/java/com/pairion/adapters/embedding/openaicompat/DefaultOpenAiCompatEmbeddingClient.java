package com.pairion.adapters.embedding.openaicompat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Production implementation of {@link OpenAiCompatEmbeddingClient} using {@link
 * java.net.http.HttpClient}.
 *
 * <p>Posts to {@code {baseUrl}/embeddings} with a JSON body and parses {@code data[0].embedding} as
 * a float array. Returns {@link Optional#empty()} on any error.
 *
 * <p>This class makes real HTTP calls and is excluded from JaCoCo coverage. It is unit-tested via
 * the {@link OpenAiCompatEmbeddingClient} boundary injected into {@link
 * OpenAiCompatEmbeddingAdapter}.
 */
@Component
@ConditionalOnProperty(
        name = "pairion.adapters.embedding.enabled",
        havingValue = "true",
        matchIfMissing = true)
class DefaultOpenAiCompatEmbeddingClient implements OpenAiCompatEmbeddingClient {

    private static final Logger log =
            LoggerFactory.getLogger(DefaultOpenAiCompatEmbeddingClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String model;
    private final Duration requestTimeout;

    /**
     * Spring-managed constructor. Builds an {@link HttpClient} with a 10-second connect timeout and
     * configures a 15-second per-request timeout.
     *
     * @param baseUrl the base URL of the OpenAI-compatible embeddings endpoint
     * @param model the embedding model identifier
     */
    DefaultOpenAiCompatEmbeddingClient(
            @Value("${pairion.adapters.embedding.baseUrl:http://localhost:11434/v1}")
                    String baseUrl,
            @Value("${pairion.adapters.embedding.model:nomic-embed-text}") String model) {
        this(
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(10))
                        .build(),
                new ObjectMapper(),
                baseUrl,
                model,
                Duration.ofSeconds(15));
    }

    /**
     * Package-private full-arg constructor for use in tests (in the same package).
     *
     * @param httpClient the HTTP client configured with connect timeout
     * @param objectMapper the Jackson mapper for request/response serialization
     * @param baseUrl the base URL of the OpenAI-compatible embeddings endpoint
     * @param model the embedding model identifier
     * @param requestTimeout the per-request timeout
     */
    DefaultOpenAiCompatEmbeddingClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String baseUrl,
            String model,
            Duration requestTimeout) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.model = model;
        this.requestTimeout = requestTimeout;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sends a HEAD request to {@code baseUrl} with a 3-second timeout. Returns {@code true} if
     * the response status is below 500. Returns {@code false} on any error or timeout.
     */
    @Override
    public boolean checkAvailability() {
        try {
            HttpClient probe =
                    HttpClient.newBuilder()
                            .version(HttpClient.Version.HTTP_1_1)
                            .connectTimeout(Duration.ofSeconds(3))
                            .build();
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl))
                            .timeout(Duration.ofSeconds(3))
                            .method("HEAD", HttpRequest.BodyPublishers.noBody())
                            .build();
            HttpResponse<Void> response =
                    probe.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Posts to {@code {baseUrl}/embeddings} and parses {@code data[0].embedding}. Returns empty
     * on any error.
     */
    @Override
    public Optional<float[]> fetchEmbedding(String text) {
        try {
            String body =
                    objectMapper.writeValueAsString(
                            java.util.Map.of("model", model, "input", text));

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/embeddings"))
                            .header("Content-Type", "application/json")
                            .timeout(requestTimeout)
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn(
                        "embedding.http.error: status={}, body={}",
                        response.statusCode(),
                        response.body());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode embeddingNode = root.path("data").path(0).path("embedding");
            if (!embeddingNode.isArray()) {
                log.warn("embedding.parse.error: no data[0].embedding array in response");
                return Optional.empty();
            }

            float[] vector = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                vector[i] = (float) embeddingNode.get(i).asDouble();
            }
            return Optional.of(vector);

        } catch (Exception e) {
            log.warn("embedding.fetch.error: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
