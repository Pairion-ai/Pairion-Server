package com.pairion.memory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairion.adapters.llm.spi.LlmAdapter;
import com.pairion.adapters.vectorstore.spi.VectorStoreAdapter;
import com.pairion.core.llm.LlmEvent;
import com.pairion.core.llm.LlmRequest;
import com.pairion.memory.entity.Episode;
import com.pairion.memory.entity.Preference;
import com.pairion.memory.entity.Turn;
import com.pairion.memory.repository.EpisodeRepository;
import com.pairion.memory.repository.PreferenceRepository;
import com.pairion.memory.repository.TurnRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for episodic memory management.
 *
 * <p>Manages the full lifecycle of conversation episodes: creation, turn recording, async
 * summarization, preference extraction, and semantic recall. Depends on the {@link
 * VectorStoreAdapter} for semantic search and the {@link LlmAdapter} for summary and preference
 * extraction.
 */
@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private static final String SUMMARY_PROMPT =
            "Summarize this conversation in 2-3 sentences. Focus on what was discussed, "
                    + "what the user wanted, and any decisions made. Also extract any user"
                    + " preferences "
                    + "revealed (name, location, interests, dietary needs, preferred settings,"
                    + " etc.). "
                    + "Respond ONLY in JSON with no markdown: "
                    + "{\"summary\": \"...\", \"preferences\": [{\"key\": \"...\","
                    + " \"value\": \"...\", \"confidence\": 0.0}]}";

    private final EpisodeRepository episodeRepository;
    private final TurnRepository turnRepository;
    private final PreferenceRepository preferenceRepository;
    private final VectorStoreAdapter vectorStoreAdapter;
    private final LlmAdapter llmAdapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructs the memory service with all required dependencies.
     *
     * @param episodeRepository the repository for episode persistence
     * @param turnRepository the repository for turn persistence
     * @param preferenceRepository the repository for preference persistence
     * @param vectorStoreAdapter the vector store for semantic embedding and search
     * @param llmAdapter the LLM adapter for summary and preference extraction
     */
    public MemoryService(
            EpisodeRepository episodeRepository,
            TurnRepository turnRepository,
            PreferenceRepository preferenceRepository,
            VectorStoreAdapter vectorStoreAdapter,
            LlmAdapter llmAdapter) {
        this.episodeRepository = episodeRepository;
        this.turnRepository = turnRepository;
        this.preferenceRepository = preferenceRepository;
        this.vectorStoreAdapter = vectorStoreAdapter;
        this.llmAdapter = llmAdapter;
    }

    /**
     * Creates and persists a new episode for the given session.
     *
     * @param sessionId the WebSocket session ID
     * @param userId the user who owns this episode
     * @return the persisted episode
     */
    @Transactional("memoryTransactionManager")
    public Episode startEpisode(String sessionId, String userId) {
        Episode ep = new Episode();
        ep.setSessionId(sessionId);
        ep.setUserId(userId);
        ep.setStartedAt(Instant.now());
        Episode saved = episodeRepository.save(ep);
        log.info("memory.episode.started: sessionId={}, episodeId={}", sessionId, saved.getId());
        return saved;
    }

    /**
     * Records a turn (user or assistant) for the active episode identified by session ID.
     *
     * <p>If no active episode is found for the session, logs a warning and returns {@code null}.
     *
     * @param sessionId the WebSocket session ID
     * @param role the speaker role ("user" or "assistant")
     * @param content the turn content
     * @return the persisted turn, or null if no episode exists for the session
     */
    @Transactional("memoryTransactionManager")
    public Turn recordTurn(String sessionId, String role, String content) {
        Episode ep = episodeRepository.findBySessionId(sessionId).orElse(null);
        if (ep == null) {
            log.warn("memory.turn.no-episode: sessionId={}", sessionId);
            return null;
        }
        long count = turnRepository.countByEpisode(ep);
        Turn turn = new Turn();
        turn.setEpisode(ep);
        turn.setUserId(ep.getUserId());
        turn.setRole(role);
        turn.setContent(content);
        turn.setCreatedAt(Instant.now());
        turn.setOrdinal((int) count + 1);
        Turn saved = turnRepository.save(turn);
        log.debug(
                "memory.turn.recorded: sessionId={}, role={}, ordinal={}",
                sessionId,
                role,
                saved.getOrdinal());
        return saved;
    }

    /**
     * Closes the episode for the session and asynchronously generates its summary, extracts
     * preferences, and stores the summary embedding.
     *
     * <p>If no episode is found for the session, this is a no-op.
     *
     * @param sessionId the WebSocket session ID
     */
    public void endEpisode(String sessionId) {
        episodeRepository
                .findBySessionId(sessionId)
                .ifPresent(
                        ep -> {
                            ep.setEndedAt(Instant.now());
                            long count = turnRepository.countByEpisode(ep);
                            ep.setTurnCount((int) count);
                            episodeRepository.save(ep);
                            log.info(
                                    "memory.episode.ended: sessionId={}, episodeId={}, turns={}",
                                    sessionId,
                                    ep.getId(),
                                    count);

                            final UUID epId = ep.getId();
                            final String epUserId = ep.getUserId();
                            Thread.ofVirtual()
                                    .name("memory-summarize-" + sessionId)
                                    .start(() -> generateSummaryAsync(epId, epUserId));
                        });
    }

    /**
     * Asynchronously generates a summary of the episode, extracts preferences, and stores the
     * summary embedding.
     *
     * <p>This method is package-private for testing. On LLM JSON parse failure, stores the raw LLM
     * response as the summary.
     *
     * @param episodeId the episode ID to summarize
     * @param userId the user ID for preference storage
     */
    void generateSummaryAsync(UUID episodeId, String userId) {
        Episode ep = episodeRepository.findById(episodeId).orElse(null);
        if (ep == null) {
            return;
        }

        List<Turn> turns = turnRepository.findByEpisodeOrderByOrdinalAsc(ep);
        if (turns.isEmpty()) {
            return;
        }

        StringBuilder conversation = new StringBuilder();
        for (Turn t : turns) {
            conversation.append(t.getRole()).append(": ").append(t.getContent()).append("\n");
        }

        StringBuilder llmResponse = new StringBuilder();
        try {
            LlmRequest request = LlmRequest.simple(SUMMARY_PROMPT, conversation.toString());
            llmAdapter.generate(
                    request,
                    event -> {
                        if (event instanceof LlmEvent.TokenDelta delta) {
                            llmResponse.append(delta.delta());
                        }
                    });
        } catch (Exception e) {
            log.warn("memory.summary.llm.error: episodeId={}, error={}", episodeId, e.getMessage());
        }

        String responseText = llmResponse.toString().trim();
        if (responseText.isEmpty()) {
            return;
        }

        String summaryText = responseText;
        try {
            JsonNode root = objectMapper.readTree(responseText);
            summaryText = root.path("summary").asText(responseText);

            JsonNode preferences = root.path("preferences");
            if (preferences.isArray()) {
                for (JsonNode prefNode : preferences) {
                    String key = prefNode.path("key").asText(null);
                    String value = prefNode.path("value").asText(null);
                    double confidence = prefNode.path("confidence").asDouble(0.0);
                    if (key != null && value != null) {
                        upsertPreference(userId, key, value, episodeId, confidence);
                    }
                }
            }
        } catch (Exception e) {
            log.warn(
                    "memory.summary.parse.error: episodeId={}, error={} — storing raw text",
                    episodeId,
                    e.getMessage());
            summaryText = responseText;
        }

        ep.setSummary(summaryText);
        episodeRepository.save(ep);
        log.info("memory.summary.stored: episodeId={}", episodeId);

        if (!summaryText.isBlank()) {
            vectorStoreAdapter.store(userId, "episode_summary", episodeId, summaryText);
        }
    }

    /**
     * Upserts a user preference: updates the value if the key already exists, otherwise inserts a
     * new preference.
     *
     * @param userId the user ID
     * @param key the preference key
     * @param value the preference value
     * @param sourceEpisodeId the episode that sourced this preference
     * @param confidence the extraction confidence score
     */
    private void upsertPreference(
            String userId, String key, String value, UUID sourceEpisodeId, double confidence) {
        Preference pref =
                preferenceRepository.findByUserIdAndKey(userId, key).orElse(new Preference());
        pref.setUserId(userId);
        pref.setKey(key);
        pref.setValue(value);
        pref.setSourceEpisodeId(sourceEpisodeId);
        pref.setExtractedAt(Instant.now());
        pref.setConfidence(confidence);
        preferenceRepository.save(pref);
        log.debug("memory.preference.upserted: userId={}, key={}", userId, key);
    }

    /**
     * Recalls relevant memory context for the given user and query.
     *
     * <p>Searches the vector store for semantically similar episode summaries and retrieves all
     * user preferences.
     *
     * @param userId the user ID to recall for
     * @param queryText the current user query for semantic search
     * @param maxResults maximum number of episode results to return
     * @return the memory context containing relevant episodes and preferences
     */
    public MemoryContext recall(String userId, String queryText, int maxResults) {
        List<VectorStoreAdapter.VectorSearchResult> results =
                vectorStoreAdapter.search(userId, queryText, maxResults);

        List<EpisodeSummary> episodes = new ArrayList<>();
        for (VectorStoreAdapter.VectorSearchResult r : results) {
            if ("episode_summary".equals(r.contentType())) {
                episodeRepository
                        .findById(r.contentId())
                        .ifPresent(
                                ep -> {
                                    if (ep.getSummary() != null) {
                                        episodes.add(
                                                new EpisodeSummary(
                                                        ep.getSummary(),
                                                        ep.getStartedAt(),
                                                        r.score()));
                                    }
                                });
            }
        }

        List<Preference> prefs = preferenceRepository.findByUserId(userId);
        boolean hasMemory = !episodes.isEmpty() || !prefs.isEmpty();
        return new MemoryContext(episodes, prefs, hasMemory);
    }

    /**
     * Returns all preferences for a user.
     *
     * @param userId the user ID
     * @return list of all preferences for the user
     */
    public List<Preference> getPreferences(String userId) {
        return preferenceRepository.findByUserId(userId);
    }

    /**
     * Returns a specific preference for a user by key.
     *
     * @param userId the user ID
     * @param key the preference key
     * @return the preference, if found
     */
    public Optional<Preference> getPreference(String userId, String key) {
        return preferenceRepository.findByUserIdAndKey(userId, key);
    }

    /**
     * Returns episodes for a user, newest first, up to the given limit.
     *
     * @param userId the user ID
     * @param limit maximum number of episodes to return
     * @return list of episodes, newest first
     */
    public List<Episode> getEpisodes(String userId, int limit) {
        return episodeRepository
                .findByUserIdOrderByStartedAtDesc(userId, PageRequest.of(0, limit))
                .getContent();
    }

    /**
     * Returns all turns for an episode in ordinal order.
     *
     * @param episodeId the episode ID
     * @return list of turns in ordinal order, or empty if the episode is not found
     */
    public List<Turn> getTurns(UUID episodeId) {
        Episode ep = episodeRepository.findById(episodeId).orElse(null);
        if (ep == null) {
            return List.of();
        }
        return turnRepository.findByEpisodeOrderByOrdinalAsc(ep);
    }

    /**
     * Computes a human-readable relative time string for the given instant.
     *
     * @param instant the past instant to describe
     * @return a relative time string such as "today", "yesterday", "3 days ago", "2 weeks ago"
     */
    public static String formatRelativeTime(Instant instant) {
        long days = ChronoUnit.DAYS.between(instant, Instant.now());
        if (days == 0) {
            return "today";
        }
        if (days == 1) {
            return "yesterday";
        }
        if (days < 7) {
            return days + " days ago";
        }
        if (days < 30) {
            long weeks = days / 7;
            return weeks + (weeks == 1 ? " week ago" : " weeks ago");
        }
        long months = days / 30;
        return months + (months == 1 ? " month ago" : " months ago");
    }

    /**
     * Summary of an episode for inclusion in memory context.
     *
     * @param summary the LLM-generated summary text
     * @param startedAt the timestamp when the episode started
     * @param similarity the cosine similarity score from the vector search
     */
    public record EpisodeSummary(String summary, Instant startedAt, double similarity) {}

    /**
     * Memory context injected into the system prompt.
     *
     * @param relevantEpisodes episodes semantically similar to the current query
     * @param preferences all known user preferences
     * @param hasMemory true if there is any memory (episodes or preferences)
     */
    public record MemoryContext(
            List<EpisodeSummary> relevantEpisodes,
            List<Preference> preferences,
            boolean hasMemory) {}
}
