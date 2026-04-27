package com.pairion.memory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/** JPA entity for a user preference extracted from conversation episodes. */
@Entity
@Table(
        name = "preferences",
        indexes = @Index(name = "idx_preference_user_id", columnList = "userId"),
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_preference_user_key",
                        columnNames = {"userId", "pref_key"}))
public class Preference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String userId;

    @Column(name = "pref_key", nullable = false)
    private String key;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String value;

    @Column private UUID sourceEpisodeId;

    @Column(nullable = false)
    private Instant extractedAt;

    @Column private double confidence;

    /** No-arg constructor required by JPA. */
    public Preference() {}

    /**
     * Returns the preference ID.
     *
     * @return the preference UUID
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets the preference ID.
     *
     * @param id the preference UUID
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Returns the user ID for this preference.
     *
     * @return the user ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Sets the user ID for this preference.
     *
     * @param userId the user ID
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Returns the preference key.
     *
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * Sets the preference key.
     *
     * @param key the key
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Returns the preference value.
     *
     * @return the value
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the preference value.
     *
     * @param value the value
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Returns the ID of the episode that was the source of this preference, or null.
     *
     * @return the source episode ID, or null
     */
    public UUID getSourceEpisodeId() {
        return sourceEpisodeId;
    }

    /**
     * Sets the ID of the source episode.
     *
     * @param sourceEpisodeId the source episode ID
     */
    public void setSourceEpisodeId(UUID sourceEpisodeId) {
        this.sourceEpisodeId = sourceEpisodeId;
    }

    /**
     * Returns the timestamp when this preference was extracted.
     *
     * @return the extraction timestamp
     */
    public Instant getExtractedAt() {
        return extractedAt;
    }

    /**
     * Sets the timestamp when this preference was extracted.
     *
     * @param extractedAt the extraction timestamp
     */
    public void setExtractedAt(Instant extractedAt) {
        this.extractedAt = extractedAt;
    }

    /**
     * Returns the confidence score for this preference extraction (0.0–1.0).
     *
     * @return the confidence score
     */
    public double getConfidence() {
        return confidence;
    }

    /**
     * Sets the confidence score for this preference extraction.
     *
     * @param confidence the confidence score (0.0–1.0)
     */
    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
}
