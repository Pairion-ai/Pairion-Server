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
 * Tests for {@link AdsbEnrichmentService} — independent rate limiters, TTL cache, and enrichment
 * logic.
 *
 * <p>A stub {@link Clock} is injected so all time-based assertions are deterministic. Each rate
 * limiter initialises its last-call timestamp to {@code -RATE_LIMIT_MS}, so the first call at
 * {@code clock.millis() == 0} always passes. Each test documents the exact clock-millis sequence
 * consumed by the code under test.
 */
class AdsbEnrichmentServiceTest {

    private AdsbDataClient client;
    private Clock clock;
    private AdsbEnrichmentService service;

    /** Shorthand for metadata rate limit window. */
    private static final long MRL = AdsbEnrichmentService.METADATA_RATE_LIMIT_MS;

    /** Shorthand for route rate limit window. */
    private static final long RRL = AdsbEnrichmentService.ROUTE_RATE_LIMIT_MS;

    @BeforeEach
    void setUp() {
        client = mock(AdsbDataClient.class);
        clock = mock(Clock.class);
        service = new AdsbEnrichmentService(client, clock);
    }

    // ── acquireMetadataRateLimit ──────────────────────────────────────────────

    @Test
    void metadataRateLimitAllowsFirstCallAtTimeZero() {
        // lastMetadataCallMs = -MRL; 0 - (-MRL) = MRL >= MRL → permitted
        when(clock.millis()).thenReturn(0L);
        assertThat(service.acquireMetadataRateLimit()).isTrue();
    }

    @Test
    void metadataRateLimitBlocksSecondCallWithinWindow() {
        // First call at t=0 passes, sets lastMetadataCallMs=0
        when(clock.millis()).thenReturn(0L);
        service.acquireMetadataRateLimit();
        // t=MRL-1: MRL-1 - 0 = MRL-1 < MRL → blocked
        when(clock.millis()).thenReturn(MRL - 1);
        assertThat(service.acquireMetadataRateLimit()).isFalse();
    }

    @Test
    void metadataRateLimitAllowsCallAfterFullWindow() {
        // First call at t=0, lastMetadataCallMs=0
        when(clock.millis()).thenReturn(0L);
        service.acquireMetadataRateLimit();
        // t=MRL: MRL - 0 = MRL, NOT less-than MRL → permitted
        when(clock.millis()).thenReturn(MRL);
        assertThat(service.acquireMetadataRateLimit()).isTrue();
    }

    // ── acquireRouteRateLimit ─────────────────────────────────────────────────

    @Test
    void routeRateLimitAllowsFirstCallAtTimeZero() {
        // lastRouteCallMs = -RRL; 0 - (-RRL) = RRL >= RRL → permitted
        when(clock.millis()).thenReturn(0L);
        assertThat(service.acquireRouteRateLimit()).isTrue();
    }

    @Test
    void routeRateLimitBlocksSecondCallWithinWindow() {
        when(clock.millis()).thenReturn(0L);
        service.acquireRouteRateLimit();
        when(clock.millis()).thenReturn(RRL - 1);
        assertThat(service.acquireRouteRateLimit()).isFalse();
    }

    @Test
    void routeRateLimitAllowsCallAfterFullWindow() {
        when(clock.millis()).thenReturn(0L);
        service.acquireRouteRateLimit();
        when(clock.millis()).thenReturn(RRL);
        assertThat(service.acquireRouteRateLimit()).isTrue();
    }

    // ── rate limiter independence ─────────────────────────────────────────────

    @Test
    void routeRateLimitIsIndependentOfMetadataRateLimit() {
        // Exhaust the metadata limiter; route limiter must still permit.
        when(clock.millis()).thenReturn(0L);
        service.acquireMetadataRateLimit();
        assertThat(service.acquireRouteRateLimit()).isTrue();
    }

    @Test
    void metadataRateLimitIsIndependentOfRouteRateLimit() {
        // Exhaust the route limiter; metadata limiter must still permit.
        when(clock.millis()).thenReturn(0L);
        service.acquireRouteRateLimit();
        assertThat(service.acquireMetadataRateLimit()).isTrue();
    }

    // ── getCachedMetadata ─────────────────────────────────────────────────────

    @Test
    void metadataCacheMissReturnsNull() {
        assertThat(service.getCachedMetadata("unknown-icao")).isNull();
    }

    @Test
    void metadataCacheHitReturnsCachedEntry() throws Exception {
        // Prime cache: acquireMetadataRateLimit(t=0), CachedEntry(t=0)
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
        // Both limiters are independent — metadata and route both acquire at t=0.
        // Reads: acquireMetadata(0), cacheMetadata(0), acquireRoute(0), cacheRoute(0)
        when(clock.millis()).thenReturn(0L, 0L, 0L, 0L);
        when(client.fetchMetadata("abc123")).thenReturn(Optional.empty());
        when(client.fetchRoute("UAL123"))
                .thenReturn(Optional.of(
                        new AdsbDataClient.RouteInfo("UAL123", "KDFW", "KLAX")));
        service.enrich(basicAircraft("abc123", "UAL123"));

        // isExpired: 100 - 0 < ROUTE_TTL → not expired
        when(clock.millis()).thenReturn(100L);
        Optional<AdsbDataClient.RouteInfo> cached = service.getCachedRoute("UAL123");
        assertThat(cached).isNotNull().isPresent();
        assertThat(cached.get().departureAirport()).isEqualTo("KDFW");
    }

