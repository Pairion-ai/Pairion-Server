package com.pairion.adapters.audio.opus;

/**
 * Internal abstraction boundary for Opus decoding.
 *
 * <p>Production implementation uses Concentus (pure-Java). Test implementations return canned PCM
 * data. This boundary enables 100% coverage of the {@link OpusDecoder} without a real Opus
 * encoder/decoder in unit tests.
 */
public interface OpusDecoderNative {

    /**
     * Decodes a single Opus frame into raw 16-bit little-endian mono PCM bytes.
     *
     * @param opusFrame the Opus-encoded frame data
     * @return decoded PCM bytes
     */
    byte[] decodeFrame(byte[] opusFrame);

    /** Resets the internal decoder state. */
    void reset();
}
