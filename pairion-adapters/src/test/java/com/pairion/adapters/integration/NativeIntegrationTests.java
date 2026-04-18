package com.pairion.adapters.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.pairion.adapters.audio.opus.ConcentusOpusDecoder;
import com.pairion.adapters.audio.opus.OpusDecoder;
import com.pairion.adapters.stt.whispercpp.DefaultWhisperCppNative;
import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusEncoder;
import io.github.jaredmdobson.concentus.OpusException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Integration tests that exercise real native dependencies. Skipped cleanly when the {@code
 * PAIRION_NATIVE_TESTS} environment variable is not set.
 *
 * <p>To run: {@code PAIRION_NATIVE_TESTS=1 mvn test -pl pairion-adapters
 * -Dtest=NativeIntegrationTests}
 */
class NativeIntegrationTests {

    /**
     * Expected transcript for the JFK fixture (case-insensitive Levenshtein distance ≤ 5 required).
     */
    private static final String JFK_EXPECTED =
            "And so, my fellow Americans: ask not what your country can do for you"
                    + " — ask what you can do for your country.";

    @BeforeAll
    static void checkNativeTestsEnabled() {
        assumeTrue(
                System.getenv("PAIRION_NATIVE_TESTS") != null,
                "Skipping native integration tests — set PAIRION_NATIVE_TESTS=1 to enable");
    }

    @Test
    void opusRoundTripProducesRealPcm() throws OpusException {
        // Encode silence to Opus
        OpusEncoder encoder = new OpusEncoder(16000, 1, OpusApplication.OPUS_APPLICATION_VOIP);
        short[] silence = new short[OpusDecoder.FRAME_SIZE];
        byte[] opusFrame = new byte[1024];
        int encodedLen =
                encoder.encode(silence, 0, OpusDecoder.FRAME_SIZE, opusFrame, 0, opusFrame.length);
        byte[] trimmed = new byte[encodedLen];
        System.arraycopy(opusFrame, 0, trimmed, 0, encodedLen);

        // Decode back to PCM
        ConcentusOpusDecoder decoder = new ConcentusOpusDecoder();
        byte[] pcm = decoder.decodeFrame(trimmed);

        assertThat(pcm).hasSize(OpusDecoder.FRAME_SIZE * 2);
    }

    @Test
    void whisperCppTranscribesWhenAvailable() {
        DefaultWhisperCppNative nativeImpl = new DefaultWhisperCppNative();
        assumeTrue(nativeImpl.isAvailable(), "whisper.cpp not installed — skipping");

        // Generate 2 seconds of silence (16 kHz)
        float[] silence = new float[32000];
        String transcript = nativeImpl.transcribe(silence);
        assertThat(transcript).isNotNull();
    }

    @Test
    void anthropicStreamingWhenApiKeySet() {
        assumeTrue(
                System.getenv("ANTHROPIC_API_KEY") != null,
                "ANTHROPIC_API_KEY not set — skipping Anthropic integration test");

        com.pairion.adapters.llm.anthropic.DefaultAnthropicClientWrapper wrapper =
                new com.pairion.adapters.llm.anthropic.DefaultAnthropicClientWrapper();
        assertThat(wrapper.isAvailable()).isTrue();

        StringBuilder response = new StringBuilder();
        long[] timing = {0};
        try {
            wrapper.streamCompletion(
                    "claude-sonnet-4-6-20250514",
                    "Respond with exactly the word: pairion",
                    "Say pairion",
                    java.util.List.of(),
                    new com.pairion.adapters.llm.anthropic.AnthropicClientWrapper.StreamCallback() {
                        @Override
                        public void onToken(String delta) {
                            response.append(delta);
                        }

                        @Override
                        public void onComplete() {
                            timing[0] = System.currentTimeMillis();
                        }

                        @Override
                        public void onError(Exception e) {
                            throw new RuntimeException("Anthropic API error", e);
                        }
                    });
        } catch (RuntimeException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            String cause = e.getCause() != null ? e.getCause().getMessage() : "";
            Assumptions.assumeTrue(
                    !msg.contains("credit balance") && !cause.contains("credit balance"),
                    "Anthropic account credit balance too low — skipping: " + cause);
            throw e;
        }

        assertThat(response.toString()).isNotEmpty();
    }

