package com.pairion.adapters.stt.whispercpp;

import com.pairion.nativelib.whisper.NativeLibraryLoader;
import com.pairion.nativelib.whisper.WhisperBindings;
import com.pairion.nativelib.whisper.whisper_context_params;
import com.pairion.nativelib.whisper.whisper_full_params;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Production implementation of {@link WhisperCppNative} using jextract-generated FFM bindings.
 *
 * <p>Loads the whisper.cpp native library from the classpath (bundled by the {@code
 * pairion-native-whisper} module) and the model from {@code $PAIRION_HOME/models/whisper/}. Apple
 * Silicon Metal acceleration is enabled by default via the build flags in the native module.
 *
 * <p>Registers a JVM shutdown hook to call {@code whisper_free()} on acquired contexts before exit,
 * preventing the GGML Metal cleanup assertion that would otherwise crash the process.
 */
@Component
@ConditionalOnProperty(
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

    /**
     * Constructs the native wrapper, loading the bundled library and initializing the context.
     *
     * <p>A JVM shutdown hook is registered to free the whisper context before exit, preventing GGML
     * Metal cleanup assertions.
     */
    public DefaultWhisperCppNative() {
        String pairionHome = System.getenv("PAIRION_HOME");
        Path basePath =
                pairionHome != null
                        ? Path.of(pairionHome)
                        : Path.of(System.getProperty("user.home"), ".pairion");

        this.modelPath = basePath.resolve("models").resolve("whisper").resolve(MODEL_FILENAME);
        this.arena = Arena.ofShared();

        // Load bundled library from classpath
        boolean loaded = NativeLibraryLoader.load();
        if (loaded) {
            // Verify symbols are resolvable via the loaded library
            loaded =
                    SymbolLookup.loaderLookup()
                            .find("whisper_init_from_file_with_params")
                            .isPresent();
        }
        this.libraryLoaded = loaded;
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
            whisperContext = initContext(modelPath.toString());
            if (whisperContext == null) {
                log.error("Failed to initialize whisper.cpp context from {}", modelPath);
            } else {
                log.info("whisper.cpp context initialized — STT available");
                registerShutdownHook();
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
     * Transcribes audio via whisper.cpp FFM call using jextract-generated bindings.
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
            // Get default full params (greedy strategy = 0) via pointer-returning API
            MemorySegment paramsPtr = WhisperBindings.whisper_full_default_params_by_ref(0);
            MemorySegment params = transcribeArena.allocate(whisper_full_params.$LAYOUT());
            params.copyFrom(paramsPtr.reinterpret(whisper_full_params.$LAYOUT().byteSize()));

            // Allocate samples in native memory
            MemorySegment samplesSeg =
                    transcribeArena.allocateArray(
                            java.lang.foreign.ValueLayout.JAVA_FLOAT, samples);

            int result =
                    WhisperBindings.whisper_full(
                            whisperContext, params, samplesSeg, samples.length);
            if (result != 0) {
                log.error("whisper_full returned error code: {}", result);
                return "";
            }

            int nSegments = WhisperBindings.whisper_full_n_segments(whisperContext);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < nSegments; i++) {
                MemorySegment textPtr =
                        WhisperBindings.whisper_full_get_segment_text(whisperContext, i);
                sb.append(textPtr.reinterpret(Long.MAX_VALUE).getUtf8String(0));
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

    /** Frees the whisper context. Called by the shutdown hook and may be called manually. */
    void freeContext() {
        if (whisperContext != null) {
            log.info("Freeing whisper.cpp context before shutdown");
            WhisperBindings.whisper_free(whisperContext);
            whisperContext = null;
        }
    }

    private MemorySegment initContext(String modelPathStr) {
        try {
            MemorySegment pathSeg = arena.allocateUtf8String(modelPathStr);

            // Get default context params via pointer-returning API (safe — no struct layout needed)
            MemorySegment defaultsPtr = WhisperBindings.whisper_context_default_params_by_ref();
            MemorySegment params = arena.allocate(whisper_context_params.$LAYOUT());
            params.copyFrom(defaultsPtr.reinterpret(whisper_context_params.$LAYOUT().byteSize()));

            MemorySegment ctx = WhisperBindings.whisper_init_from_file_with_params(pathSeg, params);
            if (ctx.equals(MemorySegment.NULL)) {
                return null;
            }
            return ctx;
        } catch (Exception e) {
            log.error("whisper_init_from_file_with_params failed: {}", e.getMessage());
            return null;
        }
    }

    private void registerShutdownHook() {
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    freeContext();
                                    arena.close();
                                },
                                "whisper-shutdown"));
    }
}
