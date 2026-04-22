package com.pairion.agent.tools.scene;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link ShowAdsbRadarTool}. */
class ShowAdsbRadarToolTest {

    private ShowAdsbRadarTool tool;

    @BeforeEach
    void setUp() {
        tool = new ShowAdsbRadarTool();
    }

    @Test
    void nameIsCorrect() {
        assertThat(tool.name()).isEqualTo("show_adsb_radar");
        assertThat(ShowAdsbRadarTool.TOOL_NAME).isEqualTo("show_adsb_radar");
    }

    @Test
    void executeReturnsActivatedStatus() {
        Map<String, Object> result = tool.execute(Map.of());
        assertThat(result).containsEntry("status", "adsb_radar_activated");
    }

    @Test
    void executeReturnsAdsbRadarSceneId() {
        Map<String, Object> result = tool.execute(Map.of());
        assertThat(result).containsEntry("scene_id", "adsb-radar");
    }

    @Test
    void executeIgnoresUnrecognizedInput() {
        Map<String, Object> result = tool.execute(Map.of("unknown_param", "value"));
        assertThat(result).containsEntry("status", "adsb_radar_activated");
    }
}
