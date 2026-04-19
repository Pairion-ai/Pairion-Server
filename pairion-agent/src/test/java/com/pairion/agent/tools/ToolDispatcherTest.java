package com.pairion.agent.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for {@link ToolDispatcher}. */
class ToolDispatcherTest {

    @Test
    void dispatchesKnownTool() throws Exception {
        AgentTool mockTool = mock(AgentTool.class);
        when(mockTool.name()).thenReturn("my_tool");
        when(mockTool.execute(any())).thenReturn(Map.of("result", "ok"));

        ToolDispatcher dispatcher = new ToolDispatcher(List.of(mockTool));
        Map<String, Object> result = dispatcher.dispatch("my_tool", Map.of("key", "value"));

        assertThat(result).containsEntry("result", "ok");
    }

    @Test
    void returnsErrorForUnknownTool() {
        ToolDispatcher dispatcher = new ToolDispatcher(List.of());
        Map<String, Object> result = dispatcher.dispatch("unknown_tool", Map.of());

        assertThat(result).containsKey("error");
        assertThat(result.get("error")).isEqualTo("unknown_tool");
    }

    @Test
    void returnsErrorWhenToolThrows() throws Exception {
        AgentTool failingTool = mock(AgentTool.class);
        when(failingTool.name()).thenReturn("bad_tool");
        when(failingTool.execute(any())).thenThrow(new RuntimeException("network error"));

        ToolDispatcher dispatcher = new ToolDispatcher(List.of(failingTool));
        Map<String, Object> result = dispatcher.dispatch("bad_tool", Map.of());

        assertThat(result).containsEntry("error", "tool_execution_failed");
        assertThat(result.get("message").toString()).contains("network error");
    }

    @Test
    void dispatchesFirstMatchingTool() throws Exception {
        AgentTool tool1 = mock(AgentTool.class);
        when(tool1.name()).thenReturn("tool_a");
        when(tool1.execute(any())).thenReturn(Map.of("from", "a"));

        AgentTool tool2 = mock(AgentTool.class);
        when(tool2.name()).thenReturn("tool_b");
        when(tool2.execute(any())).thenReturn(Map.of("from", "b"));

        ToolDispatcher dispatcher = new ToolDispatcher(List.of(tool1, tool2));
        Map<String, Object> resultA = dispatcher.dispatch("tool_a", Map.of());
        Map<String, Object> resultB = dispatcher.dispatch("tool_b", Map.of());

        assertThat(resultA.get("from")).isEqualTo("a");
        assertThat(resultB.get("from")).isEqualTo("b");
    }
}
