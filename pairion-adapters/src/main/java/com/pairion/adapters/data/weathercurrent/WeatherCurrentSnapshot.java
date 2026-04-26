package com.pairion.adapters.data.weathercurrent;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Immutable snapshot of current weather conditions pushed to the client overlay.
 *
 * <p>All numeric values use imperial units: temperature in Fahrenheit, wind in mph, precipitation
 * in inches, and pressure in millibars. The client {@code WeatherCurrentOverlay} renders these
 * values directly without conversion.
 *
 * @param city display name of the requested city (from Open-Meteo geocoding)
 * @param temperatureF current temperature in degrees Fahrenheit
 * @param feelsLikeF apparent (feels-like) temperature in degrees Fahrenheit
 * @param highF today's forecast high temperature in degrees Fahrenheit
 * @param lowF today's forecast low temperature in degrees Fahrenheit
 * @param humidity relative humidity as an integer percentage (0–100)
 * @param windSpeedMph current wind speed in miles per hour
 * @param windDirectionDeg wind direction in degrees clockwise from true north (0–359)
 * @param conditions human-readable weather description derived from the WMO weather code
 * @param precipitationIn current precipitation in inches
 * @param pressureMb surface pressure in millibars
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WeatherCurrentSnapshot(
        String city,
        double temperatureF,
        double feelsLikeF,
        double highF,
        double lowF,
        int humidity,
        double windSpeedMph,
        int windDirectionDeg,
        String conditions,
        double precipitationIn,
        double pressureMb) {}
