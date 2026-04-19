package com.pairion.core.tts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests for TTS domain types: TtsCapabilities and TtsEvent. */
class TtsTypesTest {

    @Test
    void ttsCapabilitiesAvailable() {
        TtsCapabilities caps = new TtsCapabilities(true, true);
        assertThat(caps.available()).isTrue();
        assertThat(caps.supportsStreaming()).isTrue();
    }

    @Test
    void ttsCapabilitiesUnavailable() {
        TtsCapabilities caps = TtsCapabilities.unavailable();
        assertThat(caps.available()).isFalse();
        assertThat(caps.supportsStreaming()).isFalse();
    }

    @Test
    void ttsEventChunk() {
        byte[] audio = new byte[]{0x01, 0x02};
        TtsEvent.Chunk chunk = new TtsEvent.Chunk(audio, true);
        assertThat(chunk.audio()).isSameAs(audio);
        assertThat(chunk.isOpus()).isTrue();
        assertThat(chunk).isInstanceOf(TtsEvent.class);
    }

    @Test
    void ttsEventCompleted() {
        TtsEvent.Completed completed = new TtsEvent.Completed(1500L);
        assertThat(completed.totalDurationMs()).isEqualTo(1500L);
        assertThat(completed).isInstanceOf(TtsEvent.class);
    }
}
