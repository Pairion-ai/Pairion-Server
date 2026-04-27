package com.pairion.gateway.rest.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Data transfer object for a conversation turn returned by the memory REST API.
 *
 * @param id the turn UUID
 * @param userId the user who owns this turn
 * @param role the speaker role ("user" or "assistant")
 * @param content the turn content text
 * @param createdAt when the turn was recorded
 * @param ordinal the ordinal position within the episode (1-based)
 */
public record TurnDto(
        UUID id, String userId, String role, String content, Instant createdAt, int ordinal) {}
