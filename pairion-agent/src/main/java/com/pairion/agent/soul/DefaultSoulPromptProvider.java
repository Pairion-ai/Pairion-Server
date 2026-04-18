package com.pairion.agent.soul;

import org.springframework.stereotype.Component;

/**
 * Placeholder SOUL prompt provider for M1.
 *
 * <p>Returns a minimal system prompt. Full SOUL with memory context, user preferences, and persona
 * depth is a later milestone.
 */
@Component
public class DefaultSoulPromptProvider implements SoulPromptProvider {

    private static final String PLACEHOLDER_PROMPT =
            "You are Pairion, a household AI presence. Keep responses brief — one or two sentences.";

    /**
     * Returns the placeholder system prompt.
     *
     * @param sessionId the current session ID (unused in placeholder)
     * @return the placeholder prompt
     */
    @Override
    public String getSystemPrompt(String sessionId) {
        return PLACEHOLDER_PROMPT;
    }
}
