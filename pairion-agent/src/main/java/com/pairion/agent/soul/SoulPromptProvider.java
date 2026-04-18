package com.pairion.agent.soul;

/**
 * Provides the system prompt (SOUL) for LLM generation.
 *
 * <p>Implementations supply the persona, instructions, and context that shape Pairion's behavior.
 * The full SOUL system will incorporate memory context, user preferences, and dynamic instructions;
 * the current implementation returns a hardcoded placeholder.
 */
public interface SoulPromptProvider {

    /**
     * Returns the system prompt for a given session.
     *
     * @param sessionId the current WebSocket session ID
     * @return the system prompt text
     */
    String getSystemPrompt(String sessionId);
}
