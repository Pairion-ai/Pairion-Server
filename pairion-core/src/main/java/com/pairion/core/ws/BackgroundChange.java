package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server-to-client command to switch the active background.
 *
 * <p>Emitted by the server when Jarvis calls the {@code set_background} tool. The client
 * {@code LayerManager} loads the target background and applies the specified transition animation.
 * The overlay stack is preserved — overlays reposition themselves on the new background
 * automatically.
 *
 * @param type the message type discriminator, always {@code "BackgroundChange"}
 * @param backgroundId the identifier of the background to activate (e.g. {@code "vfr"},
 *     {@code "globe"}, {@code "space"}, {@code "dashboard"})
 * @param transition optional transition animation: {@code "crossfade"}, {@code "slide"}, or
 *     {@code "instant"}; defaults to {@code "crossfade"} when absent
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BackgroundChange(
        @JsonProperty("type") String type,
        @JsonProperty("backgroundId") String backgroundId,
        @JsonProperty("transition") String transition)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "BackgroundChange";
}
