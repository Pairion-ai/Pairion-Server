package com.pairion.adapters.stt.whispercpp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DefaultWhisperCppNative} constants and contract.
 *
 * <p>Does NOT instantiate DefaultWhisperCppNative directly — construction loads the bundled
 * whisper.cpp native library which initializes Metal GPU context. The GGML Metal cleanup triggers
 * an assertion failure on JVM exit (known upstream issue ggml-org/whisper.cpp) that crashes the
 * surefire forked JVM. Full instantiation is tested via PAIRION_NATIVE_TESTS=1.
 */
class DefaultWhisperCppNativeTest {

    @Test
    void modelSha256HasCorrectLength() {
        assertThat(DefaultWhisperCppNative.MODEL_SHA256).hasSize(64);
    }

    @Test
    void modelUrlPointsToHuggingFace() {
        assertThat(DefaultWhisperCppNative.MODEL_URL).contains("huggingface.co");
    }

    @Test
    void modelSha256MatchesVerifiedValue() {
        assertThat(DefaultWhisperCppNative.MODEL_SHA256)
                .isEqualTo("c6138d6d58ecc8322097e0f987c32f1be8bb0a18532a3f88f734d1bbf9c41e5d");
    }
}
