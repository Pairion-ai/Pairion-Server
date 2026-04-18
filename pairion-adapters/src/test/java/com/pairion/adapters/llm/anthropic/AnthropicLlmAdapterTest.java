package com.pairion.adapters.llm.anthropic;

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
import org.junit.jupiter.api.Test;

/** Tests for {@link AnthropicLlmAdapter}. */
class AnthropicLlmAdapterTest {

    @Test
    void nameReturnsAnthropic() {
        AnthropicClientWrapper wrapper = mock(AnthropicClientWrapper.class);
        AnthropicLlmAdapter adapter = new AnthropicLlmAdapter(wrapper, "model-1");
        assertThat(adapter.name()).isEqualTo("anthropic");
    }

    @Test
    void capabilitiesWhenAvailable() {
        AnthropicClientWrapper wrapper = mock(AnthropicClientWrapper.class);
        when(wrapper.isAvailable()).thenReturn(true);
        AnthropicLlmAdapter adapter = new AnthropicLlmAdapter(wrapper, "model-1");
        LlmCapabilities caps = adapter.capabilities();
        assertThat(caps.available()).isTrue();
        assertThat(caps.supportsToolUse()).isTrue();
        assertThat(caps.supportsStreaming()).isTrue();
    }

    @Test
    void capabilitiesWhenUnavailable() {
        AnthropicClientWrapper wrapper = mock(AnthropicClientWrapper.class);
        when(wrapper.isAvailable()).thenReturn(false);
        AnthropicLlmAdapter adapter = new AnthropicLlmAdapter(wrapper, "model-1");
        LlmCapabilities caps = adapter.capabilities();
        assertThat(caps.available()).isFalse();
    }

    @Test
    void generateStreamsTokensAndStop() {
        AnthropicClientWrapper wrapper = mock(AnthropicClientWrapper.class);
        when(wrapper.isAvailable()).thenReturn(true);
        doAnswer(
                        inv -> {
                            AnthropicClientWrapper.StreamCallback cb = inv.getArgument(4);
                            cb.onToken("Hello");
                            cb.onToken(" world");
                            cb.onComplete();
                            return null;
                        })
                .when(wrapper)
                .streamCompletion(anyString(), anyString(), anyString(), anyList(), any());

        AnthropicLlmAdapter adapter = new AnthropicLlmAdapter(wrapper, "model-1");
        LlmRequest request = LlmRequest.simple("system", "hi");

        List<LlmEvent> events = new ArrayList<>();
        adapter.generate(request, events::add);

        assertThat(events).hasSize(3);
        assertThat(events.get(0)).isInstanceOf(LlmEvent.TokenDelta.class);
        assertThat(((LlmEvent.TokenDelta) events.get(0)).delta()).isEqualTo("Hello");
        assertThat(events.get(1)).isInstanceOf(LlmEvent.TokenDelta.class);
        assertThat(events.get(2)).isInstanceOf(LlmEvent.Stop.class);
        assertThat(((LlmEvent.Stop) events.get(2)).outputTokens()).isEqualTo(2);
    }

    @Test
    void generateWithExplicitModel() {
        AnthropicClientWrapper wrapper = mock(AnthropicClientWrapper.class);
        when(wrapper.isAvailable()).thenReturn(true);
        doAnswer(
                        inv -> {
                            String model = inv.getArgument(0);
                            assertThat(model).isEqualTo("custom-model");
                            AnthropicClientWrapper.StreamCallback cb = inv.getArgument(4);
                            cb.onComplete();
                            return null;
                        })
                .when(wrapper)
                .streamCompletion(anyString(), anyString(), anyString(), anyList(), any());

        AnthropicLlmAdapter adapter = new AnthropicLlmAdapter(wrapper, "default-model");
        LlmRequest request = new LlmRequest("sys", "msg", List.of(), "custom-model");

        List<LlmEvent> events = new ArrayList<>();
        adapter.generate(request, events::add);

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(LlmEvent.Stop.class);
    }

    @Test
    void generateHandlesError() {
        AnthropicClientWrapper wrapper = mock(AnthropicClientWrapper.class);
        when(wrapper.isAvailable()).thenReturn(true);
        doAnswer(
                        inv -> {
                            AnthropicClientWrapper.StreamCallback cb = inv.getArgument(4);
                            cb.onToken("partial");
                            cb.onError(new RuntimeException("API failure"));
                            return null;
                        })
                .when(wrapper)
                .streamCompletion(anyString(), anyString(), anyString(), anyList(), any());

        AnthropicLlmAdapter adapter = new AnthropicLlmAdapter(wrapper, "model-1");
        List<LlmEvent> events = new ArrayList<>();
        adapter.generate(LlmRequest.simple("sys", "msg"), events::add);

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(LlmEvent.TokenDelta.class);
        assertThat(events.get(1)).isInstanceOf(LlmEvent.Stop.class);
    }
}
