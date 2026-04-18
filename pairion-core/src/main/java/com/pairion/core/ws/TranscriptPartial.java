package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server-to-client partial speech-to-text transcript (interim result).
 *
 * @param type the message type discriminator, always {@code "TranscriptPartial"}
 * @param text the partial transcript text
 */
public record TranscriptPartial(
        @JsonProperty("type") String type, @JsonProperty("text") String text)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "TranscriptPartial";
}
