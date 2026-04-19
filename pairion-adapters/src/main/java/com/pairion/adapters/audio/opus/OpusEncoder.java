package com.pairion.adapters.audio.opus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encodes raw PCM audio frames into Opus-encoded binary frames with a 4-byte stream ID prefix.
 *
 * <p>Each outbound binary WebSocket frame contains a 4-byte stream ID prefix followed by an
 * Opus-encoded audio frame. This encoder accepts PCM at any sample rate supported by Opus (8000,
 * 12000, 16000, 24000, 48000 Hz); use {@link #FRAME_DURATION_MS} to compute the frame size.
 *
 * <p>Implemented using Concentus (pure-Java Opus) for zero native-library friction. Thread-safe:
 * each instance maintains its own encoder state and must be used from a single session.
 */
public class OpusEncoder {

    private static final Logger log = LoggerFactory.getLogger(OpusEncoder.class);

    /** Number of channels (mono). */
    public static final int CHANNELS = 1;

    /** Frame duration in milliseconds. */
    public static final int FRAME_DURATION_MS = 20;

    /** Stream ID prefix length in bytes (mirrors {@link OpusDecoder#STREAM_ID_PREFIX_LENGTH}). */
    public static final int STREAM_ID_PREFIX_LENGTH = 4;

    private final OpusEncoderNative encoderNative;
    private final int sampleRate;

    /**
     * Creates a new Opus encoder with the given native implementation.
     *
     * @param encoderNative the native encoder implementation
     * @param sampleRate the PCM sample rate in Hz
     */
    public OpusEncoder(OpusEncoderNative encoderNative, int sampleRate) {
        this.encoderNative = encoderNative;
        this.sampleRate = sampleRate;
    }

    /**
     * Creates a new Opus encoder using the default Concentus implementation at the given sample
     * rate.
     *
     * @param sampleRate the PCM sample rate in Hz (must be 8000, 12000, 16000, 24000, or 48000)
     * @return a new encoder instance
     */
    public static OpusEncoder create(int sampleRate) {
        try {
            return new OpusEncoder(new ConcentusOpusEncoder(sampleRate), sampleRate);
        } catch (io.github.jaredmdobson.concentus.OpusException e) {
            throw new IllegalStateException("Failed to create Opus encoder", e);
        }
    }

    /**
     * Returns the number of PCM samples per frame at this encoder's sample rate.
     *
     * @return samples per frame
     */
    public int getFrameSize() {
        return sampleRate * FRAME_DURATION_MS / 1000;
    }

    /**
     * Returns the sample rate this encoder was initialized with.
     *
     * @return sample rate in Hz
     */
    public int getSampleRate() {
        return sampleRate;
    }

    /**
     * Encodes a PCM frame and wraps it with a 4-byte stream ID prefix for WebSocket transmission.
     *
     * <p>The {@code streamIdBytes} must be exactly {@link #STREAM_ID_PREFIX_LENGTH} bytes. The
     * {@code pcmFrame} must contain exactly {@link #getFrameSize()} samples × 2 bytes in 16-bit
     * little-endian mono format.
     *
     * @param streamIdBytes the 4-byte stream ID prefix
     * @param pcmFrame the PCM bytes for one frame
     * @return Opus frame prefixed with the stream ID, ready for WebSocket binary transmission
     */
    public byte[] encode(byte[] streamIdBytes, byte[] pcmFrame) {
        byte[] opusData = encoderNative.encodeFrame(pcmFrame);
        byte[] result = new byte[STREAM_ID_PREFIX_LENGTH + opusData.length];
        System.arraycopy(streamIdBytes, 0, result, 0, STREAM_ID_PREFIX_LENGTH);
        System.arraycopy(opusData, 0, result, STREAM_ID_PREFIX_LENGTH, opusData.length);
        return result;
    }

    /** Resets the encoder state. Call between audio streams. */
    public void reset() {
        encoderNative.reset();
    }
}
