package com.pairion.gateway.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pairion.gateway.rest.dto.EpisodeDto;
import com.pairion.gateway.rest.dto.PreferenceDto;
import com.pairion.gateway.rest.dto.TurnDto;
import com.pairion.memory.entity.Episode;
import com.pairion.memory.entity.Preference;
import com.pairion.memory.entity.Turn;
import com.pairion.memory.service.MemoryService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** Unit tests for {@link MemoryController}. */
class MemoryControllerTest {

    private MemoryService memoryService;
    private MemoryController controller;

    @BeforeEach
    void setUp() {
        memoryService = mock(MemoryService.class);
        controller = new MemoryController(memoryService);
    }

    // ── listEpisodes ─────────────────────────────────────────────────────────

    @Test
    void listEpisodesReturnsEpisodeDtos() {
        Episode ep = makeEpisode(UUID.randomUUID(), "user-1", "session-1");
        when(memoryService.getEpisodes("user-1", 50)).thenReturn(List.of(ep));

        ResponseEntity<List<EpisodeDto>> response = controller.listEpisodes("user-1", 50);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        EpisodeDto dto = response.getBody().get(0);
        assertThat(dto.userId()).isEqualTo("user-1");
        assertThat(dto.sessionId()).isEqualTo("session-1");
        assertThat(dto.turnCount()).isEqualTo(3);
    }

