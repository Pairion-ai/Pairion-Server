package com.pairion.adapters.data.weathercurrent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link WeatherCurrentDataAdapter} on-demand fetch lifecycle and error handling. */
class WeatherCurrentDataAdapterTest {

    private WeatherCurrentDataClient client;
    private WeatherCurrentDataAdapter adapter;

    private static WeatherCurrentSnapshot makeSnapshot() {
        return new WeatherCurrentSnapshot(
                "Dallas, United States",
                72.3,
                68.1,
                80.0,
                61.5,
                55,
                12.5,
                180,
                "Partly cloudy",
                0.0,
                1013.2);
    }

    @BeforeEach
    void setUp() {
        client = mock(WeatherCurrentDataClient.class);
        adapter = new WeatherCurrentDataAdapter(client);
    }

    // ── fetch ─────────────────────────────────────────────────────────────────

    /**
     * When the client throws, the consumer is NOT called and no exception propagates from fetch().
     */
    @Test
    void fetchClientExceptionDoesNotPropagate() throws Exception {
        when(client.fetchSnapshot("Dallas")).thenThrow(new RuntimeException("network failure"));

        AtomicReference<WeatherCurrentSnapshot> received = new AtomicReference<>();
        adapter.fetch("Dallas", received::set);

        assertThat(received.get()).isNull();
        verify(client).fetchSnapshot("Dallas");
    }

    /**
     * When the client delivers a snapshot, the consumer receives it with all correct field values.
     */
    @Test
    void fetchDeliversSnapshotToSink() throws Exception {
        WeatherCurrentSnapshot snapshot = makeSnapshot();
        when(client.fetchSnapshot("Dallas")).thenReturn(snapshot);

        AtomicReference<WeatherCurrentSnapshot> received = new AtomicReference<>();
        adapter.fetch("Dallas", received::set);

        assertThat(received.get()).isNotNull();
        assertThat(received.get().city()).isEqualTo("Dallas, United States");
        assertThat(received.get().temperatureF()).isEqualTo(72.3);
        assertThat(received.get().feelsLikeF()).isEqualTo(68.1);
        assertThat(received.get().highF()).isEqualTo(80.0);
        assertThat(received.get().lowF()).isEqualTo(61.5);
        assertThat(received.get().humidity()).isEqualTo(55);
        assertThat(received.get().windSpeedMph()).isEqualTo(12.5);
        assertThat(received.get().windDirectionDeg()).isEqualTo(180);
        assertThat(received.get().conditions()).isEqualTo("Partly cloudy");
        assertThat(received.get().precipitationIn()).isEqualTo(0.0);
        assertThat(received.get().pressureMb()).isEqualTo(1013.2);
    }

    /**
     * When the client throws a checked Exception, the consumer is NOT called and no exception
     * propagates from fetch().
     */
    @Test
    void fetchCheckedExceptionDoesNotPropagate() throws Exception {
        when(client.fetchSnapshot("Tokyo")).thenThrow(new Exception("HTTP 503"));

        AtomicReference<WeatherCurrentSnapshot> received = new AtomicReference<>();
        adapter.fetch("Tokyo", received::set);

        assertThat(received.get()).isNull();
        verify(client).fetchSnapshot("Tokyo");
    }

    // ── start ─────────────────────────────────────────────────────────────────

    /** start() spawns a virtual thread and delivers the snapshot to the sink within 3 seconds. */
    @Test
    void startSpawnsVirtualThreadAndDeliversSnapshot() throws Exception {
        WeatherCurrentSnapshot snapshot = makeSnapshot();
        when(client.fetchSnapshot(eq("Dallas"))).thenReturn(snapshot);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<WeatherCurrentSnapshot> received = new AtomicReference<>();
        adapter.start(
                "Dallas",
                s -> {
                    received.set(s);
                    latch.countDown();
                });

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get()).isNotNull();
        assertThat(received.get().city()).isEqualTo("Dallas, United States");
    }

    /** start() with a client that throws — sink is NOT called; no exception propagates. */
    @Test
    void startWithExceptionDoesNotCallSink() throws Exception {
        when(client.fetchSnapshot(eq("Nowhere"))).thenThrow(new Exception("city not found"));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<WeatherCurrentSnapshot> received = new AtomicReference<>();
        adapter.start(
                "Nowhere",
                s -> {
                    received.set(s);
                    latch.countDown();
                });

        // Latch must NOT be triggered — sink is not called on failure
        assertThat(latch.await(2, TimeUnit.SECONDS)).isFalse();
        assertThat(received.get()).isNull();
    }

    // ── WeatherCurrentSnapshot record ─────────────────────────────────────────

    /** Verifies all 11 field accessors of the WeatherCurrentSnapshot record. */
    @Test
    void weatherCurrentSnapshotRecord() {
        WeatherCurrentSnapshot snapshot =
                new WeatherCurrentSnapshot(
                        "Tokyo, Japan",
                        59.0,
                        55.4,
                        65.3,
                        50.1,
                        70,
                        8.1,
                        270,
                        "Overcast",
                        0.1,
                        1008.5);

        assertThat(snapshot.city()).isEqualTo("Tokyo, Japan");
        assertThat(snapshot.temperatureF()).isEqualTo(59.0);
        assertThat(snapshot.feelsLikeF()).isEqualTo(55.4);
        assertThat(snapshot.highF()).isEqualTo(65.3);
        assertThat(snapshot.lowF()).isEqualTo(50.1);
        assertThat(snapshot.humidity()).isEqualTo(70);
        assertThat(snapshot.windSpeedMph()).isEqualTo(8.1);
        assertThat(snapshot.windDirectionDeg()).isEqualTo(270);
        assertThat(snapshot.conditions()).isEqualTo("Overcast");
        assertThat(snapshot.precipitationIn()).isEqualTo(0.1);
        assertThat(snapshot.pressureMb()).isEqualTo(1008.5);
    }

    /**
     * When the fetch method is called and the client succeeds, the sink is called exactly once.
     * Verifies that fetch() does not call the sink when no snapshot is returned (null check).
     */
    @Test
    void fetchDoesNotCallSinkWhenClientThrows() throws Exception {
        when(client.fetchSnapshot("BadCity")).thenThrow(new RuntimeException("DNS failure"));

        AtomicReference<WeatherCurrentSnapshot> sinkArg = new AtomicReference<>();
        adapter.fetch("BadCity", sinkArg::set);

        // Client threw, so sink must not have been called
        verify(client).fetchSnapshot("BadCity");
        assertThat(sinkArg.get()).isNull();
    }
}
