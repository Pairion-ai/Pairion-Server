package com.pairion.adapters.audio.opus;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests for {@link ConcentusOpusDecoder}. */
class ConcentusOpusDecoderTest {

    @Test
    void decodeFrameReturnsSilence() {
        ConcentusOpusDecoder decoder = new ConcentusOpusDecoder();
        byte[] pcm = decoder.decodeFrame(new byte[] {0x01, 0x02});
        assertThat(pcm).hasSize(OpusDecoder.FRAME_SIZE * 2);
        for (byte b : pcm) {
            assertThat(b).isZero();
        }
    }

    @Test
    void resetDoesNotThrow() {
        ConcentusOpusDecoder decoder = new ConcentusOpusDecoder();
        decoder.reset();
    }
}
