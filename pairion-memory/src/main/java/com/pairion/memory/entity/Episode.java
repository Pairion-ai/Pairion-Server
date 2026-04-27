package com.pairion.memory.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** JPA entity for a conversation episode (a single session interaction). */
@Entity
@Table(name = "episodes", indexes = @Index(name = "idx_episode_user_id", columnList = "userId"))
public class Episode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String userId = "default-user";

    @Column(nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private Instant startedAt;

    @Column private Instant endedAt;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column private int turnCount;

    @OneToMany(
            mappedBy = "episode",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<Turn> turns = new ArrayList<>();

    /** No-arg constructor required by JPA. */
    public Episode() {}

    /**
     * Returns the episode ID.
     *
     * @return the episode UUID
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets the episode ID.
     *
     * @param id the episode UUID
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Returns the user ID for this episode.
     *
     * @return the user ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Sets the user ID for this episode.
     *
     * @param userId the user ID
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Returns the session ID for this episode.
     *
     * @return the session ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Sets the session ID for this episode.
     *
     * @param sessionId the session ID
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Returns the timestamp when this episode started.
     *
     * @return the start timestamp
     */
    public Instant getStartedAt() {
        return startedAt;
    }

    /**
     * Sets the timestamp when this episode started.
     *
     * @param startedAt the start timestamp
     */
    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    /**
     * Returns the timestamp when this episode ended, or null if still active.
     *
     * @return the end timestamp, or null
     */
    public Instant getEndedAt() {
        return endedAt;
    }

    /**
     * Sets the timestamp when this episode ended.
     *
     * @param endedAt the end timestamp
     */
    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    /**
     * Returns the LLM-generated summary of this episode.
     *
     * @return the summary text, or null if not yet generated
     */
    public String getSummary() {
        return summary;
    }

    /**
     * Sets the LLM-generated summary of this episode.
     *
     * @param summary the summary text
     */
    public void setSummary(String summary) {
        this.summary = summary;
    }

    /**
     * Returns the number of turns in this episode.
     *
     * @return the turn count
     */
    public int getTurnCount() {
        return turnCount;
    }

    /**
     * Sets the number of turns in this episode.
     *
     * @param turnCount the turn count
     */
    public void setTurnCount(int turnCount) {
        this.turnCount = turnCount;
    }

    /**
     * Returns the turns in this episode.
     *
     * @return the list of turns
     */
    public List<Turn> getTurns() {
        return turns;
    }

    /**
     * Sets the turns for this episode.
     *
     * @param turns the list of turns
     */
    public void setTurns(List<Turn> turns) {
        this.turns = turns;
    }
}
