package com.pairion.memory.vectorstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pairion.adapters.embedding.spi.EmbeddingAdapter;
import com.pairion.adapters.vectorstore.spi.VectorStoreAdapter;
import com.pairion.memory.entity.VectorEntry;
import com.pairion.memory.repository.VectorEntryRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SqliteVectorStore}. */
class SqliteVectorStoreTest {

    private VectorEntryRepository vectorEntryRepository;
    private EmbeddingAdapter embeddingAdapter;
    private SqliteVectorStore vectorStore;

    @BeforeEach
    void setUp() {
        vectorEntryRepository = mock(VectorEntryRepository.class);
        embeddingAdapter = mock(EmbeddingAdapter.class);
        vectorStore = new SqliteVectorStore(vectorEntryRepository, embeddingAdapter);
    }

    @Test
    void nameReturnsExpectedString() {
        assertThat(vectorStore.name()).isEqualTo("sqlite-vector-store");
    }

    @Test
    void storeWhenEmbeddingAvailableSavesVectorEntry() {
        UUID contentId = UUID.randomUUID();
        float[] embedding = {0.1f, 0.2f, 0.3f};
        when(embeddingAdapter.embed("some text")).thenReturn(Optional.of(embedding));
        when(vectorEntryRepository.findByContentTypeAndContentId("episode_summary", contentId))
                .thenReturn(Optional.empty());
        when(vectorEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        vectorStore.store("user1", "episode_summary", contentId, "some text");

        verify(vectorEntryRepository).save(any(VectorEntry.class));
    }

    @Test
    void storeWhenEmbeddingUnavailableDoesNotSave() {
        UUID contentId = UUID.randomUUID();
        when(embeddingAdapter.embed(anyString())).thenReturn(Optional.empty());

        vectorStore.store("user1", "episode_summary", contentId, "some text");

        verify(vectorEntryRepository, never()).save(any());
    }

    @Test
    void storeWhenEmbeddingAdapterIsNullDoesNotSave() {
        SqliteVectorStore storeWithoutAdapter = new SqliteVectorStore(vectorEntryRepository, null);
        UUID contentId = UUID.randomUUID();

        storeWithoutAdapter.store("user1", "episode_summary", contentId, "some text");

        verify(vectorEntryRepository, never()).save(any());
    }

    @Test
    void storeUpdatesExistingEntryWhenFound() {
        UUID contentId = UUID.randomUUID();
        float[] embedding = {0.5f, 0.5f};
        VectorEntry existing = new VectorEntry();
        existing.setId(UUID.randomUUID());
        existing.setCreatedAt(Instant.now());

        when(embeddingAdapter.embed("updated text")).thenReturn(Optional.of(embedding));
        when(vectorEntryRepository.findByContentTypeAndContentId("episode_summary", contentId))
                .thenReturn(Optional.of(existing));
        when(vectorEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        vectorStore.store("user1", "episode_summary", contentId, "updated text");

        verify(vectorEntryRepository).save(existing);
    }

    @Test
    void searchWhenEmbeddingUnavailableReturnsEmptyList() {
        when(embeddingAdapter.isAvailable()).thenReturn(false);

        List<VectorStoreAdapter.VectorSearchResult> results =
                vectorStore.search("user1", "query", 5);

        assertThat(results).isEmpty();
        verify(vectorEntryRepository, never()).findByUserId(anyString());
    }

    @Test
    void searchWhenEmbeddingAdapterIsNullReturnsEmptyList() {
        SqliteVectorStore storeWithoutAdapter = new SqliteVectorStore(vectorEntryRepository, null);

        List<VectorStoreAdapter.VectorSearchResult> results =
                storeWithoutAdapter.search("user1", "query", 5);

        assertThat(results).isEmpty();
        verify(vectorEntryRepository, never()).findByUserId(anyString());
    }

    @Test
    void searchWhenEmbeddingReturnsEmptyReturnsEmptyList() {
        when(embeddingAdapter.isAvailable()).thenReturn(true);
        when(embeddingAdapter.embed(anyString())).thenReturn(Optional.empty());

        List<VectorStoreAdapter.VectorSearchResult> results =
                vectorStore.search("user1", "query", 5);

        assertThat(results).isEmpty();
    }

    @Test
    void searchReturnsResultsSortedBySimilarityDescending() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        float[] queryVec = {1.0f, 0.0f};
        float[] vec1 = {0.5f, 0.5f}; // cosine ~0.707
        float[] vec2 = {1.0f, 0.0f}; // cosine 1.0 (identical)

        when(embeddingAdapter.isAvailable()).thenReturn(true);
        when(embeddingAdapter.embed("query")).thenReturn(Optional.of(queryVec));

        VectorEntry entry1 = makeEntry(id1, "user1", "episode_summary", vec1, "text1");
        VectorEntry entry2 = makeEntry(id2, "user1", "episode_summary", vec2, "text2");

        when(vectorEntryRepository.findByUserId("user1")).thenReturn(List.of(entry1, entry2));

        List<VectorStoreAdapter.VectorSearchResult> results =
                vectorStore.search("user1", "query", 5);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).score()).isGreaterThan(results.get(1).score());
        assertThat(results.get(0).contentId()).isEqualTo(id2);
    }

    @Test
    void searchRespectsTopKLimit() {
        float[] queryVec = {1.0f, 0.0f};
        when(embeddingAdapter.isAvailable()).thenReturn(true);
        when(embeddingAdapter.embed("q")).thenReturn(Optional.of(queryVec));

        List<VectorEntry> entries = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            entries.add(
                    makeEntry(
                            UUID.randomUUID(),
                            "user1",
                            "episode_summary",
                            new float[] {(float) i / 10, 0.5f},
                            "text" + i));
        }
        when(vectorEntryRepository.findByUserId("user1")).thenReturn(entries);

        List<VectorStoreAdapter.VectorSearchResult> results = vectorStore.search("user1", "q", 3);

        assertThat(results).hasSize(3);
    }

    @Test
    void searchSkipsMalformedBlobEntries() {
        float[] queryVec = {1.0f, 0.0f};
        UUID goodId = UUID.randomUUID();
        UUID badId = UUID.randomUUID();

        when(embeddingAdapter.isAvailable()).thenReturn(true);
        when(embeddingAdapter.embed("q")).thenReturn(Optional.of(queryVec));

        // Good entry
        VectorEntry goodEntry =
                makeEntry(
                        goodId, "user1", "episode_summary", new float[] {1.0f, 0.0f}, "good text");

        // Malformed entry: 3 bytes (not a multiple of 4)
        VectorEntry badEntry = new VectorEntry();
        badEntry.setId(badId);
        badEntry.setUserId("user1");
        badEntry.setContentType("episode_summary");
        badEntry.setContentId(badId);
        badEntry.setText("bad");
        badEntry.setEmbedding(new byte[] {1, 2, 3}); // malformed
        badEntry.setCreatedAt(Instant.now());

        when(vectorEntryRepository.findByUserId("user1")).thenReturn(List.of(goodEntry, badEntry));

        List<VectorStoreAdapter.VectorSearchResult> results = vectorStore.search("user1", "q", 5);

        // Only the good entry should appear
        assertThat(results).hasSize(1);
        assertThat(results.get(0).contentId()).isEqualTo(goodId);
    }

    @Test
    void deleteCallsRepositoryDelete() {
        UUID contentId = UUID.randomUUID();

        vectorStore.delete("episode_summary", contentId);

        verify(vectorEntryRepository).deleteByContentTypeAndContentId("episode_summary", contentId);
    }

    @Test
    void cosineSimilarityZeroVectorAReturnsZero() {
        float[] a = {0.0f, 0.0f};
        float[] b = {1.0f, 0.0f};
        assertThat(SqliteVectorStore.cosineSimilarity(a, b)).isEqualTo(0.0);
    }

    @Test
    void cosineSimilarityZeroVectorBReturnsZero() {
        float[] a = {1.0f, 0.0f};
        float[] b = {0.0f, 0.0f};
        assertThat(SqliteVectorStore.cosineSimilarity(a, b)).isEqualTo(0.0);
    }

    @Test
    void cosineSimilarityIdenticalVectorsReturnsOne() {
        float[] a = {3.0f, 4.0f};
        assertThat(SqliteVectorStore.cosineSimilarity(a, a))
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void bytesToFloatArrayRoundTrip() {
        float[] original = {1.5f, -2.0f, 3.14f};
        byte[] bytes = SqliteVectorStore.floatArrayToBytes(original);
        float[] recovered = SqliteVectorStore.bytesToFloatArray(bytes);
        assertThat(recovered).containsExactly(original[0], original[1], original[2]);
    }

    // Helper to create a VectorEntry with a serialized embedding
    private VectorEntry makeEntry(
            UUID id, String userId, String contentType, float[] vec, String text) {
        VectorEntry entry = new VectorEntry();
        entry.setId(id);
        entry.setUserId(userId);
        entry.setContentType(contentType);
        entry.setContentId(id);
        entry.setText(text);
        entry.setEmbedding(SqliteVectorStore.floatArrayToBytes(vec));
        entry.setCreatedAt(Instant.now());
        return entry;
    }
}
