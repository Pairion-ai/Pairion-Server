package com.pairion.adapters.data.adsb;

import java.util.List;
import java.util.Optional;

/**
 * Boundary interface for OpenSky Network REST API calls.
 *
 * <p>The production implementation ({@link HttpAdsbDataClient}) makes real HTTP calls to the
 * OpenSky Network API. Test implementations return canned responses. This boundary enables 100%
 * coverage of {@link AdsbDataAdapter} and {@link AdsbEnrichmentService} without real network calls.
 *
 * <p>All methods are declared to throw {@link Exception} to allow implementations to propagate HTTP
 * and parsing errors to the caller without wrapping.
 */
public interface AdsbDataClient {

    /**
     * Fetches the current state vectors for all aircraft within the given geographic bounding box.
     *
     * <p>Returns each aircraft as a positional {@code List<Object>} where indices correspond to the
     * OpenSky state vector schema:
     *
     * <ul>
     *   <li>0 — icao24 (String)
     *   <li>1 — callsign (String or null)
     *   <li>5 — longitude (Double or null)
     *   <li>6 — latitude (Double or null)
     *   <li>7 — baro_altitude in metres (Double or null)
     *   <li>8 — on_ground (Boolean)
     *   <li>9 — velocity in m/s (Double or null)
     *   <li>10 — true_track in degrees (Double or null)
     *   <li>11 — vertical_rate in m/s (Double or null)
     * </ul>
     *
     * @param lamin southern boundary of the bounding box in decimal degrees
     * @param lomin western boundary of the bounding box in decimal degrees
     * @param lamax northern boundary of the bounding box in decimal degrees
     * @param lomax eastern boundary of the bounding box in decimal degrees
     * @return list of state vector arrays; empty if no aircraft are in the bounding box
     * @throws Exception if the API call fails or the response cannot be parsed
     */
    List<List<Object>> fetchStates(double lamin, double lomin, double lamax, double lomax)
            throws Exception;

    /**
     * Fetches aircraft metadata (registration, type code) for the given ICAO 24-bit address.
     *
     * @param icao24 the ICAO 24-bit address in hex (e.g. {@code "a5a07c"})
     * @return aircraft metadata, or empty if the address is not found in the OpenSky database
     * @throws Exception if the API call fails
     */
    Optional<AircraftMetadata> fetchMetadata(String icao24) throws Exception;

    /**
     * Fetches route information (departure and destination airports) for the given callsign.
     *
     * @param callsign the aircraft call sign (trimmed, upper-cased)
     * @return route info, or empty if the callsign has no known route
     * @throws Exception if the API call fails
     */
    Optional<RouteInfo> fetchRoute(String callsign) throws Exception;

    /**
     * Aircraft metadata as returned by the OpenSky aircraft database API.
     *
     * @param icao24 ICAO 24-bit address
     * @param registration aircraft registration (e.g. {@code "N123AB"})
     * @param typecode ICAO type code (e.g. {@code "B738"})
     */
    record AircraftMetadata(String icao24, String registration, String typecode) {}

    /**
     * Route information as returned by the OpenSky route API.
     *
     * @param callsign the aircraft call sign
     * @param departureAirport IATA or ICAO departure airport code
     * @param destinationAirport IATA or ICAO destination airport code
     */
    record RouteInfo(String callsign, String departureAirport, String destinationAirport) {}
}
