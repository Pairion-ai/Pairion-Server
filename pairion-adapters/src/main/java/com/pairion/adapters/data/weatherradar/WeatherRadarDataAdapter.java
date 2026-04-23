package com.pairion.adapters.data.weatherradar;

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
 * Polls the RainViewer public weather radar API on a fixed schedule and delivers radar tile
 * metadata snapshots to a registered consumer.
 *
 * <p>Polling begins when a consumer registers via {@link #startPolling(Consumer)} and stops when
 * {@link #stopPolling()} is called. Only one consumer may be active at a time. A second call to
 * {@link #startPolling(Consumer)} replaces the previous consumer and restarts polling.
 *
 * <p>The poll interval is configured via {@code pairion.data.weatherradar.poll-interval-seconds}
 * (default: 300 seconds / 5 minutes).
 */
@Component
public class WeatherRadarDataAdapter {

    private static final Logger log = LoggerFactory.getLogger(WeatherRadarDataAdapter.class);

    private final WeatherRadarDataClient client;
    private final int pollIntervalSeconds;

    private volatile Consumer<WeatherRadarSnapshot> dataSink;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pollFuture;

    /**
     * Constructs the adapter with the configured poll interval.
     *
     * @param client              the weather radar data client boundary for HTTP calls
     * @param pollIntervalSeconds polling interval in seconds (default: 300)
     */
    public WeatherRadarDataAdapter(
            WeatherRadarDataClient client,
            @Value("${pairion.data.weatherradar.poll-interval-seconds:300}") int pollIntervalSeconds) {
        this.client = client;
        this.pollIntervalSeconds = pollIntervalSeconds;
    }

    /**
     * Registers a consumer and begins polling the RainViewer API on the configured schedule.
     *
     * <p>If polling is already active, the previous consumer is replaced and the polling schedule
     * is restarted immediately.
     *
     * @param sink consumer that receives weather radar snapshots after each poll
     */
    public synchronized void startPolling(Consumer<WeatherRadarSnapshot> sink) {
        stopPolling();
        this.dataSink = sink;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "weather-radar-poller");
            t.setDaemon(true);
            return t;
        });
        this.pollFuture = scheduler.scheduleAtFixedRate(
                this::poll, 0, pollIntervalSeconds, TimeUnit.SECONDS);
        log.info("weather.radar.polling.started: interval={}s", pollIntervalSeconds);
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
        log.info("weather.radar.polling.stopped");
    }

    /**
     * Fetches the current weather radar snapshot from the RainViewer API and delivers the result
     * to the registered consumer.
     *
     * <p>If the consumer is null (polling was stopped), or if the API call fails, this method
     * returns silently without throwing.
     */
    void poll() {
        Consumer<WeatherRadarSnapshot> sink = dataSink;
        if (sink == null) {
            return;
        }
        try {
            WeatherRadarSnapshot snapshot = client.fetchSnapshot();
            log.debug("weather.radar.poll.done: frames={}", snapshot.frames().size());
            sink.accept(snapshot);
        } catch (Exception e) {
            log.warn("weather.radar.poll.error: {}", e.getMessage());
        }
    }
}
