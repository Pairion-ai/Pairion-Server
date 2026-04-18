package com.pairion.adapters.stt.whispercpp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.pairion.nativelib.whisper.NativeLibraryLoader;
import java.lang.foreign.MemorySegment;
import java.net.URL;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Tests for {@link DefaultWhisperCppNative}.
 *
 * <p>Happy-path constructor tests exercise the no-arg public constructor with the bundled classpath
 * library. Error-path tests inject model paths and library loaders via the package-private
 * constructor, covering all three failure modes without requiring {@code PAIRION_NATIVE_TESTS=1}.
 *
 * <p>Three additional tests use anonymous subclasses to override package-private methods ({@code
 * resolveModelPath}, {@code initContext}, {@code callWhisperFull}) so that the {@code pairionHome
 * != null} branch, the constructor catch block, and the {@code whisper_full} non-zero return branch
 * can be exercised in every {@code mvn verify} run.
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

    // ── loadBundledLibrary branch ─────────────────────────────────────────────

    /**
     * Exercises the {@code if (loaded)} false branch in {@link
     * DefaultWhisperCppNative#loadBundledLibrary()} by mocking {@link NativeLibraryLoader#load()}
     * to return {@code false}. The symbol-lookup step must be skipped in this case.
     */
    @Test
    void loadBundledLibraryReturnsFalseWhenNativeLibraryFailsToLoad() {
        try (MockedStatic<NativeLibraryLoader> mocked =
                Mockito.mockStatic(NativeLibraryLoader.class)) {
            mocked.when(NativeLibraryLoader::load).thenReturn(false);
            assertThat(DefaultWhisperCppNative.loadBundledLibrary()).isFalse();
        }
    }

    // ── resolveModelPath branch ───────────────────────────────────────────────

    /**
     * Exercises the {@code pairionHome != null} branch of {@link
     * DefaultWhisperCppNative#resolveModelPath(String)}, which is unreachable via the public
     * constructor in a test environment where {@code PAIRION_HOME} is not set.
     */
    @Test
    void resolveModelPathUsesPairionHomeWhenSet() {
        Path result = DefaultWhisperCppNative.resolveModelPath("/custom/pairion");
        assertThat(result.toString()).startsWith("/custom/pairion");
        assertThat(result.toString()).endsWith("ggml-small.en.bin");
    }

    @Test
    void resolveModelPathDefaultsToUserHomeWhenPairionHomeNull() {
        Path result = DefaultWhisperCppNative.resolveModelPath(null);
        assertThat(result.toString()).contains(".pairion");
        assertThat(result.toString()).endsWith("ggml-small.en.bin");
    }

    // ── Error-path tests ──────────────────────────────────────────────────────
    // These use the package-private constructor to inject failure conditions.
    // They run in every mvn verify invocation — no PAIRION_NATIVE_TESTS gate.

    /**
     * When the library loader returns {@code false} (simulating an {@code UnsatisfiedLinkError} at
     * load time), the adapter must report unavailable and return an empty transcript without
     * throwing.
     */
    @Test
    void libraryLoadFailureReportsUnavailable() {
        DefaultWhisperCppNative nativeImpl =
                new DefaultWhisperCppNative(Path.of("/any/path/ggml-small.en.bin"), () -> false);
        assertThat(nativeImpl.isAvailable()).isFalse();
        assertThat(nativeImpl.transcribe(new float[100])).isEmpty();
        nativeImpl.freeContext();
    }

    /**
     * When the model file does not exist on disk, the adapter must report unavailable and return an
     * empty transcript without throwing.
     */
    @Test
    void modelFileMissingReportsUnavailable() {
        DefaultWhisperCppNative nativeImpl =
                new DefaultWhisperCppNative(
                        Path.of("/nonexistent/path/ggml-small.en.bin"),
                        DefaultWhisperCppNative::loadBundledLibrary);
        assertThat(nativeImpl.isAvailable()).isFalse();
        assertThat(nativeImpl.transcribe(new float[100])).isEmpty();
        nativeImpl.freeContext();
    }

    /**
     * When the model file exists but is corrupted (1 KB of zeros), {@code
     * whisper_init_from_file_with_params} returns NULL and the adapter must handle the failure
     * cleanly, reporting unavailable without throwing.
     */
    @Test
    void contextInitFailureHandledCleanly() throws Exception {
        URL fixture = getClass().getClassLoader().getResource("fixtures/corrupted-model.bin");
        assertThat(fixture).as("corrupted-model.bin fixture must be on classpath").isNotNull();
        Path corruptedModel = Path.of(fixture.toURI());

        DefaultWhisperCppNative nativeImpl =
                new DefaultWhisperCppNative(
                        corruptedModel, DefaultWhisperCppNative::loadBundledLibrary);
        assertThat(nativeImpl.isAvailable()).isFalse();
        assertThat(nativeImpl.transcribe(new float[100])).isEmpty();
        nativeImpl.freeContext();
    }

    /**
     * Exercises the catch block in the constructor's context-init call path by overriding {@link
     * DefaultWhisperCppNative#initContext(String)} to throw a {@link RuntimeException}. This
     * simulates a scenario where an unexpected exception propagates out of the FFM call, which
     * cannot occur with the real whisper.cpp implementation (which returns NULL rather than
     * throwing) but must be handled gracefully.
     */
    @Test
    void contextInitExceptionHandledCleanly() throws Exception {
        URL fixture = getClass().getClassLoader().getResource("fixtures/corrupted-model.bin");
        assertThat(fixture).as("corrupted-model.bin fixture must be on classpath").isNotNull();
        Path corruptedModel = Path.of(fixture.toURI());

        DefaultWhisperCppNative nativeImpl =
                new DefaultWhisperCppNative(
                        corruptedModel, DefaultWhisperCppNative::loadBundledLibrary) {
                    @Override
                    MemorySegment initContext(String modelPath) {
                        throw new RuntimeException("simulated FFM exception for test");
                    }
                };
        assertThat(nativeImpl.isAvailable()).isFalse();
        assertThat(nativeImpl.transcribe(new float[100])).isEmpty();
        nativeImpl.freeContext();
    }

    /**
     * Exercises the exception-path branch of the {@code try (Arena transcribeArena = ...)} block in
     * {@link DefaultWhisperCppNative#transcribe(float[])} by overriding {@link
     * DefaultWhisperCppNative#callWhisperFull(MemorySegment, MemorySegment, MemorySegment, int)} to
     * throw a {@link RuntimeException}. The try-with-resources block must close the arena and then
     * let the exception propagate. Requires the bundled native library to be loadable so that the
     * parameter-setup FFM calls before {@code callWhisperFull} succeed.
     */
    @Test
    void transcribeExceptionFromWhisperFullPropagates() {
        assumeTrue(
                DefaultWhisperCppNative.loadBundledLibrary(),
                "Native library not loadable — skipping try-with-resources exception path test");

        DefaultWhisperCppNative nativeImpl =
                new DefaultWhisperCppNative(
                        Path.of("/nonexistent/ggml-small.en.bin"), () -> false) {
                    @Override
                    public boolean isAvailable() {
                        return true;
                    }

                    @Override
                    int callWhisperFull(
                            MemorySegment ctx,
                            MemorySegment params,
                            MemorySegment samples,
                            int nSamples) {
                        throw new RuntimeException("simulated whisper_full exception");
                    }
                };
        assertThatThrownBy(() -> nativeImpl.transcribe(new float[100]))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated whisper_full exception");
        nativeImpl.freeContext();
    }

    /**
     * Exercises the {@code if (result != 0)} branch in {@link
     * DefaultWhisperCppNative#transcribe(float[])} by overriding {@link
     * DefaultWhisperCppNative#callWhisperFull(MemorySegment, MemorySegment, MemorySegment, int)} to
     * return a non-zero error code. The test also overrides {@link
     * DefaultWhisperCppNative#isAvailable()} to return {@code true} so that the transcription path
     * is entered. Requires the bundled native library to be loadable (always true in {@code mvn
     * verify} since {@code pairion-native-whisper} builds the library before this module).
     */
    @Test
    void whisperFullErrorCodeReturnsEmptyTranscript() {
        assumeTrue(
                DefaultWhisperCppNative.loadBundledLibrary(),
                "Native library not loadable — skipping whisper_full error path test");

        DefaultWhisperCppNative nativeImpl =
                new DefaultWhisperCppNative(
                        Path.of("/nonexistent/ggml-small.en.bin"), () -> false) {
                    @Override
                    public boolean isAvailable() {
                        return true;
                    }

                    @Override
                    int callWhisperFull(
                            MemorySegment ctx,
                            MemorySegment params,
                            MemorySegment samples,
                            int nSamples) {
                        return 1; // non-zero error code
                    }
                };
        assertThat(nativeImpl.transcribe(new float[100])).isEmpty();
        nativeImpl.freeContext();
    }
}
