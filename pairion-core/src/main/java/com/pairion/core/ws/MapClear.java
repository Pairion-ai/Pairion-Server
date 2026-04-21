package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server-to-client command to clear the current map focus and resume globe auto-scroll.
 *
 * <p>Emitted after a 2-minute idle timeout since the last {@link MapFocus}, or when the user
 * utters an ending phrase (e.g. "Go back", "that's all").
 *
 * @param type the message type discriminator, always {@code "MapClear"}
 */
public record MapClear(@JsonProperty("type") String type) implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "MapClear";
}
