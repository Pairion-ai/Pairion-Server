package com.pairion.memory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pairion.adapters.llm.spi.LlmAdapter;
import com.pairion.adapters.vectorstore.spi.VectorStoreAdapter;
import com.pairion.core.llm.LlmEvent;
import com.pairion.core.llm.LlmRequest;
import com.pairion.memory.entity.Episode;
import com.pairion.memory.entity.Preference;
import com.pairion.memory.entity.Turn;
import com.pairion.memory.repository.EpisodeRepository;
import com.pairion.memory.repository.PreferenceRepository;
import com.pairion.memory.repository.TurnRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/** Unit tests for {@link MemoryService}. */
class MemoryServiceTest {

    private EpisodeRepository episodeRepository;
    private TurnRepository turnRepository;
    private PreferenceRepository preferenceRepository;
    private VectorStoreAdapter vectorStoreAdapter;
    private LlmAdapter llmAdapter;
    private MemoryService memoryService;

    @BeforeEach
    void setUp() {
        episodeRepository = mock(EpisodeRepository.class);
        turnRepository = mock(TurnRepository.class);
        preferenceRepository = mock(PreferenceRepository.class);
        vectorStoreAdapter = mock(VectorStoreAdapter.class);
        llmAdapter = mock(LlmAdapter.class);
        memoryService =
                new MemoryService(
                        episodeRepository,
                        turnRepository,
                        preferenceRepository,
                        vectorStoreAdapter,
                        llmAdapter);
    }

    // ── startEpisode ────────────────────────────────────────────────────────

