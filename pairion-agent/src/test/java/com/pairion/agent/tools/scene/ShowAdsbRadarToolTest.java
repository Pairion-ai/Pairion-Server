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
    void executeReturnsVfrBackgroundId() {
        Map<String, Object> result = tool.execute(Map.of());
        assertThat(result).containsEntry("background_id", "vfr");
    }

    @Test
    void executeReturnsAdsbOverlayId() {
        Map<String, Object> result = tool.execute(Map.of());
        assertThat(result).containsEntry("overlay_id", "adsb");
    }

    @Test
    void executeIgnoresUnrecognizedInput() {
        Map<String, Object> result = tool.execute(Map.of("unknown_param", "value"));
        assertThat(result).containsEntry("status", "adsb_radar_activated");
    }
}
