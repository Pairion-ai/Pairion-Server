package com.pairion.adapters.embedding.spi;

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
}
