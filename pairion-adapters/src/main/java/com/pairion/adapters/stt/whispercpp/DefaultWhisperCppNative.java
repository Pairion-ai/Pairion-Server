package com.pairion.adapters.stt.whispercpp;

import com.pairion.nativelib.whisper.NativeLibraryLoader;
import com.pairion.nativelib.whisper.WhisperBindings;
import com.pairion.nativelib.whisper.whisper_context_params;
import com.pairion.nativelib.whisper.whisper_full_params;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
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
 *
 * <p>Three package-private methods ({@link #resolveModelPath(String)}, {@link
 * #initContext(String)}, {@link #callWhisperFull(MemorySegment, MemorySegment, MemorySegment, int)}
 * ) exist solely to allow unit tests to exercise error paths (PAIRION_HOME branch, context init
 * exception, whisper_full non-zero return) without mocking the FFM layer directly.
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
    final Arena arena;

    /**
     * Constructs the native wrapper using the bundled classpath library and the model path resolved
     * from {@code PAIRION_HOME} (or {@code ~/.pairion} if unset).
     *
     * <p>A JVM shutdown hook is registered to free the whisper context before exit, preventing GGML
     * Metal cleanup assertions.
     */
    @SuppressWarnings("this-escape")
    public DefaultWhisperCppNative() {
        this(
                resolveModelPath(System.getenv("PAIRION_HOME")),
                DefaultWhisperCppNative::loadBundledLibrary);
    }

    /**
     * Package-private constructor for testing. Accepts an injected model path and library loader so
     * that error paths (library load failure, model missing, context init failure) can be exercised
     * without a real native library installation or model file.
     *
     * @param modelPath path to the whisper model file
     * @param libraryLoader functional interface that loads the native library
     */
    @SuppressWarnings("this-escape")
    DefaultWhisperCppNative(Path modelPath, LibraryLoader libraryLoader) {
        this.modelPath = modelPath;
        this.arena = Arena.ofShared();

        boolean loaded = libraryLoader.load();
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
            try {
                whisperContext = initContext(modelPath.toString());
            } catch (Exception e) {
                log.error("whisper_init_from_file_with_params failed: {}", e.getMessage());
                whisperContext = null;
            }
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

        Arena transcribeArena = Arena.ofConfined();
        try {
            // Get default full params (greedy strategy = 0) via pointer-returning API
            MemorySegment paramsPtr = WhisperBindings.whisper_full_default_params_by_ref(0);
            MemorySegment params = transcribeArena.allocate(whisper_full_params.$LAYOUT());
            params.copyFrom(paramsPtr.reinterpret(whisper_full_params.$LAYOUT().byteSize()));

            // Allocate samples in native memory
            MemorySegment samplesSeg =
                    transcribeArena.allocateArray(ValueLayout.JAVA_FLOAT, samples);

            int result = callWhisperFull(whisperContext, params, samplesSeg, samples.length);
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
        } finally {
            transcribeArena.close();
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

    /**
     * Resolves the model path from the supplied {@code pairionHome} value, falling back to {@code
     * ~/.pairion} if null.
     *
     * <p>Package-private to enable direct testing of both branches of the {@code pairionHome !=
     * null} ternary without environment-variable injection.
     */
    static Path resolveModelPath(String pairionHome) {
        Path basePath =
                pairionHome != null
                        ? Path.of(pairionHome)
                        : Path.of(System.getProperty("user.home"), ".pairion");
        return basePath.resolve("models").resolve("whisper").resolve(MODEL_FILENAME);
    }

    /**
     * Loads the bundled classpath whisper.cpp library and verifies that the required init symbol is
     * present. Used as the default {@link LibraryLoader} by the public no-arg constructor.
     */
    static boolean loadBundledLibrary() {
        boolean loaded = NativeLibraryLoader.load();
        if (loaded) {
            loaded =
                    SymbolLookup.loaderLookup()
                            .find("whisper_init_from_file_with_params")
                            .isPresent();
        }
        return loaded;
    }

    /**
     * Invokes {@code whisper_init_from_file_with_params} via FFM bindings.
     *
     * <p>Package-private so tests can override this method (via anonymous subclass) to simulate an
     * exception being thrown from the init call, exercising the catch block in the constructor.
     *
     * @param modelPathStr absolute path to the model file
     * @return the whisper context segment, or {@code null} if the model could not be loaded
     */
    MemorySegment initContext(String modelPathStr) {
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
    }

    /**
     * Delegates to {@code WhisperBindings.whisper_full} to run the transcription pass.
     *
     * <p>Package-private so tests can override this method (via anonymous subclass) to return a
     * non-zero error code, exercising the error branch in {@link #transcribe(float[])}.
     *
     * @param ctx the whisper context
     * @param params the full-params struct
     * @param samples the audio samples buffer
     * @param nSamples the number of samples
     * @return 0 on success, non-zero on failure
     */
    int callWhisperFull(
            MemorySegment ctx, MemorySegment params, MemorySegment samples, int nSamples) {
        return WhisperBindings.whisper_full(ctx, params, samples, nSamples);
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
