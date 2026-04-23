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
    void promptInstructsSetBackgroundForGlobe() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider();
        String prompt = provider.getSystemPrompt("session-1");
        assertThat(prompt).contains("set_background");
        assertThat(prompt).contains("globe");
    }

    @Test
    void promptInstructsSetBackgroundForSpace() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider();
        String prompt = provider.getSystemPrompt("session-1");
        assertThat(prompt).contains("space");
    }

    @Test
    void promptInstructsSetBackgroundForDashboard() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider();
        String prompt = provider.getSystemPrompt("session-1");
        assertThat(prompt).contains("dashboard");
    }

    @Test
    void promptInstructsSetBackgroundForVfr() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider();
        String prompt = provider.getSystemPrompt("session-1");
        assertThat(prompt).contains("vfr");
    }

    @Test
    void promptInstructsAddOverlayTool() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider();
        String prompt = provider.getSystemPrompt("session-1");
        assertThat(prompt).contains("add_overlay");
        assertThat(prompt).contains("adsb");
    }

    @Test
    void promptInstructsShowAdsbRadarTool() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider();
        String prompt = provider.getSystemPrompt("session-1");
        assertThat(prompt).contains("show_adsb_radar");
    }

    @Test
    void promptInstructsWeatherRadarOverlay() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider();
        String prompt = provider.getSystemPrompt("session-1");
        assertThat(prompt).contains("weather_radar");
        assertThat(prompt).contains("rain");
    }
}
