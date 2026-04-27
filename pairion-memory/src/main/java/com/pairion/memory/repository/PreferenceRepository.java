package com.pairion.memory.repository;

import com.pairion.memory.entity.Preference;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link Preference} entities.
 *
 * <p>Provides CRUD operations and custom finders for user preference retrieval.
 */
public interface PreferenceRepository extends JpaRepository<Preference, UUID> {

    /**
     * Finds all preferences for a user.
     *
     * @param userId the user ID to filter by
     * @return all preferences for the user
     */
    List<Preference> findByUserId(String userId);

    /**
     * Finds a specific preference for a user by key.
     *
     * @param userId the user ID
     * @param key the preference key
     * @return the preference, if found
     */
    Optional<Preference> findByUserIdAndKey(String userId, String key);
}
