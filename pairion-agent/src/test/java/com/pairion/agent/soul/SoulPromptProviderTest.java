package com.pairion.agent.soul;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests for {@link DefaultSoulPromptProvider}. */
class SoulPromptProviderTest {

    @Test
    void returnsPlaceholderPrompt() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider();
        String prompt = provider.getSystemPrompt("session-1");
        assertThat(prompt).contains("Pairion");
        assertThat(prompt).contains("household");
    }

    @Test
    void promptIsSameForDifferentSessions() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider();
        assertThat(provider.getSystemPrompt("s1")).isEqualTo(provider.getSystemPrompt("s2"));
    }
}
