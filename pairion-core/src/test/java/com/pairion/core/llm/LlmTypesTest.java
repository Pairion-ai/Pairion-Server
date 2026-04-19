package com.pairion.core.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for LLM domain types: LlmRequest, LlmEvent, LlmCapabilities, ToolDefinition. */
class LlmTypesTest {

    @Test
    void llmRequestSimple() {
        LlmRequest req = LlmRequest.simple("system", "hello");
        assertThat(req.systemPrompt()).isEqualTo("system");
        assertThat(req.userMessage()).isEqualTo("hello");
        assertThat(req.toolDefinitions()).isEmpty();
        assertThat(req.model()).isNull();
    }

    @Test
    void llmRequestFull() {
        ToolDefinition tool =
                new ToolDefinition("weather", "Gets weather", Map.of("type", "object"));
        LlmRequest req =
                new LlmRequest("sys", "msg", List.of(tool), "claude-sonnet-4-6-20250514", List.of());
        assertThat(req.model()).isEqualTo("claude-sonnet-4-6-20250514");
        assertThat(req.toolDefinitions()).hasSize(1);
        assertThat(req.toolDefinitions().get(0).name()).isEqualTo("weather");
    }

    @Test
    void toolDefinitionFields() {
        ToolDefinition td = new ToolDefinition("calc", "Calculator", Map.of("k", "v"));
        assertThat(td.name()).isEqualTo("calc");
        assertThat(td.description()).isEqualTo("Calculator");
        assertThat(td.inputSchema()).containsEntry("k", "v");
    }

    @Test
    void tokenDelta() {
        LlmEvent.TokenDelta td = new LlmEvent.TokenDelta("hello");
        assertThat(td.delta()).isEqualTo("hello");
        assertThat(td).isInstanceOf(LlmEvent.class);
    }

    @Test
    void toolCallRequest() {
        LlmEvent.ToolCallRequest tcr =
                new LlmEvent.ToolCallRequest("tc1", "weather", Map.of("city", "Dallas"));
        assertThat(tcr.toolCallId()).isEqualTo("tc1");
        assertThat(tcr.toolName()).isEqualTo("weather");
        assertThat(tcr.input()).containsEntry("city", "Dallas");
    }

    @Test
    void toolCallResult() {
        LlmEvent.ToolCallResult result = new LlmEvent.ToolCallResult("tc1", Map.of("temp", 72));
        assertThat(result.toolCallId()).isEqualTo("tc1");
        assertThat(result.output()).containsEntry("temp", 72);
    }

    @Test
    void stop() {
        LlmEvent.Stop stop = new LlmEvent.Stop(42);
        assertThat(stop.outputTokens()).isEqualTo(42);
    }

    @Test
    void capabilitiesAvailable() {
        LlmCapabilities caps = new LlmCapabilities(true, true, true);
        assertThat(caps.available()).isTrue();
        assertThat(caps.supportsToolUse()).isTrue();
        assertThat(caps.supportsStreaming()).isTrue();
    }

    @Test
    void capabilitiesUnavailable() {
        LlmCapabilities caps = LlmCapabilities.unavailable();
        assertThat(caps.available()).isFalse();
        assertThat(caps.supportsToolUse()).isFalse();
        assertThat(caps.supportsStreaming()).isFalse();
    }

    @Test
    void toolCallPairFields() {
        LlmRequest.ToolCallPair pair =
                new LlmRequest.ToolCallPair(
                        "tc-1",
                        "get_current_weather",
                        Map.of("city", "Dallas"),
                        Map.of("temperature_f", 72.0));
        assertThat(pair.toolCallId()).isEqualTo("tc-1");
        assertThat(pair.toolName()).isEqualTo("get_current_weather");
        assertThat(pair.toolInput()).containsEntry("city", "Dallas");
        assertThat(pair.toolOutput()).containsEntry("temperature_f", 72.0);
    }

    @Test
    void llmRequestToolCallHistory() {
        LlmRequest.ToolCallPair pair =
                new LlmRequest.ToolCallPair("tc-2", "calc", Map.of(), Map.of("result", 42));
        LlmRequest req = LlmRequest.simple("sys", "hi");
        assertThat(req.toolCallHistory()).isEmpty();

        LlmRequest reqWithHistory =
                new LlmRequest("sys", "follow-up", List.of(), null, List.of(pair));
        assertThat(reqWithHistory.toolCallHistory()).hasSize(1);
        assertThat(reqWithHistory.toolCallHistory().get(0).toolName()).isEqualTo("calc");
    }
}
