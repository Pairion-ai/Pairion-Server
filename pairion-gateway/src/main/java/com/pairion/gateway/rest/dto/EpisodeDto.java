package com.pairion.gateway.rest.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Data transfer object for a conversation episode returned by the memory REST API.
 *
 * @param id the episode UUID
 * @param userId the user who owns this episode
 * @param sessionId the WebSocket session ID
 * @param startedAt when the episode started
 * @param endedAt when the episode ended, or null if still active
 * @param summary the LLM-generated summary, or null if not yet generated
 * @param turnCount the number of turns in this episode
 */
public record EpisodeDto(
        UUID id,
        String userId,
        String sessionId,
        Instant startedAt,
        Instant endedAt,
        String summary,
        int turnCount) {}
