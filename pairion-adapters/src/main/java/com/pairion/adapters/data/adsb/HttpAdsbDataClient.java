package com.pairion.adapters.data.adsb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Production HTTP client for the OpenSky Network REST API.
 *
 * <p>All methods make real network calls to {@code opensky-network.org}. This class is excluded
 * from JaCoCo coverage; it is unit-tested via the {@link AdsbDataClient} boundary using the
 * in-memory stub implementation.
 *
 * <p>When {@code pairion.data.adsb.opensky-username} and {@code pairion.data.adsb.opensky-password}
 * are configured, every request includes an HTTP Basic {@code Authorization} header. If either
 * credential is blank the header is omitted and calls proceed unauthenticated (reduced rate limits).
 *
 * <p>API endpoints used:
 *
 * <ul>
 *   <li>States: {@code GET https://opensky-network.org/api/states/all?lamin=...&lomin=...}
 *   <li>Metadata: {@code GET https://opensky-network.org/api/metadata/aircraft/icao/{icao24}}
 *   <li>Routes: {@code GET https://opensky-network.org/api/routes?callsign={cs}}
 * </ul>
 */
@Component
class HttpAdsbDataClient implements AdsbDataClient {

    private static final Logger log = LoggerFactory.getLogger(HttpAdsbDataClient.class);

    private static final String STATES_URL =
            "https://opensky-network.org/api/states/all"
                    + "?lamin=%s&lomin=%s&lamax=%s&lomax=%s";

    private static final String METADATA_URL =
            "https://opensky-network.org/api/metadata/aircraft/icao/%s";

    private static final String ROUTE_URL =
            "https://opensky-network.org/api/routes?callsign=%s";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Precomputed {@code Authorization: Basic ...} header value, or {@code null} when either
     * credential is blank (unauthenticated mode).
     */
    private final String authHeader;

    /**
     * Constructs the client with a 10-second connect timeout and optional Basic auth credentials.
     *
     * @param username OpenSky Network username; empty string disables authentication
     * @param password OpenSky Network password; empty string disables authentication
     */
    HttpAdsbDataClient(
            @Value("${pairion.data.adsb.opensky-username:}") String username,
            @Value("${pairion.data.adsb.opensky-password:}") String password) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = new ObjectMapper();
        this.authHeader = buildAuthHeader(username, password);
    }

    /**
     * Builds the HTTP Basic {@code Authorization} header value from the supplied credentials.
     * Returns {@code null} when either credential is blank so callers can skip adding the header.
     *
     * @param username OpenSky Network username
     * @param password OpenSky Network password
     * @return the header value, or {@code null} if credentials are incomplete
     */
    private static String buildAuthHeader(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return null;
        }
        String encoded = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    @Override
    public List<List<Object>> fetchStates(
            double lamin, double lomin, double lamax, double lomax) throws Exception {
        String url = String.format(STATES_URL, lamin, lomin, lamax, lomax);
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET();
        if (authHeader != null) {
            rb.header("Authorization", authHeader);
        }
        HttpRequest request = rb.build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("adsb.states.error: HTTP {}", response.statusCode());
            return List.of();
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode states = root.path("states");
        if (!states.isArray() || states.isEmpty()) {
            return List.of();
        }

        List<List<Object>> result = new ArrayList<>(states.size());
        for (JsonNode state : states) {
            List<Object> row = new ArrayList<>(17);
            for (JsonNode element : state) {
                if (element.isNull()) {
                    row.add(null);
                } else if (element.isBoolean()) {
                    row.add(element.asBoolean());
                } else if (element.isDouble() || element.isFloat()) {
                    row.add(element.asDouble());
                } else if (element.isInt() || element.isLong()) {
                    row.add(element.asLong());
                } else {
                    row.add(element.asText());
                }
            }
            result.add(row);
        }
        log.debug("adsb.states.received: count={}", result.size());
        return result;
    }

    @Override
    public Optional<AircraftMetadata> fetchMetadata(String icao24) throws Exception {
        String url = String.format(METADATA_URL, icao24.toLowerCase());
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .GET();
        if (authHeader != null) {
            rb.header("Authorization", authHeader);
        }
        HttpRequest request = rb.build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            log.warn("adsb.metadata.error: icao24={}, HTTP {}", icao24, response.statusCode());
            return Optional.empty();
        }

        JsonNode root = objectMapper.readTree(response.body());
        String registration = root.path("registration").asText(null);
        String typecode = root.path("typecode").asText(null);
        return Optional.of(new AircraftMetadata(icao24, registration, typecode));
    }

    @Override
    public Optional<RouteInfo> fetchRoute(String callsign) throws Exception {
        String url = String.format(ROUTE_URL, callsign.trim().toUpperCase());
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .GET();
        if (authHeader != null) {
            rb.header("Authorization", authHeader);
        }
        HttpRequest request = rb.build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            log.warn("adsb.route.error: callsign={}, HTTP {}", callsign, response.statusCode());
            return Optional.empty();
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode route = root.path("route");
        if (!route.isArray() || route.size() < 2) {
            return Optional.empty();
        }
        String departure = route.get(0).asText(null);
        String destination = route.get(route.size() - 1).asText(null);
        return Optional.of(new RouteInfo(callsign.trim().toUpperCase(), departure, destination));
    }
}
