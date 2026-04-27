package com.pairion.memory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA entity for a stored embedding vector linked to a content entity. */
@Entity
@Table(
        name = "vector_entries",
        indexes = @Index(name = "idx_vector_user_id", columnList = "userId"))
public class VectorEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private UUID contentId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(nullable = false, columnDefinition = "BLOB")
    private byte[] embedding;

    @Column(nullable = false)
    private Instant createdAt;

    /** No-arg constructor required by JPA. */
    public VectorEntry() {}

    /**
     * Returns the vector entry ID.
     *
     * @return the entry UUID
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets the vector entry ID.
     *
     * @param id the entry UUID
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Returns the user ID for this vector entry.
     *
     * @return the user ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Sets the user ID for this vector entry.
     *
     * @param userId the user ID
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Returns the content type label (e.g., "episode_summary", "turn", "preference").
     *
     * @return the content type
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * Sets the content type label.
     *
     * @param contentType the content type
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * Returns the ID of the source entity.
     *
     * @return the content ID
     */
    public UUID getContentId() {
        return contentId;
    }

    /**
     * Sets the ID of the source entity.
     *
     * @param contentId the content ID
     */
    public void setContentId(UUID contentId) {
        this.contentId = contentId;
    }

    /**
     * Returns the original text that was embedded.
     *
     * @return the text
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the original text that was embedded.
     *
     * @param text the text
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * Returns the serialized embedding vector as little-endian IEEE 754 bytes.
     *
     * @return the embedding bytes
     */
    public byte[] getEmbedding() {
        return embedding;
    }

    /**
     * Sets the serialized embedding vector.
     *
     * @param embedding the embedding bytes
     */
    public void setEmbedding(byte[] embedding) {
        this.embedding = embedding;
    }

    /**
     * Returns the timestamp when this vector entry was created.
     *
     * @return the creation timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the timestamp when this vector entry was created.
     *
     * @param createdAt the creation timestamp
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
