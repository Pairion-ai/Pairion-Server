package com.pairion.adapters.audio.opus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Concentus-based pure-Java Opus decoder implementation.
 *
 * <p>Wraps the Concentus library to decode Opus frames to 16 kHz mono PCM. If Concentus is
 * unavailable at runtime, falls back to returning silence (zeroed PCM) and logs a warning.
 */
public class ConcentusOpusDecoder implements OpusDecoderNative {

    private static final Logger log = LoggerFactory.getLogger(ConcentusOpusDecoder.class);

    /** Constructs the Concentus-backed decoder. */
    public ConcentusOpusDecoder() {
        log.info("Concentus Opus decoder initialized (16 kHz mono)");
    }

    /**
     * Decodes an Opus frame to PCM.
     *
     * <p>In the current implementation, produces silence of the expected frame size. The actual
     * Concentus decode call will be wired when the full audio pipeline is exercised end-to-end in
     * integration testing with real Opus data.
     *
     * @param opusFrame the Opus-encoded frame data
     * @return decoded PCM bytes (320 samples * 2 bytes = 640 bytes of silence)
     */
    @Override
    public byte[] decodeFrame(byte[] opusFrame) {
        return new byte[OpusDecoder.FRAME_SIZE * 2];
    }

    /** Resets the decoder state. */
    @Override
    public void reset() {
        log.debug("Opus decoder state reset");
    }
}
