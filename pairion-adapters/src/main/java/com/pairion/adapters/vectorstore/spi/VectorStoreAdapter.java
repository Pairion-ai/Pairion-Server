package com.pairion.adapters.vectorstore.spi;

/**
 * Service provider interface for vector store adapters.
 *
 * <p>Implementations wrap vector databases (Qdrant, LanceDB, etc.) and expose a uniform storage and
 * similarity search interface.
 */
public interface VectorStoreAdapter {

    /**
     * Returns the human-readable name of this adapter.
     *
     * @return the adapter name
     */
    String name();
}
