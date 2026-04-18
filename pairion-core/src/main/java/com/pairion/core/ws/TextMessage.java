package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Client-to-server text input message (alternative to voice).
 *
 * @param type the message type discriminator, always {@code "TextMessage"}
 * @param text the text content from the user
 */
public record TextMessage(@JsonProperty("type") String type, @JsonProperty("text") String text)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "TextMessage";
}
