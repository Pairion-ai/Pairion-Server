package com.pairion.memory.repository;

import com.pairion.memory.entity.Episode;
import com.pairion.memory.entity.Turn;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link Turn} entities.
 *
 * <p>Provides CRUD operations and custom finders for turn retrieval within episodes.
 */
public interface TurnRepository extends JpaRepository<Turn, UUID> {

    /**
     * Finds all turns for an episode, ordered by ordinal position ascending.
     *
     * @param episode the episode to find turns for
     * @return the turns in ordinal order
     */
    List<Turn> findByEpisodeOrderByOrdinalAsc(Episode episode);

    /**
     * Counts the number of turns for an episode.
     *
     * @param episode the episode to count turns for
     * @return the number of turns
     */
    long countByEpisode(Episode episode);
}