    @Test
    void startEpisodeCreatesEpisodeWithCorrectFields() {
        when(episodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Episode result = memoryService.startEpisode("session-1", "user-1");

        assertThat(result.getSessionId()).isEqualTo("session-1");
        assertThat(result.getUserId()).isEqualTo("user-1");
        assertThat(result.getStartedAt()).isNotNull();
        verify(episodeRepository).save(any(Episode.class));
    }

    // ── recordTurn ──────────────────────────────────────────────────────────

    @Test
    void recordTurnWhenEpisodeExistsCreatesTurnWithCorrectOrdinal() {
        Episode ep = makeEpisode("session-1", "user-1");
        when(episodeRepository.findBySessionId("session-1")).thenReturn(Optional.of(ep));
        when(turnRepository.countByEpisode(ep)).thenReturn(2L);
        when(turnRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Turn result = memoryService.recordTurn("session-1", "user", "Hello");

        assertThat(result).isNotNull();
        assertThat(result.getRole()).isEqualTo("user");
        assertThat(result.getContent()).isEqualTo("Hello");
        assertThat(result.getOrdinal()).isEqualTo(3); // count + 1
        verify(turnRepository).save(any(Turn.class));
    }

    @Test
    void recordTurnWhenNoEpisodeReturnsNull() {
        when(episodeRepository.findBySessionId("session-x")).thenReturn(Optional.empty());

        Turn result = memoryService.recordTurn("session-x", "user", "Hello");

        assertThat(result).isNull();
        verify(turnRepository, never()).save(any());
    }

    // ── endEpisode ──────────────────────────────────────────────────────────

    @Test
    void endEpisodeWhenEpisodeExistsSetsEndedAtAndCallsAsync() throws InterruptedException {
        Episode ep = makeEpisode("session-1", "user-1");
        ep.setId(UUID.randomUUID());
        when(episodeRepository.findBySessionId("session-1")).thenReturn(Optional.of(ep));
        when(episodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(turnRepository.countByEpisode(ep)).thenReturn(3L);
        when(episodeRepository.findById(ep.getId())).thenReturn(Optional.of(ep));
        when(turnRepository.findByEpisodeOrderByOrdinalAsc(ep)).thenReturn(List.of());

        memoryService.endEpisode("session-1");

        // Allow virtual thread to complete
        Thread.sleep(100);

        assertThat(ep.getEndedAt()).isNotNull();
        assertThat(ep.getTurnCount()).isEqualTo(3);
    }

    @Test
    void endEpisodeWhenNoEpisodeIsNoOp() {
        when(episodeRepository.findBySessionId("session-x")).thenReturn(Optional.empty());

        memoryService.endEpisode("session-x");

        verify(episodeRepository, never()).save(any());
    }

    // ── generateSummaryAsync ────────────────────────────────────────────────

    @Test
    void generateSummaryAsyncWhenEpisodeNotFoundIsNoOp() {
        UUID episodeId = UUID.randomUUID();
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.empty());

        memoryService.generateSummaryAsync(episodeId, "user-1");

        verify(llmAdapter, never()).generate(any(), any());
    }

    @Test
    void generateSummaryAsyncWhenNoTurnsIsNoOp() {
        UUID episodeId = UUID.randomUUID();
        Episode ep = makeEpisode("session-1", "user-1");
        ep.setId(episodeId);
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(ep));
        when(turnRepository.findByEpisodeOrderByOrdinalAsc(ep)).thenReturn(List.of());

        memoryService.generateSummaryAsync(episodeId, "user-1");

        verify(llmAdapter, never()).generate(any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void generateSummaryAsyncCallsLlmAndParsesSummary() {
        UUID episodeId = UUID.randomUUID();
        Episode ep = makeEpisode("session-1", "user-1");
        ep.setId(episodeId);

        Turn t1 = makeTurn(ep, "user", "Hi, I live in Dallas", 1);
        Turn t2 = makeTurn(ep, "assistant", "Nice, I know Dallas well.", 2);

        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(ep));
        when(turnRepository.findByEpisodeOrderByOrdinalAsc(ep)).thenReturn(List.of(t1, t2));
        when(episodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(preferenceRepository.findByUserIdAndKey(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(preferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String llmJson =
                "{\"summary\": \"User said they live in Dallas.\","
                        + " \"preferences\": [{\"key\": \"location\", \"value\": \"Dallas\","
                        + " \"confidence\": 0.9}]}";

        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            consumer.accept(new LlmEvent.TokenDelta(llmJson));
                            consumer.accept(new LlmEvent.Stop(10));
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(LlmRequest.class), any());

        memoryService.generateSummaryAsync(episodeId, "user-1");

        verify(episodeRepository).save(ep);
        assertThat(ep.getSummary()).isEqualTo("User said they live in Dallas.");
        verify(preferenceRepository).save(any(Preference.class));
        verify(vectorStoreAdapter)
                .store(eq("user-1"), eq("episode_summary"), eq(episodeId), anyString());
    }

    @SuppressWarnings("unchecked")
    @Test
    void generateSummaryAsyncWhenMalformedJsonStoresRawText() {
        UUID episodeId = UUID.randomUUID();
        Episode ep = makeEpisode("session-1", "user-1");
        ep.setId(episodeId);

        Turn t1 = makeTurn(ep, "user", "Hello", 1);

        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(ep));
        when(turnRepository.findByEpisodeOrderByOrdinalAsc(ep)).thenReturn(List.of(t1));
        when(episodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String rawResponse = "This is not valid JSON at all";
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            consumer.accept(new LlmEvent.TokenDelta(rawResponse));
                            consumer.accept(new LlmEvent.Stop(5));
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(LlmRequest.class), any());

        memoryService.generateSummaryAsync(episodeId, "user-1");

        assertThat(ep.getSummary()).isEqualTo(rawResponse);
    }

    @SuppressWarnings("unchecked")
    @Test
    void generateSummaryAsyncSkipsPreferenceWithMissingKey() {
        UUID episodeId = UUID.randomUUID();
        Episode ep = makeEpisode("session-1", "user-1");
        ep.setId(episodeId);

        Turn t1 = makeTurn(ep, "user", "Hello", 1);

        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(ep));
        when(turnRepository.findByEpisodeOrderByOrdinalAsc(ep)).thenReturn(List.of(t1));
        when(episodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Preference with no key field — key will be null from asText(null)
        String llmJson =
                "{\"summary\": \"Test summary.\","
                        + " \"preferences\": [{\"value\": \"Dallas\", \"confidence\": 0.9}]}";
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            consumer.accept(new LlmEvent.TokenDelta(llmJson));
                            consumer.accept(new LlmEvent.Stop(5));
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(LlmRequest.class), any());

        memoryService.generateSummaryAsync(episodeId, "user-1");

        // Preference should be skipped (key is null)
        verify(preferenceRepository, never()).save(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void generateSummaryAsyncSkipsPreferenceWithMissingValue() {
        UUID episodeId = UUID.randomUUID();
        Episode ep = makeEpisode("session-1", "user-1");
        ep.setId(episodeId);

        Turn t1 = makeTurn(ep, "user", "Hello", 1);

        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(ep));
        when(turnRepository.findByEpisodeOrderByOrdinalAsc(ep)).thenReturn(List.of(t1));
        when(episodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Preference with no value field — value will be null from asText(null)
        String llmJson =
                "{\"summary\": \"Test summary.\","
                        + " \"preferences\": [{\"key\": \"location\", \"confidence\": 0.9}]}";
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            consumer.accept(new LlmEvent.TokenDelta(llmJson));
                            consumer.accept(new LlmEvent.Stop(5));
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(LlmRequest.class), any());

        memoryService.generateSummaryAsync(episodeId, "user-1");

        // Preference should be skipped (value is null)
        verify(preferenceRepository, never()).save(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void generateSummaryAsyncWhenSummaryIsBlankDoesNotCallVectorStore() {
        UUID episodeId = UUID.randomUUID();
        Episode ep = makeEpisode("session-1", "user-1");
        ep.setId(episodeId);

        Turn t1 = makeTurn(ep, "user", "Hello", 1);

        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(ep));
        when(turnRepository.findByEpisodeOrderByOrdinalAsc(ep)).thenReturn(List.of(t1));
        when(episodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // JSON with empty summary string — summaryText becomes blank after parsing
        String llmJson = "{\"summary\": \"\", \"preferences\": []}";
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            consumer.accept(new LlmEvent.TokenDelta(llmJson));
                            consumer.accept(new LlmEvent.Stop(5));
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(LlmRequest.class), any());

        memoryService.generateSummaryAsync(episodeId, "user-1");

        verify(vectorStoreAdapter, never()).store(any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void generateSummaryAsyncWhenPreferencesFieldMissingSkipsPreferenceExtraction() {
        UUID episodeId = UUID.randomUUID();
        Episode ep = makeEpisode("session-1", "user-1");
        ep.setId(episodeId);

        Turn t1 = makeTurn(ep, "user", "Hello", 1);

        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(ep));
        when(turnRepository.findByEpisodeOrderByOrdinalAsc(ep)).thenReturn(List.of(t1));
        when(episodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Valid JSON but no "preferences" field — root.path("preferences").isArray() returns false
        String llmJson = "{\"summary\": \"No prefs here.\"}";
        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            consumer.accept(new LlmEvent.TokenDelta(llmJson));
                            consumer.accept(new LlmEvent.Stop(3));
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(LlmRequest.class), any());

        memoryService.generateSummaryAsync(episodeId, "user-1");

        // Summary should be stored; no preference should be saved
        assertThat(ep.getSummary()).isEqualTo("No prefs here.");
        verify(preferenceRepository, never()).save(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void generateSummaryAsyncWhenLlmThrowsExceptionLogsAndContinues() {
        UUID episodeId = UUID.randomUUID();
        Episode ep = makeEpisode("session-1", "user-1");
        ep.setId(episodeId);

        Turn t1 = makeTurn(ep, "user", "Hello", 1);

        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(ep));
        when(turnRepository.findByEpisodeOrderByOrdinalAsc(ep)).thenReturn(List.of(t1));

        doAnswer(
                        inv -> {
                            throw new RuntimeException("LLM network error");
                        })
                .when(llmAdapter)
                .generate(any(LlmRequest.class), any());

        // Should not throw; empty response path, so no save
        memoryService.generateSummaryAsync(episodeId, "user-1");

        verify(episodeRepository, never()).save(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void generateSummaryAsyncWhenLlmReturnsEmptyDoesNotSave() {
        UUID episodeId = UUID.randomUUID();
        Episode ep = makeEpisode("session-1", "user-1");
        ep.setId(episodeId);

        Turn t1 = makeTurn(ep, "user", "Hello", 1);

        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(ep));
        when(turnRepository.findByEpisodeOrderByOrdinalAsc(ep)).thenReturn(List.of(t1));

        doAnswer(
                        inv -> {
                            Consumer<LlmEvent> consumer = inv.getArgument(1);
                            consumer.accept(new LlmEvent.Stop(0));
                            return null;
                        })
                .when(llmAdapter)
                .generate(any(LlmRequest.class), any());

        memoryService.generateSummaryAsync(episodeId, "user-1");

        verify(episodeRepository, never()).save(any());
    }

    // ── recall ──────────────────────────────────────────────────────────────

    @Test
    void recallWithResultsReturnsMemoryContextWithHasMemoryTrue() {
        UUID epId = UUID.randomUUID();
        Episode ep = makeEpisode("session-1", "user-1");
        ep.setId(epId);
        ep.setSummary("User asked about weather.");
        ep.setStartedAt(Instant.now().minus(1, ChronoUnit.DAYS));

        VectorStoreAdapter.VectorSearchResult result =
                new VectorStoreAdapter.VectorSearchResult("episode_summary", epId, "text", 0.85);

        when(vectorStoreAdapter.search("user-1", "weather", 5)).thenReturn(List.of(result));
        when(episodeRepository.findById(epId)).thenReturn(Optional.of(ep));
        when(preferenceRepository.findByUserId("user-1")).thenReturn(List.of());

        MemoryService.MemoryContext context = memoryService.recall("user-1", "weather", 5);

        assertThat(context.hasMemory()).isTrue();
        assertThat(context.relevantEpisodes()).hasSize(1);
        assertThat(context.relevantEpisodes().get(0).summary())
                .isEqualTo("User asked about weather.");
    }

    @Test
    void recallWithNoResultsReturnsHasMemoryFalse() {
        when(vectorStoreAdapter.search("user-1", "query", 5)).thenReturn(List.of());
        when(preferenceRepository.findByUserId("user-1")).thenReturn(List.of());

        MemoryService.MemoryContext context = memoryService.recall("user-1", "query", 5);

        assertThat(context.hasMemory()).isFalse();
        assertThat(context.relevantEpisodes()).isEmpty();
        assertThat(context.preferences()).isEmpty();
    }

    @Test
    void recallSkipsEpisodesWithNullSummary() {
        UUID epId = UUID.randomUUID();
        Episode ep = makeEpisode("session-1", "user-1");
        ep.setId(epId);
        ep.setSummary(null); // no summary yet

        VectorStoreAdapter.VectorSearchResult result =
                new VectorStoreAdapter.VectorSearchResult("episode_summary", epId, "text", 0.85);

        when(vectorStoreAdapter.search("user-1", "query", 5)).thenReturn(List.of(result));
        when(episodeRepository.findById(epId)).thenReturn(Optional.of(ep));
        when(preferenceRepository.findByUserId("user-1")).thenReturn(List.of());

        MemoryService.MemoryContext context = memoryService.recall("user-1", "query", 5);

        assertThat(context.relevantEpisodes()).isEmpty();
        assertThat(context.hasMemory()).isFalse();
    }

    @Test
    void recallSkipsNonEpisodeSummaryContentTypes() {
        UUID id = UUID.randomUUID();
        VectorStoreAdapter.VectorSearchResult result =
                new VectorStoreAdapter.VectorSearchResult("turn", id, "text", 0.9);

        when(vectorStoreAdapter.search("user-1", "q", 5)).thenReturn(List.of(result));
        when(preferenceRepository.findByUserId("user-1")).thenReturn(List.of());

        MemoryService.MemoryContext context = memoryService.recall("user-1", "q", 5);

        assertThat(context.relevantEpisodes()).isEmpty();
    }

    @Test
    void recallWithPreferencesOnlyHasMemoryTrue() {
        when(vectorStoreAdapter.search("user-1", "q", 5)).thenReturn(List.of());
        Preference pref = new Preference();
        pref.setKey("location");
        pref.setValue("Dallas");
        when(preferenceRepository.findByUserId("user-1")).thenReturn(List.of(pref));

        MemoryService.MemoryContext context = memoryService.recall("user-1", "q", 5);

        assertThat(context.hasMemory()).isTrue();
        assertThat(context.preferences()).hasSize(1);
    }

    // ── getPreferences / getPreference ──────────────────────────────────────

    @Test
    void getPreferencesReturnsAllPreferences() {
        Preference pref = new Preference();
        pref.setKey("name");
        pref.setValue("Alice");
        when(preferenceRepository.findByUserId("user-1")).thenReturn(List.of(pref));

        List<Preference> result = memoryService.getPreferences("user-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getKey()).isEqualTo("name");
    }

    @Test
    void getPreferenceReturnsSpecificPreference() {
        Preference pref = new Preference();
        pref.setKey("location");
        pref.setValue("Dallas");
        when(preferenceRepository.findByUserIdAndKey("user-1", "location"))
                .thenReturn(Optional.of(pref));

        Optional<Preference> result = memoryService.getPreference("user-1", "location");

        assertThat(result).isPresent();
        assertThat(result.get().getValue()).isEqualTo("Dallas");
    }

    @Test
    void getPreferenceReturnsEmptyWhenNotFound() {
        when(preferenceRepository.findByUserIdAndKey("user-1", "missing"))
                .thenReturn(Optional.empty());

        Optional<Preference> result = memoryService.getPreference("user-1", "missing");

        assertThat(result).isEmpty();
    }

    // ── getEpisodes ──────────────────────────────────────────────────────────

    @Test
    void getEpisodesReturnsPagedResults() {
        Episode ep = makeEpisode("session-1", "user-1");
        when(episodeRepository.findByUserIdOrderByStartedAtDesc(eq("user-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ep)));

        List<Episode> results = memoryService.getEpisodes("user-1", 10);

        assertThat(results).hasSize(1);
    }

    // ── getTurns ────────────────────────────────────────────────────────────

    @Test
    void getTurnsReturnsOrderedTurns() {
        UUID episodeId = UUID.randomUUID();
        Episode ep = makeEpisode("session-1", "user-1");
        ep.setId(episodeId);
        Turn t1 = makeTurn(ep, "user", "Hello", 1);
        Turn t2 = makeTurn(ep, "assistant", "Hi!", 2);

        when(episodeRepository.findById(episodeId)).thenReturn(Optional.of(ep));
        when(turnRepository.findByEpisodeOrderByOrdinalAsc(ep)).thenReturn(List.of(t1, t2));

        List<Turn> result = memoryService.getTurns(episodeId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRole()).isEqualTo("user");
    }

    @Test
    void getTurnsReturnsEmptyWhenEpisodeNotFound() {
        UUID episodeId = UUID.randomUUID();
        when(episodeRepository.findById(episodeId)).thenReturn(Optional.empty());

        List<Turn> result = memoryService.getTurns(episodeId);

        assertThat(result).isEmpty();
    }

    // ── formatRelativeTime ──────────────────────────────────────────────────

    @Test
    void formatRelativeTimeToday() {
        assertThat(MemoryService.formatRelativeTime(Instant.now())).isEqualTo("today");
    }

    @Test
    void formatRelativeTimeYesterday() {
        assertThat(MemoryService.formatRelativeTime(Instant.now().minus(1, ChronoUnit.DAYS)))
                .isEqualTo("yesterday");
    }

    @Test
    void formatRelativeTimeDaysAgo() {
        assertThat(MemoryService.formatRelativeTime(Instant.now().minus(3, ChronoUnit.DAYS)))
                .isEqualTo("3 days ago");
    }

    @Test
    void formatRelativeTimeWeeksAgo() {
        assertThat(MemoryService.formatRelativeTime(Instant.now().minus(14, ChronoUnit.DAYS)))
                .isEqualTo("2 weeks ago");
    }

    @Test
    void formatRelativeTimeOneWeekAgo() {
        assertThat(MemoryService.formatRelativeTime(Instant.now().minus(7, ChronoUnit.DAYS)))
                .isEqualTo("1 week ago");
    }

    @Test
    void formatRelativeTimeMonthsAgo() {
        assertThat(MemoryService.formatRelativeTime(Instant.now().minus(60, ChronoUnit.DAYS)))
                .isEqualTo("2 months ago");
    }

    @Test
    void formatRelativeTimeOneMonthAgo() {
        assertThat(MemoryService.formatRelativeTime(Instant.now().minus(30, ChronoUnit.DAYS)))
                .isEqualTo("1 month ago");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Episode makeEpisode(String sessionId, String userId) {
        Episode ep = new Episode();
        ep.setSessionId(sessionId);
        ep.setUserId(userId);
        ep.setStartedAt(Instant.now());
        return ep;
    }

    private Turn makeTurn(Episode ep, String role, String content, int ordinal) {
        Turn t = new Turn();
        t.setEpisode(ep);
        t.setRole(role);
        t.setContent(content);
        t.setOrdinal(ordinal);
        t.setCreatedAt(Instant.now());
        t.setUserId(ep.getUserId());
        return t;
    }
}
