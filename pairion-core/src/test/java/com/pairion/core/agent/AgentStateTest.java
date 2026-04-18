package com.pairion.core.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests for {@link AgentState}. */
class AgentStateTest {

    @Test
    void idleWireValue() {
        assertThat(AgentState.IDLE.wireValue()).isEqualTo("idle");
    }

    @Test
    void listeningWireValue() {
        assertThat(AgentState.LISTENING.wireValue()).isEqualTo("listening");
    }

    @Test
    void thinkingWireValue() {
        assertThat(AgentState.THINKING.wireValue()).isEqualTo("thinking");
    }

    @Test
    void speakingWireValue() {
        assertThat(AgentState.SPEAKING.wireValue()).isEqualTo("speaking");
    }

    @Test
    void valuesContainsAll() {
        assertThat(AgentState.values()).hasSize(4);
    }

    @Test
    void valueOfWorks() {
        assertThat(AgentState.valueOf("IDLE")).isEqualTo(AgentState.IDLE);
    }
}
