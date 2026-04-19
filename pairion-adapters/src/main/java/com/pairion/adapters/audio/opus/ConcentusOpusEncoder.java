package com.pairion.adapters.audio.opus;

import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Concentus-based pure-Java Opus encoder implementation.
 *
 * <p>Wraps the Concentus library ({@code io.github.jaredmdobson:concentus:1.0.2}) to encode 16 kHz
 * mono signed 16-bit PCM frames into Opus frames. Fully stateful — one instance per TTS stream,
 * disposed on AudioStreamEnd.
 */
public class ConcentusOpusEncoder implements OpusEncoderNative {

    private static final Logger log = LoggerFactory.getLogger(ConcentusOpusEncoder.class);

    private final io.github.jaredmdobson.concentus.OpusEncoder encoder;
    private final int sampleRate;

    /**
     * Constructs the Concentus-backed encoder at the given sample rate, mono, AUDIO application
     * mode.
     *
     * @param sampleRate the PCM sample rate in Hz (must be one of 8000, 12000, 16000, 24000, 48000)
     * @throws OpusException if the Concentus encoder cannot be initialized
     */
    public ConcentusOpusEncoder(int sampleRate) throws OpusException {
        this.sampleRate = sampleRate;
        this.encoder =
                new io.github.jaredmdobson.concentus.OpusEncoder(
                        sampleRate, OpusEncoder.CHANNELS, OpusApplication.OPUS_APPLICATION_AUDIO);
        log.info("Concentus Opus encoder initialized ({} Hz mono)", sampleRate);
    }

    /**
     * Encodes one frame of raw 16-bit little-endian PCM bytes into an Opus frame.
     *
     * @param pcmFrame the PCM bytes for one frame (must contain exactly frameSize samples × 2 bytes)
     * @return Opus-encoded frame bytes
     * @throws IllegalArgumentException if the frame cannot be encoded
     */
    @Override
    public byte[] encodeFrame(byte[] pcmFrame) {
        int frameSizeSamples = pcmFrame.length / 2;
        short[] pcmShorts = new short[frameSizeSamples];
        ByteBuffer buf = ByteBuffer.wrap(pcmFrame).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frameSizeSamples; i++) {
            pcmShorts[i] = buf.getShort();
        }

        byte[] opusOut = new byte[4096];
        try {
            int encodedLen =
                    encoder.encode(pcmShorts, 0, frameSizeSamples, opusOut, 0, opusOut.length);
            byte[] result = new byte[encodedLen];
            System.arraycopy(opusOut, 0, result, 0, encodedLen);
            return result;
        } catch (OpusException e) {
            throw new IllegalArgumentException("Failed to encode Opus frame: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the sample rate this encoder was initialized with.
     *
     * @return sample rate in Hz
     */
    public int getSampleRate() {
        return sampleRate;
    }

    /** Resets the Concentus encoder state. */
    @Override
    public void reset() {
        encoder.resetState();
        log.debug("Opus encoder state reset");
    }
}
