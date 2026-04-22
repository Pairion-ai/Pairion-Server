package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server-to-client command to remove a specific named overlay from the display stack.
 *
 * <p>Emitted by the server when Jarvis calls the {@code remove_overlay} tool. If the named overlay
 * is not currently active, the client silently ignores this message.
 *
 * @param type the message type discriminator, always {@code "OverlayRemove"}
 * @param overlayId the identifier of the overlay to deactivate (e.g. {@code "adsb"})
 */
public record OverlayRemove(
        @JsonProperty("type") String type,
        @JsonProperty("overlayId") String overlayId)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "OverlayRemove";
}
