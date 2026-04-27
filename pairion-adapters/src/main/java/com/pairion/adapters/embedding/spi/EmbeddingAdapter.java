package com.pairion.adapters.embedding.spi;

import java.util.Optional;

/**
 * Service provider interface for text embedding adapters.
 *
 * <p>Implementations wrap embedding models (bge-m3, nomic-embed-text, etc.) and expose a uniform
 * vector embedding interface.
 */
public interface EmbeddingAdapter {

    /**
     * Returns the human-readable name of this adapter.
     *
     * @return the adapter name
     */
    String name();

    /**
     * Embeds the given text and returns the embedding vector.
     *
     * @param text the text to embed
     * @return the embedding vector, or empty if the service is unavailable
     */
    Optional<float[]> embed(String text);

    /**
     * Returns true if the embedding service is currently reachable.
     *
     * @return true if available
     */
    boolean isAvailable();
}
