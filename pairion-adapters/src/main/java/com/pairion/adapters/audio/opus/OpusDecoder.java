package com.pairion.adapters.audio.opus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decodes Opus-encoded audio frames into 16 kHz mono signed 16-bit PCM.
 *
 * <p>Each incoming binary WebSocket frame contains a 4-byte stream ID prefix followed by an
 * Opus-encoded audio frame (20 ms at 16 kHz mono). This decoder strips the prefix and decodes the
 * Opus payload to raw PCM.
 *
 * <p>Implemented using Concentus (pure-Java Opus) for zero native-library friction. Thread-safe:
 * each instance maintains its own decoder state and must be used from a single session.
 */
public class OpusDecoder {

    private static final Logger log = LoggerFactory.getLogger(OpusDecoder.class);

    /** Sample rate in Hz for decoded PCM output. */
    public static final int SAMPLE_RATE = 16000;

    /** Number of channels (mono). */
    public static final int CHANNELS = 1;

    /** Frame duration in milliseconds. */
    public static final int FRAME_DURATION_MS = 20;

    /** Number of PCM samples per frame (16000 Hz * 20 ms = 320 samples). */
    public static final int FRAME_SIZE = SAMPLE_RATE * FRAME_DURATION_MS / 1000;

    /** Stream ID prefix length in bytes. */
    public static final int STREAM_ID_PREFIX_LENGTH = 4;

    private final OpusDecoderNative decoderNative;

    /**
     * Creates a new Opus decoder with the given native implementation.
     *
     * @param decoderNative the native decoder implementation
     */
    public OpusDecoder(OpusDecoderNative decoderNative) {
        this.decoderNative = decoderNative;
    }

    /**
     * Creates a new Opus decoder using the default Concentus implementation.
     *
     * @return a new decoder instance
     */
    public static OpusDecoder create() {
        try {
            return new OpusDecoder(new ConcentusOpusDecoder());
        } catch (io.github.jaredmdobson.concentus.OpusException e) {
            throw new IllegalStateException("Failed to create Opus decoder", e);
        }
    }

    /**
     * Extracts the 4-byte stream ID prefix from a binary WebSocket frame.
     *
     * @param frame the raw binary frame
     * @return the stream ID as a string
     */
    public String extractStreamId(byte[] frame) {
        if (frame.length < STREAM_ID_PREFIX_LENGTH) {
            log.warn("Binary frame too short for stream ID prefix: {} bytes", frame.length);
            return "";
        }
        return new String(
                frame, 0, STREAM_ID_PREFIX_LENGTH, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Decodes an Opus frame from a binary WebSocket message (after stripping the 4-byte stream ID
     * prefix) into raw 16-bit PCM samples.
     *
     * @param frame the raw binary frame including the 4-byte stream ID prefix
     * @return decoded PCM bytes (16-bit little-endian mono), or empty array if frame is too short
     */
    public byte[] decode(byte[] frame) {
        if (frame.length <= STREAM_ID_PREFIX_LENGTH) {
            log.warn("Binary frame too short to contain Opus data: {} bytes", frame.length);
            return new byte[0];
        }
        byte[] opusData = new byte[frame.length - STREAM_ID_PREFIX_LENGTH];
        System.arraycopy(frame, STREAM_ID_PREFIX_LENGTH, opusData, 0, opusData.length);
        return decoderNative.decodeFrame(opusData);
    }

    /** Resets the decoder state. Call between audio streams. */
    public void reset() {
        decoderNative.reset();
    }
}
