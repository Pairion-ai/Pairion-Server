package com.pairion.memory.vectorstore;

import com.pairion.adapters.embedding.spi.EmbeddingAdapter;
import com.pairion.adapters.vectorstore.spi.VectorStoreAdapter;
import com.pairion.memory.entity.VectorEntry;
import com.pairion.memory.repository.VectorEntryRepository;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * SQLite-backed implementation of {@link VectorStoreAdapter}.
 *
 * <p>Stores embedding vectors as little-endian IEEE 754 blobs in the SQLite database. Similarity
 * search is performed in-process by loading all user vectors and computing cosine similarity.
 *
 * <p>If the {@link EmbeddingAdapter} is unavailable, store and search operations degrade
 * gracefully: store logs a warning and returns without saving; search returns an empty list.
 */
@Component
public class SqliteVectorStore implements VectorStoreAdapter {

    private static final Logger log = LoggerFactory.getLogger(SqliteVectorStore.class);

    private final VectorEntryRepository vectorEntryRepository;

    @Nullable private final EmbeddingAdapter embeddingAdapter;

    /**
     * Constructs the SQLite vector store with its repository and embedding adapter dependencies.
     *
     * <p>If {@code embeddingAdapter} is {@code null} (e.g., the embedding adapter is disabled via
     * configuration), {@link #store} is a no-op and {@link #search} returns an empty list.
     *
     * @param vectorEntryRepository the repository for vector entry persistence
     * @param embeddingAdapter the embedding adapter for text vectorization; may be null when the
     *     embedding adapter is disabled
     */
    public SqliteVectorStore(
            VectorEntryRepository vectorEntryRepository,
            @Nullable EmbeddingAdapter embeddingAdapter) {
        this.vectorEntryRepository = vectorEntryRepository;
        this.embeddingAdapter = embeddingAdapter;
    }

    /**
     * Returns the adapter name.
     *
     * @return {@code "sqlite-vector-store"}
     */
    @Override
    public String name() {
        return "sqlite-vector-store";
    }

    /**
     * Embeds the given text and stores it as a vector entry in the SQLite database.
     *
     * <p>If the embedding adapter returns empty (service unavailable), logs a warning and returns
     * without persisting anything. If an entry already exists for the given content type and ID, it
     * is updated in place.
     *
     * @param userId the user the entry belongs to
     * @param contentType the content type label
     * @param contentId the source entity ID
     * @param text the text to embed and store
     */
    @Override
    public void store(String userId, String contentType, UUID contentId, String text) {
        if (embeddingAdapter == null) {
            log.warn(
                    "vector.store.skipped: no embedding adapter configured for contentType={},"
                            + " contentId={}",
                    contentType,
                    contentId);
            return;
        }
        Optional<float[]> embedding = embeddingAdapter.embed(text);
        if (embedding.isEmpty()) {
            log.warn(
                    "vector.store.skipped: embedding unavailable for contentType={}, contentId={}",
                    contentType,
                    contentId);
            return;
        }

        byte[] embeddingBytes = floatArrayToBytes(embedding.get());

        VectorEntry entry =
                vectorEntryRepository
                        .findByContentTypeAndContentId(contentType, contentId)
                        .orElse(new VectorEntry());

        entry.setUserId(userId);
        entry.setContentType(contentType);
        entry.setContentId(contentId);
        entry.setText(text);
        entry.setEmbedding(embeddingBytes);
        if (entry.getCreatedAt() == null) {
            entry.setCreatedAt(Instant.now());
        }

        vectorEntryRepository.save(entry);
        log.debug(
                "vector.stored: contentType={}, contentId={}, dims={}",
                contentType,
                contentId,
                embedding.get().length);
    }

    /**
     * Searches for the most semantically similar entries for a user.
     *
     * <p>If the embedding adapter is unavailable or fails to embed the query, returns an empty
     * list. Malformed BLOB entries are skipped with a warning.
     *
     * @param userId the user to search within
     * @param queryText the search query text
     * @param topK maximum number of results to return
     * @return list of results sorted by cosine similarity descending
     */
    @Override
    public List<VectorSearchResult> search(String userId, String queryText, int topK) {
        if (embeddingAdapter == null || !embeddingAdapter.isAvailable()) {
            return List.of();
        }

        Optional<float[]> queryEmbedding = embeddingAdapter.embed(queryText);
        if (queryEmbedding.isEmpty()) {
            return List.of();
        }

        float[] queryVector = queryEmbedding.get();
        List<VectorEntry> entries = vectorEntryRepository.findByUserId(userId);
        List<VectorSearchResult> results = new ArrayList<>();

        for (VectorEntry entry : entries) {
            try {
                float[] entryVector = bytesToFloatArray(entry.getEmbedding());
                double score = cosineSimilarity(queryVector, entryVector);
                results.add(
                        new VectorSearchResult(
                                entry.getContentType(),
                                entry.getContentId(),
                                entry.getText(),
                                score));
            } catch (Exception e) {
                log.warn(
                        "vector.search.malformed: skipping entry id={}, error={}",
                        entry.getId(),
                        e.getMessage());
            }
        }

        results.sort(Comparator.comparingDouble(VectorSearchResult::score).reversed());
        return results.size() > topK ? results.subList(0, topK) : results;
    }

    /**
     * Deletes the vector entry for a given content type and ID.
     *
     * @param contentType the content type label
     * @param contentId the source entity ID
     */
    @Override
    public void delete(String contentType, UUID contentId) {
        vectorEntryRepository.deleteByContentTypeAndContentId(contentType, contentId);
        log.debug("vector.deleted: contentType={}, contentId={}", contentType, contentId);
    }

    /**
     * Serializes a float array to little-endian IEEE 754 bytes.
     *
     * @param floats the float array to serialize
     * @return the byte array representation
     */
    static byte[] floatArrayToBytes(float[] floats) {
        ByteBuffer buffer =
                ByteBuffer.allocate(floats.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : floats) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }

    /**
     * Deserializes a little-endian IEEE 754 byte array to a float array.
     *
     * @param bytes the byte array to deserialize
     * @return the float array
     * @throws IllegalArgumentException if bytes length is not a multiple of 4
     */
    static float[] bytesToFloatArray(byte[] bytes) {
        if (bytes.length % Float.BYTES != 0) {
            throw new IllegalArgumentException(
                    "Byte array length " + bytes.length + " is not a multiple of 4");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] floats = new float[bytes.length / Float.BYTES];
        for (int i = 0; i < floats.length; i++) {
            floats[i] = buffer.getFloat();
        }
        return floats;
    }

    /**
     * Computes the cosine similarity between two vectors.
     *
     * <p>Returns 0.0 if either vector has zero magnitude.
     *
     * @param a the first vector
     * @param b the second vector
     * @return cosine similarity in [0.0, 1.0] range
     */
    static double cosineSimilarity(float[] a, float[] b) {
        int len = Math.min(a.length, b.length);
        double dot = 0.0;
        double magA = 0.0;
        double magB = 0.0;
        for (int i = 0; i < len; i++) {
            dot += (double) a[i] * b[i];
            magA += (double) a[i] * a[i];
            magB += (double) b[i] * b[i];
        }
        magA = Math.sqrt(magA);
        magB = Math.sqrt(magB);
        if (magA == 0.0 || magB == 0.0) {
            return 0.0;
        }
        return dot / (magA * magB);
    }
}
