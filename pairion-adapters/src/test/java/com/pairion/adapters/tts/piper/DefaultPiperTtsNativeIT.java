package com.pairion.adapters.tts.piper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.pairion.core.util.ModelDownloader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Integration test for {@link DefaultPiperTtsNative} end-to-end synthesis.
 *
 * <p>Verifies that the bundled {@code libpiper.dylib} + voice model produce non-trivial PCM output
 * with an RMS amplitude above the minimum audibility threshold.
 *
 * <p>Prerequisites:
 *
 * <ul>
 *   <li>{@code PAIRION_NATIVE_TESTS=1} — enables native integration tests
 *   <li>{@code PAIRION_HOME} — directory where model files are downloaded (e.g.
 *       {@code /tmp/pairion-test})
 * </ul>
 *
 * <p>Run: {@code PAIRION_HOME=/tmp/pairion-test PAIRION_NATIVE_TESTS=1 mvn verify -pl
 * pairion-adapters -Dtest=DefaultPiperTtsNativeIT}
 */
class DefaultPiperTtsNativeIT {

    private static final String VOICE = "en_GB-alan-medium";
    private static final String TEST_TEXT = "Hello from Pairion. Speech synthesis is working.";

    /** Minimum RMS amplitude for synthesized speech to be considered audible. */
    private static final double MIN_RMS = 100.0;

    @BeforeAll
    static void checkPrerequisites() {
        assumeTrue(
                System.getenv("PAIRION_NATIVE_TESTS") != null,
                "Skipping Piper IT — set PAIRION_NATIVE_TESTS=1 to enable");

        String pairionHome = System.getenv("PAIRION_HOME");
        assumeTrue(
                pairionHome != null && !pairionHome.isBlank(),
                "Skipping Piper IT — set PAIRION_HOME=/tmp/pairion-test");

        ensureModelsDownloaded(pairionHome);
    }

    /**
     * Verifies that synthesis produces audio chunks with non-trivial PCM content and RMS above
     * the minimum audibility threshold.
     */
    @Test
    void synthesizesSpeechWithAudibleOutput() {
        DefaultPiperTtsNative nativeImpl = new DefaultPiperTtsNative(VOICE);
        assumeTrue(nativeImpl.isAvailable(), "Piper TTS not available — skipping");

        List<byte[]> chunks = new ArrayList<>();
        nativeImpl.synthesize(TEST_TEXT, chunks::add);

        assertThat(chunks).as("Expected at least one PCM audio chunk").isNotEmpty();

        long totalBytes = chunks.stream().mapToLong(c -> c.length).sum();
        assertThat(totalBytes).as("PCM output must be non-trivial (> 1000 bytes)").isGreaterThan(1000L);

        double rms = computeRms(chunks);
        assertThat(rms)
                .as(
                        "RMS amplitude %.1f is below minimum audibility threshold %.1f",
                        rms, MIN_RMS)
                .isGreaterThan(MIN_RMS);
    }

    /**
     * Downloads model files to {@code PAIRION_HOME} if not already present.
     *
     * @param pairionHome the PAIRION_HOME directory path
     */
    private static void ensureModelsDownloaded(String pairionHome) {
        Path ttsDir = Path.of(pairionHome).resolve("models").resolve("tts");

        Path onnxPath = ttsDir.resolve(VOICE + ".onnx");
        Path configPath = ttsDir.resolve(VOICE + ".onnx.json");

        if (Files.exists(onnxPath) && Files.exists(configPath)) {
            return;
        }

        ModelDownloader downloader = new ModelDownloader();
        String base = DefaultPiperTtsNative.MODEL_BASE_URL;

        boolean onnxOk =
                downloader.download(
                        base + VOICE + ".onnx", onnxPath, DefaultPiperTtsNative.MODEL_ONNX_SHA256);
        boolean configOk =
                downloader.download(
                        base + VOICE + ".onnx.json",
                        configPath,
                        DefaultPiperTtsNative.MODEL_CONFIG_SHA256);

        assumeTrue(
                onnxOk && configOk,
                "Could not download Piper model files to " + ttsDir + " — skipping IT");
    }

    /**
     * Computes the RMS amplitude of 16-bit little-endian PCM samples across all chunks.
     *
     * @param chunks the PCM byte chunks to analyze
     * @return the root-mean-square amplitude
     */
    private static double computeRms(List<byte[]> chunks) {
        double sumSq = 0.0;
        long totalSamples = 0;

        for (byte[] chunk : chunks) {
            for (int i = 0; i + 1 < chunk.length; i += 2) {
                short sample = (short) ((chunk[i] & 0xFF) | ((chunk[i + 1] & 0xFF) << 8));
                sumSq += (double) sample * sample;
                totalSamples++;
            }
        }

        return totalSamples > 0 ? Math.sqrt(sumSq / totalSamples) : 0.0;
    }
}
