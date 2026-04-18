package com.pairion.adapters.stt.whispercpp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DefaultWhisperCppNative} with bundled classpath library.
 *
 * <p>The bundled whisper.cpp library loads from classpath during construction. Model file presence
 * determines full availability. The shutdown hook ensures clean Metal cleanup on JVM exit.
 */
class DefaultWhisperCppNativeTest {

    @Test
    void constructorLoadsLibraryAndChecksModel() {
        DefaultWhisperCppNative nativeImpl = new DefaultWhisperCppNative();
        assertThat(nativeImpl).isNotNull();
        nativeImpl.freeContext();
    }

    @Test
    void expectedLibraryPathDescribesClasspath() {
        DefaultWhisperCppNative nativeImpl = new DefaultWhisperCppNative();
        assertThat(nativeImpl.expectedLibraryPath()).contains("bundled");
        nativeImpl.freeContext();
    }

    @Test
    void expectedModelPathContainsModelName() {
        DefaultWhisperCppNative nativeImpl = new DefaultWhisperCppNative();
        assertThat(nativeImpl.expectedModelPath()).contains("ggml-small.en.bin");
        nativeImpl.freeContext();
    }

    @Test
    void modelSha256HasCorrectLength() {
        assertThat(DefaultWhisperCppNative.MODEL_SHA256).hasSize(64);
    }

    @Test
    void modelUrlPointsToHuggingFace() {
        assertThat(DefaultWhisperCppNative.MODEL_URL).contains("huggingface.co");
    }

    @Test
    void isAvailableReflectsModelPresence() {
        DefaultWhisperCppNative nativeImpl = new DefaultWhisperCppNative();
        boolean available = nativeImpl.isAvailable();
        if (available) {
            assertThat(nativeImpl.transcribe(new float[16000])).isNotNull();
        } else {
            assertThat(nativeImpl.transcribe(new float[16000])).isEmpty();
        }
        nativeImpl.freeContext();
    }

    @Test
    void freeContextIdempotent() {
        DefaultWhisperCppNative nativeImpl = new DefaultWhisperCppNative();
        nativeImpl.freeContext();
        nativeImpl.freeContext();
    }
}
