package com.pairion.adapters.audio.opus;

import io.github.jaredmdobson.concentus.OpusException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Concentus-based pure-Java Opus decoder implementation.
 *
 * <p>Wraps the Concentus library ({@code io.github.jaredmdobson:concentus:1.0.2}) to decode Opus
 * frames to 16 kHz mono signed 16-bit PCM. Fully stateful — one instance per audio stream, disposed
 * on AudioStreamEnd.
 */
public class ConcentusOpusDecoder implements OpusDecoderNative {

    private static final Logger log = LoggerFactory.getLogger(ConcentusOpusDecoder.class);

    private final io.github.jaredmdobson.concentus.OpusDecoder decoder;

    /**
     * Constructs the Concentus-backed decoder at 16 kHz mono.
     *
     * @throws OpusException if the Concentus decoder cannot be initialized
     */
    public ConcentusOpusDecoder() throws OpusException {
        this.decoder =
                new io.github.jaredmdobson.concentus.OpusDecoder(
                        OpusDecoder.SAMPLE_RATE, OpusDecoder.CHANNELS);
        log.info("Concentus Opus decoder initialized (16 kHz mono)");
    }

    /**
     * Decodes an Opus frame to 16-bit little-endian PCM bytes.
     *
     * @param opusFrame the Opus-encoded frame data
     * @return decoded PCM bytes (up to FRAME_SIZE * 2 bytes)
     * @throws IllegalArgumentException if the frame cannot be decoded
     */
    @Override
    public byte[] decodeFrame(byte[] opusFrame) {
        short[] pcmShorts = new short[OpusDecoder.FRAME_SIZE];
        try {
            int samplesDecoded =
                    decoder.decode(
                            opusFrame,
                            0,
                            opusFrame.length,
                            pcmShorts,
                            0,
                            OpusDecoder.FRAME_SIZE,
                            false);
            byte[] pcmBytes = new byte[samplesDecoded * 2];
            ByteBuffer buf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < samplesDecoded; i++) {
                buf.putShort(pcmShorts[i]);
            }
            return pcmBytes;
        } catch (OpusException e) {
            throw new IllegalArgumentException("Failed to decode Opus frame: " + e.getMessage(), e);
        }
    }

    /** Resets the Concentus decoder state. */
    @Override
    public void reset() {
        decoder.resetState();
        log.debug("Opus decoder state reset");
    }
}
