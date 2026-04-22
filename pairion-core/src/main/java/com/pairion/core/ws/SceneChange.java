package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Server-to-client command to switch the active background scene.
 *
 * <p>Emitted by the server when Jarvis calls the {@code set_scene} tool during a turn. The client
 * {@code SceneManager} dynamically loads the target scene's entry QML file and starts the
 * specified transition animation.
 *
 * @param type the message type discriminator, always {@code "SceneChange"}
 * @param sceneId the identifier of the scene to activate (e.g. {@code "globe"}, {@code "space"},
 *     {@code "dashboard"})
 * @param params optional scene-specific parameters passed from the LLM tool call
 * @param transition optional transition animation: {@code "crossfade"}, {@code "slide"}, or
 *     {@code "instant"}; defaults to {@code "crossfade"} when absent
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SceneChange(
        @JsonProperty("type") String type,
        @JsonProperty("sceneId") String sceneId,
        @JsonProperty("params") Map<String, Object> params,
        @JsonProperty("transition") String transition)
        implements WebSocketMessage {

    /** Default type value for this message. */
    public static final String TYPE = "SceneChange";
}
