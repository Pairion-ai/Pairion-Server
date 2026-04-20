package com.pairion.adapters.llm.openaicompat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pairion.core.llm.LlmCapabilities;
import com.pairion.core.llm.LlmEvent;
import com.pairion.core.llm.LlmRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for {@link OpenAiCompatLlmAdapter}. */
class OpenAiCompatLlmAdapterTest {

    @Test
    void nameReturnsOpenaicompat() {
        OpenAiCompatClientWrapper wrapper = mock(OpenAiCompatClientWrapper.class);
        OpenAiCompatLlmAdapter adapter =
                new OpenAiCompatLlmAdapter(wrapper, "http://localhost:1234/v1", "gpt-4o-mini", "");
        assertThat(adapter.name()).isEqualTo("openaicompat");
    }

    @Test
    void capabilitiesWhenAvailable() {
        OpenAiCompatClientWrapper wrapper = mock(OpenAiCompatClientWrapper.class);
        when(wrapper.isAvailable()).thenReturn(true);
        OpenAiCompatLlmAdapter adapter =
                new OpenAiCompatLlmAdapter(wrapper, "http://localhost:1234/v1", "gpt-4o-mini", "");
        LlmCapabilities caps = adapter.capabilities();
        assertThat(caps.available()).isTrue();
        assertThat(caps.supportsToolUse()).isTrue();
        assertThat(caps.supportsStreaming()).isTrue();
    }

    @Test
    void capabilitiesWhenUnavailable() {
        OpenAiCompatClientWrapper wrapper = mock(OpenAiCompatClientWrapper.class);
        when(wrapper.isAvailable()).thenReturn(false);
        OpenAiCompatLlmAdapter adapter =
                new OpenAiCompatLlmAdapter(wrapper, "http://localhost:1234/v1", "gpt-4o-mini", "");
        LlmCapabilities caps = adapter.capabilities();
        assertThat(caps.available()).isFalse();
    }

    @Test
    void generateStreamsTokensAndStop() {
        OpenAiCompatClientWrapper wrapper = mock(OpenAiCompatClientWrapper.class);
        when(wrapper.isAvailable()).thenReturn(true);
        doAnswer(
                        inv -> {
                            // streamCompletion(baseUrl, apiKey, model, sys, user, tools, history, cb)
                            OpenAiCompatClientWrapper.StreamCallback cb = inv.getArgument(7);
                            cb.onToken("Hello");
                            cb.onToken(" world");
                            cb.onComplete(5);
                            return null;
                        })
                .when(wrapper)
                .streamCompletion(
                        anyString(), anyString(), anyString(), anyString(), anyString(),
                        anyList(), anyList(), any());

        OpenAiCompatLlmAdapter adapter =
                new OpenAiCompatLlmAdapter(wrapper, "http://localhost:1234/v1", "gpt-4o-mini", "");
        LlmRequest request = LlmRequest.simple("system", "hi");

        List<LlmEvent> events = new ArrayList<>();
        adapter.generate(request, events::add);

        assertThat(events).hasSize(3);
        assertThat(events.get(0)).isInstanceOf(LlmEvent.TokenDelta.class);
        assertThat(((LlmEvent.TokenDelta) events.get(0)).delta()).isEqualTo("Hello");
        assertThat(events.get(1)).isInstanceOf(LlmEvent.TokenDelta.class);
        assertThat(((LlmEvent.TokenDelta) events.get(1)).delta()).isEqualTo(" world");
        assertThat(events.get(2)).isInstanceOf(LlmEvent.Stop.class);
        assertThat(((LlmEvent.Stop) events.get(2)).outputTokens()).isEqualTo(5);
    }

    @Test
    void generateUsesExplicitModelFromRequest() {
        OpenAiCompatClientWrapper wrapper = mock(OpenAiCompatClientWrapper.class);
        when(wrapper.isAvailable()).thenReturn(true);
        doAnswer(
                        inv -> {
                            String model = inv.getArgument(2);
                            assertThat(model).isEqualTo("llama-3.3-70b");
                            OpenAiCompatClientWrapper.StreamCallback cb = inv.getArgument(7);
                            cb.onComplete(1);
                            return null;
                        })
                .when(wrapper)
                .streamCompletion(
                        anyString(), anyString(), anyString(), anyString(), anyString(),
                        anyList(), anyList(), any());

        OpenAiCompatLlmAdapter adapter =
                new OpenAiCompatLlmAdapter(
                        wrapper, "http://localhost:1234/v1", "default-model", "");
        LlmRequest request =
                new LlmRequest("sys", "msg", List.of(), "llama-3.3-70b", List.of());

        List<LlmEvent> events = new ArrayList<>();
        adapter.generate(request, events::add);

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(LlmEvent.Stop.class);
    }

