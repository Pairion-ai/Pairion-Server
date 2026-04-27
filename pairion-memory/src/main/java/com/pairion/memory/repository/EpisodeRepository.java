package com.pairion.memory.repository;

import com.pairion.memory.entity.Episode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link Episode} entities.
 *
 * <p>Provides CRUD operations and custom finders for episode retrieval.
 */
public interface EpisodeRepository extends JpaRepository<Episode, UUID> {

    /**
     * Finds all episodes for a user, ordered by start time descending (newest first).
     *
     * @param userId the user ID to filter by
     * @param pageable the pagination parameters
     * @return a page of episodes
     */
    Page<Episode> findByUserIdOrderByStartedAtDesc(String userId, Pageable pageable);

    /**
     * Finds the episode for a given WebSocket session ID.
     *
     * @param sessionId the session ID to look up
     * @return the episode, if found
     */
    Optional<Episode> findBySessionId(String sessionId);
}
