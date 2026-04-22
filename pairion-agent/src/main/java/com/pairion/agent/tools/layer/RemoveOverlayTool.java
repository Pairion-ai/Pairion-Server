package com.pairion.agent.tools.layer;

import com.pairion.agent.tools.AgentTool;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Agent tool that removes a specific named overlay from the client display stack.
 *
 * <p>On success, {@link com.pairion.agent.session.AgentSession} intercepts the result and emits an
 * {@code OverlayRemove} WebSocket message to the client. If the named overlay is not currently
 * active, the client silently ignores the message.
 */
@Component
public class RemoveOverlayTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(RemoveOverlayTool.class);

    /** Tool name as registered in the LLM tool definition. */
    public static final String TOOL_NAME = "remove_overlay";

    @Override
    public String name() {
        return TOOL_NAME;
    }

    /**
     * Validates the {@code overlay_id} parameter and returns a result map for {@code AgentSession}
     * to intercept and forward to the client as an {@code OverlayRemove} WebSocket message.
     *
     * <p>Input parameters:
     * <ul>
     *   <li>{@code overlay_id} (required) — identifier of the overlay to deactivate</li>
     * </ul>
     *
     * <p>On success, returns a map with keys: {@code status} and {@code overlay_id}. On failure,
     * returns a map with {@code error} and {@code message}.
     *
     * @param input tool call input from the LLM
     * @return overlay remove result or an error response
     */
    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        Object overlayIdObj = input.get("overlay_id");
        if (overlayIdObj == null) {
            return Map.of("error", "missing_parameter",
                    "message", "overlay_id parameter is required");
        }
        String overlayId = overlayIdObj.toString();
        log.info("layer.overlay.remove: overlayId={}", overlayId);
        return Map.of("status", "overlay_removed", "overlay_id", overlayId);
    }
}
