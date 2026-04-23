/**
 * Weather radar data adapter — fetches RainViewer tile metadata and delivers snapshots to
 * registered consumers.
 *
 * <p>The boundary interface {@link com.pairion.adapters.data.weatherradar.WeatherRadarDataClient}
 * decouples the polling logic from the HTTP implementation, enabling full unit test coverage
 * without real network calls.
 */
package com.pairion.adapters.data.weatherradar;
