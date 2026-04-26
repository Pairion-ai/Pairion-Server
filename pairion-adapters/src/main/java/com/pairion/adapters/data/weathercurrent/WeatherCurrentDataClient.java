package com.pairion.adapters.data.weathercurrent;

/**
 * Boundary interface for Open-Meteo current-weather API calls.
 *
 * <p>The production implementation ({@link HttpWeatherCurrentDataClient}) makes real HTTP calls to
 * the Open-Meteo geocoding and forecast APIs. Test implementations return canned responses. This
 * boundary enables 100% coverage of {@link WeatherCurrentDataAdapter} without real network calls.
 *
 * <p>All methods are declared to throw {@link Exception} to allow implementations to propagate HTTP
 * and parsing errors to the caller without wrapping.
 */
public interface WeatherCurrentDataClient {

    /**
     * Geocodes the city name and fetches current weather conditions.
     *
     * @param city the city name to look up (e.g. {@code "Tokyo"}, {@code "Dallas, TX"})
     * @return a snapshot with current conditions in imperial units
     * @throws Exception if geocoding fails, the city is not found, or the forecast API returns an
     *     error response
     */
    WeatherCurrentSnapshot fetchSnapshot(String city) throws Exception;
}
