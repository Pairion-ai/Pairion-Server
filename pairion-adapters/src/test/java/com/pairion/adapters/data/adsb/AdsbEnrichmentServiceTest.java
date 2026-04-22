package com.pairion.adapters.data.adsb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AdsbEnrichmentService} — rate limiting, TTL cache, and enrichment logic.
 *
 * <p>A stub {@link Clock} is injected so all time-based assertions are deterministic. The service
 * initialises {@code lastCallMs} to {@code -RATE_LIMIT_MS}, so the first call at
 * {@code clock.millis() == 0} always passes. Each test documents the exact clock-millis sequence
 * consumed by the code under test.
 */
class AdsbEnrichmentServiceTest {

    private AdsbDataClient client;
    private Clock clock;
    private AdsbEnrichmentService service;

    /** Shorthand constant for {@link AdsbEnrichmentService#RATE_LIMIT_MS}. */
    private static final long RL = AdsbEnrichmentService.RATE_LIMIT_MS;

    @BeforeEach
    void setUp() {
        client = mock(AdsbDataClient.class);
        clock = mock(Clock.class);
        service = new AdsbEnrichmentService(client, clock);
    }

    // ── acquireRateLimit ──────────────────────────────────────────────────────

    @Test
    void rateLimitAllowsFirstCallAtTimeZero() {
        // lastCallMs = -RL; 0 - (-RL) = RL >= RL → permitted
        when(clock.millis()).thenReturn(0L);
        assertThat(service.acquireRateLimit()).isTrue();
    }

    @Test
    void rateLimitBlocksSecondCallWithinWindow() {
        // First call at t=0 passes and sets lastCallMs=0
        when(clock.millis()).thenReturn(0L);
        service.acquireRateLimit();
        // t=500: 500 - 0 = 500 < RL(1000) → blocked
        when(clock.millis()).thenReturn(500L);
        assertThat(service.acquireRateLimit()).isFalse();
    }

    @Test
    void rateLimitAllowsCallAfterFullWindow() {
        // First call at t=0 passes, lastCallMs=0
        when(clock.millis()).thenReturn(0L);
        service.acquireRateLimit();
        // t=RL: RL - 0 = RL, NOT less-than RL → permitted
        when(clock.millis()).thenReturn(RL);
        assertThat(service.acquireRateLimit()).isTrue();
    }

    // ── getCachedMetadata ─────────────────────────────────────────────────────

    @Test
    void metadataCacheMissReturnsNull() {
        assertThat(service.getCachedMetadata("unknown-icao")).isNull();
    }

    @Test
    void metadataCacheHitReturnsCachedEntry() throws Exception {
        // Prime cache: clock t=0 for acquire, t=0 for CachedEntry.createdAtMs
        when(clock.millis()).thenReturn(0L, 0L);
        when(client.fetchMetadata("abc123"))
                .thenReturn(Optional.of(
                        new AdsbDataClient.AircraftMetadata("abc123", "N123AB", "B738")));
        service.enrich(basicAircraft("abc123", null));

        // isExpired: clock t=100; 100 - 0 = 100 < METADATA_TTL → not expired
        when(clock.millis()).thenReturn(100L);
        Optional<AdsbDataClient.AircraftMetadata> cached = service.getCachedMetadata("abc123");
        assertThat(cached).isNotNull().isPresent();
        assertThat(cached.get().registration()).isEqualTo("N123AB");
    }

    @Test
    void metadataCacheExpiredReturnsNull() throws Exception {
        // Prime cache at t=0
        when(clock.millis()).thenReturn(0L, 0L);
        when(client.fetchMetadata("abc123")).thenReturn(Optional.empty());
        service.enrich(basicAircraft("abc123", null));

        // isExpired: now > METADATA_TTL → expired
        when(clock.millis()).thenReturn(AdsbEnrichmentService.METADATA_TTL_MS + 1);
        assertThat(service.getCachedMetadata("abc123")).isNull();
    }

    // ── getCachedRoute ────────────────────────────────────────────────────────

    @Test
    void routeCacheMissReturnsNull() {
        assertThat(service.getCachedRoute("UAL123")).isNull();
    }

