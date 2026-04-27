package com.pairion.agent.soul;

import com.pairion.memory.entity.Preference;
import com.pairion.memory.service.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Default SOUL prompt provider that augments the base system prompt with recalled memory context.
 *
 * <p>When a {@link MemoryService} is available, this provider calls {@link
 * MemoryService#recall(String, String, int)} to retrieve semantically relevant past episodes and
 * user preferences. If memory is found, it is appended to the base prompt. If memory is unavailable
 * or {@link MemoryService} is null, the base prompt is returned unchanged.
 */
@Component
public class DefaultSoulPromptProvider implements SoulPromptProvider {

    private static final Logger log = LoggerFactory.getLogger(DefaultSoulPromptProvider.class);

    private static final String PLACEHOLDER_PROMPT =
            "You are Jarvis, a household AI assistant. You speak out loud — your responses will"
                    + " be read by a text-to-speech engine and heard through speakers.\n\n"
                    + "Rules:\n"
                    + "- ALWAYS respond in English only, regardless of any other language.\n"
                    + "- Answer directly. Never narrate your reasoning process.\n"
                    + "- When you need to call a tool, call it immediately with NO text output"
                    + " beforehand.\n"
                    + "- Never use markdown formatting (no bold, italic, headers, bullet points, code"
                    + " blocks).\n"
                    + "- Keep responses concise — one to three sentences for simple questions.\n"
                    + "- Speak naturally as if talking to someone in their home.\n"
                    + "- Use plain numbers and words, not symbols (say \"seventy-one degrees\" not"
                    + " \"71°F\").\n"
                    + "- When reporting weather or facts, state them directly without preamble.\n"
                    + "- You MUST use the get_current_weather tool for ANY weather, temperature, or"
                    + " forecast question. The tool provides real-time data. Do not refuse or suggest"
                    + " websites. Always use the tool first.\n"
                    + "- You MUST call focus_map whenever the user mentions or asks about any specific"
                    + " geographic location — city, country, region, or landmark. Call it immediately"
                    + " with NO text output beforehand. If the user also asks a weather question about"
                    + " that location, call focus_map first, then get_current_weather.\n"
                    + "- You have a set_background tool to switch the background display. Use it"
                    + " immediately with NO text output beforehand when the conversation topic"
                    + " shifts:\n"
                    + "  - Geography, maps, or location questions → set_background with"
                    + " background_id='globe'\n"
                    + "  - Astronomy, space, stars, planets, or cosmos → set_background with"
                    + " background_id='space'\n"
                    + "  - Aviation, flights, or aircraft topics → set_background with"
                    + " background_id='vfr'\n"
                    + "  - Local navigation, directions, nearby places, or street-level views →"
                    + " set_background with background_id='osm'\n"
                    + "  - When returning to general topics → set_background with"
                    + " background_id='dashboard'\n"
                    + "  - Do not call set_background again if the background is already correct for"
                    + " the topic.\n"
                    + "- You have an add_overlay tool to add data overlays on top of the background."
                    + " Available overlays: adsb (live ADS-B aircraft radar).\n"
                    + "- You have a remove_overlay tool to remove a specific overlay from the display"
                    + " stack by overlay_id.\n"
                    + "- You have a clear_overlays tool to remove all active overlays at once.\n"
                    + "- You have a show_adsb_radar tool. Call it immediately with NO text output"
                    + " beforehand when the user asks about live aircraft, planes in the sky, flight"
                    + " traffic, or air traffic radar. This activates the VFR sectional chart"
                    + " background with a live ADS-B aircraft overlay.\n"
                    + "- You have a weather_radar overlay. When the user asks about rain, storms,"
                    + " precipitation, weather radar, or whether it will rain, you MUST call BOTH:"
                    + " (1) set_background with background_id='osm' and (2) add_overlay with"
                    + " overlay_id='weather_radar'. Call them immediately with NO text output"
                    + " beforehand. Always use osm — never globe — for weather radar. The globe"
                    + " background does not support radar tile positioning.";

    @Nullable private final MemoryService memoryService;

    /**
     * Constructs the provider with an optional {@link MemoryService} dependency.
     *
     * <p>Spring will inject the {@link MemoryService} bean if it is available. If the memory module
     * is not on the classpath or the bean is not registered, this parameter will be {@code null}.
     *
     * @param memoryService the memory service for context recall; may be null
     */
    public DefaultSoulPromptProvider(@Nullable MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    /**
     * Returns the system prompt for the given session, optionally augmented with memory context.
     *
     * <p>If {@link MemoryService} is available and has relevant memory for the user and query, a
     * memory section is appended to the base prompt containing preferences and relevant past
     * episodes. If no memory is found, the base prompt is returned unchanged.
     *
     * @param sessionId the current WebSocket session ID (unused in this implementation)
     * @param userId the user identifier for memory recall
     * @param userQuery the current user query text for semantic memory search
     * @return the system prompt text, optionally augmented with memory context
     */
    @Override
    public String getSystemPrompt(String sessionId, String userId, String userQuery) {
        if (memoryService == null) {
            return PLACEHOLDER_PROMPT;
        }

        MemoryService.MemoryContext ctx;
        try {
            ctx = memoryService.recall(userId, userQuery, 5);
        } catch (Exception e) {
            log.warn("soul.memory.recall.error: sessionId={}, error={}", sessionId, e.getMessage());
            return PLACEHOLDER_PROMPT;
        }

        if (!ctx.hasMemory()) {
            return PLACEHOLDER_PROMPT;
        }

        StringBuilder prompt = new StringBuilder(PLACEHOLDER_PROMPT);
        prompt.append("\n\n## What you remember about this user\n\n");

        for (Preference pref : ctx.preferences()) {
            String capitalizedKey = capitalize(pref.getKey());
            prompt.append("- ")
                    .append(capitalizedKey)
                    .append(": ")
                    .append(pref.getValue())
                    .append("\n");
        }

        if (!ctx.relevantEpisodes().isEmpty()) {
            prompt.append("\n### Relevant past conversations\n");
            for (MemoryService.EpisodeSummary ep : ctx.relevantEpisodes()) {
                String relativeTime = MemoryService.formatRelativeTime(ep.startedAt());
                prompt.append("- [")
                        .append(relativeTime)
                        .append("] ")
                        .append(ep.summary())
                        .append("\n");
            }
        }

        return prompt.toString();
    }

    /**
     * Capitalizes the first character of a string.
     *
     * @param s the string to capitalize
     * @return the capitalized string, or the original if null or empty
     */
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
