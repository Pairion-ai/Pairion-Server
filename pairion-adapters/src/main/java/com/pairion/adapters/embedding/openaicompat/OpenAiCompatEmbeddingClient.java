package com.pairion.adapters.embedding.openaicompat;

import java.util.Optional;

/**
 * Boundary interface for the OpenAI-compatible embeddings HTTP calls.
 *
 * <p>Separating this interface allows {@link OpenAiCompatEmbeddingAdapter} to be unit-tested
 * without a real HTTP server. The production implementation is {@link
 * DefaultOpenAiCompatEmbeddingClient}, which is excluded from JaCoCo coverage.
 */
interface OpenAiCompatEmbeddingClient {

    /**
     * Fetches the embedding vector for the given text from the remote embeddings endpoint.
     *
     * @param text the text to embed
     * @return the embedding vector, or empty on any error (connection refused, timeout, non-200
     *     response)
     */
    Optional<float[]> fetchEmbedding(String text);

    /**
     * Checks whether the embeddings endpoint is reachable by issuing a lightweight HEAD request.
     *
     * @return true if the endpoint responds with a status below 500; false on any error or timeout
     */
    boolean checkAvailability();
}
