package com.pairion.agent.tools.layer;

import com.pairion.agent.tools.AgentTool;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Agent tool that adds an overlay layer on top of the current background.
 *
 * <p>Multiple overlays may be active simultaneously. On success, {@link
 * com.pairion.agent.session.AgentSession} intercepts the result and emits an {@code OverlayAdd}
 * WebSocket message to the client. The client stacks overlays in the order received; if the named
 * overlay is already active, the client replaces it with the new parameters.
 *
 * <p>Available overlay identifiers:
 *
 * <ul>
 *   <li>{@code adsb} — live ADS-B aircraft radar from OpenSky Network
 *   <li>{@code weather_radar} — live weather radar tiles from RainViewer
 *   <li>{@code weather_current} — current conditions panel showing temperature, humidity, and wind
 *       for a named city
 * </ul>
 */
@Component
public class AddOverlayTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(AddOverlayTool.class);

    /** Tool name as registered in the LLM tool definition. */
    public static final String TOOL_NAME = "add_overlay";

    @Override
    public String name() {
        return TOOL_NAME;
    }

    /**
     * Validates the {@code overlay_id} parameter and returns a result map for {@code AgentSession}
     * to intercept and forward to the client as an {@code OverlayAdd} WebSocket message.
     *
     * <p>Input parameters:
     *
     * <ul>
     *   <li>{@code overlay_id} (required) — identifier of the overlay to activate
     *   <li>{@code params} (optional) — overlay-specific parameters (passed through to client)
     * </ul>
     *
     * <p>On success, returns a map with keys: {@code status}, {@code overlay_id}, and optionally
     * {@code params}. On failure, returns a map with {@code error} and {@code message}.
     *
     * @param input tool call input from the LLM
     * @return overlay add result or an error response
     */
    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        Object overlayIdObj = input.get("overlay_id");
        if (overlayIdObj == null) {
            return Map.of(
                    "error", "missing_parameter", "message", "overlay_id parameter is required");
        }
        String overlayId = overlayIdObj.toString();

        @SuppressWarnings("unchecked")
        Map<String, Object> params =
                input.containsKey("params") ? (Map<String, Object>) input.get("params") : null;

        log.info("layer.overlay.add: overlayId={}", overlayId);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "overlay_added");
        result.put("overlay_id", overlayId);
        if (params != null) {
            result.put("params", params);
        }
        return result;
    }
}
