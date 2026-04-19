package com.pairion.adapters.tts.piper;

/**
 * Functional interface for injecting the Piper native library load operation.
 *
 * <p>In production, the implementation calls {@link System#load} with the extracted library path.
 * In tests, a no-op implementation prevents actual native library loading, enabling unit coverage
 * of the adapter without a built Piper library.
 *
 * <p>Mirrors the same pattern used in the STT adapter ({@code
 * com.pairion.adapters.stt.whispercpp.LibraryLoader}).
 */
@FunctionalInterface
interface LibraryLoader {

    /**
     * Loads the native library.
     *
     * @return true if the library was loaded successfully, false otherwise
     */
    boolean load();
}