    @Test
    void listEpisodesReturnsEmptyListWhenNoEpisodes() {
        when(memoryService.getEpisodes(anyString(), anyInt())).thenReturn(List.of());

        ResponseEntity<List<EpisodeDto>> response = controller.listEpisodes("user-1", 50);

        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void listEpisodesUsesProvidedLimitParameter() {
        when(memoryService.getEpisodes("user-1", 10)).thenReturn(List.of());

        controller.listEpisodes("user-1", 10);

        verify(memoryService).getEpisodes("user-1", 10);
    }

    @Test
    void listEpisodesDefaultsUserIdWhenNotProvided() {
        when(memoryService.getEpisodes(eq("default-user"), anyInt())).thenReturn(List.of());

        ResponseEntity<List<EpisodeDto>> response = controller.listEpisodes("default-user", 50);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(memoryService).getEpisodes("default-user", 50);
    }

    @Test
    void listEpisodesMapsAllFieldsCorrectly() {
        UUID id = UUID.randomUUID();
        UUID epId = UUID.randomUUID();
        Instant start = Instant.now().minusSeconds(3600);
        Instant end = Instant.now();
        Episode ep = new Episode();
        ep.setId(epId);
        ep.setUserId("user-1");
        ep.setSessionId("session-abc");
        ep.setStartedAt(start);
        ep.setEndedAt(end);
        ep.setSummary("Test summary");
        ep.setTurnCount(5);
        when(memoryService.getEpisodes("user-1", 50)).thenReturn(List.of(ep));

        ResponseEntity<List<EpisodeDto>> response = controller.listEpisodes("user-1", 50);
        EpisodeDto dto = response.getBody().get(0);

        assertThat(dto.id()).isEqualTo(epId);
        assertThat(dto.startedAt()).isEqualTo(start);
        assertThat(dto.endedAt()).isEqualTo(end);
        assertThat(dto.summary()).isEqualTo("Test summary");
        assertThat(dto.turnCount()).isEqualTo(5);
    }

    // ── listTurns ────────────────────────────────────────────────────────────

    @Test
    void listTurnsReturnsTurnDtos() {
        UUID episodeId = UUID.randomUUID();
        Episode ep = makeEpisode(UUID.randomUUID(), "user-1", "session-1");
        Turn turn = makeTurn(ep, "user", "Hello", 1);
        when(memoryService.getTurns(episodeId)).thenReturn(List.of(turn));

        ResponseEntity<List<TurnDto>> response = controller.listTurns(episodeId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        TurnDto dto = response.getBody().get(0);
        assertThat(dto.role()).isEqualTo("user");
        assertThat(dto.content()).isEqualTo("Hello");
        assertThat(dto.ordinal()).isEqualTo(1);
    }

    @Test
    void listTurnsReturnsEmptyListWhenNoTurns() {
        UUID episodeId = UUID.randomUUID();
        when(memoryService.getTurns(episodeId)).thenReturn(List.of());

        ResponseEntity<List<TurnDto>> response = controller.listTurns(episodeId);

        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void listTurnsMapsAllFieldsCorrectly() {
        UUID episodeId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        Episode ep = makeEpisode(UUID.randomUUID(), "user-1", "session-1");
        Turn turn = new Turn();
        turn.setId(turnId);
        turn.setEpisode(ep);
        turn.setUserId("user-1");
        turn.setRole("assistant");
        turn.setContent("I can help with that.");
        turn.setCreatedAt(Instant.now());
        turn.setOrdinal(2);
        when(memoryService.getTurns(episodeId)).thenReturn(List.of(turn));

        ResponseEntity<List<TurnDto>> response = controller.listTurns(episodeId);
        TurnDto dto = response.getBody().get(0);

        assertThat(dto.id()).isEqualTo(turnId);
        assertThat(dto.userId()).isEqualTo("user-1");
        assertThat(dto.role()).isEqualTo("assistant");
        assertThat(dto.ordinal()).isEqualTo(2);
    }

    // ── listPreferences ──────────────────────────────────────────────────────

    @Test
    void listPreferencesReturnsPreferenceDtos() {
        Preference pref = makePreference("user-1", "location", "Dallas");
        when(memoryService.getPreferences("user-1")).thenReturn(List.of(pref));

        ResponseEntity<List<PreferenceDto>> response = controller.listPreferences("user-1");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        PreferenceDto dto = response.getBody().get(0);
        assertThat(dto.key()).isEqualTo("location");
        assertThat(dto.value()).isEqualTo("Dallas");
    }

    @Test
    void listPreferencesReturnsEmptyListWhenNoPreferences() {
        when(memoryService.getPreferences(anyString())).thenReturn(List.of());

        ResponseEntity<List<PreferenceDto>> response = controller.listPreferences("user-1");

        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void listPreferencesDefaultsToDefaultUser() {
        when(memoryService.getPreferences("default-user")).thenReturn(List.of());

        ResponseEntity<List<PreferenceDto>> response = controller.listPreferences("default-user");

        verify(memoryService).getPreferences("default-user");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void listPreferencesMapsAllFieldsCorrectly() {
        UUID prefId = UUID.randomUUID();
        UUID sourceEpId = UUID.randomUUID();
        Preference pref = new Preference();
        pref.setId(prefId);
        pref.setUserId("user-1");
        pref.setKey("name");
        pref.setValue("Alice");
        pref.setSourceEpisodeId(sourceEpId);
        pref.setExtractedAt(Instant.now());
        pref.setConfidence(0.95);
        when(memoryService.getPreferences("user-1")).thenReturn(List.of(pref));

        ResponseEntity<List<PreferenceDto>> response = controller.listPreferences("user-1");
        PreferenceDto dto = response.getBody().get(0);

        assertThat(dto.id()).isEqualTo(prefId);
        assertThat(dto.sourceEpisodeId()).isEqualTo(sourceEpId);
        assertThat(dto.confidence()).isEqualTo(0.95);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Episode makeEpisode(UUID id, String userId, String sessionId) {
        Episode ep = new Episode();
        ep.setId(id);
        ep.setUserId(userId);
        ep.setSessionId(sessionId);
        ep.setStartedAt(Instant.now());
        ep.setTurnCount(3);
        return ep;
    }

    private Turn makeTurn(Episode ep, String role, String content, int ordinal) {
        Turn t = new Turn();
        t.setId(UUID.randomUUID());
        t.setEpisode(ep);
        t.setUserId(ep.getUserId());
        t.setRole(role);
        t.setContent(content);
        t.setCreatedAt(Instant.now());
        t.setOrdinal(ordinal);
        return t;
    }

    private Preference makePreference(String userId, String key, String value) {
        Preference p = new Preference();
        p.setId(UUID.randomUUID());
        p.setUserId(userId);
        p.setKey(key);
        p.setValue(value);
        p.setExtractedAt(Instant.now());
        p.setConfidence(0.9);
        return p;
    }
}
