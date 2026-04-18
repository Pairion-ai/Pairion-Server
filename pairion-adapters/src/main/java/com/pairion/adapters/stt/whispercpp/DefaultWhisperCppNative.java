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

    /** Expected SHA-256 hash for ggml-small.en.bin (whisper.cpp GGML release). */
    public static final String MODEL_SHA256 =
            "ed3a3a91c2dbc0bd45078a20d1dd5dd063bdb9e299b0287936aafdab4bfba399";

    /** Download URL for the whisper model. */
    public static final String MODEL_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en.bin";

    private final Path nativePath;
    private final Path modelPath;
    private final boolean libraryLoaded;
    private final boolean modelPresent;
    private MemorySegment whisperContext;
    private final Arena arena;

    /** Constructs the native wrapper, attempting to load the library and model. */
    public DefaultWhisperCppNative() {
        String pairionHome = System.getenv("PAIRION_HOME");
        Path basePath =
                pairionHome != null
                        ? Path.of(pairionHome)
                        : Path.of(System.getProperty("user.home"), ".pairion");

        this.nativePath = basePath.resolve("native").resolve(LIB_FILENAME);
        this.modelPath = basePath.resolve("models").resolve("whisper").resolve(MODEL_FILENAME);
        this.arena = Arena.ofShared();

        boolean libLoaded = false;
        if (Files.exists(nativePath)) {
            libLoaded = WhisperCpp.loadLibrary(nativePath.toString(), arena);
            if (libLoaded) {
                log.info("whisper.cpp library loaded from {}", nativePath);
            } else {
                log.error("Failed to load whisper.cpp library from {}", nativePath);
            }
        } else {
            log.warn(
                    "whisper.cpp library not found at {}. Install whisper.cpp and copy the shared"
                            + " library to this location.",
                    nativePath);
        }
        this.libraryLoaded = libLoaded;

        this.modelPresent = Files.exists(modelPath);
        if (!modelPresent) {
            log.warn(
                    "whisper.cpp model not found at {}. Download ggml-small.en.bin from"
                            + " huggingface.co/ggerganov/whisper.cpp and place it here.",
                    modelPath);
        }

        if (libraryLoaded && modelPresent) {
            log.info("whisper.cpp native library and model found — initializing context");
            whisperContext = WhisperCpp.initFromFile(modelPath.toString(), arena);
            if (whisperContext == null || whisperContext.equals(MemorySegment.NULL)) {
                log.error("Failed to initialize whisper.cpp context from {}", modelPath);
                whisperContext = null;
            } else {
                log.info("whisper.cpp context initialized — STT available");
            }
        }
    }

    /**
     * Returns whether the native library is loaded, model present, and context initialized.
     *
     * @return true if ready to transcribe
     */
    @Override
    public boolean isAvailable() {
        return libraryLoaded && modelPresent && whisperContext != null;
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
                String segText = WhisperCpp.fullGetSegmentText(whisperContext, i);
                sb.append(segText);
            }
            return sb.toString().trim();
        }
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
