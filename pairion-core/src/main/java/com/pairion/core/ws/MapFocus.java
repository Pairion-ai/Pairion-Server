package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server-to-client command to pan and zoom the globe to a specific geographic location.
 *
 * <p>Emitted by the server when Jarvis calls the {@code focus_map} tool during a turn. The client
 * should immediately animate the world map to centre on {@code lat}/{@code lon} at the requested
 * zoom level.
 *
 * @param type the message type discriminator, always {@code "MapFocus"}
 * @param lat latitude of the target location in decimal degrees
 * @param lon longitude of the target location in decimal degrees
 * @param label human-readable display label for the location (e.g. "Tokyo, Japan")
 * @param zoom zoom level: one of {@code continent}, {@code country}, {@code region}, {@code city}
 */
public record MapFocus(
        @JsonProperty("type") String type,
        @JsonProperty("lat") double lat,
        @JsonProperty("lon") double lon,
        @JsonProperty("label") String label,
        @JsonProperty("zoom") String zoom)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "MapFocus";
}
