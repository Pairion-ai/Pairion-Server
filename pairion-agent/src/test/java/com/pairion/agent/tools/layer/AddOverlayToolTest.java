package com.pairion.agent.tools.layer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link AddOverlayTool} parameter validation and result construction. */
class AddOverlayToolTest {

    private AddOverlayTool tool;

    @BeforeEach
    void setUp() {
        tool = new AddOverlayTool();
    }

    @Test
    void nameIsCorrect() {
        assertThat(tool.name()).isEqualTo("add_overlay");
        assertThat(AddOverlayTool.TOOL_NAME).isEqualTo("add_overlay");
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
        assertThat(result).containsEntry("status", "overlay_added");
        assertThat(result).containsEntry("overlay_id", "adsb");
    }

    @Test
    void paramsPassedThroughWhenProvided() {
        Map<String, Object> params = Map.of("radius_nm", 8);
        Map<String, Object> result = tool.execute(Map.of("overlay_id", "adsb", "params", params));
        assertThat(result).containsKey("params");
        @SuppressWarnings("unchecked")
        Map<String, Object> returnedParams = (Map<String, Object>) result.get("params");
        assertThat(returnedParams).containsEntry("radius_nm", 8);
    }

    @Test
    void paramsAbsentWhenNotProvided() {
        Map<String, Object> result = tool.execute(Map.of("overlay_id", "adsb"));
        assertThat(result).doesNotContainKey("params");
    }
}
