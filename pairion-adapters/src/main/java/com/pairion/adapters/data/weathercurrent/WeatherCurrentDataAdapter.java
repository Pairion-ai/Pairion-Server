package com.pairion.adapters.data.weathercurrent;

import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * On-demand adapter that fetches current weather conditions for a named city and delivers a {@link
 * WeatherCurrentSnapshot} to a registered consumer.
 *
 * <p>Unlike polling adapters, this adapter fetches exactly once per {@link #start(String,
 * Consumer)} call. The fetch runs on a virtual thread so the caller is not blocked while the HTTP
 * calls complete. The result is pushed to the consumer when the fetch succeeds; on failure, the
 * error is logged and the consumer is not called.
 */
@Component
public class WeatherCurrentDataAdapter {

    private static final Logger log = LoggerFactory.getLogger(WeatherCurrentDataAdapter.class);

    private final WeatherCurrentDataClient client;

    /**
     * Constructs the adapter with the given data client.
     *
     * @param client the data client boundary for HTTP calls
     */
    public WeatherCurrentDataAdapter(WeatherCurrentDataClient client) {
        this.client = client;
    }

    /**
     * Initiates an on-demand weather fetch for the given city on a virtual thread.
     *
     * <p>The fetch completes asynchronously. On success, the snapshot is pushed to {@code sink}. On
     * failure, the error is logged and {@code sink} is not called.
     *
     * @param city the city name to fetch weather for
     * @param sink consumer that receives the {@link WeatherCurrentSnapshot} on success
     */
    public void start(String city, Consumer<WeatherCurrentSnapshot> sink) {
        log.info("weather.current.fetch.started: city={}", city);
        Thread.ofVirtual().name("weather-current-fetch").start(() -> fetch(city, sink));
    }

    /**
     * Fetches the current weather snapshot for the given city and pushes the result to the sink.
     *
     * <p>Called from a virtual thread by {@link #start(String, Consumer)}. Package-private to allow
     * direct synchronous invocation in unit tests without spawning a thread.
     *
     * @param city the city name to fetch weather for
     * @param sink consumer that receives the snapshot on success
     */
    void fetch(String city, Consumer<WeatherCurrentSnapshot> sink) {
        try {
            WeatherCurrentSnapshot snapshot = client.fetchSnapshot(city);
            log.debug("weather.current.fetched: city={}", snapshot.city());
            sink.accept(snapshot);
        } catch (Exception e) {
            log.warn("weather.current.fetch.error: city={}, error={}", city, e.getMessage());
        }
    }
}
