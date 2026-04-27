package com.pairion.memory.repository;

import com.pairion.memory.entity.VectorEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA repository for {@link VectorEntry} entities.
 *
 * <p>Provides CRUD operations and custom finders for embedding vector retrieval and deletion.
 */
public interface VectorEntryRepository extends JpaRepository<VectorEntry, UUID> {

    /**
     * Finds all vector entries for a user.
     *
     * @param userId the user ID to filter by
     * @return all vector entries for the user
     */
    List<VectorEntry> findByUserId(String userId);

    /**
     * Finds a vector entry by content type and content ID.
     *
     * @param contentType the content type label
     * @param contentId the source entity ID
     * @return the vector entry, if found
     */
    Optional<VectorEntry> findByContentTypeAndContentId(String contentType, UUID contentId);

    /**
     * Deletes the vector entry for a given content type and ID.
     *
     * @param contentType the content type label
     * @param contentId the source entity ID
     */
    @Transactional
    void deleteByContentTypeAndContentId(String contentType, UUID contentId);
}
