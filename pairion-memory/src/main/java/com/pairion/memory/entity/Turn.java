package com.pairion.memory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA entity for a single conversational turn within an episode. */
@Entity
@Table(name = "turns")
public class Turn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;

    @Column(nullable = false)
    private String userId = "default-user";

    @Column(nullable = false)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Instant createdAt;

    @Column private int ordinal;

    /** No-arg constructor required by JPA. */
    public Turn() {}

    /**
     * Returns the turn ID.
     *
     * @return the turn UUID
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets the turn ID.
     *
     * @param id the turn UUID
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Returns the episode this turn belongs to.
     *
     * @return the episode
     */
    public Episode getEpisode() {
        return episode;
    }

    /**
     * Sets the episode this turn belongs to.
     *
     * @param episode the episode
     */
    public void setEpisode(Episode episode) {
        this.episode = episode;
    }

    /**
     * Returns the user ID for this turn.
     *
     * @return the user ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Sets the user ID for this turn.
     *
     * @param userId the user ID
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Returns the role for this turn (e.g., "user" or "assistant").
     *
     * @return the role
     */
    public String getRole() {
        return role;
    }

    /**
     * Sets the role for this turn.
     *
     * @param role the role
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * Returns the content of this turn.
     *
     * @return the content text
     */
    public String getContent() {
        return content;
    }

    /**
     * Sets the content of this turn.
     *
     * @param content the content text
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * Returns the timestamp when this turn was created.
     *
     * @return the creation timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the timestamp when this turn was created.
     *
     * @param createdAt the creation timestamp
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns the ordinal position of this turn within the episode.
     *
     * @return the ordinal (1-based)
     */
    public int getOrdinal() {
        return ordinal;
    }

    /**
     * Sets the ordinal position of this turn within the episode.
     *
     * @param ordinal the ordinal (1-based)
     */
    public void setOrdinal(int ordinal) {
        this.ordinal = ordinal;
    }
}
