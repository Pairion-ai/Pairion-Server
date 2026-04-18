package com.pairion.adapters.stt.whispercpp.ffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Manual FFM bindings for the whisper.cpp C API.
 *
 * <p><strong>AUTOGEN NOTICE:</strong> Manually written FFM bindings matching whisper.h. Regenerate
 * via jextract when available. Covers the minimal API surface for streaming transcription.
 *
 * <p>Pinned to whisper.cpp API compatible with release v1.7.3.
 */
public final class WhisperCpp {

    private WhisperCpp() {}

    private static SymbolLookup lookup;

    /**
     * Loads the whisper.cpp shared library from the given path.
     *
     * @param libraryPath path to the shared library
     * @param arena the arena for library lifecycle
     * @return true if the library was loaded successfully
     */
    public static boolean loadLibrary(String libraryPath, Arena arena) {
        try {
            System.load(libraryPath);
            lookup = SymbolLookup.loaderLookup();
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    /**
     * Returns whether the library has been loaded.
     *
     * @return true if whisper.cpp is loaded
     */
    public static boolean isLoaded() {
        return lookup != null;
    }

    /**
     * Calls whisper_init_from_file_with_params to initialize a context.
     *
     * @param modelPath path to the GGML model file
     * @param arena arena for native memory allocation
     * @return the whisper_context pointer, or MemorySegment.NULL on failure
     */
    public static MemorySegment initFromFile(String modelPath, Arena arena) {
        try {
            MemorySegment pathSeg = arena.allocateUtf8String(modelPath);
            MemorySegment params = arena.allocate(64);
            MethodHandle mh =
                    downcall(
                            "whisper_init_from_file_with_params",
                            FunctionDescriptor.of(
                                    ValueLayout.ADDRESS,
                                    ValueLayout.ADDRESS,
                                    MemoryLayout.structLayout(
                                            ValueLayout.JAVA_INT.withName("use_gpu"))));
            return (MemorySegment) mh.invoke(pathSeg, params);
        } catch (Throwable t) {
            return MemorySegment.NULL;
        }
    }

    /**
     * Calls whisper_full to run full transcription on PCM audio.
     *
     * @param ctx the whisper context
     * @param samples float32 PCM audio samples
     * @param nSamples number of samples
     * @param arena arena for parameter struct allocation
     * @return 0 on success
     */
    public static int whisperFull(MemorySegment ctx, float[] samples, int nSamples, Arena arena) {
        try {
            MemorySegment params = arena.allocate(512);
            MemorySegment samplesSeg = arena.allocateArray(ValueLayout.JAVA_FLOAT, samples);
            MethodHandle mh =
                    downcall(
                            "whisper_full",
                            FunctionDescriptor.of(
                                    ValueLayout.JAVA_INT,
                                    ValueLayout.ADDRESS,
                                    MemoryLayout.structLayout(
                                            MemoryLayout.sequenceLayout(
                                                    512, ValueLayout.JAVA_BYTE)),
                                    ValueLayout.ADDRESS,
                                    ValueLayout.JAVA_INT));
            return (int) mh.invoke(ctx, params, samplesSeg, nSamples);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Calls whisper_full_n_segments to get the number of transcript segments.
     *
     * @param ctx the whisper context
     * @return number of segments
     */
    public static int fullNSegments(MemorySegment ctx) {
        try {
            MethodHandle mh =
                    downcall(
                            "whisper_full_n_segments",
                            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            return (int) mh.invoke(ctx);
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Calls whisper_full_get_segment_text to get the text of a segment.
     *
     * @param ctx the whisper context
     * @param segmentIndex segment index
     * @return the segment text, or empty string on error
     */
    public static String fullGetSegmentText(MemorySegment ctx, int segmentIndex) {
        try {
            MethodHandle mh =
                    downcall(
                            "whisper_full_get_segment_text",
                            FunctionDescriptor.of(
                                    ValueLayout.ADDRESS,
                                    ValueLayout.ADDRESS,
                                    ValueLayout.JAVA_INT));
            MemorySegment textPtr = (MemorySegment) mh.invoke(ctx, segmentIndex);
            return textPtr.reinterpret(Long.MAX_VALUE).getUtf8String(0);
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * Calls whisper_free to free a whisper context.
     *
     * @param ctx the whisper context to free
     */
    public static void free(MemorySegment ctx) {
        try {
            MethodHandle mh =
                    downcall("whisper_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            mh.invoke(ctx);
        } catch (Throwable t) {
            // best-effort cleanup
        }
    }

    private static MethodHandle downcall(String name, FunctionDescriptor desc) {
        MemorySegment symbol =
                lookup.find(name)
                        .orElseThrow(() -> new UnsatisfiedLinkError("Symbol not found: " + name));
        return Linker.nativeLinker().downcallHandle(symbol, desc);
    }
}
