package com.pairion.adapters.embedding.openaicompat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link OpenAiCompatEmbeddingAdapter} using a mock client boundary. */
class OpenAiCompatEmbeddingAdapterTest {

    private OpenAiCompatEmbeddingClient mockClient;
    private OpenAiCompatEmbeddingAdapter adapter;

    @BeforeEach
    void setUp() {
        mockClient = mock(OpenAiCompatEmbeddingClient.class);
        adapter = new OpenAiCompatEmbeddingAdapter(mockClient, "http://localhost:11434/v1");
    }

    @Test
    void nameReturnsExpectedString() {
        assertThat(adapter.name()).isEqualTo("openaicompat-embedding");
    }

    @Test
    void embedSuccessfulResponseReturnsFloatArray() {
        float[] expected = {0.1f, 0.2f, 0.3f};
        when(mockClient.fetchEmbedding("hello world")).thenReturn(Optional.of(expected));

        Optional<float[]> result = adapter.embed("hello world");

        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(expected);
        verify(mockClient).fetchEmbedding("hello world");
    }

    @Test
    void embedWithHttpErrorReturnsEmptyOptional() {
        when(mockClient.fetchEmbedding(anyString())).thenReturn(Optional.empty());

        Optional<float[]> result = adapter.embed("some text");

        assertThat(result).isEmpty();
    }

    @Test
    void embedWithIoExceptionReturnsEmptyOptional() {
        when(mockClient.fetchEmbedding(anyString())).thenReturn(Optional.empty());

        Optional<float[]> result = adapter.embed("another text");

        assertThat(result).isEmpty();
    }

    @Test
    void embedWithNullTextReturnsEmptyWithoutCallingClient() {
        Optional<float[]> result = adapter.embed(null);

        assertThat(result).isEmpty();
        verify(mockClient, never()).fetchEmbedding(anyString());
    }

    @Test
    void embedWithBlankTextReturnsEmptyWithoutCallingClient() {
        Optional<float[]> result = adapter.embed("   ");

        assertThat(result).isEmpty();
        verify(mockClient, never()).fetchEmbedding(anyString());
    }

    @Test
    void embedCallsClientOnce() {
        when(mockClient.fetchEmbedding("test")).thenReturn(Optional.of(new float[] {0.5f}));

        adapter.embed("test");

        verify(mockClient, times(1)).fetchEmbedding("test");
    }

    @Test
    void getBaseUrlReturnsConfiguredUrl() {
        assertThat(adapter.getBaseUrl()).isEqualTo("http://localhost:11434/v1");
    }

    // ── isAvailable tests ────────────────────────────────────────────────────

    @Test
    void isAvailableReturnsTrueWhenClientReturnsTrue() {
        when(mockClient.checkAvailability()).thenReturn(true);

        assertThat(adapter.isAvailable()).isTrue();
    }

    @Test
    void isAvailableReturnsFalseWhenClientReturnsFalse() {
        when(mockClient.checkAvailability()).thenReturn(false);

        assertThat(adapter.isAvailable()).isFalse();
    }

    @Test
    void isAvailableLogsWarningOnlyOnceWhenUnavailable() {
        when(mockClient.checkAvailability()).thenReturn(false);

        // Two consecutive unavailable calls — warning should fire only once (AtomicBoolean guard)
        adapter.isAvailable();
        adapter.isAvailable();

        verify(mockClient, times(2)).checkAvailability();
    }

    @Test
    void isAvailableResetsLogFlagOnRecovery() {
        when(mockClient.checkAvailability()).thenReturn(false);
        adapter.isAvailable(); // sets unavailableLogged

        when(mockClient.checkAvailability()).thenReturn(true);
        boolean recovered = adapter.isAvailable(); // should reset flag and return true

        assertThat(recovered).isTrue();
    }
}
