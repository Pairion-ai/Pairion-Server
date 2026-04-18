package com.pairion.adapters.llm.spi;

import com.pairion.core.llm.LlmCapabilities;
import com.pairion.core.llm.LlmEvent;
import com.pairion.core.llm.LlmRequest;
import java.util.function.Consumer;

/**
 * Service provider interface for Large Language Model adapters.
 *
 * <p>Implementations wrap vendor-specific SDKs (Anthropic, OpenAI-compatible, etc.) and expose a
 * uniform streaming generation interface. Vendor SDK imports are only permitted inside the
 * implementation subpackage.
 */
public interface LlmAdapter {

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
    LlmCapabilities capabilities();

    /**
     * Generates a streaming LLM completion, emitting events to the provided consumer.
     *
     * <p>This method blocks the calling virtual thread until generation is complete. Events are
     * delivered in order: zero or more {@link LlmEvent.TokenDelta}, optionally interspersed with
     * {@link LlmEvent.ToolCallRequest}/{@link LlmEvent.ToolCallResult} pairs, ending with a single
     * {@link LlmEvent.Stop}.
     *
     * @param request the generation request
     * @param eventConsumer callback receiving streaming events
     */
    void generate(LlmRequest request, Consumer<LlmEvent> eventConsumer);
}
