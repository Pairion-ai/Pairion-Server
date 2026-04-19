package com.pairion.adapters.tts.piper;

/**
 * Internal abstraction boundary for the Piper TTS native layer.
 *
 * <p>The production implementation ({@link DefaultPiperTtsNative}) calls the real Piper TTS library
 * via jextract-generated FFM bindings. Test implementations return canned PCM data. This boundary
 * enables 100% coverage of {@link PiperTtsAdapter} without a real Piper installation.
 */
public interface PiperTtsNative {

    /**
     * Returns whether the Piper native library and voice model are loaded and available.
     *
     * @return true if the adapter can synthesize speech
     */
    boolean isAvailable();

    /**
     * Returns the sample rate of the loaded voice in Hz.
     *
     * @return sample rate (e.g. 22050 for en_GB-alan-medium)
     */
    int getSampleRate();

    /**
     * Synthesizes text to raw signed 16-bit little-endian PCM audio.
     *
     * <p>The callback is invoked once per sentence chunk with interleaved PCM bytes. Synthesis is
     * synchronous — this method returns after all callbacks have been dispatched.
     *
     * @param text the UTF-8 text to synthesize
     * @param pcmConsumer callback receiving PCM byte chunks per synthesized sentence
     */
    void synthesize(String text, java.util.function.Consumer<byte[]> pcmConsumer);
}
