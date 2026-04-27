package com.pairion.adapters.vectorstore.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link VectorStoreAdapter.VectorSearchResult}. */
class VectorSearchResultTest {

    @Test
    void constructorAndAccessorsReturnExpectedValues() {
        UUID id = UUID.randomUUID();
        VectorStoreAdapter.VectorSearchResult result =
                new VectorStoreAdapter.VectorSearchResult("episode_summary", id, "some text", 0.95);

        assertThat(result.contentType()).isEqualTo("episode_summary");
        assertThat(result.contentId()).isEqualTo(id);
        assertThat(result.text()).isEqualTo("some text");
        assertThat(result.score()).isEqualTo(0.95);
    }

    @Test
    void equalRecordsAreEqual() {
        UUID id = UUID.randomUUID();
        VectorStoreAdapter.VectorSearchResult a =
                new VectorStoreAdapter.VectorSearchResult("turn", id, "hello", 0.8);
        VectorStoreAdapter.VectorSearchResult b =
                new VectorStoreAdapter.VectorSearchResult("turn", id, "hello", 0.8);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void toStringContainsFields() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        VectorStoreAdapter.VectorSearchResult result =
                new VectorStoreAdapter.VectorSearchResult("preference", id, "text", 0.5);

        String str = result.toString();

        assertThat(str).contains("preference");
        assertThat(str).contains(id.toString());
    }
}
