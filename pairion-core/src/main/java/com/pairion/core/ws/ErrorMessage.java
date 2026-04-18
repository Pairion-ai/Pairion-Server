package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server-to-client error notification.
 *
 * @param type the message type discriminator, always {@code "Error"}
 * @param code machine-readable error code
 * @param message human-readable error description
 */
public record ErrorMessage(
        @JsonProperty("type") String type,
        @JsonProperty("code") String code,
        @JsonProperty("message") String message)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "Error";
}
