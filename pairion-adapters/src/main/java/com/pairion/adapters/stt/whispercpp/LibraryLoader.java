package com.pairion.adapters.stt.whispercpp;

/**
 * Package-private functional interface for injecting the native library load operation into {@link
 * DefaultWhisperCppNative}.
 *
 * <p>The production implementation delegates to {@link
 * com.pairion.nativelib.whisper.NativeLibraryLoader} and verifies symbol availability. Test
 * implementations can return {@code false} to simulate library load failure without requiring a
 * real native library.
 */
@FunctionalInterface
interface LibraryLoader {

    /**
     * Loads the native library and verifies symbol availability.
     *
     * @return {@code true} if the library loaded successfully and required symbols are present
     */
    boolean load();
}
