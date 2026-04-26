package com.pairion.adapters.data.weathercurrent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Production HTTP client for the Open-Meteo current weather API.
 *
 * <p>Makes real network calls to {@code geocoding-api.open-meteo.com} (geocoding) and {@code
 * api.open-meteo.com} (forecast). This class is excluded from JaCoCo coverage; it is unit-tested
 * via the {@link WeatherCurrentDataClient} boundary using an in-memory stub implementation.
 *
 * <p>All numeric weather values are returned in imperial units (Fahrenheit, mph, inches) with
 * 1-decimal-place rounding applied via {@code Math.round(value * 10.0) / 10.0}.
 */
@Component
class HttpWeatherCurrentDataClient implements WeatherCurrentDataClient {

    private static final Logger log = LoggerFactory.getLogger(HttpWeatherCurrentDataClient.class);

    private static final String GEOCODING_URL =
            "https://geocoding-api.open-meteo.com/v1/search?name=%s&count=1&language=en&format=json";

    private static final String FORECAST_URL =
            "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=%s&longitude=%s"
                    + "&current=temperature_2m,relative_humidity_2m,weather_code,"
                    + "wind_speed_10m,wind_direction_10m,apparent_temperature,"
                    + "precipitation,surface_pressure"
                    + "&daily=temperature_2m_max,temperature_2m_min"
                    + "&temperature_unit=fahrenheit"
                    + "&wind_speed_unit=mph"
                    + "&precipitation_unit=inch"
                    + "&timezone=auto";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    HttpWeatherCurrentDataClient() {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public WeatherCurrentSnapshot fetchSnapshot(String city) throws Exception {
        // Step 1: Geocode the city name
        String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
        String geocodingUrl = String.format(GEOCODING_URL, encodedCity);

        HttpRequest geoRequest =
                HttpRequest.newBuilder()
                        .uri(URI.create(geocodingUrl))
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();

        HttpResponse<String> geoResponse =
                httpClient.send(geoRequest, HttpResponse.BodyHandlers.ofString());
        if (geoResponse.statusCode() != 200) {
            log.warn("weather.current.geo.error: HTTP {}", geoResponse.statusCode());
            throw new Exception(
                    "Open-Meteo geocoding API returned HTTP " + geoResponse.statusCode());
        }

        JsonNode geoJson = objectMapper.readTree(geoResponse.body());
        JsonNode results = geoJson.path("results");
        if (!results.isArray() || results.isEmpty()) {
            log.warn("weather.current.geo.error: city not found: {}", city);
            throw new Exception("City not found: " + city);
        }

        JsonNode firstResult = results.get(0);
        double lat = firstResult.path("latitude").asDouble();
        double lon = firstResult.path("longitude").asDouble();
        String resolvedName = firstResult.path("name").asText(city);
        String country = firstResult.path("country").asText("");
        String displayName = resolvedName + (country.isEmpty() ? "" : ", " + country);

        log.info("weather.current.geocoded: city={}, lat={}, lon={}", displayName, lat, lon);

        // Step 2: Fetch current forecast
        String forecastUrl = String.format(FORECAST_URL, lat, lon);
        HttpRequest forecastRequest =
                HttpRequest.newBuilder()
                        .uri(URI.create(forecastUrl))
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();

        HttpResponse<String> forecastResponse =
                httpClient.send(forecastRequest, HttpResponse.BodyHandlers.ofString());
        if (forecastResponse.statusCode() != 200) {
            log.warn("weather.current.api.error: HTTP {}", forecastResponse.statusCode());
            throw new Exception(
                    "Open-Meteo forecast API returned HTTP " + forecastResponse.statusCode());
        }

        JsonNode forecastJson = objectMapper.readTree(forecastResponse.body());
        JsonNode current = forecastJson.path("current");
        JsonNode daily = forecastJson.path("daily");

        double tempF = round1(current.path("temperature_2m").asDouble());
        double feelsLikeF = round1(current.path("apparent_temperature").asDouble());
        int humidity = current.path("relative_humidity_2m").asInt();
        int weatherCode = current.path("weather_code").asInt();
        double windSpeedMph = round1(current.path("wind_speed_10m").asDouble());
        int windDirectionDeg = current.path("wind_direction_10m").asInt();
        double precipitationIn = round1(current.path("precipitation").asDouble());
        double pressureMb = round1(current.path("surface_pressure").asDouble());

        double highF = round1(daily.path("temperature_2m_max").path(0).asDouble());
        double lowF = round1(daily.path("temperature_2m_min").path(0).asDouble());

        String conditions = describeWeatherCode(weatherCode);

        WeatherCurrentSnapshot snapshot =
                new WeatherCurrentSnapshot(
                        displayName,
                        tempF,
                        feelsLikeF,
                        highF,
                        lowF,
                        humidity,
                        windSpeedMph,
                        windDirectionDeg,
                        conditions,
                        precipitationIn,
                        pressureMb);

        log.info(
                "weather.current.result: city={}, temp={}F, conditions={}",
                displayName,
                tempF,
                conditions);
        return snapshot;
    }

    /**
     * Rounds a double to one decimal place.
     *
     * @param value the value to round
     * @return the value rounded to 1 decimal place
     */
    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /**
     * Maps a WMO weather interpretation code to a human-readable description.
     *
     * @param code the WMO weather code
     * @return a concise description (e.g. {@code "Partly cloudy"})
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
