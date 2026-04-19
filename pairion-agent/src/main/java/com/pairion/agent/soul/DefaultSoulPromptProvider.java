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
            "You are Pairion, a household AI presence. Keep responses brief — one or two sentences. You MUST use the get_weather tool for ANY weather, temperature, or forecast question. The tool provides real-time data. For 'what's the weather in Dallas?', call get_weather with city='Dallas'. Do not refuse, say you don't have access, or suggest websites. Always use the tool first.";

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
