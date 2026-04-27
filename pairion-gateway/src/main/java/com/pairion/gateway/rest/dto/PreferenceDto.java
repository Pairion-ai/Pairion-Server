package com.pairion.gateway.rest.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Data transfer object for a user preference returned by the memory REST API.
 *
 * @param id the preference UUID
 * @param userId the user who owns this preference
 * @param key the preference key
 * @param value the preference value
 * @param sourceEpisodeId the episode that sourced this preference, or null
 * @param extractedAt when the preference was extracted
 * @param confidence the extraction confidence score (0.0–1.0)
 */
public record PreferenceDto(
        UUID id,
        String userId,
        String key,
        String value,
        UUID sourceEpisodeId,
        Instant extractedAt,
        double confidence) {}
