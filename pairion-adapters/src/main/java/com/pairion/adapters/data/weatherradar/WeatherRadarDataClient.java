package com.pairion.adapters.data.weatherradar;

/**
 * Boundary interface for RainViewer weather radar API calls.
 *
 * <p>The production implementation ({@link HttpWeatherRadarDataClient}) makes real HTTP calls to
 * the RainViewer public API. Test implementations return canned responses. This boundary enables
 * 100% coverage of {@link WeatherRadarDataAdapter} without real network calls.
 *
 * <p>All methods are declared to throw {@link Exception} to allow implementations to propagate HTTP
 * and parsing errors to the caller without wrapping.
 */
public interface WeatherRadarDataClient {

    /**
     * Fetches the current weather radar tile metadata snapshot from the RainViewer API.
     *
     * <p>API endpoint: {@code GET https://api.rainviewer.com/public/weather-maps.json}
     *
     * @return a snapshot containing the available radar frames and tile parameters
     * @throws Exception if the API call fails or the response cannot be parsed
     */
    WeatherRadarSnapshot fetchSnapshot() throws Exception;
}
