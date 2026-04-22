package com.pairion.agent.tools.layer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link RemoveOverlayTool} parameter validation and result construction. */
class RemoveOverlayToolTest {

    private RemoveOverlayTool tool;

    @BeforeEach
    void setUp() {
        tool = new RemoveOverlayTool();
    }

    @Test
    void nameIsCorrect() {
        assertThat(tool.name()).isEqualTo("remove_overlay");
        assertThat(RemoveOverlayTool.TOOL_NAME).isEqualTo("remove_overlay");
    }

    @Test
    void missingOverlayIdReturnsError() {
        Map<String, Object> result = tool.execute(Map.of());
        assertThat(result).containsEntry("error", "missing_parameter");
        assertThat(result.get("message").toString()).contains("overlay_id");
    }

    @Test
    void validOverlayIdReturnsSuccess() {
        Map<String, Object> result = tool.execute(Map.of("overlay_id", "adsb"));
        assertThat(result).containsEntry("status", "overlay_removed");
        assertThat(result).containsEntry("overlay_id", "adsb");
    }

    @Test
    void overlayIdPassedThroughInResult() {
        Map<String, Object> result = tool.execute(Map.of("overlay_id", "weather_radar"));
        assertThat(result).containsEntry("overlay_id", "weather_radar");
    }
}
