package com.pairion.adapters.audio.opus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

/** Tests for {@link OpusEncoder}. */
class OpusEncoderTest {

    @Test
    void encodeProducesPrefixedFrame() {
        OpusEncoderNative mockNative = mock(OpusEncoderNative.class);
        byte[] fakeOpus = {0x01, 0x02, 0x03};
        when(mockNative.encodeFrame(org.mockito.ArgumentMatchers.any())).thenReturn(fakeOpus);

        OpusEncoder encoder = new OpusEncoder(mockNative, 16000);
        byte[] streamId = "stm1".getBytes();
        byte[] pcm = new byte[640]; // 320 samples * 2 bytes

        byte[] result = encoder.encode(streamId, pcm);

        assertThat(result).hasSize(4 + 3);
        assertThat(result[0]).isEqualTo((byte) 's');
        assertThat(result[1]).isEqualTo((byte) 't');
        assertThat(result[2]).isEqualTo((byte) 'm');
        assertThat(result[3]).isEqualTo((byte) '1');
        assertThat(result[4]).isEqualTo((byte) 0x01);
    }

    @Test
    void getFrameSizeIsCorrectFor16kHz() {
        OpusEncoderNative mockNative = mock(OpusEncoderNative.class);
        OpusEncoder encoder = new OpusEncoder(mockNative, 16000);
        // 16000 Hz * 20 ms / 1000 = 320 samples
        assertThat(encoder.getFrameSize()).isEqualTo(320);
    }

    @Test
    void getFrameSizeIsCorrectFor22050Hz() {
        OpusEncoderNative mockNative = mock(OpusEncoderNative.class);
        // 22050 Hz * 20 ms / 1000 = 441 samples
        OpusEncoder encoder = new OpusEncoder(mockNative, 22050);
        assertThat(encoder.getFrameSize()).isEqualTo(441);
    }

    @Test
    void getSampleRateReturnsConfiguredRate() {
        OpusEncoderNative mockNative = mock(OpusEncoderNative.class);
        OpusEncoder encoder = new OpusEncoder(mockNative, 16000);
        assertThat(encoder.getSampleRate()).isEqualTo(16000);
    }

    @Test
    void resetDelegatesToNative() {
        OpusEncoderNative mockNative = mock(OpusEncoderNative.class);
        OpusEncoder encoder = new OpusEncoder(mockNative, 16000);
        encoder.reset();
        verify(mockNative).reset();
    }
}
