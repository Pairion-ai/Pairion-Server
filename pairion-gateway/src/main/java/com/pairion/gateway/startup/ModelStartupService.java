package com.pairion.gateway.startup;

import com.pairion.adapters.stt.whispercpp.DefaultWhisperCppNative;
import com.pairion.adapters.tts.piper.DefaultPiperTtsNative;
import com.pairion.core.util.ModelDownloader;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Downloads required ML model files on startup using virtual threads.
 *
 * <p>Triggered by {@link ApplicationReadyEvent} after the Spring context is fully initialized and
 * the server is ready to accept requests. Each model is downloaded on its own virtual thread so
 * that startup is non-blocking and the server accepts requests even if downloads are in progress.
 *
 * <p>Downloads are idempotent: if the model file already exists at the target path, the download
 * is skipped. SHA-256 verification is performed for all newly downloaded files.
 *
 * <p>Models downloaded:
 *
 * <ul>
 *   <li>Whisper STT model: {@code ggml-small.en.bin} → {@code $PAIRION_HOME/models/whisper/}
 *   <li>Piper TTS ONNX model: {@code <voice>.onnx} → {@code $PAIRION_HOME/models/tts/}
 *   <li>Piper TTS config: {@code <voice>.onnx.json} → {@code $PAIRION_HOME/models/tts/}
 * </ul>
 */
@Component
public class ModelStartupService {

    private static final Logger log = LoggerFactory.getLogger(ModelStartupService.class);

    private final String piperVoice;
    private final ModelDownloader modelDownloader;

    /**
     * Constructs the service with the configured Piper voice name and a default downloader.
     *
     * @param piperVoice the Piper voice model name (e.g. {@code en_GB-alan-medium})
     */
    @Autowired
    public ModelStartupService(
            @Value("${pairion.adapters.tts.piper.voice:en_GB-alan-medium}") String piperVoice) {
        this(piperVoice, new ModelDownloader());
    }

    /**
     * Package-private constructor for testing — allows injecting a stub downloader.
     *
     * @param piperVoice the Piper voice model name
     * @param modelDownloader the model downloader implementation
     */
    ModelStartupService(String piperVoice, ModelDownloader modelDownloader) {
        this.piperVoice = piperVoice;
        this.modelDownloader = modelDownloader;
    }

    /**
     * Triggers model downloads after the application context is ready.
     *
     * <p>Each download runs on a separate virtual thread to avoid blocking the server. Log messages
     * use the {@code model.startup} prefix for easy filtering.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("model.startup.begin: scheduling model downloads on virtual threads");
        downloadOnVirtualThread("whisper-model", this::downloadWhisperModel);
        downloadOnVirtualThread("piper-model", this::downloadPiperModel);
    }

    private void downloadOnVirtualThread(String name, Runnable task) {
        Thread.ofVirtual()
                .name("model-download-" + name)
                .start(
                        () -> {
                            try {
                                task.run();
                            } catch (Exception e) {
                                log.error("model.startup.error: task={}, error={}", name, e.getMessage());
                            }
                        });
    }

    /**
     * Downloads the Whisper STT model if not already present.
     */
    void downloadWhisperModel() {
        Path targetPath = resolveModelPath(System.getenv("PAIRION_HOME"), "whisper", DefaultWhisperCppNative.MODEL_FILENAME);
        log.info("model.startup.whisper: target={}", targetPath);
        boolean ok =
                modelDownloader.download(
                        DefaultWhisperCppNative.MODEL_URL,
                        targetPath,
                        DefaultWhisperCppNative.MODEL_SHA256);
        if (ok) {
            log.info("model.startup.whisper.ready: path={}", targetPath);
        } else {
            log.warn("model.startup.whisper.failed: path={}", targetPath);
        }
    }

    /**
     * Downloads the Piper TTS voice ONNX model and config if not already present.
     */
    void downloadPiperModel() {
        String pairionHome = System.getenv("PAIRION_HOME");
        Path onnxPath = resolveModelPath(pairionHome, "tts", piperVoice + ".onnx");
        Path configPath = resolveModelPath(pairionHome, "tts", piperVoice + ".onnx.json");

        String baseUrl = DefaultPiperTtsNative.MODEL_BASE_URL;
        String onnxUrl = baseUrl + piperVoice + ".onnx";
        String configUrl = baseUrl + piperVoice + ".onnx.json";

        log.info("model.startup.piper: voice={}, onnxTarget={}", piperVoice, onnxPath);
        boolean onnxOk =
                modelDownloader.download(onnxUrl, onnxPath, DefaultPiperTtsNative.MODEL_ONNX_SHA256);
        boolean configOk =
                modelDownloader.download(
                        configUrl, configPath, DefaultPiperTtsNative.MODEL_CONFIG_SHA256);

        if (onnxOk && configOk) {
            log.info("model.startup.piper.ready: voice={}", piperVoice);
        } else {
            log.warn(
                    "model.startup.piper.failed: voice={}, onnxOk={}, configOk={}",
                    piperVoice,
                    onnxOk,
                    configOk);
        }
    }

    /**
     * Resolves a model file path from the supplied {@code pairionHome} value, falling back to
     * {@code ~/.pairion} if null.
     *
     * <p>Package-private to enable direct testing of both branches of the {@code pairionHome !=
     * null} ternary without environment-variable injection.
     *
     * @param pairionHome the value of {@code $PAIRION_HOME}, or null to use {@code ~/.pairion}
     * @param category the model category subdirectory (e.g. {@code "whisper"}, {@code "tts"})
     * @param filename the model filename
     * @return the resolved path
     */
    Path resolveModelPath(String pairionHome, String category, String filename) {
        Path base =
                pairionHome != null
                        ? Path.of(pairionHome)
                        : Path.of(System.getProperty("user.home"), ".pairion");
        return base.resolve("models").resolve(category).resolve(filename);
    }
}
