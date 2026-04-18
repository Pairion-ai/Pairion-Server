package com.pairion.adapters.stt.whispercpp;

import com.pairion.adapters.stt.whispercpp.ffm.WhisperCpp;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Production implementation of {@link WhisperCppNative} using Java 21 FFM API.
 *
 * <p>Loads the whisper.cpp native library from the classpath (bundled by the {@code
 * pairion-native-whisper} module) and the model from {@code $PAIRION_HOME/models/whisper/}. Apple
 * Silicon Metal acceleration is enabled by default via the build flags in the native module.
 *
 * <p>If the bundled library or model is absent, reports unavailable rather than failing. The server
 * continues running; the STT adapter simply cannot transcribe until the model is downloaded.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "pairion.stt.native.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DefaultWhisperCppNative implements WhisperCppNative {

    private static final Logger log = LoggerFactory.getLogger(DefaultWhisperCppNative.class);
    private static final String MODEL_FILENAME = "ggml-small.en.bin";

    /** Expected SHA-256 hash for ggml-small.en.bin (current HuggingFace version). */
    public static final String MODEL_SHA256 =
            "c6138d6d58ecc8322097e0f987c32f1be8bb0a18532a3f88f734d1bbf9c41e5d";

    /** Download URL for the whisper model. */
    public static final String MODEL_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en.bin";

    private final Path modelPath;
    private final boolean libraryLoaded;
    private MemorySegment whisperContext;
    private final Arena arena;

    /** Constructs the native wrapper, loading the bundled library and initializing the context. */
    public DefaultWhisperCppNative() {
        String pairionHome = System.getenv("PAIRION_HOME");
        Path basePath =
                pairionHome != null
                        ? Path.of(pairionHome)
                        : Path.of(System.getProperty("user.home"), ".pairion");

        this.modelPath = basePath.resolve("models").resolve("whisper").resolve(MODEL_FILENAME);
        this.arena = Arena.ofShared();

        // Load bundled library from classpath
        this.libraryLoaded = WhisperCpp.initialize();
        if (!libraryLoaded) {
            log.warn("whisper.cpp bundled library could not be loaded — STT unavailable");
        }

        boolean modelExists = Files.exists(modelPath);
        if (!modelExists) {
            log.warn(
                    "Whisper model not found at {}. Download ggml-small.en.bin from {} and place it"
                            + " there.",
                    modelPath,
                    MODEL_URL);
        }

        if (libraryLoaded && modelExists) {
            log.info("Initializing whisper.cpp context from {}", modelPath);
            whisperContext = WhisperCpp.initFromFile(modelPath.toString(), arena);
            if (whisperContext == null) {
                log.error("Failed to initialize whisper.cpp context from {}", modelPath);
            } else {
                log.info("whisper.cpp context initialized — STT available");
            }
        }
    }

    /**
     * Returns whether the library is loaded, model present, and context initialized.
     *
     * @return true if ready to transcribe
     */
    @Override
    public boolean isAvailable() {
        return libraryLoaded && whisperContext != null;
    }

    /**
     * Transcribes audio via whisper.cpp FFM call.
     *
     * @param samples float32 audio samples at 16 kHz
     * @return the transcript text
     */
    @Override
    public String transcribe(float[] samples) {
        if (!isAvailable()) {
            return "";
        }

        try (Arena transcribeArena = Arena.ofConfined()) {
            int result =
                    WhisperCpp.whisperFull(
                            whisperContext, samples, samples.length, transcribeArena);
            if (result != 0) {
                log.error("whisper_full returned error code: {}", result);
                return "";
            }

            int nSegments = WhisperCpp.fullNSegments(whisperContext);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < nSegments; i++) {
                sb.append(WhisperCpp.fullGetSegmentText(whisperContext, i));
            }
            return sb.toString().trim();
        }
    }

    /**
     * Returns the expected path for the native library.
     *
     * @return description of the library loading mechanism
     */
    @Override
    public String expectedLibraryPath() {
        return "classpath:native/<platform>/libwhisper.dylib (bundled)";
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
