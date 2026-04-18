package com.pairion.adapters.audio.opus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusEncoder;
import io.github.jaredmdobson.concentus.OpusException;
import org.junit.jupiter.api.Test;

/** Tests for {@link ConcentusOpusDecoder}. */
class ConcentusOpusDecoderTest {

    @Test
    void decodeRealOpusFrameProducesNonEmptyPcm() throws OpusException {
        OpusEncoder encoder = new OpusEncoder(16000, 1, OpusApplication.OPUS_APPLICATION_VOIP);
        short[] silenceSamples = new short[OpusDecoder.FRAME_SIZE];
        byte[] opusFrame = new byte[1024];
        int encodedLen =
                encoder.encode(
                        silenceSamples, 0, OpusDecoder.FRAME_SIZE, opusFrame, 0, opusFrame.length);
        byte[] trimmedFrame = new byte[encodedLen];
        System.arraycopy(opusFrame, 0, trimmedFrame, 0, encodedLen);

        ConcentusOpusDecoder decoder = new ConcentusOpusDecoder();
        byte[] pcm = decoder.decodeFrame(trimmedFrame);

        assertThat(pcm).isNotEmpty();
        assertThat(pcm.length).isEqualTo(OpusDecoder.FRAME_SIZE * 2);
    }

    @Test
    void decodeInvalidFrameThrowsIllegalArgument() throws OpusException {
        ConcentusOpusDecoder decoder = new ConcentusOpusDecoder();
        byte[] garbage = new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        assertThatThrownBy(() -> decoder.decodeFrame(garbage))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to decode Opus frame");
    }

    @Test
    void resetDoesNotThrow() throws OpusException {
        ConcentusOpusDecoder decoder = new ConcentusOpusDecoder();
        decoder.reset();
    }
}
