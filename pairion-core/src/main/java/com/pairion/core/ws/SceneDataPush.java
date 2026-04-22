package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server-to-client message carrying a data model payload for the active scene.
 *
 * <p>Sent by the server whenever a data model adapter produces a new snapshot. The client
 * {@code SceneManager} routes this to the active scene's {@code sceneData} property, which QML
 * property bindings consume directly.
 *
 * @param type the message type discriminator, always {@code "SceneDataPush"}
 * @param modelId the data model identifier (e.g. {@code "adsb"}, {@code "stock_quotes"})
 * @param data the data payload; structure is defined by the named data model's schema
 */
public record SceneDataPush(
        @JsonProperty("type") String type,
        @JsonProperty("modelId") String modelId,
        @JsonProperty("data") Object data)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "SceneDataPush";
}
