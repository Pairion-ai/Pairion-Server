package com.pairion.adapters.audio.opus;

/**
 * Internal abstraction boundary for Opus encoding.
 *
 * <p>Production implementation uses Concentus (pure-Java). Test implementations return canned Opus
 * data. This boundary enables 100% coverage of the {@link OpusEncoder} without a real encoder in
 * unit tests.
 */
public interface OpusEncoderNative {

    /**
     * Encodes a frame of raw 16-bit little-endian mono PCM bytes into an Opus frame.
     *
     * @param pcmFrame the PCM bytes for one frame (must contain exactly {@link
     *     OpusEncoder#FRAME_SIZE} samples × 2 bytes)
     * @return Opus-encoded frame bytes
     */
    byte[] encodeFrame(byte[] pcmFrame);

    /** Resets the internal encoder state. */
    void reset();
}
