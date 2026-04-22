package com.pairion.agent.tools.layer;

import com.pairion.agent.tools.AgentTool;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Agent tool that removes all active overlays from the client display stack.
 *
 * <p>On success, {@link com.pairion.agent.session.AgentSession} intercepts the result and emits an
 * {@code OverlayClear} WebSocket message to the client. The active background remains unchanged;
 * only the overlay stack is cleared.
 *
 * <p>No input parameters are required or consumed.
 */
@Component
public class ClearOverlaysTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(ClearOverlaysTool.class);

    /** Tool name as registered in the LLM tool definition. */
    public static final String TOOL_NAME = "clear_overlays";

    @Override
    public String name() {
        return TOOL_NAME;
    }

    /**
     * Returns a success result that {@link com.pairion.agent.session.AgentSession} uses to clear
     * all active overlays on the client.
     *
     * <p>No input parameters are required or consumed.
     *
     * @param input tool call input from the LLM (ignored)
     * @return a map with {@code status="overlays_cleared"}
     */
    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        log.info("layer.overlay.clear");
        return Map.of("status", "overlays_cleared");
    }
}
