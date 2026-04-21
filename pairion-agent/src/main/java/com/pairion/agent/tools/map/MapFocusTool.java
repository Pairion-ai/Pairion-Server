package com.pairion.agent.tools.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairion.agent.tools.AgentTool;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Agent tool that resolves a location name to geographic coordinates for map focus.
 *
 * <p>No API key is required. Uses the Open-Meteo geocoding API:
 * {@code https://geocoding-api.open-meteo.com/v1/search?name=<location>&count=1}
 *
 * <p>The LLM calls this tool when the conversation references a specific geographic place.
 * On success, {@link com.pairion.agent.session.AgentSession} emits a {@code MapFocus} WebSocket
 * message to the client, which pans and zooms the globe to that location.
 *
 * <p>Supported zoom levels (passed through from LLM input):
 * <ul>
 *   <li>{@code continent} — 1.2× zoom, shows a broad continental region</li>
 *   <li>{@code country} — 1.8× zoom, fills the screen with the country</li>
 *   <li>{@code region} — 2.8× zoom, shows a state, prefecture, or province</li>
 *   <li>{@code city} — 4.0× zoom, centres on a city or specific location</li>
 * </ul>
 */
@Component
public class MapFocusTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(MapFocusTool.class);

    /** Tool name as registered in the LLM tool definition. */
    public static final String TOOL_NAME = "focus_map";

    private static final String GEOCODING_URL =
            "https://geocoding-api.open-meteo.com/v1/search"
                    + "?name=%s&count=1&language=en&format=json";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the map focus tool with a default 5-second timeout HTTP client.
     */
    public MapFocusTool() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                new ObjectMapper());
    }

    /**
     * Package-private constructor for testing — allows injecting a stub HTTP client.
     *
     * @param httpClient the HTTP client to use for geocoding calls
     * @param objectMapper the Jackson mapper for JSON parsing
     */
    MapFocusTool(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    /**
     * Geocodes the location specified in {@code input.get("location")} and returns its coordinates.
     *
     * <p>Input parameters:
     * <ul>
     *   <li>{@code location} (required) — place name, e.g. "Tokyo", "Okinawa Prefecture", "Japan"</li>
     *   <li>{@code zoom} (optional, default: {@code "city"}) — zoom level for the client map view</li>
     * </ul>
     *
     * <p>On success, returns a map with keys: {@code lat}, {@code lon}, {@code label}, {@code zoom},
     * {@code status}. On failure, returns a map with {@code error} and {@code message}.
     *
     * @param input tool call input from the LLM
     * @return geocoded location data or an error response
     */
    @Override
    public Map<String, Object> execute(Map<String, Object> input) throws Exception {
        Object locationObj = input.get("location");
        if (locationObj == null) {
            return Map.of("error", "missing_parameter",
                    "message", "location parameter is required");
        }
        String location = locationObj.toString();
        String zoom = input.containsKey("zoom") ? input.get("zoom").toString() : "city";

        log.info("map.focus: location={}, zoom={}", location, zoom);

        String encodedLocation = URLEncoder.encode(location, StandardCharsets.UTF_8);
        String geocodingUrl = String.format(GEOCODING_URL, encodedLocation);

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(geocodingUrl))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return Map.of("error", "geocoding_failed",
                    "message", "HTTP " + response.statusCode());
        }

        JsonNode json = objectMapper.readTree(response.body());
        JsonNode results = json.path("results");
        if (!results.isArray() || results.isEmpty()) {
            return Map.of("error", "location_not_found",
                    "message", "No geocoding results for: " + location);
        }

        JsonNode first = results.get(0);
        double lat = first.path("latitude").asDouble();
        double lon = first.path("longitude").asDouble();
        String resolvedName = first.path("name").asText(location);
        String country = first.path("country").asText("");

        String label = country.isEmpty() ? resolvedName : resolvedName + ", " + country;
        log.info("map.geocoded: label={}, lat={}, lon={}", label, lat, lon);

        return Map.of(
                "lat", lat,
                "lon", lon,
                "label", label,
                "zoom", zoom,
                "status", "focused");
    }
}
