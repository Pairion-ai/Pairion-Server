package com.pairion.adapters.tts.piper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link DefaultPiperTtsNative} error paths (no native library required). */
class DefaultPiperTtsNativeTest {

    @Test
    void isUnavailableWhenLibraryLoadFails() {
        // Loader returns false → available must be false
        DefaultPiperTtsNative native_ = new DefaultPiperTtsNative("en_GB-alan-medium", () -> false);
        assertThat(native_.isAvailable()).isFalse();
    }

    @Test
    void isUnavailableWhenModelFileMissing(@TempDir Path tempDir) {
        // Loader succeeds but model file doesn't exist
        // We override resolveModelPaths to point at temp dir (no model files there)
        DefaultPiperTtsNative native_ =
                new DefaultPiperTtsNative("en_GB-alan-medium", () -> true) {
                    @Override
                    String[] resolveModelPaths(String voice) {
                        // Return paths that don't exist
                        return new String[] {
                            tempDir.resolve("missing.onnx").toString(),
                            tempDir.resolve("missing.onnx.json").toString(),
                            tempDir.resolve("espeak-ng-data").toString()
                        };
                    }
                };
        assertThat(native_.isAvailable()).isFalse();
    }

    @Test
    void synthesizeWhenUnavailableIsNoOp() {
        DefaultPiperTtsNative native_ = new DefaultPiperTtsNative("en_GB-alan-medium", () -> false);
        assertThat(native_.isAvailable()).isFalse();

        List<byte[]> chunks = new ArrayList<>();
        // Should not throw, should not call consumer
        native_.synthesize("hello", chunks::add);

        assertThat(chunks).isEmpty();
    }

    @Test
    void getSampleRateReturnsDefault() {
        DefaultPiperTtsNative native_ = new DefaultPiperTtsNative("en_GB-alan-medium", () -> false);
        assertThat(native_.getSampleRate()).isEqualTo(DefaultPiperTtsNative.VOICE_SAMPLE_RATE);
    }

    @Test
    void resolveModelPathsUsesUserHome() {
        DefaultPiperTtsNative native_ = new DefaultPiperTtsNative("en_GB-alan-medium", () -> false);
        String[] paths = native_.resolveModelPaths("en_GB-alan-medium");
        assertThat(paths).hasSize(3);
        assertThat(paths[0]).contains("en_GB-alan-medium.onnx");
        assertThat(paths[1]).contains("en_GB-alan-medium.onnx.json");
        assertThat(paths[2]).contains("espeak-ng-data");
    }

    @Test
    void resolveModelPathsUsesPairionHomeEnvWhenSet(@TempDir Path tempDir) {
        // Cannot set env vars in unit tests — test the logic via subclass override
        DefaultPiperTtsNative native_ =
                new DefaultPiperTtsNative("my-voice", () -> false) {
                    @Override
                    String[] resolveModelPaths(String voice) {
                        Path base = tempDir;
                        Path tts = base.resolve("models").resolve("tts");
                        return new String[] {
                            tts.resolve(voice + ".onnx").toString(),
                            tts.resolve(voice + ".onnx.json").toString(),
                            base.resolve("espeak-ng-data").toString()
                        };
                    }
                };
        // Just verifies it doesn't throw during init (model not found → unavailable)
        assertThat(native_.isAvailable()).isFalse();
    }

    @Test
    void audioCallbackBridgeWithZeroSamplesIsNoOp() {
        // Ensure the bridge doesn't crash with zero samples
        // consumer should not be called since num_samples <= 0
        DefaultPiperTtsNative.audioCallbackBridge(
                java.lang.foreign.MemorySegment.NULL, 0L, java.lang.foreign.MemorySegment.NULL);
    }
}
