package com.pairion.adapters.tts.spi;

import com.pairion.core.tts.TtsCapabilities;
import com.pairion.core.tts.TtsEvent;
import java.util.function.Consumer;

/**
 * Service provider interface for Text-to-Speech adapters.
 *
 * <p>Implementations wrap TTS engines (Piper, etc.) and expose a uniform streaming
 * synthesis interface. Matches SttAdapter pattern for consistency.
 */
public interface TtsAdapter {

    /**
     * Returns the human-readable name of this adapter.
     *
     * @return the adapter name
     */
    String name();

    /**
     * Returns the current capabilities of this adapter.
     *
     * @return capability descriptor
     */
    TtsCapabilities capabilities();

    /**
     * Synthesizes text to speech, streaming audio chunks via the consumer.
     *
     * @param text the text to speak
     * @param eventConsumer callback receiving TtsEvent chunks (PCM or Opus) and completion
     */
    void speak(String text, Consumer<TtsEvent> eventConsumer);
}