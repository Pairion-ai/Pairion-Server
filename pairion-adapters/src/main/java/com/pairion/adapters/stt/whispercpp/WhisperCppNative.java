package com.pairion.adapters.stt.whispercpp;

/**
 * Internal abstraction boundary for whisper.cpp native operations.
 *
 * <p>The production implementation uses Java 21 FFM API to call the real whisper.cpp shared
 * library. The test implementation returns canned transcripts. This boundary enables 100% coverage
 * of the {@link WhisperCppSttAdapter} without a real whisper.cpp installation.
 */
public interface WhisperCppNative {

    /**
     * Whether the native library is loaded and a model is available.
     *
     * @return true if whisper.cpp is ready to transcribe
     */
    boolean isAvailable();

    /**
     * Transcribes accumulated PCM audio data.
     *
     * <p>Accepts 16 kHz mono float32 audio samples and returns the transcript text.
     *
     * @param samples float32 audio samples at 16 kHz
     * @return the transcript text
     */
    String transcribe(float[] samples);

    /**
     * Returns the path where the native library is expected.
     *
     * @return the expected library path
     */
    String expectedLibraryPath();

    /**
     * Returns the path where the model file is expected.
     *
     * @return the expected model path
     */
    String expectedModelPath();
}
