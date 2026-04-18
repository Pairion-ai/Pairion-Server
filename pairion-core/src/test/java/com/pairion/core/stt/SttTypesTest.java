package com.pairion.core.stt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests for STT domain types: SttEvent, SttCapabilities. */
class SttTypesTest {

    @Test
    void partialEvent() {
        SttEvent.Partial partial = new SttEvent.Partial("hel");
        assertThat(partial.text()).isEqualTo("hel");
        assertThat(partial).isInstanceOf(SttEvent.class);
    }

    @Test
    void finalEvent() {
        SttEvent.Final finalEvent = new SttEvent.Final("hello world", 2500);
        assertThat(finalEvent.text()).isEqualTo("hello world");
        assertThat(finalEvent.durationMs()).isEqualTo(2500);
    }

    @Test
    void capabilitiesAvailable() {
        SttCapabilities caps = new SttCapabilities(true, true);
        assertThat(caps.available()).isTrue();
        assertThat(caps.supportsStreaming()).isTrue();
    }

    @Test
    void capabilitiesUnavailable() {
        SttCapabilities caps = SttCapabilities.unavailable();
        assertThat(caps.available()).isFalse();
        assertThat(caps.supportsStreaming()).isFalse();
    }
}
