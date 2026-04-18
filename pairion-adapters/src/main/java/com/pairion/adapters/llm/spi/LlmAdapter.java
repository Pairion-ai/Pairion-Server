package com.pairion.adapters.llm.spi;

/**
 * Service provider interface for Large Language Model adapters.
 *
 * <p>Implementations wrap vendor-specific SDKs (Anthropic, OpenAI, Ollama, etc.) and expose a
 * uniform generation interface to the rest of Pairion.
 */
public interface LlmAdapter {

    /**
     * Returns the human-readable name of this adapter.
     *
     * @return the adapter name
     */
    String name();
}
