package com.pairion.core.llm;

/**
 * Describes the capabilities of an LLM adapter.
 *
 * @param available whether the adapter is ready to accept requests
 * @param supportsToolUse whether the adapter supports tool-use calls
 * @param supportsStreaming whether the adapter supports streaming token output
 */
public record LlmCapabilities(
        boolean available, boolean supportsToolUse, boolean supportsStreaming) {

    /**
     * Returns capabilities indicating the adapter is unavailable.
     *
     * @return unavailable capabilities
     */
    public static LlmCapabilities unavailable() {
        return new LlmCapabilities(false, false, false);
    }
}
