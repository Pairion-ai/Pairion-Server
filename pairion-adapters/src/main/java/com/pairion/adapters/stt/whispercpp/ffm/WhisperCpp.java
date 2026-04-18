package com.pairion.adapters.stt.whispercpp.ffm;

import com.pairion.nativelib.whisper.NativeLibraryLoader;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FFM bindings for the whisper.cpp C API, pinned to v1.8.4 ABI.
 *
 * <p>Uses pointer-returning API functions ({@code _by_ref}) to obtain default parameter structs,
 * then copies them into managed memory for by-value function calls. Struct sizes are measured from
 * the pinned whisper.cpp version: {@code whisper_context_params} = 48 bytes, {@code
 * whisper_full_params} = 304 bytes.
 *
 * <p>The native library is loaded from the classpath via {@link NativeLibraryLoader}.
 */
public final class WhisperCpp {

    private static final Logger log = LoggerFactory.getLogger(WhisperCpp.class);

    /** Size of whisper_context_params struct in bytes (v1.8.4). */
    static final int CONTEXT_PARAMS_SIZE = 48;

    /** Size of whisper_full_params struct in bytes (v1.8.4). */
    static final int FULL_PARAMS_SIZE = 304;

    private WhisperCpp() {}

    private static SymbolLookup lookup;

    /**
     * Initializes the FFM bindings by loading the bundled whisper.cpp library.
     *
     * @return true if the library was loaded and symbols are resolvable
     */
    public static synchronized boolean initialize() {
        if (lookup != null) {
            return true;
        }
        boolean loaded = NativeLibraryLoader.load();
        if (loaded) {
            lookup = SymbolLookup.loaderLookup();
            log.info("whisper.cpp FFM bindings initialized");
        }
        return loaded;
    }

    /**
     * Returns whether the library has been loaded and symbols are available.
     *
     * @return true if initialized
     */
    public static boolean isInitialized() {
        return lookup != null;
    }

    /**
     * Initializes a whisper context from a model file with default parameters.
     *
     * @param modelPath path to the GGML model file
     * @param arena arena for native memory allocation
     * @return the whisper_context pointer, or null on failure
     */
    public static MemorySegment initFromFile(String modelPath, Arena arena) {
        try {
            MethodHandle getDefaults =
                    downcall(
                            "whisper_context_default_params_by_ref",
                            FunctionDescriptor.of(ValueLayout.ADDRESS));
            MemorySegment defaultsPtr = (MemorySegment) getDefaults.invoke();

            MemorySegment params = arena.allocate(CONTEXT_PARAMS_SIZE);
            params.copyFrom(defaultsPtr.reinterpret(CONTEXT_PARAMS_SIZE));

            MemorySegment pathSeg = arena.allocateUtf8String(modelPath);
            MethodHandle initFn =
                    downcall(
                            "whisper_init_from_file_with_params",
                            FunctionDescriptor.of(
                                    ValueLayout.ADDRESS,
                                    ValueLayout.ADDRESS,
                                    MemoryLayout.structLayout(
                                            MemoryLayout.sequenceLayout(
                                                    CONTEXT_PARAMS_SIZE, ValueLayout.JAVA_BYTE))));
            MemorySegment ctx = (MemorySegment) initFn.invoke(pathSeg, params);

            if (ctx.equals(MemorySegment.NULL)) {
                return null;
            }
            return ctx;
        } catch (Throwable t) {
            log.error("whisper_init_from_file_with_params failed: {}", t.getMessage());
            return null;
        }
    }

    /**
     * Runs full transcription on PCM audio with default greedy-strategy parameters.
     *
     * @param ctx the whisper context
     * @param samples float32 PCM audio samples at 16 kHz
     * @param nSamples number of samples
     * @param arena arena for memory allocation
     * @return 0 on success, negative on failure
     */
    public static int whisperFull(MemorySegment ctx, float[] samples, int nSamples, Arena arena) {
        try {
            MethodHandle getDefaults =
                    downcall(
                            "whisper_full_default_params_by_ref",
                            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            MemorySegment defaultsPtr = (MemorySegment) getDefaults.invoke(0);

            MemorySegment params = arena.allocate(FULL_PARAMS_SIZE);
            params.copyFrom(defaultsPtr.reinterpret(FULL_PARAMS_SIZE));

            MemorySegment samplesSeg = arena.allocateArray(ValueLayout.JAVA_FLOAT, samples);

            MethodHandle fullFn =
                    downcall(
                            "whisper_full",
                            FunctionDescriptor.of(
                                    ValueLayout.JAVA_INT,
                                    ValueLayout.ADDRESS,
                                    MemoryLayout.structLayout(
                                            MemoryLayout.sequenceLayout(
                                                    FULL_PARAMS_SIZE, ValueLayout.JAVA_BYTE)),
                                    ValueLayout.ADDRESS,
                                    ValueLayout.JAVA_INT));
            return (int) fullFn.invoke(ctx, params, samplesSeg, nSamples);
        } catch (Throwable t) {
            log.error("whisper_full failed: {}", t.getMessage());
            return -1;
        }
    }

    /**
     * Returns the number of transcript segments produced by the last whisper_full call.
     *
     * @param ctx the whisper context
     * @return number of segments, or 0 on error
     */
    public static int fullNSegments(MemorySegment ctx) {
        try {
            MethodHandle mh =
                    downcall(
                            "whisper_full_n_segments",
                            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            return (int) mh.invoke(ctx);
        } catch (Throwable t) {
            log.error("whisper_full_n_segments failed: {}", t.getMessage());
            return 0;
        }
    }

    /**
     * Returns the text of a transcript segment.
     *
     * @param ctx the whisper context
     * @param segmentIndex zero-based segment index
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
            log.error("whisper_full_get_segment_text failed: {}", t.getMessage());
            return "";
        }
    }

    /**
     * Frees a whisper context and all associated resources.
     *
     * @param ctx the whisper context to free
     */
    public static void free(MemorySegment ctx) {
        try {
            MethodHandle mh =
                    downcall("whisper_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            mh.invoke(ctx);
        } catch (Throwable t) {
            log.error("whisper_free failed: {}", t.getMessage());
        }
    }

    private static MethodHandle downcall(String name, FunctionDescriptor desc) {
        MemorySegment symbol =
                lookup.find(name)
                        .orElseThrow(() -> new UnsatisfiedLinkError("Symbol not found: " + name));
        return Linker.nativeLinker().downcallHandle(symbol, desc);
    }
}