    @Test
    void generateUsesDefaultModelWhenRequestModelIsNull() {
        OpenAiCompatClientWrapper wrapper = mock(OpenAiCompatClientWrapper.class);
        when(wrapper.isAvailable()).thenReturn(true);
        doAnswer(
                        inv -> {
                            String model = inv.getArgument(2);
                            assertThat(model).isEqualTo("gpt-4o-mini");
                            OpenAiCompatClientWrapper.StreamCallback cb = inv.getArgument(7);
                            cb.onComplete(1);
                            return null;
                        })
                .when(wrapper)
                .streamCompletion(
                        anyString(), anyString(), anyString(), anyString(), anyString(),
                        anyList(), anyList(), any());

        OpenAiCompatLlmAdapter adapter =
                new OpenAiCompatLlmAdapter(
                        wrapper, "http://localhost:1234/v1", "gpt-4o-mini", "");
        LlmRequest request = LlmRequest.simple("sys", "msg");

        List<LlmEvent> events = new ArrayList<>();
        adapter.generate(request, events::add);

        assertThat(events).hasSize(1);
    }

    @Test
    void generateHandlesError() {
        OpenAiCompatClientWrapper wrapper = mock(OpenAiCompatClientWrapper.class);
        when(wrapper.isAvailable()).thenReturn(true);
        doAnswer(
                        inv -> {
                            OpenAiCompatClientWrapper.StreamCallback cb = inv.getArgument(7);
                            cb.onToken("partial");
                            cb.onError(new RuntimeException("Connection refused"));
                            return null;
                        })
                .when(wrapper)
                .streamCompletion(
                        anyString(), anyString(), anyString(), anyString(), anyString(),
                        anyList(), anyList(), any());

        OpenAiCompatLlmAdapter adapter =
                new OpenAiCompatLlmAdapter(wrapper, "http://localhost:1234/v1", "gpt-4o-mini", "");
        List<LlmEvent> events = new ArrayList<>();
        adapter.generate(LlmRequest.simple("sys", "msg"), events::add);

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(LlmEvent.TokenDelta.class);
        assertThat(events.get(1)).isInstanceOf(LlmEvent.Stop.class);
        // tokenCount at time of error was 1 (one onToken call)
        assertThat(((LlmEvent.Stop) events.get(1)).outputTokens()).isEqualTo(1);
    }

    @Test
    void generateForwardsToolCallRequest() {
        OpenAiCompatClientWrapper wrapper = mock(OpenAiCompatClientWrapper.class);
        when(wrapper.isAvailable()).thenReturn(true);
        doAnswer(
                        inv -> {
                            OpenAiCompatClientWrapper.StreamCallback cb = inv.getArgument(7);
                            cb.onToolCallRequest(
                                    "call_abc", "get_current_weather", Map.of("city", "Austin"));
                            cb.onComplete(8);
                            return null;
                        })
                .when(wrapper)
                .streamCompletion(
                        anyString(), anyString(), anyString(), anyString(), anyString(),
                        anyList(), anyList(), any());

        OpenAiCompatLlmAdapter adapter =
                new OpenAiCompatLlmAdapter(wrapper, "http://localhost:1234/v1", "gpt-4o-mini", "");
        List<LlmEvent> events = new ArrayList<>();
        adapter.generate(LlmRequest.simple("sys", "weather?"), events::add);

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(LlmEvent.ToolCallRequest.class);
        LlmEvent.ToolCallRequest req = (LlmEvent.ToolCallRequest) events.get(0);
        assertThat(req.toolCallId()).isEqualTo("call_abc");
        assertThat(req.toolName()).isEqualTo("get_current_weather");
        assertThat(req.input()).containsEntry("city", "Austin");
        assertThat(events.get(1)).isInstanceOf(LlmEvent.Stop.class);
        assertThat(((LlmEvent.Stop) events.get(1)).outputTokens()).isEqualTo(8);
    }
}
