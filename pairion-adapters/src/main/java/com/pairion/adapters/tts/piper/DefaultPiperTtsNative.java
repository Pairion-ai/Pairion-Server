package com.pairion.adapters.tts.piper;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
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
 * pairion-native-piper} module) and the voice model from {@code $PAIRION_HOME/models/tts/}. The
 * eSpeak-NG data directory is extracted from the classpath alongside the library.
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

    /**
     * Download URL pattern for Piper voice models from Hugging Face.
     *
     * <p>Requires the model name and both the .onnx and .onnx.json files to be present.
     */
    /** Base URL for downloading Piper TTS voice model files. */
    public static final String MODEL_BASE_URL =
            "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/en/en_GB/alan/medium/";

    /** SHA-256 for en_GB-alan-medium.onnx. */
    public static final String MODEL_ONNX_SHA256 =
            "4bba54bab9e04db07f2bf33cbcdf9cf96e2f0a26ab5f8e22b3b5fde15fe14e29";

    /** SHA-256 for en_GB-alan-medium.onnx.json. */
    public static final String MODEL_CONFIG_SHA256 =
            "87ede00d6ae41d85e0e8a6c0fcf22a2cc6a2e4d6dad7c6d3e6fe8b9a0f4c1b23";

    private final String voiceName;
    private final LibraryLoader libraryLoader;
    private boolean available;
    private int sampleRate;
    private Path modelPath;
    private Path modelConfigPath;
    private Path espeakDataPath;
    private SymbolLookup lookup;
    final Arena arena;

    // FFM method handles — populated after successful library load
    private MethodHandle mhInitialize;
    private MethodHandle mhLoadVoice;
    private MethodHandle mhSynthesize;
    private MethodHandle mhFreeVoice;
    private MethodHandle mhTerminate;
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

            initNative(espeakDataPath.toString());
            available = true;
            log.info(
                    "Piper TTS initialized: voice={}, sampleRate={}", voiceName, sampleRate);
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
     * Initializes the Piper native engine via FFM.
     *
     * @param espeakPath path to espeak-ng-data directory
     */
    void initNative(String espeakPath) {
        Linker linker = Linker.nativeLinker();
        lookup = SymbolLookup.loaderLookup();

        mhInitialize =
                linker.downcallHandle(
                        lookup.find("piper_initialize").orElseThrow(),
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        mhLoadVoice =
                linker.downcallHandle(
                        lookup.find("piper_load_voice").orElseThrow(),
                        FunctionDescriptor.of(
                                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        mhSynthesize =
                linker.downcallHandle(
                        lookup.find("piper_synthesize").orElseThrow(),
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS));

        mhFreeVoice =
                linker.downcallHandle(
                        lookup.find("piper_free_voice").orElseThrow(),
                        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

        mhTerminate =
                linker.downcallHandle(
                        lookup.find("piper_terminate").orElseThrow(),
                        FunctionDescriptor.ofVoid());

        // Initialize eSpeak
        try (Arena localArena = Arena.ofConfined()) {
            MemorySegment espeakPathSeg = localArena.allocateUtf8String(espeakPath);
            int result = (int) mhInitialize.invoke(espeakPathSeg);
            if (result != 0) {
                throw new IllegalStateException("piper_initialize returned " + result);
            }
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to initialize Piper TTS engine", t);
        }

        // Load voice
        try (Arena localArena = Arena.ofConfined()) {
            MemorySegment modelSeg = localArena.allocateUtf8String(modelPath.toString());
            MemorySegment configSeg = localArena.allocateUtf8String(modelConfigPath.toString());
            voiceHandle = (MemorySegment) mhLoadVoice.invoke(modelSeg, configSeg);
            if (MemorySegment.NULL.equals(voiceHandle)) {
                throw new IllegalStateException("piper_load_voice returned NULL");
            }
            log.info("Piper voice loaded: {}", voiceName);
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to load Piper voice: " + voiceName, t);
        }

        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    try {
                                        if (voiceHandle != null) {
                                            mhFreeVoice.invoke(voiceHandle);
                                        }
                                        mhTerminate.invoke();
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
     * Invokes piper_synthesize via FFM, routing PCM audio chunks to the consumer.
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
                    java.lang.invoke.MethodHandles.lookup()
                            .findStatic(
                                    DefaultPiperTtsNative.class,
                                    "audioCallbackBridge",
                                    java.lang.invoke.MethodType.methodType(
                                            void.class,
                                            MemorySegment.class,
                                            long.class,
                                            MemorySegment.class));
            // Store consumer in a holder so the upcall stub can access it
            PcmConsumerHolder.set(pcmConsumer);
            MemorySegment callbackStub =
                    Linker.nativeLinker()
                            .upcallStub(callbackHandle, callbackDesc, callArena);

            int result =
                    (int)
                            mhSynthesize.invoke(
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
     * Extracts the libpiper native library from the classpath to a temp file and loads it via
     * {@link System#load}.
     *
     * @return true if loading succeeded, false otherwise
     */
    private static boolean loadLibraryFromClasspath() {
        String platform = detectPlatform();
        String libName = platform.contains("darwin") ? "libpiper.dylib" : "libpiper.so";
        String resourcePath = "/native/" + platform + "/" + libName;

        try (InputStream in = DefaultPiperTtsNative.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                log.warn("Piper native library not on classpath: {}", resourcePath);
                return false;
            }
            Path tmp = Files.createTempFile("libpiper-", libName.substring(3));
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            tmp.toFile().deleteOnExit();
            System.load(tmp.toString());
            log.info("Loaded Piper native library from classpath: {}", resourcePath);
            return true;
        } catch (IOException | UnsatisfiedLinkError e) {
            log.error("Failed to load Piper native library: {}", e.getMessage());
            return false;
        }
    }

    private static String detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            return arch.contains("aarch64") || arch.contains("arm") ? "darwin-aarch64" : "darwin-x86_64";
        }
        if (os.contains("linux")) {
            return arch.contains("aarch64") || arch.contains("arm") ? "linux-aarch64" : "linux-x86_64";
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
