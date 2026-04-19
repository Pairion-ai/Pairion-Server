package com.pairion.agent.tools.weather;

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
 * Agent tool that fetches current weather using the Open-Meteo free API.
 *
 * <p>No API key is required. The tool performs two HTTP calls:
 *
 * <ol>
 *   <li>Geocoding: {@code https://geocoding-api.open-meteo.com/v1/search?name=<city>&count=1} —
 *       resolves the city name to latitude/longitude.
 *   <li>Forecast: {@code https://api.open-meteo.com/v1/forecast?latitude=...&longitude=...
 *       &current=temperature_2m,weathercode,windspeed_10m&temperature_unit=fahrenheit} — fetches
 *       current conditions.
 * </ol>
 *
 * <p>Both calls use a 5-second timeout. Returns a structured map describing current temperature
 * (°F), WMO weather code description, and wind speed (km/h). On any error, returns a structured
 * error map so the LLM can respond gracefully.
 */
@Component
public class OpenMeteoWeatherTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoWeatherTool.class);

    /** Tool name as registered in LLM tool definition. */
    public static final String TOOL_NAME = "get_current_weather";

    private static final String GEOCODING_URL =
            "https://geocoding-api.open-meteo.com/v1/search?name=%s&count=1&language=en&format=json";

    private static final String FORECAST_URL =
            "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=%s&longitude=%s"
                    + "&current=temperature_2m,weathercode,windspeed_10m"
                    + "&temperature_unit=fahrenheit"
                    + "&wind_speed_unit=mph"
                    + "&timezone=auto";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the weather tool with a default 5-second timeout HTTP client.
     */
    public OpenMeteoWeatherTool() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                new ObjectMapper());
    }

    /**
     * Package-private constructor for testing — allows injecting a stub HTTP client.
     *
     * @param httpClient the HTTP client to use for API calls
     * @param objectMapper the Jackson mapper for JSON parsing
     */
    OpenMeteoWeatherTool(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    /**
     * Fetches current weather for the city specified in {@code input.get("city")}.
     *
     * <p>Returns a map with keys: {@code city}, {@code temperature_f}, {@code conditions},
     * {@code wind_speed_mph}, and {@code latitude}/{@code longitude}. On failure, returns a map
     * with {@code error} and {@code message}.
     *
     * @param input must contain {@code "city"} key with a city name string
     * @return current weather data or an error response
     */
    @Override
    public Map<String, Object> execute(Map<String, Object> input) throws Exception {
        Object cityObj = input.get("city");
        if (cityObj == null) {
            return Map.of("error", "missing_parameter", "message", "city parameter is required");
        }
        String city = cityObj.toString();
        log.info("weather.fetch: city={}", city);

        // Step 1: Geocode the city
        String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
        String geocodingUrl = String.format(GEOCODING_URL, encodedCity);

        HttpRequest geoRequest =
                HttpRequest.newBuilder()
                        .uri(URI.create(geocodingUrl))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();

        HttpResponse<String> geoResponse =
                httpClient.send(geoRequest, HttpResponse.BodyHandlers.ofString());
        if (geoResponse.statusCode() != 200) {
            return Map.of("error", "geocoding_failed", "message", "HTTP " + geoResponse.statusCode());
        }

        JsonNode geoJson = objectMapper.readTree(geoResponse.body());
        JsonNode results = geoJson.path("results");
        if (!results.isArray() || results.isEmpty()) {
            return Map.of("error", "city_not_found", "message", "No results for city: " + city);
        }

        JsonNode firstResult = results.get(0);
        double lat = firstResult.path("latitude").asDouble();
        double lon = firstResult.path("longitude").asDouble();
        String resolvedName = firstResult.path("name").asText(city);
        String country = firstResult.path("country").asText("");

        log.info("weather.geocoded: city={}, lat={}, lon={}", resolvedName, lat, lon);

        // Step 2: Fetch current forecast
        String forecastUrl = String.format(FORECAST_URL, lat, lon);
        HttpRequest forecastRequest =
                HttpRequest.newBuilder()
                        .uri(URI.create(forecastUrl))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();

        HttpResponse<String> forecastResponse =
                httpClient.send(forecastRequest, HttpResponse.BodyHandlers.ofString());
        if (forecastResponse.statusCode() != 200) {
            return Map.of("error", "forecast_failed", "message", "HTTP " + forecastResponse.statusCode());
        }

        JsonNode forecastJson = objectMapper.readTree(forecastResponse.body());
        JsonNode current = forecastJson.path("current");

        double tempF = current.path("temperature_2m").asDouble();
        int weatherCode = current.path("weathercode").asInt();
        double windMph = current.path("windspeed_10m").asDouble();

        String conditions = describeWeatherCode(weatherCode);
        log.info("weather.result: city={}, temp={}F, conditions={}", resolvedName, tempF, conditions);

        return Map.of(
                "city", resolvedName + (country.isEmpty() ? "" : ", " + country),
                "temperature_f", Math.round(tempF * 10.0) / 10.0,
                "conditions", conditions,
                "wind_speed_mph", Math.round(windMph * 10.0) / 10.0,
                "latitude", lat,
                "longitude", lon);
    }

    /**
     * Maps a WMO weather interpretation code to a human-readable description.
     *
     * @param code the WMO weather code
     * @return a concise description (e.g. "Partly cloudy")
     * @see <a href="https://open-meteo.com/en/docs">Open-Meteo WMO codes</a>
     */
    static String describeWeatherCode(int code) {
        if (code == 0) return "Clear sky";
        if (code == 1) return "Mainly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code >= 45 && code <= 48) return "Foggy";
        if (code >= 51 && code <= 55) return "Drizzle";
        if (code >= 56 && code <= 57) return "Freezing drizzle";
        if (code >= 61 && code <= 65) return "Rain";
        if (code >= 66 && code <= 67) return "Freezing rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Rain showers";
        if (code >= 85 && code <= 86) return "Snow showers";
        if (code == 95) return "Thunderstorm";
        if (code >= 96 && code <= 99) return "Thunderstorm with hail";
        return "Unknown (" + code + ")";
    }
}
