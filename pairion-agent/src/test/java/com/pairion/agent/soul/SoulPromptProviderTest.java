package com.pairion.agent.soul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pairion.memory.entity.Preference;
import com.pairion.memory.service.MemoryService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for {@link DefaultSoulPromptProvider}. */
class SoulPromptProviderTest {

    @Test
    void returnsPlaceholderPromptWhenNoMemoryService() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider(null);
        String prompt = provider.getSystemPrompt("session-1", "user-1", "query");
        assertThat(prompt).contains("Jarvis");
        assertThat(prompt).contains("household");
    }

    @Test
    void promptIsSameForDifferentSessionsWhenNoMemoryService() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider(null);
        assertThat(provider.getSystemPrompt("s1", "u1", "q"))
                .isEqualTo(provider.getSystemPrompt("s2", "u2", "q"));
    }

    @Test
    void promptInstructsNoMarkdown() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider(null);
        String prompt = provider.getSystemPrompt("session-1", "user-1", "q").toLowerCase();
        assertThat(prompt).contains("never use markdown");
    }

    @Test
    void promptInstructsConciseResponses() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider(null);
        String prompt = provider.getSystemPrompt("session-1", "user-1", "q").toLowerCase();
        assertThat(prompt).contains("concise");
    }

    @Test
    void promptInstructsNoReasoningNarration() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider(null);
        String prompt = provider.getSystemPrompt("session-1", "user-1", "q").toLowerCase();
        assertThat(prompt).contains("answer directly");
    }

    @Test
    void promptInstructsPlainNumbersNotSymbols() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider(null);
        String prompt = provider.getSystemPrompt("session-1", "user-1", "q").toLowerCase();
        assertThat(prompt).contains("plain numbers");
    }

    @Test
    void promptInstructsTtsAwareness() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider(null);
        String prompt = provider.getSystemPrompt("session-1", "user-1", "q").toLowerCase();
        assertThat(prompt).contains("text-to-speech");
    }

    @Test
    void promptInstructsSetBackgroundForGlobe() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider(null);
        String prompt = provider.getSystemPrompt("session-1", "user-1", "q");
        assertThat(prompt).contains("set_background");
        assertThat(prompt).contains("globe");
    }

    @Test
    void promptInstructsSetBackgroundForSpace() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider(null);
        String prompt = provider.getSystemPrompt("session-1", "user-1", "q");
        assertThat(prompt).contains("space");
    }

    @Test
    void promptInstructsSetBackgroundForDashboard() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider(null);
        String prompt = provider.getSystemPrompt("session-1", "user-1", "q");
        assertThat(prompt).contains("dashboard");
    }

    @Test
    void promptInstructsSetBackgroundForVfr() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider(null);
        String prompt = provider.getSystemPrompt("session-1", "user-1", "q");
        assertThat(prompt).contains("vfr");
    }

    @Test
    void promptInstructsAddOverlayTool() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider(null);
        String prompt = provider.getSystemPrompt("session-1", "user-1", "q");
        assertThat(prompt).contains("add_overlay");
        assertThat(prompt).contains("adsb");
    }

    @Test
    void promptInstructsShowAdsbRadarTool() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider(null);
        String prompt = provider.getSystemPrompt("session-1", "user-1", "q");
        assertThat(prompt).contains("show_adsb_radar");
    }

    @Test
    void promptInstructsWeatherRadarOverlay() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider(null);
        String prompt = provider.getSystemPrompt("session-1", "user-1", "q");
        assertThat(prompt).contains("weather_radar");
        assertThat(prompt).contains("rain");
    }

    @Test
    void promptPairsWeatherRadarWithOsmNotGlobe() {
        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider(null);
        String prompt = provider.getSystemPrompt("session-1", "user-1", "q");
        assertThat(prompt).contains("osm");
        assertThat(prompt).containsIgnoringCase("never globe");
    }

    // ── Memory augmentation tests ────────────────────────────────────────────

    @Test
    void promptReturnedUnchangedWhenMemoryServiceHasNoMemory() {
        MemoryService memoryService = mock(MemoryService.class);
        MemoryService.MemoryContext emptyCtx =
                new MemoryService.MemoryContext(List.of(), List.of(), false);
        when(memoryService.recall(anyString(), anyString(), anyInt())).thenReturn(emptyCtx);

        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider(memoryService);
        String prompt = provider.getSystemPrompt("s1", "user-1", "hello");

        assertThat(prompt).doesNotContain("## What you remember");
    }

    @Test
    void promptAppendsPreferencesSectionWhenHasMemory() {
        MemoryService memoryService = mock(MemoryService.class);

        Preference pref = new Preference();
        pref.setKey("location");
        pref.setValue("Dallas");

        MemoryService.MemoryContext ctx =
                new MemoryService.MemoryContext(List.of(), List.of(pref), true);
        when(memoryService.recall(anyString(), anyString(), anyInt())).thenReturn(ctx);

        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider(memoryService);
        String prompt = provider.getSystemPrompt("s1", "user-1", "hello");

        assertThat(prompt).contains("## What you remember about this user");
        assertThat(prompt).contains("- Location: Dallas");
    }

    @Test
    void promptAppendsEpisodeSectionWhenHasEpisodes() {
        MemoryService memoryService = mock(MemoryService.class);
        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);

        MemoryService.EpisodeSummary ep =
                new MemoryService.EpisodeSummary("User asked about weather.", yesterday, 0.9);

        MemoryService.MemoryContext ctx =
                new MemoryService.MemoryContext(List.of(ep), List.of(), true);
        when(memoryService.recall(anyString(), anyString(), anyInt())).thenReturn(ctx);

        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider(memoryService);
        String prompt = provider.getSystemPrompt("s1", "user-1", "weather");

        assertThat(prompt).contains("### Relevant past conversations");
        assertThat(prompt).contains("[yesterday]");
        assertThat(prompt).contains("User asked about weather.");
    }

    @Test
    void promptHandlesMemoryServiceExceptionGracefully() {
        MemoryService memoryService = mock(MemoryService.class);
        when(memoryService.recall(anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("DB error"));

        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider(memoryService);
        String prompt = provider.getSystemPrompt("s1", "user-1", "hello");

        // Falls back to base prompt
        assertThat(prompt).contains("Jarvis");
        assertThat(prompt).doesNotContain("## What you remember");
    }

    @Test
    void promptCapitalizesPreferenceKeys() {
        MemoryService memoryService = mock(MemoryService.class);

        Preference pref = new Preference();
        pref.setKey("name");
        pref.setValue("Alice");

        MemoryService.MemoryContext ctx =
                new MemoryService.MemoryContext(List.of(), List.of(pref), true);
        when(memoryService.recall(anyString(), anyString(), anyInt())).thenReturn(ctx);

        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider(memoryService);
        String prompt = provider.getSystemPrompt("s1", "user-1", "hello");

        assertThat(prompt).contains("- Name: Alice");
    }

    @Test
    void promptWithNullKeyDoesNotCapitalize() {
        MemoryService memoryService = mock(MemoryService.class);

        Preference pref = new Preference();
        pref.setKey(null);
        pref.setValue("someValue");

        MemoryService.MemoryContext ctx =
                new MemoryService.MemoryContext(List.of(), List.of(pref), true);
        when(memoryService.recall(anyString(), anyString(), anyInt())).thenReturn(ctx);

        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider(memoryService);
        // Should not throw; null key is passed through capitalize which returns null
        String prompt = provider.getSystemPrompt("s1", "user-1", "hello");

        assertThat(prompt).contains("## What you remember about this user");
    }

    @Test
    void promptWithEmptyKeyDoesNotCapitalize() {
        MemoryService memoryService = mock(MemoryService.class);

        Preference pref = new Preference();
        pref.setKey("");
        pref.setValue("someValue");

        MemoryService.MemoryContext ctx =
                new MemoryService.MemoryContext(List.of(), List.of(pref), true);
        when(memoryService.recall(anyString(), anyString(), anyInt())).thenReturn(ctx);

        DefaultSoulPromptProvider provider = new DefaultSoulPromptProvider(memoryService);
        // Empty key is passed through capitalize which returns it unchanged
        String prompt = provider.getSystemPrompt("s1", "user-1", "hello");

        assertThat(prompt).contains("## What you remember about this user");
    }
}
