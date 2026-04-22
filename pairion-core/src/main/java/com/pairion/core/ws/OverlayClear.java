package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server-to-client command to remove all active overlays from the display stack.
 *
 * <p>Emitted by the server when Jarvis calls the {@code clear_overlays} tool. The active
 * background remains unchanged; only the overlay stack is cleared.
 *
 * @param type the message type discriminator, always {@code "OverlayClear"}
 */
public record OverlayClear(
        @JsonProperty("type") String type)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "OverlayClear";
}