    @Test
    void routeCacheHitReturnsCachedEntry() throws Exception {
        // Prime route cache: acquire at t=0 (metadata), t=RL (route acquire), cache entries at same times
        when(clock.millis()).thenReturn(0L, 0L, RL, RL);
        when(client.fetchMetadata("abc123")).thenReturn(Optional.empty());
        when(client.fetchRoute("UAL123"))
                .thenReturn(Optional.of(
                        new AdsbDataClient.RouteInfo("UAL123", "KDFW", "KLAX")));
        service.enrich(basicAircraft("abc123", "UAL123"));

        // isExpired: RL+100 - RL = 100 < ROUTE_TTL → not expired
        when(clock.millis()).thenReturn(RL + 100L);
        Optional<AdsbDataClient.RouteInfo> cached = service.getCachedRoute("UAL123");
        assertThat(cached).isNotNull().isPresent();
        assertThat(cached.get().departureAirport()).isEqualTo("KDFW");
    }

    @Test
    void routeCacheExpiredReturnsNull() throws Exception {
        when(clock.millis()).thenReturn(0L, 0L, RL, RL);
        when(client.fetchMetadata("abc123")).thenReturn(Optional.empty());
        when(client.fetchRoute("UAL123")).thenReturn(Optional.empty());
        service.enrich(basicAircraft("abc123", "UAL123"));

        // isExpired: now - RL > ROUTE_TTL → expired
        when(clock.millis()).thenReturn(RL + AdsbEnrichmentService.ROUTE_TTL_MS + 1);
        assertThat(service.getCachedRoute("UAL123")).isNull();
    }

    // ── enrich ────────────────────────────────────────────────────────────────

    @Test
    void enrichReturnsOriginalWhenMetadataEmptyAndNoCallsign() throws Exception {
        // metadata: acquire(t=0) + cache(t=0); callsign null → no route
        when(clock.millis()).thenReturn(0L, 0L);
        when(client.fetchMetadata("abc123")).thenReturn(Optional.empty());

        AdsbAircraft aircraft = basicAircraft("abc123", null);
        assertThat(service.enrich(aircraft)).isSameAs(aircraft);
    }

    @Test
    void enrichSetsRegistrationAndType() throws Exception {
        when(clock.millis()).thenReturn(0L, 0L);
        when(client.fetchMetadata("abc123"))
                .thenReturn(Optional.of(
                        new AdsbDataClient.AircraftMetadata("abc123", "N123AB", "B738")));

        AdsbAircraft result = service.enrich(basicAircraft("abc123", null));
        assertThat(result.registration()).isEqualTo("N123AB");
        assertThat(result.aircraftType()).isEqualTo("B738");
    }

    @Test
    void enrichSetsOriginAndDestination() throws Exception {
        // metadata at t=0 (acquire, cache), route at t=RL (acquire, cache)
        when(clock.millis()).thenReturn(0L, 0L, RL, RL);
        when(client.fetchMetadata("abc123")).thenReturn(Optional.empty());
        when(client.fetchRoute("UAL123"))
                .thenReturn(Optional.of(
                        new AdsbDataClient.RouteInfo("UAL123", "KDFW", "KLAX")));

        AdsbAircraft result = service.enrich(basicAircraft("abc123", "UAL123"));
        assertThat(result.origin()).isEqualTo("KDFW");
        assertThat(result.destination()).isEqualTo("KLAX");
    }

    @Test
    void enrichEmptyRouteReturnsOriginal() throws Exception {
        // route fetch succeeds but returns empty → nothing enriched
        when(clock.millis()).thenReturn(0L, 0L, RL, RL);
        when(client.fetchMetadata("abc123")).thenReturn(Optional.empty());
        when(client.fetchRoute("UAL123")).thenReturn(Optional.empty());

        AdsbAircraft aircraft = basicAircraft("abc123", "UAL123");
        assertThat(service.enrich(aircraft)).isSameAs(aircraft);
    }

    @Test
    void enrichSkipsRouteWhenCallsignNull() throws Exception {
        when(clock.millis()).thenReturn(0L, 0L);
        when(client.fetchMetadata("abc123")).thenReturn(Optional.empty());

        service.enrich(basicAircraft("abc123", null));
        verify(client, never()).fetchRoute(anyString());
    }

    @Test
    void enrichSkipsRouteWhenCallsignBlank() throws Exception {
        when(clock.millis()).thenReturn(0L, 0L);
        when(client.fetchMetadata("abc123")).thenReturn(Optional.empty());

        service.enrich(basicAircraft("abc123", "   "));
        verify(client, never()).fetchRoute(anyString());
    }

    @Test
    void enrichMetadataClientExceptionDoesNotPropagate() throws Exception {
        // acquireRateLimit passes (t=0), then client throws
        when(clock.millis()).thenReturn(0L);
        when(client.fetchMetadata("abc123")).thenThrow(new RuntimeException("network error"));

        AdsbAircraft aircraft = basicAircraft("abc123", null);
        assertThat(service.enrich(aircraft)).isSameAs(aircraft);
    }

