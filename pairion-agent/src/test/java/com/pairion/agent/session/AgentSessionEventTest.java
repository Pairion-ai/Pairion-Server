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

    @Test
    void mapFocusEvent() {
        AgentSessionEvent.MapFocusEvent event =
                new AgentSessionEvent.MapFocusEvent(35.6762, 139.6503, "Tokyo, Japan", "city");
        assertThat(event.lat()).isEqualTo(35.6762);
        assertThat(event.lon()).isEqualTo(139.6503);
        assertThat(event.label()).isEqualTo("Tokyo, Japan");
        assertThat(event.zoom()).isEqualTo("city");
        assertThat(event).isInstanceOf(AgentSessionEvent.class);
    }

    @Test
    void mapClearEvent() {
        AgentSessionEvent.MapClearEvent event = new AgentSessionEvent.MapClearEvent();
        assertThat(event).isInstanceOf(AgentSessionEvent.class);
    }

    @Test
    void backgroundChangeEvent() {
        AgentSessionEvent.BackgroundChangeEvent event =
                new AgentSessionEvent.BackgroundChangeEvent("vfr", "crossfade");
        assertThat(event.backgroundId()).isEqualTo("vfr");
        assertThat(event.transition()).isEqualTo("crossfade");
        assertThat(event).isInstanceOf(AgentSessionEvent.class);
    }

    @Test
    void overlayAddEvent() {
        AgentSessionEvent.OverlayAddEvent event =
                new AgentSessionEvent.OverlayAddEvent("adsb", java.util.Map.of("radius_nm", 8));
        assertThat(event.overlayId()).isEqualTo("adsb");
        assertThat(event.params()).containsKey("radius_nm");
        assertThat(event).isInstanceOf(AgentSessionEvent.class);
    }

    @Test
    void overlayAddEventNullParams() {
        AgentSessionEvent.OverlayAddEvent event =
                new AgentSessionEvent.OverlayAddEvent("adsb", null);
        assertThat(event.overlayId()).isEqualTo("adsb");
        assertThat(event.params()).isNull();
    }

    @Test
    void overlayRemoveEvent() {
        AgentSessionEvent.OverlayRemoveEvent event =
                new AgentSessionEvent.OverlayRemoveEvent("adsb");
        assertThat(event.overlayId()).isEqualTo("adsb");
        assertThat(event).isInstanceOf(AgentSessionEvent.class);
    }

    @Test
    void overlayClearEvent() {
        AgentSessionEvent.OverlayClearEvent event = new AgentSessionEvent.OverlayClearEvent();
        assertThat(event).isInstanceOf(AgentSessionEvent.class);
    }
}
