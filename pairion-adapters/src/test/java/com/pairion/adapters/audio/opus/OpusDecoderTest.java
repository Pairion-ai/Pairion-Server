package com.pairion.adapters.audio.opus;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests for {@link OpusDecoder}. */
class OpusDecoderTest {

    @Test
    void extractStreamIdFromValidFrame() {
        byte[] frame = "stm1opusdata".getBytes();
        OpusDecoder decoder = OpusDecoder.create();
        assertThat(decoder.extractStreamId(frame)).isEqualTo("stm1");
    }

    @Test
    void extractStreamIdFromShortFrame() {
        byte[] frame = new byte[] {0x01, 0x02};
        OpusDecoder decoder = OpusDecoder.create();
        assertThat(decoder.extractStreamId(frame)).isEmpty();
    }

    @Test
    void decodeStripsPrefixAndDecodesFrame() {
        byte[] mockPcm = new byte[] {0x10, 0x20, 0x30, 0x40};
        OpusDecoderNative mockNative =
                new OpusDecoderNative() {
                    @Override
                    public byte[] decodeFrame(byte[] opusFrame) {
                        return mockPcm;
                    }

                    @Override
                    public void reset() {}
                };
        OpusDecoder decoder = new OpusDecoder(mockNative);

        byte[] frame = "stm1opusdata".getBytes();
        byte[] result = decoder.decode(frame);
        assertThat(result).isEqualTo(mockPcm);
    }

    @Test
    void decodeTooShortFrame() {
        OpusDecoder decoder = OpusDecoder.create();
        byte[] frame = new byte[] {0x01, 0x02, 0x03, 0x04};
        byte[] result = decoder.decode(frame);
        assertThat(result).isEmpty();
    }

    @Test
    void resetDelegates() {
        boolean[] resetCalled = {false};
        OpusDecoderNative mockNative =
                new OpusDecoderNative() {
                    @Override
                    public byte[] decodeFrame(byte[] opusFrame) {
                        return new byte[0];
                    }

                    @Override
                    public void reset() {
                        resetCalled[0] = true;
                    }
                };
        OpusDecoder decoder = new OpusDecoder(mockNative);
        decoder.reset();
        assertThat(resetCalled[0]).isTrue();
    }

    @Test
    void createReturnsWorkingDecoder() {
        OpusDecoder decoder = OpusDecoder.create();
        assertThat(decoder).isNotNull();
    }

    @Test
    void constants() {
        assertThat(OpusDecoder.SAMPLE_RATE).isEqualTo(16000);
        assertThat(OpusDecoder.CHANNELS).isEqualTo(1);
        assertThat(OpusDecoder.FRAME_DURATION_MS).isEqualTo(20);
        assertThat(OpusDecoder.FRAME_SIZE).isEqualTo(320);
        assertThat(OpusDecoder.STREAM_ID_PREFIX_LENGTH).isEqualTo(4);
    }
}
