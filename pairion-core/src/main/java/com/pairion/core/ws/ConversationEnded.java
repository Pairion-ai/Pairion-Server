package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server-to-client command signalling that the current conversation session has ended.
 *
 * <p>Emitted when the user utters a dismissal phrase such as "that's all" or "goodbye".
 * The client should exit conversation mode and return to wake-word listening.
 *
 * @param type the message type discriminator, always {@code "ConversationEnded"}
 */
public record ConversationEnded(@JsonProperty("type") String type) implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "ConversationEnded";
}
