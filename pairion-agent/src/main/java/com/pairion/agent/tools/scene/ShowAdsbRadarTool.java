package com.pairion.agent.tools.scene;

import com.pairion.agent.tools.AgentTool;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Agent tool that activates the live ADS-B radar scene.
 *
 * <p>When the LLM calls this tool, {@link com.pairion.agent.session.AgentSession} intercepts the
 * result, emits a {@code SceneChange} WebSocket message with {@code scene_id="adsb-radar"}, and
 * starts the {@link com.pairion.adapters.data.adsb.AdsbDataAdapter} polling loop. The adapter then
 * pushes periodic {@code SceneDataPush} frames containing live aircraft state snapshots.
 *
 * <p>No input parameters are required. The tool simply signals intent; all activation logic lives
 * in {@code AgentSession}.
 */
@Component
public class ShowAdsbRadarTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(ShowAdsbRadarTool.class);

    /** Tool name as registered in the LLM tool definition. */
    public static final String TOOL_NAME = "show_adsb_radar";

    @Override
    public String name() {
        return TOOL_NAME;
    }

    /**
     * Returns a success result that {@link com.pairion.agent.session.AgentSession} uses to
     * activate the ADS-B radar scene and begin polling.
     *
     * <p>No input parameters are required or consumed.
     *
     * @param input tool call input from the LLM (ignored)
     * @return a map with {@code status="adsb_radar_activated"} and {@code scene_id="adsb-radar"}
     */
    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        log.info("adsb.radar.tool.called");
        return Map.of("status", "adsb_radar_activated", "scene_id", "adsb-radar");
    }
}
