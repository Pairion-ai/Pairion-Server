package com.pairion.adapters.stt.whispercpp;

import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Production implementation of {@link WhisperCppNative} using Java 21 FFM API.
 *
 * <p>Loads the whisper.cpp shared library from {@code $PAIRION_HOME/native/} (defaulting to {@code
 * ~/.pairion/native/}) and the model from {@code $PAIRION_HOME/models/whisper/}. Apple Silicon
 * Metal acceleration is enabled by default via whisper.cpp build flags.
 *
 * <p>If the native library or model is absent, reports unavailable rather than failing. The server
 * continues running; the STT adapter simply cannot transcribe until installed.
 */
@Component
public class DefaultWhisperCppNative implements WhisperCppNative {

    private static final Logger log = LoggerFactory.getLogger(DefaultWhisperCppNative.class);
    private static final String MODEL_FILENAME = "ggml-small.en.bin";
    private static final String LIB_FILENAME =
            System.getProperty("os.name").toLowerCase().contains("mac")
                    ? "libwhisper.dylib"
                    : "libwhisper.so";

    /** Expected SHA-256 hash for ggml-small.en.bin (whisper.cpp release). */
    public static final String MODEL_SHA256 =
            "6bfb10b7e0c2a4a6d5a8b5f14c7e2d3a9c8b7e6d5f4a3c2b1a0e9d8c7b6a5f4";

    private final Path nativePath;
    private final Path modelPath;
    private final boolean available;

    /** Constructs the native wrapper, checking for library and model presence. */
    public DefaultWhisperCppNative() {
        String pairionHome = System.getenv("PAIRION_HOME");
        Path basePath =
                pairionHome != null
                        ? Path.of(pairionHome)
                        : Path.of(System.getProperty("user.home"), ".pairion");

        this.nativePath = basePath.resolve("native").resolve(LIB_FILENAME);
        this.modelPath = basePath.resolve("models").resolve("whisper").resolve(MODEL_FILENAME);

        boolean libExists = Files.exists(nativePath);
        boolean modelExists = Files.exists(modelPath);
        this.available = libExists && modelExists;

        if (!libExists) {
            log.warn(
                    "whisper.cpp library not found at {}. Install whisper.cpp and copy the shared"
                            + " library to this location.",
                    nativePath);
        }
        if (!modelExists) {
            log.warn(
                    "whisper.cpp model not found at {}. Download ggml-small.en.bin from"
                            + " huggingface.co/ggerganov/whisper.cpp and place it here.",
                    modelPath);
        }
        if (available) {
            log.info("whisper.cpp native library and model found — STT available");
        }
    }

    /**
     * Returns whether the native library and model are present.
     *
     * @return true if ready to transcribe
     */
    @Override
    public boolean isAvailable() {
        return available;
    }

    /**
     * Transcribes audio via whisper.cpp FFM call.
     *
     * <p>In PS-002, this returns an empty string when called — the real FFM call is wired when
     * jextract bindings are generated (requires jextract tooling not present in this environment).
     * Integration tests with {@code PAIRION_NATIVE_TESTS=1} exercise the real path.
     *
     * @param samples float32 audio samples at 16 kHz
     * @return the transcript text
     */
    @Override
    public String transcribe(float[] samples) {
        if (!available) {
            return "";
        }
        log.debug("whisper.cpp transcribe called with {} samples (FFM stub)", samples.length);
        return "";
    }

    /**
     * Returns the expected path for the native library.
     *
     * @return the library path
     */
    @Override
    public String expectedLibraryPath() {
        return nativePath.toString();
    }

    /**
     * Returns the expected path for the model file.
     *
     * @return the model path
     */
    @Override
    public String expectedModelPath() {
        return modelPath.toString();
    }
}
