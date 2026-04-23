package com.pairion.adapters.data.weatherradar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Production HTTP client for the RainViewer public weather radar API.
 *
 * <p>Makes a real network call to {@code api.rainviewer.com}. This class is excluded from JaCoCo
 * coverage; it is unit-tested via the {@link WeatherRadarDataClient} boundary using an in-memory
 * stub implementation.
 *
 * <p>API endpoint: {@code GET https://api.rainviewer.com/public/weather-maps.json}
 * No authentication is required.
 */
@Component
class HttpWeatherRadarDataClient implements WeatherRadarDataClient {

    private static final Logger log = LoggerFactory.getLogger(HttpWeatherRadarDataClient.class);

    private static final String WEATHER_MAPS_URL =
            "https://api.rainviewer.com/public/weather-maps.json";

    /** Fixed tile size used by RainViewer. */
    private static final int TILE_SIZE = 256;

    /** Default RainViewer color scheme index (4 = TurboMap). */
    private static final int COLOR_SCHEME = 4;

    /** RainViewer tile options: smooth=1, snow=1. */
    private static final String OPTIONS = "1_1";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    HttpWeatherRadarDataClient() {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public WeatherRadarSnapshot fetchSnapshot() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(WEATHER_MAPS_URL))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("weather.radar.api.error: HTTP {}", response.statusCode());
            throw new Exception("RainViewer API returned HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String host = root.path("host").asText();
        JsonNode past = root.path("radar").path("past");

        List<WeatherRadarFrame> frames = new ArrayList<>();
        String latestPath = null;
        if (past.isArray()) {
            for (JsonNode frame : past) {
                long time = frame.path("time").asLong();
                String path = frame.path("path").asText();
                frames.add(new WeatherRadarFrame(time, path));
                latestPath = path;
            }
        }

        log.debug("weather.radar.snapshot.received: host={}, frames={}", host, frames.size());
        return new WeatherRadarSnapshot(host, frames, latestPath, TILE_SIZE, COLOR_SCHEME, OPTIONS);
    }
}
