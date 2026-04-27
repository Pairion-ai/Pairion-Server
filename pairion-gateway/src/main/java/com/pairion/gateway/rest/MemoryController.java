package com.pairion.gateway.rest;

import com.pairion.gateway.rest.dto.EpisodeDto;
import com.pairion.gateway.rest.dto.PreferenceDto;
import com.pairion.gateway.rest.dto.TurnDto;
import com.pairion.memory.entity.Episode;
import com.pairion.memory.entity.Preference;
import com.pairion.memory.entity.Turn;
import com.pairion.memory.service.MemoryService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for memory browsing endpoints.
 *
 * <p>Implements episode, turn, and preference listing as defined in {@code openapi.yaml}. Delegates
 * all data access to {@link MemoryService}.
 */
@RestController
@RequestMapping("/v1/memory")
public class MemoryController {

    private final MemoryService memoryService;

    /**
     * Constructs the controller with the memory service dependency.
     *
     * @param memoryService the memory service for data retrieval
     */
    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    /**
     * Lists episodes for a given user, newest first.
     *
     * @param userId the user whose episodes to list (default: "default-user")
     * @param limit maximum number of episodes to return (default: 50)
     * @return the list of episodes as DTOs
     */
    @GetMapping("/episodes")
    public ResponseEntity<List<EpisodeDto>> listEpisodes(
            @RequestParam(defaultValue = "default-user") String userId,
            @RequestParam(defaultValue = "50") int limit) {
        List<Episode> episodes = memoryService.getEpisodes(userId, limit);
        List<EpisodeDto> dtos =
                episodes.stream()
                        .map(
                                ep ->
                                        new EpisodeDto(
                                                ep.getId(),
                                                ep.getUserId(),
                                                ep.getSessionId(),
                                                ep.getStartedAt(),
                                                ep.getEndedAt(),
                                                ep.getSummary(),
                                                ep.getTurnCount()))
                        .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Lists all turns for a specific episode, in ordinal order.
     *
     * @param episodeId the episode UUID
     * @return the list of turns as DTOs
     */
    @GetMapping("/episodes/{episodeId}/turns")
    public ResponseEntity<List<TurnDto>> listTurns(@PathVariable UUID episodeId) {
        List<Turn> turns = memoryService.getTurns(episodeId);
        List<TurnDto> dtos =
                turns.stream()
                        .map(
                                t ->
                                        new TurnDto(
                                                t.getId(),
                                                t.getUserId(),
                                                t.getRole(),
                                                t.getContent(),
                                                t.getCreatedAt(),
                                                t.getOrdinal()))
                        .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Lists all preferences for a given user.
     *
     * @param userId the user whose preferences to list (default: "default-user")
     * @return the list of preferences as DTOs
     */
    @GetMapping("/preferences")
    public ResponseEntity<List<PreferenceDto>> listPreferences(
            @RequestParam(defaultValue = "default-user") String userId) {
        List<Preference> prefs = memoryService.getPreferences(userId);
        List<PreferenceDto> dtos =
                prefs.stream()
                        .map(
                                p ->
                                        new PreferenceDto(
                                                p.getId(),
                                                p.getUserId(),
                                                p.getKey(),
                                                p.getValue(),
                                                p.getSourceEpisodeId(),
                                                p.getExtractedAt(),
                                                p.getConfidence()))
                        .toList();
        return ResponseEntity.ok(dtos);
    }
}
