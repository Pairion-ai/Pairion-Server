package com.pairion.adapters.data.adsb;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Immutable snapshot of a single aircraft's state as reported by the OpenSky Network, after unit
 * conversion and optional enrichment with registration, type, and route information.
 *
 * <p>Null fields indicate the value was not available in the OpenSky response or the enrichment
 * cache. The {@link com.fasterxml.jackson.annotation.JsonInclude} annotation suppresses null fields
 * from the JSON wire representation sent to the client.
 *
 * @param icao24 ICAO 24-bit address in hex (e.g. {@code "a5a07c"})
 * @param callsign aircraft call sign (trimmed); null if not available
 * @param lat latitude in decimal degrees; null if position unknown
 * @param lon longitude in decimal degrees; null if position unknown
 * @param altitudeFt barometric altitude in feet; null if not available
 * @param speedKnots ground speed in knots; null if not available
 * @param trackDeg true track in degrees clockwise from north; null if not available
 * @param verticalRateFpm vertical rate in feet per minute (positive = climbing); null if not
 *     available
 * @param onGround true if the aircraft is currently on the ground
 * @param registration ICAO aircraft registration (e.g. {@code "N123AB"}); null if not enriched
 * @param aircraftType ICAO type code (e.g. {@code "B738"}); null if not enriched
 * @param origin IATA/ICAO departure airport code; null if not enriched
 * @param destination IATA/ICAO destination airport code; null if not enriched
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdsbAircraft(
        String icao24,
        String callsign,
        Double lat,
        Double lon,
        Double altitudeFt,
        Double speedKnots,
        Double trackDeg,
        Double verticalRateFpm,
        boolean onGround,
        String registration,
        String aircraftType,
        String origin,
        String destination) {}
