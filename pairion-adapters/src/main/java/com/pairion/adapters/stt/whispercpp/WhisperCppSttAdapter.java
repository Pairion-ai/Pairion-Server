package com.pairion.adapters.stt.whispercpp;

import com.pairion.adapters.stt.spi.SttAdapter;
import com.pairion.core.stt.SttCapabilities;
import com.pairion.core.stt.SttEvent;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * STT adapter backed by whisper.cpp via Java 21 FFM API.
 *
 * <p>Activated when {@code pairion.adapters.stt=whispercpp} (the default). If the native library or
 * model is absent, the adapter reports itself as unavailable with a clear log message indicating
 * install instructions.
 */
@Component
@ConditionalOnProperty(
        name = "pairion.adapters.stt",
        havingValue = "whispercpp",
        matchIfMissing = true)
public class WhisperCppSttAdapter implements SttAdapter {

    private static final Logger log = LoggerFactory.getLogger(WhisperCppSttAdapter.class);

    /** Minimum milliseconds between partial transcript emissions. */
    static final long PARTIAL_THROTTLE_MS = 200;

    private final WhisperCppNative nativeImpl;

    /**
     * Constructs the adapter with the given native implementation.
     *
     * @param nativeImpl the whisper.cpp native abstraction
     */
    public WhisperCppSttAdapter(WhisperCppNative nativeImpl) {
        this.nativeImpl = nativeImpl;
        if (!nativeImpl.isAvailable()) {
            log.error(
                    "whisper.cpp native library not found at {}. Install whisper.cpp and place the"
                            + " shared library at the expected path. STT adapter is unavailable.",
                    nativeImpl.expectedLibraryPath());
            log.error(
                    "whisper.cpp model not found at {}. The model will be downloaded on first"
                            + " successful run.",
                    nativeImpl.expectedModelPath());
        } else {
            log.info("whisper.cpp STT adapter initialized");
        }
    }

    /**
     * Returns the adapter name.
     *
     * @return "whispercpp"
     */
    @Override
    public String name() {
        return "whispercpp";
    }

    /**
     * Returns current capabilities.
     *
     * @return capabilities reflecting native library availability
     */
    @Override
    public SttCapabilities capabilities() {
        return nativeImpl.isAvailable()
                ? new SttCapabilities(true, true)
                : SttCapabilities.unavailable();
    }

    /**
     * Creates a streaming transcription session.
     *
     * @param eventConsumer callback receiving partial and final transcript events
     * @return the session handle
     */
    @Override
    public SttSession createSession(Consumer<SttEvent> eventConsumer) {
        return new WhisperSttSession(nativeImpl, eventConsumer);
    }

    /**
     * Streaming transcription session backed by whisper.cpp.
     *
     * <p>Accumulates PCM audio data and periodically emits partial transcripts. On finalization,
     * runs a full transcription pass and emits the final transcript.
     */
    static class WhisperSttSession implements SttSession {

        private static final Logger log = LoggerFactory.getLogger(WhisperSttSession.class);

        private final WhisperCppNative nativeImpl;
        private final Consumer<SttEvent> eventConsumer;
        private final List<byte[]> pcmChunks = new ArrayList<>();
        private final long startTimeMs;
        private long lastPartialMs;
        private int totalBytes;

        WhisperSttSession(WhisperCppNative nativeImpl, Consumer<SttEvent> eventConsumer) {
            this.nativeImpl = nativeImpl;
            this.eventConsumer = eventConsumer;
            this.startTimeMs = System.currentTimeMillis();
            this.lastPartialMs = startTimeMs;
        }

        /**
         * Feeds PCM audio data and optionally emits a partial transcript.
         *
         * @param pcmData 16 kHz mono signed 16-bit LE PCM
         */
        @Override
        public void feedAudio(byte[] pcmData) {
            pcmChunks.add(pcmData);
            totalBytes += pcmData.length;

            long now = System.currentTimeMillis();
            if (now - lastPartialMs >= PARTIAL_THROTTLE_MS && nativeImpl.isAvailable()) {
                float[] samples = pcmToFloat(combineChunks());
                String partial = nativeImpl.transcribe(samples);
                if (!partial.isBlank()) {
                    eventConsumer.accept(new SttEvent.Partial(partial));
                    long elapsedMs = now - startTimeMs;
                    log.debug("stt.partial: text='{}', elapsed_ms={}", partial, elapsedMs);
                }
                lastPartialMs = now;
            }
        }

        /** Finalizes the stream and emits the final transcript. */
        @Override
        public void finalizeStream() {
            String sessionId = MDC.get("sessionId");
            long finalMs = System.currentTimeMillis() - startTimeMs;
            long audioDurationMs = (totalBytes / 2) * 1000L / 16000;

            if (nativeImpl.isAvailable()) {
                float[] samples = pcmToFloat(combineChunks());
                String transcript = nativeImpl.transcribe(samples);
                log.info(
                        "stt.final_ms={}, stt.total_audio_ms={}, sessionId={}",
                        finalMs,
                        audioDurationMs,
                        sessionId);
                eventConsumer.accept(new SttEvent.Final(transcript, audioDurationMs));
            } else {
                log.warn("STT unavailable — emitting empty transcript");
                eventConsumer.accept(new SttEvent.Final("", audioDurationMs));
            }
        }

        private byte[] combineChunks() {
            byte[] combined = new byte[totalBytes];
            int offset = 0;
            for (byte[] chunk : pcmChunks) {
                System.arraycopy(chunk, 0, combined, offset, chunk.length);
                offset += chunk.length;
            }
            return combined;
        }

        /**
         * Converts 16-bit signed little-endian PCM to float32 samples normalized to [-1, 1].
         *
         * @param pcm the PCM byte data
         * @return float32 samples
         */
        static float[] pcmToFloat(byte[] pcm) {
            int sampleCount = pcm.length / 2;
            float[] samples = new float[sampleCount];
            ByteBuffer buf = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < sampleCount; i++) {
                samples[i] = buf.getShort() / 32768.0f;
            }
            return samples;
        }
    }
}
