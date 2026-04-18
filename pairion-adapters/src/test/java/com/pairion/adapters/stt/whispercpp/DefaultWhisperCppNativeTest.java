package com.pairion.adapters.stt.whispercpp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests for {@link DefaultWhisperCppNative}. */
class DefaultWhisperCppNativeTest {

    @Test
    void unavailableWhenLibraryMissing() {
        DefaultWhisperCppNative native_ = new DefaultWhisperCppNative();
        assertThat(native_.isAvailable()).isFalse();
    }

    @Test
    void transcribeReturnsEmptyWhenUnavailable() {
        DefaultWhisperCppNative native_ = new DefaultWhisperCppNative();
        String result = native_.transcribe(new float[] {0.0f, 0.1f});
        assertThat(result).isEmpty();
    }

    @Test
    void expectedLibraryPathNotNull() {
        DefaultWhisperCppNative native_ = new DefaultWhisperCppNative();
        assertThat(native_.expectedLibraryPath()).isNotEmpty();
    }

    @Test
    void expectedModelPathNotNull() {
        DefaultWhisperCppNative native_ = new DefaultWhisperCppNative();
        assertThat(native_.expectedModelPath()).isNotEmpty();
    }

    @Test
    void modelSha256Constant() {
        assertThat(DefaultWhisperCppNative.MODEL_SHA256).isNotEmpty();
    }
}
