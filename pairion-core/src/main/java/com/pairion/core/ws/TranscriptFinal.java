package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server-to-client final speech-to-text transcript.
 *
 * @param type the message type discriminator, always {@code "TranscriptFinal"}
 * @param text the final transcript text
 */
public record TranscriptFinal(@JsonProperty("type") String type, @JsonProperty("text") String text)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "TranscriptFinal";
}
