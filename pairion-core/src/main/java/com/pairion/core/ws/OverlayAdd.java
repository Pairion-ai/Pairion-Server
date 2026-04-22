package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Server-to-client command to add an overlay on top of the current background.
 *
 * <p>Emitted by the server when Jarvis calls the {@code add_overlay} tool. Multiple overlays may
 * be active simultaneously; the client stacks them in the order received. If the named overlay is
 * already active, the client replaces it with the new parameters.
 *
 * @param type the message type discriminator, always {@code "OverlayAdd"}
 * @param overlayId the identifier of the overlay to activate (e.g. {@code "adsb"})
 * @param params optional overlay-specific parameters from the LLM tool call
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OverlayAdd(
        @JsonProperty("type") String type,
        @JsonProperty("overlayId") String overlayId,
        @JsonProperty("params") Map<String, Object> params)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "OverlayAdd";
}
