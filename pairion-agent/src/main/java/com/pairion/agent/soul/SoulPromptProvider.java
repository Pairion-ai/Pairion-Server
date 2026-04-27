package com.pairion.agent.soul;

/**
 * Provides the system prompt (SOUL) for LLM generation.
 *
 * <p>Implementations supply the persona, instructions, and context that shape Pairion's behavior.
 * The full SOUL system incorporates memory context, user preferences, and dynamic instructions; the
 * current implementation augments a base prompt with recalled memory when available.
 */
public interface SoulPromptProvider {

    /**
     * Returns the system prompt for the given session, user, and current query.
     *
     * <p>Implementations may use the user ID and query text to recall relevant memory and append
     * personalized context to the base prompt.
     *
     * @param sessionId the current WebSocket session ID
     * @param userId the user identifier for memory recall
     * @param userQuery the current user query text for semantic memory search
     * @return the system prompt text, optionally augmented with memory context
     */
    String getSystemPrompt(String sessionId, String userId, String userQuery);
}
