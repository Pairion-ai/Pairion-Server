package com.pairion.adapters.data.adsb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link AdsbDataAdapter} polling, parsing, and unit conversion. */
class AdsbDataAdapterTest {

    private AdsbDataClient client;
    private AdsbEnrichmentService enrichmentService;
    private AdsbDataAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(AdsbDataClient.class);
        enrichmentService = mock(AdsbEnrichmentService.class);
        // Default: enrichment returns aircraft unchanged
        when(enrichmentService.enrich(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));
        adapter = new AdsbDataAdapter(client, enrichmentService, 24.0, -125.0, 49.0, -66.0, 10);
    }

    // ── parseState ────────────────────────────────────────────────────────────

    @Test
    void parseStateConvertsUnitsCorrectly() {
        // altitude 3048m → 10000.06ft, velocity 257m/s → ~499.57kn, vertRate 5m/s → ~984.25fpm
        List<Object> state = makeState(
                "abc123", "UAL123",
                -97.0, 35.0,
                3048.0,   // baro_alt metres
                false,
                257.0,    // velocity m/s
                90.0,     // track
                5.0       // vert_rate m/s
        );
        AdsbAircraft aircraft = adapter.parseState(state);

        assertThat(aircraft).isNotNull();
        assertThat(aircraft.icao24()).isEqualTo("abc123");
        assertThat(aircraft.callsign()).isEqualTo("UAL123");
        assertThat(aircraft.lat()).isEqualTo(35.0);
        assertThat(aircraft.lon()).isEqualTo(-97.0);
        // 3048m × 3.28084 ft/m = 10000.00032 → round2 → 10000.0
        assertThat(aircraft.altitudeFt()).isEqualTo(10000.0);
        // 257 * 1.94384 ≈ 499.57
        assertThat(aircraft.speedKnots()).isEqualTo(499.57);
        // 5 * 196.850 = 984.25
        assertThat(aircraft.verticalRateFpm()).isEqualTo(984.25);
        assertThat(aircraft.trackDeg()).isEqualTo(90.0);
        assertThat(aircraft.onGround()).isFalse();
    }

    @Test
    void parseStateNullLatitudeReturnsNull() {
        List<Object> state = makeState("abc123", null, -97.0, null, null, false, null, null, null);
        assertThat(adapter.parseState(state)).isNull();
    }

    @Test
    void parseStateNullLongitudeReturnsNull() {
        List<Object> state = makeState("abc123", null, null, 35.0, null, false, null, null, null);
        assertThat(adapter.parseState(state)).isNull();
    }

    @Test
    void parseStateNullIcaoReturnsNull() {
        List<Object> state = makeState(null, null, -97.0, 35.0, null, false, null, null, null);
        assertThat(adapter.parseState(state)).isNull();
    }

    @Test
    void parseStateBlankIcaoReturnsNull() {
        List<Object> state = makeState("   ", null, -97.0, 35.0, null, false, null, null, null);
        assertThat(adapter.parseState(state)).isNull();
    }

    @Test
    void parseStateNullVelocityProducesNullSpeedKnots() {
        List<Object> state = makeState("abc123", null, -97.0, 35.0, 1000.0, false, null, null, null);
        AdsbAircraft aircraft = adapter.parseState(state);
        assertThat(aircraft).isNotNull();
        assertThat(aircraft.speedKnots()).isNull();
    }

    @Test
    void parseStateNullAltitudeProducesNullAltitudeFt() {
        List<Object> state = makeState("abc123", null, -97.0, 35.0, null, false, 100.0, null, null);
        AdsbAircraft aircraft = adapter.parseState(state);
        assertThat(aircraft).isNotNull();
        assertThat(aircraft.altitudeFt()).isNull();
    }

    @Test
    void parseStateNullVertRateProducesNullVerticalRateFpm() {
        List<Object> state = makeState("abc123", null, -97.0, 35.0, 1000.0, false, 100.0, 90.0, null);
        AdsbAircraft aircraft = adapter.parseState(state);
        assertThat(aircraft).isNotNull();
        assertThat(aircraft.verticalRateFpm()).isNull();
    }

    @Test
    void parseStateNullTrackProducesNullTrackDeg() {
        List<Object> state = makeState("abc123", null, -97.0, 35.0, 1000.0, false, 100.0, null, 2.0);
        AdsbAircraft aircraft = adapter.parseState(state);
        assertThat(aircraft).isNotNull();
        assertThat(aircraft.trackDeg()).isNull();
    }

    @Test
    void parseStateBlankCallsignBecomesNull() {
        List<Object> state = makeState("abc123", "   ", -97.0, 35.0, 1000.0, false, 100.0, 90.0, 0.5);
        AdsbAircraft aircraft = adapter.parseState(state);
        assertThat(aircraft).isNotNull();
        assertThat(aircraft.callsign()).isNull();
    }

    @Test
    void parseStateOnGroundTrue() {
        List<Object> state = makeState("abc123", "N1AB", -97.0, 35.0, 0.0, true, 0.0, 0.0, 0.0);
        AdsbAircraft aircraft = adapter.parseState(state);
        assertThat(aircraft).isNotNull();
        assertThat(aircraft.onGround()).isTrue();
    }

    @Test
    void parseStateNullStateReturnsNull() {
        assertThat(adapter.parseState(null)).isNull();
    }

    @Test
    void parseStateTooShortReturnsNull() {
        assertThat(adapter.parseState(Arrays.asList("abc123", "UAL1"))).isNull();
    }

    @Test
    void parseStateNonBooleanOnGroundDefaultsFalse() {
        List<Object> state = makeState("abc123", null, -97.0, 35.0, null, false, null, null, null);
        // Replace index 8 (on_ground) with a non-Boolean value
        state.set(8, "not-a-bool");
        AdsbAircraft aircraft = adapter.parseState(state);
        assertThat(aircraft).isNotNull();
        assertThat(aircraft.onGround()).isFalse();
    }

    @Test
    void parseStateNonNumberVelocityProducesNullSpeed() {
        List<Object> state = makeState("abc123", null, -97.0, 35.0, null, false, null, null, null);
        state.set(9, "fast"); // non-numeric velocity
        AdsbAircraft aircraft = adapter.parseState(state);
        assertThat(aircraft).isNotNull();
        assertThat(aircraft.speedKnots()).isNull();
    }

    // ── poll ─────────────────────────────────────────────────────────────────

    @Test
    void pollDeliversEnrichedSnapshotToSink() throws Exception {
        List<List<Object>> states =
                List.of(makeState("abc123", "UAL123", -97.0, 35.0, 3000.0, false, 200.0, 90.0, 3.0));
        when(client.fetchStates(24.0, -125.0, 49.0, -66.0)).thenReturn(states);

        AtomicReference<List<AdsbAircraft>> received = new AtomicReference<>();
        adapter.poll();
        // No sink registered — should not throw
        verify(client, never()).fetchStates(anyDouble(), anyDouble(), anyDouble(), anyDouble());

        // Register sink and poll
        CountDownLatch latch = new CountDownLatch(1);
        adapter.startPolling(list -> {
            received.set(list);
            latch.countDown();
        });

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        adapter.stopPolling();

        assertThat(received.get()).hasSize(1);
        assertThat(received.get().get(0).icao24()).isEqualTo("abc123");
    }

    @Test
    void pollWithNullSinkDoesNotThrow() {
        // Called before startPolling — sink is null
        adapter.poll();
    }

    @Test
    void pollFiltersAircraftWithNoPosition() throws Exception {
        // One valid, one with no lat/lon
        List<List<Object>> states = new ArrayList<>();
        states.add(makeState("valid1", "AAL1", -97.0, 35.0, 3000.0, false, 200.0, 90.0, 0.0));
        states.add(makeState("nopos", null, null, null, null, false, null, null, null));
        when(client.fetchStates(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(states);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<AdsbAircraft>> received = new AtomicReference<>();
        adapter.startPolling(list -> {
            received.set(list);
            latch.countDown();
        });

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        adapter.stopPolling();

        assertThat(received.get()).hasSize(1);
        assertThat(received.get().get(0).icao24()).isEqualTo("valid1");
    }

    @Test
    void pollClientExceptionDoesNotPropagate() throws Exception {
        when(client.fetchStates(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("network failure"));

        CountDownLatch latch = new CountDownLatch(1);
        adapter.startPolling(list -> latch.countDown());

        // Wait a bit — latch should NOT be triggered since poll failed
        assertThat(latch.await(2, TimeUnit.SECONDS)).isFalse();
        adapter.stopPolling();
    }

    @Test
    void stopPollingIdempotent() {
        adapter.stopPolling(); // never started — should not throw
        adapter.stopPolling(); // second call — also should not throw
    }

    @Test
    void startPollingReplacesPreviousSink() throws Exception {
        when(client.fetchStates(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());

        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        adapter.startPolling(list -> latch1.countDown());
        // Replace with second sink
        adapter.startPolling(list -> latch2.countDown());

        assertThat(latch2.await(3, TimeUnit.SECONDS)).isTrue();
        adapter.stopPolling();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Constructs a 17-element state array matching the OpenSky schema. Index assignments:
     * 0=icao24, 1=callsign, 5=lon, 6=lat, 7=baro_alt, 8=on_ground, 9=velocity, 10=track,
     * 11=vert_rate. Indices 2-4 and 12-16 are null.
     */
    private static List<Object> makeState(
            String icao24,
            String callsign,
            Double lon,
            Double lat,
            Double baroAlt,
            boolean onGround,
            Double velocity,
            Double track,
            Double vertRate) {
        List<Object> row = new ArrayList<>(17);
        row.add(icao24);       // 0
        row.add(callsign);     // 1
        row.add(null);         // 2 origin_country
        row.add(null);         // 3 time_position
        row.add(null);         // 4 last_contact
        row.add(lon);          // 5 longitude
        row.add(lat);          // 6 latitude
        row.add(baroAlt);      // 7 baro_altitude
        row.add(onGround);     // 8 on_ground
        row.add(velocity);     // 9 velocity
        row.add(track);        // 10 true_track
        row.add(vertRate);     // 11 vertical_rate
        row.add(null);         // 12 sensors
        row.add(null);         // 13 geo_altitude
        row.add(null);         // 14 squawk
        row.add(false);        // 15 spi
        row.add(0);            // 16 position_source
        return row;
    }
}
