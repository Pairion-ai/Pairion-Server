package com.pairion.agent.tools.scene;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link SetSceneTool} parameter validation and result construction. */
class SetSceneToolTest {

    private SetSceneTool tool;

    @BeforeEach
    void setUp() {
        tool = new SetSceneTool();
    }

    @Test
    void nameIsCorrect() {
        assertThat(tool.name()).isEqualTo("set_scene");
        assertThat(SetSceneTool.TOOL_NAME).isEqualTo("set_scene");
    }

    @Test
    void missingSceneIdReturnsError() {
        Map<String, Object> result = tool.execute(Map.of());
        assertThat(result).containsEntry("error", "missing_parameter");
        assertThat(result.get("message").toString()).contains("scene_id");
    }

    @Test
    void validSceneIdReturnsSuccess() {
        Map<String, Object> result = tool.execute(Map.of("scene_id", "globe"));
        assertThat(result).containsEntry("status", "scene_changed");
        assertThat(result).containsEntry("scene_id", "globe");
    }

    @Test
    void defaultTransitionIsCrossfade() {
        Map<String, Object> result = tool.execute(Map.of("scene_id", "space"));
        assertThat(result).containsEntry("transition", "crossfade");
    }

    @Test
    void explicitTransitionPassedThrough() {
        Map<String, Object> result = tool.execute(
                Map.of("scene_id", "dashboard", "transition", "instant"));
        assertThat(result).containsEntry("transition", "instant");
    }

    @Test
    void paramsPassedThroughWhenProvided() {
        Map<String, Object> params = Map.of("lat", 35.6, "lon", 139.6);
        Map<String, Object> result = tool.execute(
                Map.of("scene_id", "globe", "params", params));
        assertThat(result).containsKey("params");
        @SuppressWarnings("unchecked")
        Map<String, Object> returnedParams = (Map<String, Object>) result.get("params");
        assertThat(returnedParams).containsEntry("lat", 35.6);
    }

    @Test
    void paramsAbsentWhenNotProvided() {
        Map<String, Object> result = tool.execute(Map.of("scene_id", "space"));
        assertThat(result).doesNotContainKey("params");
    }

    @Test
    void dashboardSceneActivatesSuccessfully() {
        Map<String, Object> result = tool.execute(Map.of("scene_id", "dashboard"));
        assertThat(result).containsEntry("status", "scene_changed");
        assertThat(result).containsEntry("scene_id", "dashboard");
    }
}
