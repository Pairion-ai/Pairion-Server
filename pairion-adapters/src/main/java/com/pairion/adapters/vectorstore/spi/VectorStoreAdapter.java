package com.pairion.adapters.vectorstore.spi;

import java.util.List;
import java.util.UUID;

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

    /**
     * Record returned from a similarity search.
     *
     * @param contentType the type of content (e.g., "episode_summary", "turn", "preference")
     * @param contentId the ID of the source entity
     * @param text the text that was embedded
     * @param score cosine similarity score (0.0–1.0)
     */
    record VectorSearchResult(String contentType, UUID contentId, String text, double score) {}

    /**
     * Embeds the given text and stores it as a vector entry.
     *
     * @param userId the user the entry belongs to
     * @param contentType the content type label
     * @param contentId the source entity ID
     * @param text the text to embed and store
     */
    void store(String userId, String contentType, UUID contentId, String text);

    /**
     * Searches for the most semantically similar entries for a user.
     *
     * @param userId the user to search within
     * @param queryText the search query text
     * @param topK maximum number of results to return
     * @return list of results sorted by similarity descending
     */
    List<VectorSearchResult> search(String userId, String queryText, int topK);

    /**
     * Deletes the vector entry for a given content type and ID.
     *
     * @param contentType the content type label
     * @param contentId the source entity ID
     */
    void delete(String contentType, UUID contentId);
}
