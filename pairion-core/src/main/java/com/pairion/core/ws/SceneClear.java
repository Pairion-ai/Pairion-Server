package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Server-to-client command to clear the active scene and return to the default dashboard.
 *
 * <p>Sent when the conversation moves away from any topic-specific scene. The client
 * {@code SceneManager} unloads the current scene and restores {@code DashboardScene}.
 *
 * @param type the message type discriminator, always {@code "SceneClear"}
 */
public record SceneClear(@JsonProperty("type") String type) implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "SceneClear";
}
