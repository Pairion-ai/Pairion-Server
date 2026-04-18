package com.pairion.adapters.stt.whispercpp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests for {@link DefaultWhisperCppNative}. */
class DefaultWhisperCppNativeTest {

    @Test
    void unavailableWhenLibraryMissing() {
        DefaultWhisperCppNative nativeImpl = new DefaultWhisperCppNative();
        assertThat(nativeImpl.isAvailable()).isFalse();
    }

    @Test
    void transcribeReturnsEmptyWhenUnavailable() {
        DefaultWhisperCppNative nativeImpl = new DefaultWhisperCppNative();
        String result = nativeImpl.transcribe(new float[] {0.0f, 0.1f});
        assertThat(result).isEmpty();
    }

    @Test
    void expectedLibraryPathContainsLibName() {
        DefaultWhisperCppNative nativeImpl = new DefaultWhisperCppNative();
        assertThat(nativeImpl.expectedLibraryPath()).contains("libwhisper");
    }

    @Test
    void expectedModelPathContainsModelName() {
        DefaultWhisperCppNative nativeImpl = new DefaultWhisperCppNative();
        assertThat(nativeImpl.expectedModelPath()).contains("ggml-small.en.bin");
    }

    @Test
    void modelSha256Constant() {
        assertThat(DefaultWhisperCppNative.MODEL_SHA256).hasSize(64);
    }

    @Test
    void modelUrlConstant() {
        assertThat(DefaultWhisperCppNative.MODEL_URL).contains("huggingface.co");
    }
}