    @Test
    void routeCacheExpiredReturnsNull() throws Exception {
        when(clock.millis()).thenReturn(0L, 0L, 0L, 0L);
        when(client.fetchMetadata("abc123")).thenReturn(Optional.empty());
        when(client.fetchRoute("UAL123")).thenReturn(Optional.empty());
        service.enrich(basicAircraft("abc123", "UAL123"));

        // isExpired: now - 0 > ROUTE_TTL → expired
        when(clock.millis()).thenReturn(AdsbEnrichmentService.ROUTE_TTL_MS + 1);
        assertThat(service.getCachedRoute("UAL123")).isNull();
    }

    // ── enrich ────────────────────────────────────────────────────────────────

    @Test
    void enrichReturnsOriginalWhenMetadataEmptyAndNoCallsign() throws Exception {
        // acquireMetadata(0) + cache(0); no callsign → no route
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
        // Independent limiters: both metadata and route fetch at t=0 in a single enrich() call.
        // Reads: acquireMetadata(0), cacheMetadata(0), acquireRoute(0), cacheRoute(0)
        when(clock.millis()).thenReturn(0L, 0L, 0L, 0L);
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
        when(clock.millis()).thenReturn(0L, 0L, 0L, 0L);
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
        // acquireMetadataRateLimit passes (t=0), then client throws
        when(clock.millis()).thenReturn(0L);
        when(client.fetchMetadata("abc123")).thenThrow(new RuntimeException("network error"));

        AdsbAircraft aircraft = basicAircraft("abc123", null);
        assertThat(service.enrich(aircraft)).isSameAs(aircraft);
    }

    @Test
    void enrichRouteClientExceptionDoesNotPropagate() throws Exception {
        // metadata: acquireMetadata(0), cache(0); route: acquireRoute(0), client throws before cache
        when(clock.millis()).thenReturn(0L, 0L, 0L);
        when(client.fetchMetadata("abc123")).thenReturn(Optional.empty());
        when(client.fetchRoute("UAL123")).thenThrow(new RuntimeException("route error"));

        AdsbAircraft aircraft = basicAircraft("abc123", "UAL123");
        assertThat(service.enrich(aircraft)).isSameAs(aircraft);
    }

    @Test
    void enrichFetchMetadataRateLimitedReturnsOriginal() throws Exception {
        // Consume metadata rate limit manually at t=0
        when(clock.millis()).thenReturn(0L);
        service.acquireMetadataRateLimit();

        // Enrich: cache miss + rate limited → fetchMetadata not called → meta=null → original returned
        when(clock.millis()).thenReturn(0L);
        AdsbAircraft aircraft = basicAircraft("abc123", null);
        assertThat(service.enrich(aircraft)).isSameAs(aircraft);
        verify(client, never()).fetchMetadata(anyString());
    }

    @Test
    void enrichFetchRouteRateLimitedSkipsApiCall() throws Exception {
        // Consume route rate limit manually at t=0 (independent of metadata limiter)
        when(clock.millis()).thenReturn(0L);
        service.acquireRouteRateLimit();

        // Enrich: metadata acquires its independent limiter and fetches; route limiter is blocked.
        // Reads: acquireMetadata(0), cacheMetadata(0), acquireRoute(0 → blocked)
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
        // Prime both caches at t=0 (independent limiters, both acquire at t=0)
        when(clock.millis()).thenReturn(0L, 0L, 0L, 0L);
        when(client.fetchMetadata("abc123")).thenReturn(Optional.empty());
        when(client.fetchRoute("UAL123"))
                .thenReturn(Optional.of(
                        new AdsbDataClient.RouteInfo("UAL123", "KDFW", "KLAX")));
        service.enrich(basicAircraft("abc123", "UAL123"));

        // Second enrich: both caches hit (isExpired checks at t=100)
        when(clock.millis()).thenReturn(100L, 100L);
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

        // Second enrich: cache hit returns empty Optional; anyEnriched stays false → original returned
        when(clock.millis()).thenReturn(100L);
        AdsbAircraft aircraft = basicAircraft("abc123", null);
        assertThat(service.enrich(aircraft)).isSameAs(aircraft);
        verify(client, times(1)).fetchMetadata(anyString());
    }

    @Test
    void productionConstructorCreatesServiceWithRealClock() {
        // Exercises the public constructor: AdsbEnrichmentService(AdsbDataClient)
        // Both limiters initialise to -RATE_LIMIT_MS; real clock >> 0 → both first calls pass.
        AdsbEnrichmentService svc = new AdsbEnrichmentService(client);
        assertThat(svc.acquireMetadataRateLimit()).isTrue();
        assertThat(svc.acquireRouteRateLimit()).isTrue();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static AdsbAircraft basicAircraft(String icao24, String callsign) {
        return new AdsbAircraft(
                icao24, callsign, 35.0, -97.0, 10000.0, 480.0, 90.0, 0.0, false,
                null, null, null, null);
    }
}
