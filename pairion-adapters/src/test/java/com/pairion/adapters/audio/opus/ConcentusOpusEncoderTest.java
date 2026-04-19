package com.pairion.adapters.audio.opus;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jaredmdobson.concentus.OpusException;
import org.junit.jupiter.api.Test;

/** Tests for {@link ConcentusOpusEncoder}. */
class ConcentusOpusEncoderTest {

    @Test
    void getSampleRateReturnsConfiguredRate() throws OpusException {
        ConcentusOpusEncoder encoder = new ConcentusOpusEncoder(16000);
        assertThat(encoder.getSampleRate()).isEqualTo(16000);
    }

    @Test
    void encodeFrameProducesNonEmptyOpusBytes() throws OpusException {
        ConcentusOpusEncoder encoder = new ConcentusOpusEncoder(16000);
        // 320 samples * 2 bytes = 640 bytes of silence
        byte[] pcmFrame = new byte[640];
        byte[] opus = encoder.encodeFrame(pcmFrame);
        assertThat(opus).isNotEmpty();
    }

    @Test
    void resetDoesNotThrow() throws OpusException {
        ConcentusOpusEncoder encoder = new ConcentusOpusEncoder(16000);
        // reset() should not throw
        encoder.reset();
    }
}
