package com.pairion.adapters.data.adsb;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Polls the OpenSky Network ADS-B API on a fixed 10-second schedule and delivers enriched aircraft
 * state snapshots to a registered consumer.
 *
 * <p>Polling begins when a consumer registers via {@link #startPolling(Consumer)} and stops when
 * {@link #stopPolling()} is called. Only one consumer may be active at a time. A second call to
 * {@link #startPolling(Consumer)} replaces the previous consumer and restarts polling.
 *
 * <p>The geographic bounding box is configured via the following properties (all required):
 *
 * <ul>
 *   <li>{@code pairion.data.adsb.lamin} — southern boundary (decimal degrees)
 *   <li>{@code pairion.data.adsb.lomin} — western boundary (decimal degrees)
 *   <li>{@code pairion.data.adsb.lamax} — northern boundary (decimal degrees)
 *   <li>{@code pairion.data.adsb.lomax} — eastern boundary (decimal degrees)
 *   <li>{@code pairion.data.adsb.poll-interval-seconds} — poll interval (default: 10)
 * </ul>
 *
 * <p>Unit conversions applied to raw OpenSky values:
 *
 * <ul>
 *   <li>Altitude: metres × 3.28084 → feet
 *   <li>Speed: m/s × 1.94384 → knots
 *   <li>Vertical rate: m/s × 196.850 → feet per minute
 * </ul>
 */
@Component
public class AdsbDataAdapter {

    private static final Logger log = LoggerFactory.getLogger(AdsbDataAdapter.class);

    /** Conversion factor from metres to feet. */
    static final double METRES_TO_FEET = 3.28084;

    /** Conversion factor from metres/second to knots. */
    static final double MS_TO_KNOTS = 1.94384;

    /** Conversion factor from metres/second to feet per minute. */
    static final double MS_TO_FPM = 196.850;

    private final AdsbDataClient client;
    private final AdsbEnrichmentService enrichmentService;
    private final double lamin;
    private final double lomin;
    private final double lamax;
    private final double lomax;
    private final int pollIntervalSeconds;

    private volatile Consumer<List<AdsbAircraft>> dataSink;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pollFuture;

    /**
     * Constructs the adapter with the configured bounding box and poll interval.
     *
     * @param client the ADS-B data client boundary for HTTP calls
     * @param enrichmentService the enrichment service for registration/type/route lookups
     * @param lamin southern latitude boundary in decimal degrees
     * @param lomin western longitude boundary in decimal degrees
     * @param lamax northern latitude boundary in decimal degrees
     * @param lomax eastern longitude boundary in decimal degrees
     * @param pollIntervalSeconds polling interval in seconds (default: 10)
     */
    public AdsbDataAdapter(
            AdsbDataClient client,
            AdsbEnrichmentService enrichmentService,
            @Value("${pairion.data.adsb.lamin:24.0}") double lamin,
            @Value("${pairion.data.adsb.lomin:-125.0}") double lomin,
            @Value("${pairion.data.adsb.lamax:49.0}") double lamax,
            @Value("${pairion.data.adsb.lomax:-66.0}") double lomax,
            @Value("${pairion.data.adsb.poll-interval-seconds:10}") int pollIntervalSeconds) {
        this.client = client;
        this.enrichmentService = enrichmentService;
        this.lamin = lamin;
        this.lomin = lomin;
        this.lamax = lamax;
        this.lomax = lomax;
        this.pollIntervalSeconds = pollIntervalSeconds;
    }

    /**
     * Registers a consumer and begins polling the OpenSky API on the configured schedule.
     *
     * <p>If polling is already active, the previous consumer is replaced and the polling schedule
     * is restarted immediately.
     *
     * @param sink consumer that receives enriched aircraft snapshots after each poll
     */
    public synchronized void startPolling(Consumer<List<AdsbAircraft>> sink) {
        stopPolling();
        this.dataSink = sink;
        this.scheduler =
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "adsb-poller");
                    t.setDaemon(true);
                    return t;
                });
        this.pollFuture =
                scheduler.scheduleAtFixedRate(
                        this::poll, 0, pollIntervalSeconds, TimeUnit.SECONDS);
        log.info(
                "adsb.polling.started: interval={}s, bbox=[{},{},{},{}]",
                pollIntervalSeconds,
                lamin,
                lomin,
                lamax,
                lomax);
    }

    /**
     * Deregisters the consumer and stops polling. Safe to call multiple times.
     */
    public synchronized void stopPolling() {
        if (pollFuture != null) {
            pollFuture.cancel(false);
            pollFuture = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        dataSink = null;
        log.info("adsb.polling.stopped");
    }

    /**
     * Fetches the current ADS-B state snapshot, parses and converts all aircraft state vectors,
     * enriches them, and delivers the result to the registered consumer.
     *
     * <p>If the consumer is null (polling was stopped), or if the API call fails, this method
     * returns silently without throwing.
     */
    void poll() {
        Consumer<List<AdsbAircraft>> sink = dataSink;
        if (sink == null) {
            return;
        }
        try {
            List<List<Object>> states = client.fetchStates(lamin, lomin, lamax, lomax);
            List<AdsbAircraft> aircraft = new ArrayList<>(states.size());
            for (List<Object> state : states) {
                AdsbAircraft basic = parseState(state);
                if (basic != null) {
                    aircraft.add(enrichmentService.enrich(basic));
                }
            }
            log.debug("adsb.poll.done: count={}", aircraft.size());
            sink.accept(aircraft);
        } catch (Exception e) {
            log.warn("adsb.poll.error: {}", e.getMessage());
        }
    }

    /**
     * Parses a single OpenSky state vector array into an {@link AdsbAircraft}.
     *
     * <p>Returns {@code null} if the state vector does not have a valid latitude and longitude
     * (i.e., the aircraft position is unknown). Aircraft on the ground with no position are
     * filtered out.
     *
     * @param state the OpenSky state array (positional, 0-indexed per OpenSky schema)
     * @return the parsed aircraft, or null if position is unknown
     */
    AdsbAircraft parseState(List<Object> state) {
        if (state == null || state.size() < 11) {
            return null;
        }

        String icao24 = asString(state, 0);
        if (icao24 == null || icao24.isBlank()) {
            return null;
        }

        String callsign = asString(state, 1);
        if (callsign != null) {
            callsign = callsign.trim();
            if (callsign.isEmpty()) {
                callsign = null;
            }
        }

        Double lon = asDouble(state, 5);
        Double lat = asDouble(state, 6);

        // Filter out aircraft with no known position
        if (lat == null || lon == null) {
            return null;
        }

        Double baroAltMetres = asDouble(state, 7);
        Double altitudeFt = baroAltMetres != null ? round2(baroAltMetres * METRES_TO_FEET) : null;

        boolean onGround = asBoolean(state, 8);

        Double velocityMs = asDouble(state, 9);
        Double speedKnots = velocityMs != null ? round2(velocityMs * MS_TO_KNOTS) : null;

        Double trackDeg = asDouble(state, 10);

        Double vertRateMs = asDouble(state, 11);
        Double verticalRateFpm =
                vertRateMs != null ? round2(vertRateMs * MS_TO_FPM) : null;

        return new AdsbAircraft(
                icao24,
                callsign,
                round5(lat),
                round5(lon),
                altitudeFt,
                speedKnots,
                trackDeg != null ? round2(trackDeg) : null,
                verticalRateFpm,
                onGround,
                null,
                null,
                null,
                null);
    }

    private static String asString(List<Object> state, int index) {
        Object v = state.get(index);
        return v != null ? v.toString() : null;
    }

    private static Double asDouble(List<Object> state, int index) {
        Object v = state.get(index);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }

    private static boolean asBoolean(List<Object> state, int index) {
        Object v = state.get(index);
        if (v instanceof Boolean b) {
            return b;
        }
        return false;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double round5(double value) {
        return Math.round(value * 100000.0) / 100000.0;
    }
}
