package com.pairion.adapters.tts.piper;

import com.pairion.adapters.audio.opus.OpusEncoder;
import com.pairion.adapters.tts.spi.TtsAdapter;
import com.pairion.core.tts.TtsCapabilities;
import com.pairion.core.tts.TtsEvent;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * TTS adapter backed by Piper TTS via FFM native bindings.
 *
 * <p>Synthesizes text using {@link DefaultPiperTtsNative}, resamples Piper's output PCM to 16 kHz,
 * Opus-encodes each sentence chunk via Concentus, and delivers {@link TtsEvent.Chunk} events to the
 * caller. A {@link TtsEvent.Completed} event is emitted after all chunks.
 *
 * <p>Activated when {@code pairion.adapters.tts=piper} (the default) and a {@link PiperTtsNative}
 * bean is present.
 */
@Component
@ConditionalOnProperty(name = "pairion.adapters.tts", havingValue = "piper", matchIfMissing = true)
@ConditionalOnBean(PiperTtsNative.class)
public class PiperTtsAdapter implements TtsAdapter {

    private static final Logger log = LoggerFactory.getLogger(PiperTtsAdapter.class);

    /** Opus target sample rate for outbound audio. */
    static final int OPUS_SAMPLE_RATE = 16000;

    private final PiperTtsNative piperNative;

    /**
     * Constructs the adapter with the given native Piper implementation.
     *
     * @param piperNative the native Piper wrapper
     */
    public PiperTtsAdapter(PiperTtsNative piperNative) {
        this.piperNative = piperNative;
        log.info(
                "Piper TTS adapter initialized: available={}, sampleRate={}",
                piperNative.isAvailable(),
                piperNative.getSampleRate());
    }

    /**
     * Returns the adapter name.
     *
     * @return "piper"
     */
    @Override
    public String name() {
        return "piper";
    }

    /**
     * Returns the current capabilities.
     *
     * @return capabilities reflecting native Piper availability
     */
    @Override
    public TtsCapabilities capabilities() {
        return piperNative.isAvailable()
                ? new TtsCapabilities(true, true)
                : TtsCapabilities.unavailable();
    }

    /**
     * Synthesizes text using Piper TTS, streaming Opus-encoded audio chunks to the consumer.
     *
     * <p>Each sentence chunk from Piper is:
     *
     * <ol>
     *   <li>Resampled from Piper's native rate to {@link #OPUS_SAMPLE_RATE} (16 kHz) if needed.
     *   <li>Padded to a multiple of the Opus frame size (320 samples at 16 kHz / 20 ms).
     *   <li>Opus-encoded frame-by-frame with a 4-byte stream ID prefix.
     *   <li>Delivered as a {@link TtsEvent.Chunk} with {@code isOpus=true}.
     * </ol>
     *
     * @param text the text to speak
     * @param eventConsumer callback receiving TtsEvent chunks and completion
     */
    @Override
    public void speak(String text, Consumer<TtsEvent> eventConsumer) {
        if (!piperNative.isAvailable()) {
            log.warn("Piper TTS unavailable — skipping synthesis");
            eventConsumer.accept(new TtsEvent.Completed(0L));
            return;
        }

        log.info("tts.synthesize.start: text_length={}", text.length());
        long startMs = System.currentTimeMillis();

        // Stable stream ID for this synthesis session (4 ASCII bytes)
        String streamId = UUID.randomUUID().toString().replace("-", "").substring(0, 4);
        byte[] streamIdBytes = streamId.getBytes(StandardCharsets.UTF_8);

        int piperSampleRate = piperNative.getSampleRate();
        OpusEncoder encoder = OpusEncoder.create(OPUS_SAMPLE_RATE);
        int frameSize = encoder.getFrameSize(); // 320 samples at 16 kHz / 20 ms

        piperNative.synthesize(
                text,
                pcmChunk -> {
                    // Resample if Piper rate differs from Opus rate
                    byte[] resampled =
                            piperSampleRate != OPUS_SAMPLE_RATE
                                    ? resample(pcmChunk, piperSampleRate, OPUS_SAMPLE_RATE)
                                    : pcmChunk;

                    // Encode in frame-sized chunks; pad last frame if needed
                    encodeChunk(resampled, frameSize, streamIdBytes, encoder, eventConsumer);
                });

        long totalMs = System.currentTimeMillis() - startMs;
        log.info("tts.synthesize.complete: elapsed_ms={}", totalMs);
        eventConsumer.accept(new TtsEvent.Completed(totalMs));
    }

    /**
     * Encodes a PCM byte chunk into Opus frames and delivers them as {@link TtsEvent.Chunk} events.
     *
     * @param pcm the raw PCM bytes (16-bit LE mono at OPUS_SAMPLE_RATE)
     * @param frameSize frame size in samples
     * @param streamIdBytes the 4-byte stream ID prefix
     * @param encoder the Opus encoder
     * @param eventConsumer the event sink
     */
    private void encodeChunk(
            byte[] pcm,
            int frameSize,
            byte[] streamIdBytes,
            OpusEncoder encoder,
            Consumer<TtsEvent> eventConsumer) {
        int frameSizeBytes = frameSize * 2; // 2 bytes per sample (16-bit)
        int offset = 0;

        while (offset < pcm.length) {
            int remaining = pcm.length - offset;
            byte[] frame;
            if (remaining >= frameSizeBytes) {
                frame = new byte[frameSizeBytes];
                System.arraycopy(pcm, offset, frame, 0, frameSizeBytes);
                offset += frameSizeBytes;
            } else {
                // Pad the last frame with silence
                frame = new byte[frameSizeBytes];
                System.arraycopy(pcm, offset, frame, 0, remaining);
                offset = pcm.length;
            }
            byte[] opusFrame = encoder.encode(streamIdBytes, frame);
            eventConsumer.accept(new TtsEvent.Chunk(opusFrame, true));
        }
    }

    /**
     * Resamples 16-bit little-endian PCM from {@code fromRate} to {@code toRate} using linear
     * interpolation.
     *
     * @param pcm input PCM bytes (16-bit LE mono)
     * @param fromRate source sample rate in Hz
     * @param toRate target sample rate in Hz
     * @return resampled PCM bytes (16-bit LE mono)
     */
    static byte[] resample(byte[] pcm, int fromRate, int toRate) {
        if (fromRate == toRate) return pcm;

        int numInputSamples = pcm.length / 2;
        int numOutputSamples = (int) Math.ceil((double) numInputSamples * toRate / fromRate);

        short[] inputShorts = new short[numInputSamples];
        for (int i = 0; i < numInputSamples; i++) {
            inputShorts[i] = (short) ((pcm[i * 2] & 0xFF) | (pcm[i * 2 + 1] << 8));
        }

        byte[] output = new byte[numOutputSamples * 2];
        double ratio = (double) fromRate / toRate;
        for (int i = 0; i < numOutputSamples; i++) {
            double srcPos = i * ratio;
            int srcIdx = (int) srcPos;
            double frac = srcPos - srcIdx;
            short s0 = inputShorts[srcIdx];
            short s1 = (srcIdx + 1) < numInputSamples ? inputShorts[srcIdx + 1] : s0;
            short interpolated = (short) (s0 + frac * (s1 - s0));
            output[i * 2] = (byte) (interpolated & 0xFF);
            output[i * 2 + 1] = (byte) ((interpolated >> 8) & 0xFF);
        }
        return output;
    }
}
