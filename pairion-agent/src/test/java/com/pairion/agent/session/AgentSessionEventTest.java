package com.pairion.agent.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.pairion.core.agent.AgentState;
import org.junit.jupiter.api.Test;

/** Tests for {@link AgentSessionEvent} sealed hierarchy. */
class AgentSessionEventTest {

    @Test
    void stateChangeEvent() {
        AgentSessionEvent.StateChangeEvent event =
                new AgentSessionEvent.StateChangeEvent(AgentState.THINKING);
        assertThat(event.state()).isEqualTo(AgentState.THINKING);
        assertThat(event).isInstanceOf(AgentSessionEvent.class);
    }

    @Test
    void transcriptPartialEvent() {
        AgentSessionEvent.TranscriptPartialEvent event =
                new AgentSessionEvent.TranscriptPartialEvent("hel");
        assertThat(event.text()).isEqualTo("hel");
    }

    @Test
    void transcriptFinalEvent() {
        AgentSessionEvent.TranscriptFinalEvent event =
                new AgentSessionEvent.TranscriptFinalEvent("hello world");
        assertThat(event.text()).isEqualTo("hello world");
    }

    @Test
    void llmTokenEvent() {
        AgentSessionEvent.LlmTokenEvent event = new AgentSessionEvent.LlmTokenEvent("token");
        assertThat(event.delta()).isEqualTo("token");
    }
}
