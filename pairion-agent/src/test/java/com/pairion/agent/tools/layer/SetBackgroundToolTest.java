package com.pairion.agent.tools.layer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link SetBackgroundTool} parameter validation and result construction. */
class SetBackgroundToolTest {

    private SetBackgroundTool tool;

    @BeforeEach
    void setUp() {
        tool = new SetBackgroundTool();
    }

    @Test
    void nameIsCorrect() {
        assertThat(tool.name()).isEqualTo("set_background");
        assertThat(SetBackgroundTool.TOOL_NAME).isEqualTo("set_background");
    }

    @Test
    void missingBackgroundIdReturnsError() {
        Map<String, Object> result = tool.execute(Map.of());
        assertThat(result).containsEntry("error", "missing_parameter");
        assertThat(result.get("message").toString()).contains("background_id");
    }

    @Test
    void validBackgroundIdReturnsSuccess() {
        Map<String, Object> result = tool.execute(Map.of("background_id", "globe"));
        assertThat(result).containsEntry("status", "background_set");
        assertThat(result).containsEntry("background_id", "globe");
    }

    @Test
    void defaultTransitionIsCrossfade() {
        Map<String, Object> result = tool.execute(Map.of("background_id", "space"));
        assertThat(result).containsEntry("transition", "crossfade");
    }

    @Test
    void explicitTransitionPassedThrough() {
        Map<String, Object> result = tool.execute(
                Map.of("background_id", "dashboard", "transition", "instant"));
        assertThat(result).containsEntry("transition", "instant");
    }

    @Test
    void vfrBackgroundActivatesSuccessfully() {
        Map<String, Object> result = tool.execute(Map.of("background_id", "vfr"));
        assertThat(result).containsEntry("status", "background_set");
        assertThat(result).containsEntry("background_id", "vfr");
    }

    @Test
    void dashboardBackgroundActivatesSuccessfully() {
        Map<String, Object> result = tool.execute(Map.of("background_id", "dashboard"));
        assertThat(result).containsEntry("status", "background_set");
        assertThat(result).containsEntry("background_id", "dashboard");
    }
}
