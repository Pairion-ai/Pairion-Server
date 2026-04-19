package com.pairion.adapters.tts.piper;

import com.pairion.nativelib.piper.PiperBindings;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Production implementation of {@link PiperTtsNative} using jextract-generated FFM bindings.
 *
 * <p>Loads the libpiper native library from the classpath (bundled by the {@code
 * pairion-native-piper} module) along with its runtime dependencies (libpiper_phonemize,
 * libespeak-ng, libonnxruntime). The voice model is loaded from {@code
 * $PAIRION_HOME/models/tts/}. The eSpeak-NG data directory is extracted from the classpath
 * alongside the libraries if bundled; otherwise falls back to {@code
 * $PAIRION_HOME/espeak-ng-data}.
 *
 * <p>Three package-private methods ({@link #resolveModelPaths(String)}, {@link
 * #initNative(String)}, {@link #callSynthesize(String, Consumer)}) exist to allow unit tests to
 * exercise error paths without mocking the FFM layer directly.
 *
 * <p>Activated when {@code pairion.adapters.tts=piper} (the default).
 */
@Component
@ConditionalOnProperty(name = "pairion.adapters.tts", havingValue = "piper", matchIfMissing = true)
public class DefaultPiperTtsNative implements PiperTtsNative {

    private static final Logger log = LoggerFactory.getLogger(DefaultPiperTtsNative.class);

    /** Default voice model name. */
    static final String DEFAULT_VOICE = "en_GB-alan-medium";

    /** Sample rate for en_GB-alan-medium and most Piper medium quality models. */
    static final int VOICE_SAMPLE_RATE = 22050;

    /** Base URL for downloading Piper TTS voice model files. */
    public static final String MODEL_BASE_URL =
            "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/en/en_GB/alan/medium/";

    /** SHA-256 for en_GB-alan-medium.onnx. */
    public static final String MODEL_ONNX_SHA256 =
            "0a309668932205e762801f1efc2736cd4b0120329622adf62be09e56339d3330";

    /** SHA-256 for en_GB-alan-medium.onnx.json. */
    public static final String MODEL_CONFIG_SHA256 =
            "c0f0d124e5895c00e7c03b35dcc8287f319a6998a365b182deb5c8e752ee8c1e";

    /**
     * Extracted espeak-ng-data path from the bundled classpath resource, or null if not extracted.
     *
     * <p>Set once by {@link #loadLibraryFromClasspath()} and reused by all instances.
     */
    private static volatile Path bundledEspeakDataPath = null;

    private final String voiceName;
    private final LibraryLoader libraryLoader;
    private boolean available;
    private int sampleRate;
    private Path modelPath;
    private Path modelConfigPath;
    private Path espeakDataPath;
    final Arena arena;
    private MemorySegment voiceHandle;

    /**
     * Constructs the native wrapper using the bundled classpath library and the model path resolved
     * from {@code PAIRION_HOME} (or {@code ~/.pairion} if unset).
     *
     * @param voiceName the voice model name (e.g. {@code en_GB-alan-medium})
     */
    @Autowired
    public DefaultPiperTtsNative(
            @Value("${pairion.adapters.tts.piper.voice:en_GB-alan-medium}") String voiceName) {
        this(voiceName, DefaultPiperTtsNative::loadLibraryFromClasspath);
    }

    /**
     * Package-private constructor for testing — allows injecting a custom {@link LibraryLoader}.
     *
     * @param voiceName the voice model name
     * @param libraryLoader the library loader implementation
     */
    DefaultPiperTtsNative(String voiceName, LibraryLoader libraryLoader) {
        this.voiceName = voiceName;
        this.libraryLoader = libraryLoader;
        this.arena = Arena.ofShared();
        this.sampleRate = VOICE_SAMPLE_RATE;
        init();
    }

    private void init() {
        if (!libraryLoader.load()) {
            log.warn("Piper native library not loaded — TTS adapter unavailable");
            available = false;
            return;
        }

        try {
            String[] paths = resolveModelPaths(voiceName);
            this.modelPath = Path.of(paths[0]);
            this.modelConfigPath = Path.of(paths[1]);
            this.espeakDataPath = Path.of(paths[2]);

            if (!Files.exists(modelPath) || !Files.exists(modelConfigPath)) {
                log.warn(
                        "Piper voice model not found at {} — TTS unavailable (run ModelStartupService to download)",
                        modelPath);
                available = false;
                return;
            }

            // Use bundled espeak-ng-data extracted from the JAR when available;
            // otherwise fall back to the configured $PAIRION_HOME path.
            Path resolvedEspeakPath =
                    (bundledEspeakDataPath != null && Files.isDirectory(bundledEspeakDataPath))
                            ? bundledEspeakDataPath
                            : espeakDataPath;

            initNative(resolvedEspeakPath.toString());
            available = true;
            log.info("Piper TTS initialized: voice={}, sampleRate={}", voiceName, sampleRate);
        } catch (Exception e) {
            log.error("Piper TTS initialization failed: {}", e.getMessage());
            available = false;
        }
    }

    /**
     * Resolves model paths from PAIRION_HOME.
     *
     * @param voice the voice name
     * @return array of [modelPath, modelConfigPath, espeakDataPath]
     */
    String[] resolveModelPaths(String voice) {
        String pairionHome = System.getenv("PAIRION_HOME");
        Path base =
                pairionHome != null
                        ? Path.of(pairionHome)
                        : Path.of(System.getProperty("user.home"), ".pairion");
        Path ttsDir = base.resolve("models").resolve("tts");
        return new String[] {
            ttsDir.resolve(voice + ".onnx").toString(),
            ttsDir.resolve(voice + ".onnx.json").toString(),
            base.resolve("espeak-ng-data").toString()
        };
    }

    /**
     * Initializes the Piper native engine via the jextract-generated {@link PiperBindings}.
     *
     * @param espeakPath path to espeak-ng-data directory
     */
    void initNative(String espeakPath) {
        try (Arena localArena = Arena.ofConfined()) {
            MemorySegment espeakSeg = localArena.allocateUtf8String(espeakPath);
            int result = PiperBindings.piper_initialize(espeakSeg);
            if (result != 0) {
                throw new IllegalStateException("piper_initialize returned " + result);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize Piper TTS engine", e);
        }

        try (Arena localArena = Arena.ofConfined()) {
            MemorySegment modelSeg = localArena.allocateUtf8String(modelPath.toString());
            MemorySegment configSeg = localArena.allocateUtf8String(modelConfigPath.toString());
            voiceHandle = PiperBindings.piper_load_voice(modelSeg, configSeg);
            if (MemorySegment.NULL.equals(voiceHandle)) {
                throw new IllegalStateException("piper_load_voice returned NULL");
            }
            log.info("Piper voice loaded: {}", voiceName);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load Piper voice: " + voiceName, e);
        }

        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    try {
                                        if (voiceHandle != null) {
                                            PiperBindings.piper_free_voice(voiceHandle);
                                        }
                                        PiperBindings.piper_terminate();
                                    } catch (Throwable ignored) {
                                        // Best-effort cleanup
                                    }
                                },
                                "piper-shutdown"));
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public int getSampleRate() {
        return sampleRate;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link #callSynthesize(String, Consumer)} with the loaded voice handle.
     */
    @Override
    public void synthesize(String text, Consumer<byte[]> pcmConsumer) {
        if (!available) {
            log.warn("Piper TTS not available — skipping synthesis");
            return;
        }
        callSynthesize(text, pcmConsumer);
    }

    /**
     * Invokes piper_synthesize via the jextract-generated bindings, routing PCM audio chunks to
     * the consumer.
     *
     * @param text the text to synthesize
     * @param pcmConsumer callback receiving raw 16-bit little-endian PCM byte chunks
     */
    void callSynthesize(String text, Consumer<byte[]> pcmConsumer) {
        try (Arena callArena = Arena.ofConfined()) {
            MemorySegment textSeg = callArena.allocateUtf8String(text);

            // Upcall stub: the C callback receives (int16_t* samples, size_t num_samples, void*)
            FunctionDescriptor callbackDesc =
                    FunctionDescriptor.ofVoid(
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_LONG,
                            ValueLayout.ADDRESS);
            MethodHandle callbackHandle =
                    MethodHandles.lookup()
                            .findStatic(
                                    DefaultPiperTtsNative.class,
                                    "audioCallbackBridge",
                                    MethodType.methodType(
                                            void.class,
                                            MemorySegment.class,
                                            long.class,
                                            MemorySegment.class));
            PcmConsumerHolder.set(pcmConsumer);
            MemorySegment callbackStub =
                    Linker.nativeLinker().upcallStub(callbackHandle, callbackDesc, callArena);

            int result =
                    PiperBindings.piper_synthesize(
                            voiceHandle, textSeg, callbackStub, MemorySegment.NULL);
            if (result != 0) {
                log.error("piper_synthesize returned non-zero: {}", result);
            }
        } catch (Throwable t) {
            log.error("Piper TTS synthesis failed: {}", t.getMessage());
        } finally {
            PcmConsumerHolder.clear();
        }
    }

    /**
     * Static bridge method invoked from the native C audio callback.
     *
     * <p>Converts the raw int16_t sample pointer to a Java byte[] and delivers it to the thread-
     * local consumer.
     *
     * @param samples pointer to 16-bit PCM samples
     * @param numSamples number of samples
     * @param userData unused user data pointer
     */
    static void audioCallbackBridge(
            MemorySegment samples, long numSamples, MemorySegment userData) {
        Consumer<byte[]> consumer = PcmConsumerHolder.get();
        if (consumer == null || numSamples <= 0) return;

        // Copy samples to a Java byte array (16-bit LE, 2 bytes per sample)
        byte[] pcm = new byte[(int) numSamples * 2];
        MemorySegment slice =
                samples.reinterpret(numSamples * ValueLayout.JAVA_SHORT.byteSize());
        for (int i = 0; i < (int) numSamples; i++) {
            short s = slice.getAtIndex(ValueLayout.JAVA_SHORT, i);
            pcm[i * 2] = (byte) (s & 0xFF);
            pcm[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }
        consumer.accept(pcm);
    }

    /**
     * Extracts the libpiper native library and all its runtime dependencies from the classpath to
     * a shared temp directory, loads them in dependency order, and extracts espeak-ng-data.
     *
     * <p>Loading order: libonnxruntime → libespeak-ng → libpiper_phonemize → libpiper. The
     * libpiper dylib has {@code @loader_path} set as its rpath, so all dependencies are resolved
     * from the same temp directory automatically.
     *
     * @return true if the main libpiper library was loaded successfully, false otherwise
     */
    private static boolean loadLibraryFromClasspath() {
        String platform = detectPlatform();
        String resourceBase = "/native/" + platform + "/";

        boolean isDarwin = platform.contains("darwin");
        String mainLib = isDarwin ? "libpiper.dylib" : "libpiper.so";

        // Dependency libraries to extract alongside the main library.
        // Load order matters: each lib must be loadable before its dependents.
        String[] depLibs =
                isDarwin
                        ? new String[] {
                            "libonnxruntime.1.14.1.dylib",
                            "libespeak-ng.1.dylib",
                            "libpiper_phonemize.1.dylib"
                        }
                        : new String[] {
                            "libonnxruntime.so.1.14.1",
                            "libespeak-ng.so.1",
                            "libpiper_phonemize.so"
                        };

        try {
            Path tmpDir = Files.createTempDirectory("piper-native-");
            tmpDir.toFile().deleteOnExit();

            // Extract dependency libraries so @loader_path/@rpath can find them
            for (String dep : depLibs) {
                extractResource(resourceBase + dep, tmpDir.resolve(dep));
            }

            // Extract and load the main library — deps auto-loaded via @loader_path rpath
            URL mainLibUrl =
                    DefaultPiperTtsNative.class.getResource(resourceBase + mainLib);
            if (mainLibUrl == null) {
                log.warn("Piper native library not on classpath: {}", resourceBase + mainLib);
                return false;
            }
            Path mainLibPath = tmpDir.resolve(mainLib);
            try (InputStream in = mainLibUrl.openStream()) {
                Files.copy(in, mainLibPath, StandardCopyOption.REPLACE_EXISTING);
            }
            mainLibPath.toFile().deleteOnExit();
            System.load(mainLibPath.toString());

            // Extract espeak-ng-data from classpath into the same temp dir
            Path espeakOut = extractEspeakNgData(tmpDir);
            if (espeakOut != null) {
                bundledEspeakDataPath = espeakOut;
                log.info("Extracted bundled espeak-ng-data to {}", espeakOut);
            } else {
                log.warn(
                        "espeak-ng-data not found in classpath — will use PAIRION_HOME fallback");
            }

            log.info("Loaded Piper native library from classpath: {}", resourceBase + mainLib);
            return true;
        } catch (IOException | UnsatisfiedLinkError e) {
            log.error("Failed to load Piper native library: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extracts a single classpath resource to the given target path.
     *
     * <p>Silently skips resources not found on the classpath (dep may not exist on all platforms).
     *
     * @param resourcePath the classpath resource path
     * @param target the local file path to extract to
     * @throws IOException if extraction fails
     */
    private static void extractResource(String resourcePath, Path target) throws IOException {
        try (InputStream in = DefaultPiperTtsNative.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                log.debug("Classpath resource not found (skipping): {}", resourcePath);
                return;
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            target.toFile().deleteOnExit();
        }
    }

    /**
     * Extracts the {@code /espeak-ng-data} directory tree from the classpath into a subdirectory
     * of {@code tmpDir}.
     *
     * <p>Handles both JAR and exploded-classpath (IDE / test) environments by using the
     * {@link FileSystems} API to open the resource container as a virtual filesystem.
     *
     * @param tmpDir the temp directory to extract into
     * @return the extracted {@code espeak-ng-data} path, or null if not found
     */
    private static Path extractEspeakNgData(Path tmpDir) {
        URL rootUrl = DefaultPiperTtsNative.class.getResource("/espeak-ng-data");
        if (rootUrl == null) {
            return null;
        }
        Path outDir = tmpDir.resolve("espeak-ng-data");
        try {
            URI uri = rootUrl.toURI();
            String scheme = uri.getScheme();
            if ("jar".equals(scheme)) {
                try (FileSystem fs = FileSystems.newFileSystem(uri, Map.of())) {
                    copyDirectoryTree(fs.getPath("/espeak-ng-data"), outDir);
                }
            } else {
                copyDirectoryTree(Path.of(uri), outDir);
            }
            return Files.isDirectory(outDir) ? outDir : null;
        } catch (Exception e) {
            log.warn("Could not extract espeak-ng-data from classpath: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Recursively copies a directory tree from {@code src} to {@code dst}.
     *
     * @param src the source directory
     * @param dst the destination directory
     * @throws IOException if copying fails
     */
    private static void copyDirectoryTree(Path src, Path dst) throws IOException {
        try (Stream<Path> walk = Files.walk(src)) {
            walk.forEach(
                    source -> {
                        try {
                            Path relative = src.relativize(source);
                            Path target = dst.resolve(relative.toString());
                            if (Files.isDirectory(source)) {
                                Files.createDirectories(target);
                            } else {
                                Files.createDirectories(target.getParent());
                                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }

    private static String detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            return arch.contains("aarch64") || arch.contains("arm")
                    ? "darwin-aarch64"
                    : "darwin-x86_64";
        }
        if (os.contains("linux")) {
            return arch.contains("aarch64") || arch.contains("arm")
                    ? "linux-aarch64"
                    : "linux-x86_64";
        }
        return "unknown";
    }

    /** Thread-local holder for the PCM consumer, used by the native audio callback bridge. */
    private static final class PcmConsumerHolder {
        private static final ThreadLocal<Consumer<byte[]>> HOLDER = new ThreadLocal<>();

        static void set(Consumer<byte[]> consumer) {
            HOLDER.set(consumer);
        }

        static Consumer<byte[]> get() {
            return HOLDER.get();
        }

        static void clear() {
            HOLDER.remove();
        }
    }
}
