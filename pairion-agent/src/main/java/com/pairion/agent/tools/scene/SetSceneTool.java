package com.pairion.agent.tools.scene;

import com.pairion.agent.tools.AgentTool;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Agent tool that switches the client background scene to match the current conversation topic.
 *
 * <p>The LLM calls this tool when the conversation shifts to a topic that maps to a specific scene
 * (e.g. geography/weather → {@code globe}, astronomy → {@code space}). On success,
 * {@link com.pairion.agent.session.AgentSession} intercepts the result and emits a
 * {@code SceneChange} WebSocket message to the client.
 *
 * <p>No external HTTP calls are made — this tool performs only input validation and constructs
 * the result map. The client {@code SceneManager} handles all scene lifecycle management.
 *
 * <p>Built-in scene identifiers:
 * <ul>
 *   <li>{@code dashboard} — default ambient home screen</li>
 *   <li>{@code globe} — 3D interactive globe; use for geography, weather, or location topics</li>
 *   <li>{@code space} — animated star field; use for astronomy or space topics</li>
 * </ul>
 */
@Component
public class SetSceneTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(SetSceneTool.class);

    /** Tool name as registered in the LLM tool definition. */
    public static final String TOOL_NAME = "set_scene";

    /** Default transition animation used when the LLM does not specify one. */
    static final String DEFAULT_TRANSITION = "crossfade";

    @Override
    public String name() {
        return TOOL_NAME;
    }

    /**
     * Validates the {@code scene_id} parameter and returns a result map for {@code AgentSession}
     * to intercept and forward to the client as a {@code SceneChange} WebSocket message.
     *
     * <p>Input parameters:
     * <ul>
     *   <li>{@code scene_id} (required) — identifier of the scene to activate</li>
     *   <li>{@code params} (optional) — scene-specific parameters (passed through to client)</li>
     *   <li>{@code transition} (optional, default: {@code "crossfade"}) — transition animation:
     *       {@code crossfade}, {@code slide}, or {@code instant}</li>
     * </ul>
     *
     * <p>On success, returns a map with keys: {@code status}, {@code scene_id},
     * {@code transition}, and optionally {@code params}. On failure, returns a map with
     * {@code error} and {@code message}.
     *
     * @param input tool call input from the LLM
     * @return scene change result or an error response
     */
    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        Object sceneIdObj = input.get("scene_id");
        if (sceneIdObj == null) {
            return Map.of("error", "missing_parameter",
                    "message", "scene_id parameter is required");
        }
        String sceneId = sceneIdObj.toString();
        String transition = input.containsKey("transition")
                ? input.get("transition").toString()
                : DEFAULT_TRANSITION;

        @SuppressWarnings("unchecked")
        Map<String, Object> params = input.containsKey("params")
                ? (Map<String, Object>) input.get("params")
                : null;

        log.info("scene.set: sceneId={}, transition={}", sceneId, transition);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "scene_changed");
        result.put("scene_id", sceneId);
        result.put("transition", transition);
        if (params != null) {
            result.put("params", params);
        }
        return result;
    }
}