    @Test
    void enrichRouteClientExceptionDoesNotPropagate() throws Exception {
        // metadata at t=0, route acquire at t=RL, client throws before cache
        when(clock.millis()).thenReturn(0L, 0L, RL);
        when(client.fetchMetadata("abc123")).thenReturn(Optional.empty());
        when(client.fetchRoute("UAL123")).thenThrow(new RuntimeException("route error"));

        AdsbAircraft aircraft = basicAircraft("abc123", "UAL123");
        assertThat(service.enrich(aircraft)).isSameAs(aircraft);
    }

    @Test
    void enrichFetchMetadataRateLimitedReturnsOriginal() throws Exception {
        // Consume rate limit manually at t=0 (lastCallMs = 0)
        when(clock.millis()).thenReturn(0L);
        service.acquireRateLimit();

        // Enrich: cache miss + rate limited → fetchMetadata not called → meta=null → original returned
        when(clock.millis()).thenReturn(0L);
        AdsbAircraft aircraft = basicAircraft("abc123", null);
        assertThat(service.enrich(aircraft)).isSameAs(aircraft);
        verify(client, never()).fetchMetadata(anyString());
    }

    @Test
    void enrichFetchRouteRateLimitedSkipsApiCall() throws Exception {
        // metadata at t=0 (acquire→lastCallMs=0, cache); route acquire at t=0 → blocked
        when(clock.millis()).thenReturn(0L, 0L, 0L);
        when(client.fetchMetadata("abc123")).thenReturn(Optional.empty());

        AdsbAircraft aircraft = basicAircraft("abc123", "UAL123");
        assertThat(service.enrich(aircraft)).isSameAs(aircraft);
        verify(client, never()).fetchRoute(anyString());
    }

    @Test
    void enrichUsesMetadataCacheOnSecondCall() throws Exception {
        // Prime metadata cache at t=0
        when(clock.millis()).thenReturn(0L, 0L);
        when(client.fetchMetadata("abc123"))
                .thenReturn(Optional.of(
                        new AdsbDataClient.AircraftMetadata("abc123", "N999ZZ", "A320")));
        service.enrich(basicAircraft("abc123", null));

        // Second enrich: isExpired check at t=100 → cache hit → no API call
        when(clock.millis()).thenReturn(100L);
        service.enrich(basicAircraft("abc123", null));

        verify(client, times(1)).fetchMetadata("abc123");
    }

    @Test
    void enrichUsesRouteCacheOnSecondCall() throws Exception {
        // Prime both caches
        when(clock.millis()).thenReturn(0L, 0L, RL, RL);
        when(client.fetchMetadata("abc123")).thenReturn(Optional.empty());
        when(client.fetchRoute("UAL123"))
                .thenReturn(Optional.of(
                        new AdsbDataClient.RouteInfo("UAL123", "KDFW", "KLAX")));
        service.enrich(basicAircraft("abc123", "UAL123"));

        // Second enrich: both caches hit (isExpired checks at t=RL+100)
        when(clock.millis()).thenReturn(RL + 100L, RL + 100L);
        AdsbAircraft result = service.enrich(basicAircraft("abc123", "UAL123"));

        assertThat(result.origin()).isEqualTo("KDFW");
        verify(client, times(1)).fetchRoute(anyString());
    }

    @Test
    void enrichMetadataCacheHitWithEmptyOptional() throws Exception {
        // Prime with empty metadata at t=0
        when(clock.millis()).thenReturn(0L, 0L);
        when(client.fetchMetadata("abc123")).thenReturn(Optional.empty());
        service.enrich(basicAircraft("abc123", null));

        // Second enrich: cache hit returns empty Optional; meta != null, !isPresent → no fields set
        when(clock.millis()).thenReturn(100L);
        AdsbAircraft aircraft = basicAircraft("abc123", null);
        assertThat(service.enrich(aircraft)).isSameAs(aircraft);
        verify(client, times(1)).fetchMetadata(anyString());
    }

    @Test
    void productionConstructorCreatesServiceWithRealClock() {
        // Exercises lines 67-68: public AdsbEnrichmentService(AdsbDataClient client)
        // which delegates to this(client, Clock.systemUTC())
        AdsbEnrichmentService svc = new AdsbEnrichmentService(client);
        // lastCallMs = -RATE_LIMIT_MS; real clock >> 0, so first call always passes
        assertThat(svc.acquireRateLimit()).isTrue();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static AdsbAircraft basicAircraft(String icao24, String callsign) {
        return new AdsbAircraft(
                icao24, callsign, 35.0, -97.0, 10000.0, 480.0, 90.0, 0.0, false,
                null, null, null, null);
    }
}
