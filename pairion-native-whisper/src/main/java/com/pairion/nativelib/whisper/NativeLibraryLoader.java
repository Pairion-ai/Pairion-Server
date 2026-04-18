package com.pairion.nativelib.whisper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts and loads the bundled whisper.cpp native library from the classpath.
 *
 * <p>The library is packaged as a classpath resource under {@code
 * native/<platform>/libwhisper.dylib}. On first call, it is extracted to a temp directory and
 * loaded via {@link System#load(String)}. The temp file is marked for deletion on JVM exit.
 */
public final class NativeLibraryLoader {

    private static final Logger log = LoggerFactory.getLogger(NativeLibraryLoader.class);
    private static boolean loaded = false;
    private static String loadedPath = "";

    private NativeLibraryLoader() {}

    /**
     * Loads the bundled whisper.cpp library for the current platform.
     *
     * @return true if the library was loaded successfully
     */
    public static synchronized boolean load() {
        if (loaded) {
            return true;
        }
        String platform = detectPlatform();
        String resourcePath = "native/" + platform + "/" + libraryName();
        log.info("Loading whisper.cpp from classpath: {}", resourcePath);

        try (InputStream in =
                NativeLibraryLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                log.error(
                        "Bundled whisper.cpp library not found in classpath at {}. "
                                + "Ensure pairion-native-whisper was built for this platform.",
                        resourcePath);
                return false;
            }

            Path tempFile = Files.createTempFile("libwhisper", platformSuffix());
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            tempFile.toFile().deleteOnExit();

            System.load(tempFile.toAbsolutePath().toString());
            loaded = true;
            loadedPath = tempFile.toAbsolutePath().toString();
            log.info("whisper.cpp loaded from classpath-extracted temp: {}", loadedPath);
            return true;
        } catch (IOException e) {
            log.error("Failed to extract whisper.cpp library: {}", e.getMessage());
            return false;
        } catch (UnsatisfiedLinkError e) {
            log.error("Failed to load whisper.cpp library: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Returns whether the library has been loaded.
     *
     * @return true if loaded
     */
    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * Returns the path from which the library was loaded.
     *
     * @return the library path, or empty string if not loaded
     */
    public static String loadedPath() {
        return loadedPath;
    }

    /**
     * Detects the current platform identifier for resource lookup.
     *
     * @return platform string like "darwin-aarch64" or "linux-x86_64"
     */
    static String detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "");
        String osPrefix =
                os.contains("mac") ? "darwin" : os.contains("linux") ? "linux" : "windows";
        return osPrefix + "-" + arch;
    }

    private static String libraryName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            return "libwhisper.dylib";
        }
        if (os.contains("win")) {
            return "whisper.dll";
        }
        return "libwhisper.so";
    }

    private static String platformSuffix() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            return ".dylib";
        }
        if (os.contains("win")) {
            return ".dll";
        }
        return ".so";
    }
}
