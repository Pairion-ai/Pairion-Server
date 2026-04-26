/**
 * Current-conditions weather data adapter — fetches Open-Meteo current weather and delivers
 * snapshots on demand to registered consumers.
 *
 * <p>The boundary interface {@link
 * com.pairion.adapters.data.weathercurrent.WeatherCurrentDataClient} decouples the fetch logic from
 * the HTTP implementation, enabling full unit test coverage without real network calls.
 */
package com.pairion.adapters.data.weathercurrent;
