package com.pairion.adapters.data.weatherradar;

/**
 * A single radar frame from the RainViewer weather-maps API.
 *
 * @param time Unix epoch time of the radar frame
 * @param path RainViewer API path for this frame, relative to the host
 */
public record WeatherRadarFrame(long time, String path) {}
