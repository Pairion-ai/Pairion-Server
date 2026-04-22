package com.pairion.agent.tools.layer;

import com.pairion.agent.tools.AgentTool;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Agent tool that switches the client background layer to match the current conversation topic.
 *
 * <p>The LLM calls this tool when the conversation shifts to a topic that maps to a specific
 * background (e.g. geography/weather → {@code globe}, aviation → {@code vfr}, astronomy →
 * {@code space}). On success, {@link com.pairion.agent.session.AgentSession} intercepts the result
 * and emits a {@code BackgroundChange} WebSocket message to the client.
 *
 * <p>No external HTTP calls are made — this tool performs only input validation and constructs the
 * result map. The client {@code LayerManager} handles all background lifecycle management. The
 * overlay stack is preserved across background switches.
 *
 * <p>Available background identifiers:
 * <ul>
 *   <li>{@code dashboard} — default ambient home screen</li>
 *   <li>{@code globe} — 3D interactive globe; use for geography, weather, or location topics</li>
 *   <li>{@code space} — animated star field; use for astronomy or space topics</li>
 *   <li>{@code vfr} — FAA VFR sectional chart; use for aviation and flight topics</li>
 * </ul>
 */
@Component
public class SetBackgroundTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(SetBackgroundTool.class);

    /** Tool name as registered in the LLM tool definition. */
    public static final String TOOL_NAME = "set_background";

    /** Default transition animation used when the LLM does not specify one. */
    static final String DEFAULT_TRANSITION = "crossfade";

    @Override
    public String name() {
        return TOOL_NAME;
    }

    /**
     * Validates the {@code background_id} parameter and returns a result map for
     * {@code AgentSession} to intercept and forward to the client as a {@code BackgroundChange}
     * WebSocket message.
     *
     * <p>Input parameters:
     * <ul>
     *   <li>{@code background_id} (required) — identifier of the background to activate</li>
     *   <li>{@code transition} (optional, default: {@code "crossfade"}) — transition animation:
     *       {@code crossfade}, {@code slide}, or {@code instant}</li>
     * </ul>
     *
     * <p>On success, returns a map with keys: {@code status}, {@code background_id}, and
     * {@code transition}. On failure, returns a map with {@code error} and {@code message}.
     *
     * @param input tool call input from the LLM
     * @return background change result or an error response
     */
    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        Object backgroundIdObj = input.get("background_id");
        if (backgroundIdObj == null) {
            return Map.of("error", "missing_parameter",
                    "message", "background_id parameter is required");
        }
        String backgroundId = backgroundIdObj.toString();
        String transition = input.containsKey("transition")
                ? input.get("transition").toString()
                : DEFAULT_TRANSITION;

        log.info("layer.background.set: backgroundId={}, transition={}", backgroundId, transition);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "background_set");
        result.put("background_id", backgroundId);
        result.put("transition", transition);
        return result;
    }
}
