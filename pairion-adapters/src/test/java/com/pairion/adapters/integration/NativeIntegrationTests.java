package com.pairion.adapters.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.pairion.adapters.audio.opus.ConcentusOpusDecoder;
import com.pairion.adapters.audio.opus.OpusDecoder;
import com.pairion.adapters.stt.whispercpp.DefaultWhisperCppNative;
import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusEncoder;
import io.github.jaredmdobson.concentus.OpusException;
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

        assertThat(response.toString()).isNotEmpty();
    }
}
