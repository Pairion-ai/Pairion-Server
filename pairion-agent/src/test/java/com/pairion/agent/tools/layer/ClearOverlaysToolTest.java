package com.pairion.agent.tools.layer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link ClearOverlaysTool}. */
class ClearOverlaysToolTest {

    private ClearOverlaysTool tool;

    @BeforeEach
    void setUp() {
        tool = new ClearOverlaysTool();
    }

    @Test
    void nameIsCorrect() {
        assertThat(tool.name()).isEqualTo("clear_overlays");
        assertThat(ClearOverlaysTool.TOOL_NAME).isEqualTo("clear_overlays");
    }

    @Test
    void executeClearsSuccessfully() {
        Map<String, Object> result = tool.execute(Map.of());
        assertThat(result).containsEntry("status", "overlays_cleared");
    }

    @Test
    void executeIgnoresUnrecognizedInput() {
        Map<String, Object> result = tool.execute(Map.of("unused_param", "value"));
        assertThat(result).containsEntry("status", "overlays_cleared");
    }
}
