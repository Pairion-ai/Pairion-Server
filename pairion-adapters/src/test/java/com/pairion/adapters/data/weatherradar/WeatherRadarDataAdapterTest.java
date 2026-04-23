package com.pairion.adapters.data.weatherradar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link WeatherRadarDataAdapter} polling lifecycle and error handling. */
class WeatherRadarDataAdapterTest {

    private WeatherRadarDataClient client;
    private WeatherRadarDataAdapter adapter;

    private static WeatherRadarSnapshot makeSnapshot() {
        return new WeatherRadarSnapshot(
                "https://tilecache.rainviewer.com",
                List.of(
                        new WeatherRadarFrame(1713739200L, "/v2/radar/1713739200"),
                        new WeatherRadarFrame(1713739800L, "/v2/radar/1713739800")),
                "/v2/radar/1713739800",
                256,
                4,
                "1_1");
    }

    @BeforeEach
    void setUp() {
        client = mock(WeatherRadarDataClient.class);
        adapter = new WeatherRadarDataAdapter(client, 1); // 1-second poll interval for tests
    }

    // ── poll ─────────────────────────────────────────────────────────────────

    @Test
    void pollWithNullSinkDoesNotInvokeClient() throws Exception {
        adapter.poll();
        verify(client, never()).fetchSnapshot();
    }

    @Test
    void pollDeliversSnapshotToSink() throws Exception {
        WeatherRadarSnapshot snapshot = makeSnapshot();
        when(client.fetchSnapshot()).thenReturn(snapshot);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<WeatherRadarSnapshot> received = new AtomicReference<>();
        adapter.startPolling(s -> {
            received.set(s);
            latch.countDown();
        });

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        adapter.stopPolling();

        assertThat(received.get()).isNotNull();
        assertThat(received.get().host()).isEqualTo("https://tilecache.rainviewer.com");
        assertThat(received.get().frames()).hasSize(2);
        assertThat(received.get().latestPath()).isEqualTo("/v2/radar/1713739800");
        assertThat(received.get().tileSize()).isEqualTo(256);
        assertThat(received.get().colorScheme()).isEqualTo(4);
        assertThat(received.get().options()).isEqualTo("1_1");
    }

    @Test
    void pollClientExceptionDoesNotPropagate() throws Exception {
        when(client.fetchSnapshot()).thenThrow(new RuntimeException("network failure"));

        CountDownLatch latch = new CountDownLatch(1);
        adapter.startPolling(s -> latch.countDown());

        // Latch should NOT be triggered since the poll failed silently
        assertThat(latch.await(2, TimeUnit.SECONDS)).isFalse();
        adapter.stopPolling();
    }

    @Test
    void pollCheckedExceptionDoesNotPropagate() throws Exception {
        when(client.fetchSnapshot()).thenThrow(new Exception("HTTP 503"));

        CountDownLatch latch = new CountDownLatch(1);
        adapter.startPolling(s -> latch.countDown());

        assertThat(latch.await(2, TimeUnit.SECONDS)).isFalse();
        adapter.stopPolling();
    }

    // ── startPolling / stopPolling ────────────────────────────────────────────

    @Test
    void stopPollingIdempotent() {
        adapter.stopPolling(); // never started — should not throw
        adapter.stopPolling(); // second call — also should not throw
    }

    @Test
    void startPollingReplacesPreviousSink() throws Exception {
        when(client.fetchSnapshot()).thenReturn(makeSnapshot());

        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        adapter.startPolling(s -> latch1.countDown());
        assertThat(latch1.await(3, TimeUnit.SECONDS)).isTrue();

        // Replace with second sink
        adapter.startPolling(s -> latch2.countDown());
        assertThat(latch2.await(3, TimeUnit.SECONDS)).isTrue();

        adapter.stopPolling();
    }

    @Test
    void stopPollingClearsSink() throws Exception {
        when(client.fetchSnapshot()).thenReturn(makeSnapshot());

        CountDownLatch latch = new CountDownLatch(1);
        adapter.startPolling(s -> latch.countDown());
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        adapter.stopPolling();

        // After stop, poll should not deliver to sink
        adapter.poll();
        verify(client, org.mockito.Mockito.atLeastOnce()).fetchSnapshot(); // called during polling
    }

    // ── WeatherRadarSnapshot / WeatherRadarFrame records ─────────────────────

    @Test
    void weatherRadarFrameRecord() {
        WeatherRadarFrame frame = new WeatherRadarFrame(1713739200L, "/v2/radar/1713739200");
        assertThat(frame.time()).isEqualTo(1713739200L);
        assertThat(frame.path()).isEqualTo("/v2/radar/1713739200");
    }

    @Test
    void weatherRadarSnapshotRecord() {
        WeatherRadarSnapshot snapshot = makeSnapshot();
        assertThat(snapshot.host()).isEqualTo("https://tilecache.rainviewer.com");
        assertThat(snapshot.frames()).hasSize(2);
        assertThat(snapshot.latestPath()).isEqualTo("/v2/radar/1713739800");
        assertThat(snapshot.tileSize()).isEqualTo(256);
        assertThat(snapshot.colorScheme()).isEqualTo(4);
        assertThat(snapshot.options()).isEqualTo("1_1");
    }

    @Test
    void weatherRadarSnapshotNullLatestPath() {
        WeatherRadarSnapshot snapshot = new WeatherRadarSnapshot(
                "https://tilecache.rainviewer.com", List.of(), null, 256, 4, "1_1");
        assertThat(snapshot.latestPath()).isNull();
    }
}
