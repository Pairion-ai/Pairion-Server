package com.pairion.agent.soul;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests for {@link DefaultSoulPromptProvider}. */
class SoulPromptProviderTest {

    @Test
    void returnsPlaceholderPrompt() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider();
        String prompt = provider.getSystemPrompt("session-1");
        assertThat(prompt).contains("Jarvis");
        assertThat(prompt).contains("household");
    }

    @Test
    void promptIsSameForDifferentSessions() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider();
        assertThat(provider.getSystemPrompt("s1")).isEqualTo(provider.getSystemPrompt("s2"));
    }

    @Test
    void promptInstructsNoMarkdown() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider();
        String prompt = provider.getSystemPrompt("session-1").toLowerCase();
        assertThat(prompt).contains("never use markdown");
    }

    @Test
    void promptInstructsConciseResponses() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider();
        String prompt = provider.getSystemPrompt("session-1").toLowerCase();
        assertThat(prompt).contains("concise");
    }

    @Test
    void promptInstructsNoReasoningNarration() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider();
        String prompt = provider.getSystemPrompt("session-1").toLowerCase();
        assertThat(prompt).contains("answer directly");
    }

    @Test
    void promptInstructsPlainNumbersNotSymbols() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider();
        String prompt = provider.getSystemPrompt("session-1").toLowerCase();
        assertThat(prompt).contains("plain numbers");
    }

    @Test
    void promptInstructsTtsAwareness() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider();
        String prompt = provider.getSystemPrompt("session-1").toLowerCase();
        assertThat(prompt).contains("text-to-speech");
    }

    @Test
    void promptInstructsSetSceneForGlobe() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider();
        String prompt = provider.getSystemPrompt("session-1");
        assertThat(prompt).contains("set_scene");
        assertThat(prompt).contains("globe");
    }

    @Test
    void promptInstructsSetSceneForSpace() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider();
        String prompt = provider.getSystemPrompt("session-1");
        assertThat(prompt).contains("space");
    }

    @Test
    void promptInstructsSetSceneForDashboard() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider();
        String prompt = provider.getSystemPrompt("session-1");
        assertThat(prompt).contains("dashboard");
    }
}
