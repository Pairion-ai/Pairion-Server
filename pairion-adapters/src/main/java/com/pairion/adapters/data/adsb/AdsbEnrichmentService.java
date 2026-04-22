package com.pairion.adapters.data.adsb;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Enriches basic {@link AdsbAircraft} state snapshots with aircraft registration, type, and route
 * information fetched from the OpenSky Network metadata and route APIs.
 *
 * <p>Enrichment is subject to two constraints:
 *
 * <ol>
 *   <li><strong>TTL cache</strong> — metadata is cached for {@value #METADATA_TTL_MS} ms and route
 *       info for {@value #ROUTE_TTL_MS} ms. A negative sentinel ({@code Optional.empty()}) is also
 *       cached to avoid re-querying unknown aircraft.
 *   <li><strong>Rate limiter</strong> — at most one enrichment lookup per second (across all
 *       aircraft) to avoid flooding the OpenSky API, which imposes strict anonymous rate limits.
 * </ol>
 *
 * <p>This class is thread-safe. All mutable state is accessed through {@link ConcurrentHashMap} and
 * {@link AtomicLong}.
 */
@Component
public class AdsbEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(AdsbEnrichmentService.class);

    /** Metadata cache TTL: 1 hour in milliseconds. */
    static final long METADATA_TTL_MS = Duration.ofHours(1).toMillis();

    /** Route cache TTL: 30 minutes in milliseconds. */
    static final long ROUTE_TTL_MS = Duration.ofMinutes(30).toMillis();

    /** Minimum gap between any enrichment API call in milliseconds (1 call/sec). */
    static final long RATE_LIMIT_MS = 1_000L;

    private final AdsbDataClient client;
    private final Clock clock;

    /** Cache keyed by icao24 → cached metadata entry (may hold an empty Optional). */
    private final ConcurrentHashMap<String, CachedEntry<Optional<AdsbDataClient.AircraftMetadata>>>
            metadataCache = new ConcurrentHashMap<>();

    /** Cache keyed by callsign → cached route entry (may hold an empty Optional). */
    private final ConcurrentHashMap<String, CachedEntry<Optional<AdsbDataClient.RouteInfo>>>
            routeCache = new ConcurrentHashMap<>();

    /**
     * Epoch-millis timestamp of the last enrichment API call. Initialized to
     * {@code -RATE_LIMIT_MS} so the first call at any time (including t=0 in tests) is always
     * permitted — equivalent to "last call happened one full window in the past."
     */
    private final AtomicLong lastCallMs = new AtomicLong(-RATE_LIMIT_MS);

    /**
     * Constructs the enrichment service with a production clock.
     *
     * @param client the ADS-B data client boundary used for API lookups
     */
    @Autowired
    public AdsbEnrichmentService(AdsbDataClient client) {
        this(client, Clock.systemUTC());
    }

    /**
     * Package-private constructor for testing — allows injecting a stub clock.
     *
     * @param client the ADS-B data client boundary
     * @param clock the clock used for TTL and rate-limit decisions
     */
    AdsbEnrichmentService(AdsbDataClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    /**
     * Returns an enriched copy of {@code aircraft} with registration, type, and route fields
     * populated where available from the cache or (rate-limited) from the OpenSky API.
     *
     * <p>If the rate limit is active or enrichment lookups fail, the original aircraft object is
     * returned unchanged.
     *
     * @param aircraft the basic aircraft state snapshot to enrich
     * @return an enriched {@link AdsbAircraft}, or {@code aircraft} unchanged if enrichment is
     *     unavailable
     */
    public AdsbAircraft enrich(AdsbAircraft aircraft) {
        String icao24 = aircraft.icao24();
        String registration = null;
        String aircraftType = null;
        String origin = null;
        String destination = null;
        boolean anyEnriched = false;

        // Metadata lookup
        Optional<AdsbDataClient.AircraftMetadata> meta = getCachedMetadata(icao24);
        if (meta == null) {
            meta = fetchMetadata(icao24);
        }
        if (meta != null && meta.isPresent()) {
            registration = meta.get().registration();
            aircraftType = meta.get().typecode();
            anyEnriched = true;
        }

        // Route lookup — only if callsign is available
        String callsign = aircraft.callsign();
        if (callsign != null && !callsign.isBlank()) {
            Optional<AdsbDataClient.RouteInfo> route = getCachedRoute(callsign);
            if (route == null) {
                route = fetchRoute(callsign);
            }
            if (route != null && route.isPresent()) {
                origin = route.get().departureAirport();
                destination = route.get().destinationAirport();
                anyEnriched = true;
            }
        }

        // If nothing was enriched, return the original to avoid allocation
        if (!anyEnriched) {
            return aircraft;
        }

        return new AdsbAircraft(
                aircraft.icao24(),
                aircraft.callsign(),
                aircraft.lat(),
                aircraft.lon(),
                aircraft.altitudeFt(),
                aircraft.speedKnots(),
                aircraft.trackDeg(),
                aircraft.verticalRateFpm(),
                aircraft.onGround(),
                registration,
                aircraftType,
                origin,
                destination);
    }

    /**
     * Returns the cached metadata for {@code icao24} if present and not expired. Returns
     * {@code null} if the cache entry is absent or expired.
     *
     * @param icao24 the ICAO 24-bit address
     * @return cached value (may be empty Optional), or null if cache miss
     */
    Optional<AdsbDataClient.AircraftMetadata> getCachedMetadata(String icao24) {
        CachedEntry<Optional<AdsbDataClient.AircraftMetadata>> entry =
                metadataCache.get(icao24);
        if (entry == null || isExpired(entry, METADATA_TTL_MS)) {
            return null;
        }
        return entry.value();
    }

    /**
     * Returns the cached route for {@code callsign} if present and not expired. Returns
     * {@code null} if the cache entry is absent or expired.
     *
     * @param callsign the trimmed call sign
     * @return cached value (may be empty Optional), or null if cache miss
     */
    Optional<AdsbDataClient.RouteInfo> getCachedRoute(String callsign) {
        CachedEntry<Optional<AdsbDataClient.RouteInfo>> entry = routeCache.get(callsign);
        if (entry == null || isExpired(entry, ROUTE_TTL_MS)) {
            return null;
        }
        return entry.value();
    }

    /**
     * Fetches aircraft metadata from the API, subject to the rate limit. Caches both positive and
     * empty results.
     *
     * @param icao24 the ICAO 24-bit address
     * @return the lookup result, or null if rate-limited
     */
    private Optional<AdsbDataClient.AircraftMetadata> fetchMetadata(String icao24) {
        if (!acquireRateLimit()) {
            return null;
        }
        try {
            Optional<AdsbDataClient.AircraftMetadata> result = client.fetchMetadata(icao24);
            metadataCache.put(icao24, new CachedEntry<>(result, clock.millis()));
            log.debug("adsb.enrich.metadata: icao24={}, found={}", icao24, result.isPresent());
            return result;
        } catch (Exception e) {
            log.warn("adsb.enrich.metadata.error: icao24={}, error={}", icao24, e.getMessage());
            return null;
        }
    }

    /**
     * Fetches route information from the API, subject to the rate limit. Caches both positive and
     * empty results.
     *
     * @param callsign the trimmed call sign
     * @return the lookup result, or null if rate-limited
     */
    private Optional<AdsbDataClient.RouteInfo> fetchRoute(String callsign) {
        if (!acquireRateLimit()) {
            return null;
        }
        try {
            Optional<AdsbDataClient.RouteInfo> result = client.fetchRoute(callsign);
            routeCache.put(callsign, new CachedEntry<>(result, clock.millis()));
            log.debug(
                    "adsb.enrich.route: callsign={}, found={}", callsign, result.isPresent());
            return result;
        } catch (Exception e) {
            log.warn(
                    "adsb.enrich.route.error: callsign={}, error={}", callsign, e.getMessage());
            return null;
        }
    }

    /**
     * Attempts to acquire the rate limit token. Returns {@code true} if at least {@value
     * #RATE_LIMIT_MS} ms have elapsed since the last call, updating the last-call timestamp
     * atomically. Returns {@code false} if the rate limit is active.
     *
     * @return true if the caller may proceed with an API call
     */
    boolean acquireRateLimit() {
        long now = clock.millis();
        long last = lastCallMs.get();
        if (now - last < RATE_LIMIT_MS) {
            return false;
        }
        return lastCallMs.compareAndSet(last, now);
    }

    /**
     * Returns true if the cache entry's age exceeds the given TTL.
     *
     * @param entry the cache entry
     * @param ttlMs TTL in milliseconds
     * @return true if expired
     */
    private boolean isExpired(CachedEntry<?> entry, long ttlMs) {
        return (clock.millis() - entry.createdAtMs()) >= ttlMs;
    }

    /**
     * Immutable wrapper pairing a cached value with its creation timestamp.
     *
     * @param value the cached value
     * @param createdAtMs epoch-millisecond timestamp when this entry was created
     * @param <T> the value type
     */
    record CachedEntry<T>(T value, long createdAtMs) {}
}