    /**
     * Verifies end-to-end transcription correctness using the public-domain JFK fixture WAV.
     *
     * <p>Loads {@code src/test/resources/fixtures/jfk.wav} (16 kHz mono 16-bit PCM), feeds the
     * samples to the real whisper.cpp context, and asserts the returned transcript is within
     * Levenshtein distance 5 of the expected text (case-insensitive).
     */
    @Test
    void transcribesJfkFixture() throws Exception {
        DefaultWhisperCppNative nativeImpl = new DefaultWhisperCppNative();
        assumeTrue(
                nativeImpl.isAvailable(),
                "whisper.cpp model not installed — skipping JFK fixture test");

        float[] samples = loadJfkWav();
        String transcript = nativeImpl.transcribe(samples);

        int distance = levenshtein(transcript.toLowerCase(), JFK_EXPECTED.toLowerCase());
        assertThat(distance)
                .as("Transcript Levenshtein distance %d > 5. Actual: [%s]", distance, transcript)
                .isLessThanOrEqualTo(5);
    }

    // ── WAV loader ────────────────────────────────────────────────────────────

    /**
     * Loads jfk.wav from the test classpath and returns 16 kHz mono float32 samples.
     *
     * <p>Parses the RIFF/WAVE header to locate the {@code data} chunk, then converts 16-bit signed
     * little-endian PCM to float32 by dividing by 32768.0f.
     */
    private static float[] loadJfkWav() throws Exception {
        URL resource =
                NativeIntegrationTests.class.getClassLoader().getResource("fixtures/jfk.wav");
        assertThat(resource).as("fixtures/jfk.wav must be on classpath").isNotNull();

        try (InputStream in = resource.openStream()) {
            byte[] allBytes = in.readAllBytes();
            ByteBuffer buf = ByteBuffer.wrap(allBytes).order(ByteOrder.LITTLE_ENDIAN);

            // Validate RIFF header
            byte[] riff = new byte[4];
            buf.get(riff);
            assertThat(new String(riff, StandardCharsets.US_ASCII))
                    .as("Expected RIFF header")
                    .isEqualTo("RIFF");
            buf.getInt(); // file size - 8, skip
            byte[] wave = new byte[4];
            buf.get(wave);
            assertThat(new String(wave, StandardCharsets.US_ASCII))
                    .as("Expected WAVE marker")
                    .isEqualTo("WAVE");

            // Scan chunks to find "data"
            int dataOffset = -1;
            int dataSize = -1;
            while (buf.remaining() >= 8) {
                byte[] chunkId = new byte[4];
                buf.get(chunkId);
                int chunkSize = buf.getInt();
                String id = new String(chunkId, StandardCharsets.US_ASCII);
                if ("data".equals(id)) {
                    dataOffset = buf.position();
                    dataSize = chunkSize;
                    break;
                }
                // Skip this chunk (pad to even boundary per RIFF spec)
                int skip = chunkSize + (chunkSize % 2);
                buf.position(buf.position() + skip);
            }

            assertThat(dataOffset).as("WAV data chunk not found").isGreaterThan(0);

            // Convert 16-bit signed PCM → float32
            int numSamples = dataSize / 2;
            float[] samples = new float[numSamples];
            buf.position(dataOffset);
            for (int i = 0; i < numSamples; i++) {
                samples[i] = buf.getShort() / 32768.0f;
            }
            return samples;
        }
    }

    // ── Levenshtein distance ──────────────────────────────────────────────────

    /**
     * Computes the Levenshtein edit distance between two strings.
     *
     * @param a first string
     * @param b second string
     * @return the minimum number of single-character edits to transform {@code a} into {@code b}
     */
    private static int levenshtein(String a, String b) {
        int m = a.length();
        int n = b.length();
        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];
        for (int j = 0; j <= n; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            for (int j = 1; j <= n; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    curr[j] = prev[j - 1];
                } else {
                    curr[j] = 1 + Math.min(prev[j - 1], Math.min(prev[j], curr[j - 1]));
                }
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[n];
    }
}
