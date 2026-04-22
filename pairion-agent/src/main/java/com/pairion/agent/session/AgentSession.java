package com.pairion.agent.session;

import com.pairion.adapters.audio.opus.OpusDecoder;
import com.pairion.adapters.data.adsb.AdsbDataAdapter;
import com.pairion.adapters.llm.spi.LlmAdapter;
import com.pairion.adapters.stt.spi.SttAdapter;
import com.pairion.adapters.tts.spi.TtsAdapter;
import com.pairion.agent.soul.SoulPromptProvider;
import com.pairion.agent.tools.ToolDispatcher;
import com.pairion.agent.tools.map.MapFocusTool;
import com.pairion.agent.tools.scene.SetSceneTool;
import com.pairion.agent.tools.scene.ShowAdsbRadarTool;
import com.pairion.agent.util.MarkdownStripper;
import com.pairion.core.agent.AgentState;
import com.pairion.core.llm.LlmEvent;
import com.pairion.core.llm.LlmRequest;
import com.pairion.core.llm.ToolDefinition;
import com.pairion.core.stt.SttEvent;
import com.pairion.core.tts.TtsEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Manages the agent turn loop for a single WebSocket session.
 *
 * <p>Lifecycle: one instance per active WebSocket session. Handles the M1 Part 2 turn loop:
 *
 * <ol>
 *   <li>AudioStreamStart → allocate STT session, emit AgentStateChange(listening)
 *   <li>AudioChunkIn binary → Opus decode → feed PCM to STT
 *   <li>SpeechEnded → finalize STT → emit TranscriptFinal
 *   <li>Emit AgentStateChange(thinking) → call LLM → stream tokens
 *   <li>If LLM requests tool: dispatch tool → re-invoke LLM with history (multi-turn loop)
 *   <li>LLM text response → TTS synthesis → Opus encode → emit AudioStreamStart/Chunks/End
 *   <li>Emit AgentStateChange(idle)
 * </ol>
 *
 * <p>Each stage of the turn loop is instrumented with wall-clock latency logging using
 * {@code System.nanoTime()}. Log lines are prefixed with {@code [LATENCY]} for grep-friendly
 * extraction. Stage definitions:
 * <ul>
 *   <li>A (STT): SpeechEnded → SttEvent.Final received</li>
 *   <li>B (LLM-1): first LLM generate() call</li>
 *   <li>C (Tool): tool dispatch (cumulative across rounds; omitted when no tool used)</li>
 *   <li>D (LLM-2): subsequent LLM generate() calls (cumulative; omitted when no tool used)</li>
 *   <li>E (TTS): TTS synthesis start → synthesis complete</li>
 *   <li>F (Send): first TTS chunk received → first binary WebSocket frame written</li>
 *   <li>T (Total): SpeechEnded → first binary audio frame sent to client</li>
 * </ul>
 */
public class AgentSession {

    private static final Logger log = LoggerFactory.getLogger(AgentSession.class);

    /** Maximum number of tool-call rounds per turn to prevent infinite loops. */
    private static final int MAX_TOOL_ROUNDS = 5;

    private final String sessionId;
    private final SttAdapter sttAdapter;
    private final LlmAdapter llmAdapter;
    private final TtsAdapter ttsAdapter;
    private final SoulPromptProvider soulProvider;
    private final ToolDispatcher toolDispatcher;
    private final AdsbDataAdapter adsbDataAdapter;
    private final Consumer<AgentSessionEvent> eventSink;

    private OpusDecoder opusDecoder;
    private SttAdapter.SttSession sttSession;
    private AgentState currentState = AgentState.IDLE;

    /** Scheduler used solely to emit {@link AgentSessionEvent.MapClearEvent} after 2-minute idle. */
    private final ScheduledExecutorService clearScheduler;

    /** Pending clear timer, or {@code null} when no map focus is active. */
    private volatile ScheduledFuture<?> clearFuture;

    /**
     * Phrases that, when detected in a user transcript, trigger an immediate map clear.
     * Checked case-insensitively as substrings.
     */
    private static final List<String> MAP_CLEAR_PHRASES = List.of(
            "go back", "that's all", "thats all", "never mind", "nevermind",
            "clear the map", "zoom out", "we're done", "we are done");

    /**
     * Phrases that, when detected in a user transcript, signal the end of the conversation.
     * A superset may overlap with {@link #MAP_CLEAR_PHRASES}; both checks run independently.
     * Checked case-insensitively as substrings.
     */
    private static final List<String> CONVERSATION_END_PHRASES = List.of(
            "that's all", "thats all", "goodbye", "good bye", "bye bye", "see you later",
            "we're done", "we are done", "stop listening", "that will be all",
            "thank you goodbye", "thanks goodbye");

    /**
     * Nanosecond timestamp captured at the start of {@link #onSpeechEnded()} to anchor Stage A
     * (STT) and Stage T (Total) latency measurements. Reset at the beginning of each turn.
     */
    private long sttStartNano;

    /**
     * Constructs an agent session.
     *
     * @param sessionId the WebSocket session ID
     * @param sttAdapter the speech-to-text adapter (may be null if STT unavailable)
     * @param llmAdapter the LLM adapter
     * @param ttsAdapter the text-to-speech adapter (may be null if TTS unavailable)
     * @param soulProvider the SOUL prompt provider
     * @param toolDispatcher the tool dispatcher for LLM tool calls
     * @param adsbDataAdapter the ADS-B data adapter for live aircraft radar; null if unavailable
     * @param eventSink consumer receiving agent events to forward to the Client
     */
    public AgentSession(
            String sessionId,
            SttAdapter sttAdapter,
            LlmAdapter llmAdapter,
            TtsAdapter ttsAdapter,
            SoulPromptProvider soulProvider,
            ToolDispatcher toolDispatcher,
            AdsbDataAdapter adsbDataAdapter,
            Consumer<AgentSessionEvent> eventSink) {
        this.sessionId = sessionId;
        this.sttAdapter = sttAdapter;
        this.llmAdapter = llmAdapter;
        this.ttsAdapter = ttsAdapter;
        this.soulProvider = soulProvider;
        this.toolDispatcher = toolDispatcher;
        this.adsbDataAdapter = adsbDataAdapter;
        this.eventSink = eventSink;
        this.clearScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "map-clear-" + sessionId);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Handles an inbound AudioStreamStart message. Allocates STT and Opus decoder pipelines.
     *
     * @param streamId the audio stream identifier
     */
    public void onAudioStreamStart(String streamId) {
        MDC.put("sessionId", sessionId);
        log.info("audio.stream.start: streamId={}", streamId);

        opusDecoder = OpusDecoder.create();
        sttSession = sttAdapter.createSession(this::handleSttEvent);

        transitionState(AgentState.LISTENING);
    }

    /**
     * Handles an inbound binary audio frame (Opus-encoded with 4-byte stream ID prefix).
     *
     * @param frameData the raw binary frame
     */
    public void onAudioChunk(byte[] frameData) {
        if (opusDecoder == null) {
            log.warn("Audio chunk received without active stream — ignoring");
            return;
        }
        byte[] pcm = opusDecoder.decode(frameData);
        if (pcm.length > 0) {
            sttSession.feedAudio(pcm);
        }
    }

    /**
     * Handles an inbound SpeechEnded message. Records the Stage A (STT) start time and finalizes
     * the STT session.
     */
    public void onSpeechEnded() {
        MDC.put("sessionId", sessionId);
        sttStartNano = System.nanoTime();
        log.info("speech.ended");
        if (sttSession != null) {
            sttSession.finalizeStream();
        }
    }

    /**
     * Returns the current agent state.
     *
     * @return the current state
     */
    public AgentState currentState() {
        return currentState;
    }

    /**
     * Releases the map-clear scheduler. Call this when the WebSocket session is closed to avoid
     * thread leaks.
     */
    public void close() {
        cancelClear();
        clearScheduler.shutdownNow();
        if (adsbDataAdapter != null) {
            adsbDataAdapter.stopPolling();
        }
    }

    /**
     * Schedules a {@link AgentSessionEvent.MapClearEvent} 2 minutes from now, cancelling any
     * previously scheduled clear.
     */
    private void rescheduleClear() {
        cancelClear();
        clearFuture = clearScheduler.schedule(this::emitTimedMapClear, 2, TimeUnit.MINUTES);
    }

    /**
     * Emits the timer-driven {@link AgentSessionEvent.MapClearEvent}. Extracted from the lambda
     * in {@link #rescheduleClear()} so that unit tests can invoke it directly without waiting
     * 2 minutes.
     */
    void emitTimedMapClear() {
        eventSink.accept(new AgentSessionEvent.MapClearEvent());
    }

    /**
     * Cancels any pending map-clear timer without emitting the event.
     */
    private void cancelClear() {
        ScheduledFuture<?> f = clearFuture;
        if (f != null) {
            f.cancel(false);
            clearFuture = null;
        }
    }

    /**
     * Emits a {@link AgentSessionEvent.MapFocusEvent} derived from a {@code get_current_weather}
     * tool result. Uses the {@code latitude}/{@code longitude} coordinates and {@code city} label
     * returned by the weather tool so the map always focuses when weather is fetched, even when
     * the LLM does not call {@code focus_map} explicitly.
     *
     * @param result the weather tool result containing {@code latitude}, {@code longitude},
     *               and {@code city}
     */
    private void emitMapFocusFromWeather(Map<String, Object> result) {
        double lat = ((Number) result.get("latitude")).doubleValue();
        double lon = ((Number) result.get("longitude")).doubleValue();
        String label = (String) result.getOrDefault("city", "");
        eventSink.accept(new AgentSessionEvent.MapFocusEvent(lat, lon, label, "city"));
        rescheduleClear();
        log.info("map.focus.weather: label={}, lat={}, lon={}", label, lat, lon);
    }

    /**
     * Returns {@code true} when the transcript contains an ending phrase that should clear the map.
     *
     * @param transcript the STT transcript, compared case-insensitively
     */
    private boolean isMapClearPhrase(String transcript) {
        String lower = transcript.toLowerCase();
        return MAP_CLEAR_PHRASES.stream().anyMatch(lower::contains);
    }

    /**
     * Returns {@code true} when the transcript contains a dismissal phrase that should end the
     * conversation session and return the client to wake-word listening.
     *
     * @param transcript the STT transcript, compared case-insensitively
     */
    private boolean isConversationEndPhrase(String transcript) {
        String lower = transcript.toLowerCase();
        return CONVERSATION_END_PHRASES.stream().anyMatch(lower::contains);
    }

    /**
     * Emits a {@link AgentSessionEvent.MapFocusEvent} from a successful {@code focus_map} tool
     * result and starts the 2-minute auto-clear timer.
     *
     * @param result the tool result map containing {@code lat}, {@code lon}, {@code label},
     *               {@code zoom}
     */
    private void emitMapFocus(Map<String, Object> result) {
        double lat = ((Number) result.get("lat")).doubleValue();
        double lon = ((Number) result.get("lon")).doubleValue();
        String label = (String) result.get("label");
        String zoom = (String) result.get("zoom");
        eventSink.accept(new AgentSessionEvent.MapFocusEvent(lat, lon, label, zoom));
        rescheduleClear();
        log.info("map.focus.emitted: label={}, lat={}, lon={}, zoom={}", label, lat, lon, zoom);
    }

    /**
     * Activates the ADS-B radar scene by emitting a {@link AgentSessionEvent.SceneChangeEvent}
     * and, if an {@link AdsbDataAdapter} is configured, starts the polling loop. Each poll
     * delivers a {@link AgentSessionEvent.SceneDataPushEvent} with model ID {@code "adsb"}.
     *
     * <p>Public so the WebSocket handler can auto-activate on session start during debugging.
     */
    public void activateAdsbRadar() {
        emitAdsbRadar();
    }

    /**
     * @see #activateAdsbRadar()
     */
    private void emitAdsbRadar() {
        eventSink.accept(
                new AgentSessionEvent.SceneChangeEvent("adsb-radar", null, "crossfade"));
        log.info("scene.adsb-radar.activated");
        if (adsbDataAdapter != null) {
            adsbDataAdapter.startPolling(
                    aircraft ->
                            eventSink.accept(
                                    new AgentSessionEvent.SceneDataPushEvent("adsb", aircraft)));
        }
    }

    /**
     * Emits a {@link AgentSessionEvent.SceneChangeEvent} derived from a successful {@code set_scene}
     * tool result and forwards it to the client.
     *
     * @param result the tool result map containing {@code scene_id}, {@code transition}, and
     *               optionally {@code params}
     */
    private void emitSceneChange(Map<String, Object> result) {
        String sceneId = (String) result.get("scene_id");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) result.get("params");
        String transition = (String) result.getOrDefault("transition", "crossfade");
        eventSink.accept(new AgentSessionEvent.SceneChangeEvent(sceneId, params, transition));
        log.info("scene.change.emitted: sceneId={}, transition={}", sceneId, transition);
    }

    /**
     * Handles an STT event by forwarding it and triggering the LLM phase on final transcript.
     *
     * @param event the STT event
     */
    void handleSttEvent(SttEvent event) {
        switch (event) {
            case SttEvent.Partial partial ->
                    eventSink.accept(
                            new AgentSessionEvent.TranscriptPartialEvent(partial.text()));
            case SttEvent.Final finalEvent -> {
                long stageAMs = toMs(System.nanoTime() - sttStartNano);
                eventSink.accept(
                        new AgentSessionEvent.TranscriptFinalEvent(finalEvent.text()));
                onTranscriptFinal(finalEvent.text(), stageAMs);
            }
        }
    }

    /**
     * Runs the full turn: multi-turn LLM tool loop followed by TTS synthesis. Instruments each
     * stage with wall-clock latency measurements.
     *
     * <p>Transitions: thinking → (tool rounds) → speaking → idle
     *
     * @param transcript the final STT transcript
     * @param stageAMs Stage A (STT) latency in milliseconds
     */
    private void onTranscriptFinal(String transcript, long stageAMs) {
        // If the user said something that ends the map discussion, clear immediately.
        if (isMapClearPhrase(transcript)) {
            cancelClear();
            eventSink.accept(new AgentSessionEvent.MapClearEvent());
            log.info("map.clear.phrase: transcript={}", transcript);
        }
        // If the user said a dismissal phrase, end the conversation session.
        if (isConversationEndPhrase(transcript)) {
            eventSink.accept(new AgentSessionEvent.ConversationEndedEvent());
            log.info("conversation.end.phrase: transcript={}", transcript);
        }

        transitionState(AgentState.THINKING);

        String systemPrompt = soulProvider.getSystemPrompt(sessionId);
        List<ToolDefinition> toolDefinitions = buildToolDefinitions();

        // Multi-turn tool dispatch loop with per-stage latency tracking
        List<LlmRequest.ToolCallPair> toolHistory = new ArrayList<>();
        StringBuilder responseText = new StringBuilder();
        int rounds = 0;
        boolean firstLlmRound = true;
        long stageBMs = 0;
        boolean hadToolCalls = false;
        long toolAccumMs = 0;
        long llm2AccumMs = 0;

        while (rounds < MAX_TOOL_ROUNDS) {
            rounds++;
            LlmRequest request =
                    new LlmRequest(
                            systemPrompt,
                            transcript,
                            toolDefinitions,
                            null,
                            List.copyOf(toolHistory));

            List<LlmEvent.ToolCallRequest> pendingToolCalls = new ArrayList<>();

            long llmRoundStart = System.nanoTime();
            llmAdapter.generate(
                    request,
                    event -> handleLlmEvent(event, responseText, pendingToolCalls));
            long llmRoundMs = toMs(System.nanoTime() - llmRoundStart);

            if (firstLlmRound) {
                stageBMs = llmRoundMs;
                firstLlmRound = false;
            } else {
                llm2AccumMs += llmRoundMs;
            }

            if (pendingToolCalls.isEmpty()) {
                // LLM finished with text — no more tool calls
                break;
            }

            // Execute each tool call and accumulate dispatch time
            hadToolCalls = true;
            long toolRoundStart = System.nanoTime();
            for (LlmEvent.ToolCallRequest tc : pendingToolCalls) {
                eventSink.accept(
                        new AgentSessionEvent.ToolCallStartedEvent(
                                tc.toolCallId(), tc.toolName(), tc.input()));
                Map<String, Object> result =
                        toolDispatcher.dispatch(tc.toolName(), tc.input());
                // Side-effect: notify client to focus the map immediately on success.
                if (MapFocusTool.TOOL_NAME.equals(tc.toolName())
                        && !result.containsKey("error")) {
                    emitMapFocus(result);
                } else if ("get_current_weather".equals(tc.toolName())
                        && result.containsKey("latitude")
                        && result.containsKey("longitude")) {
                    emitMapFocusFromWeather(result);
                } else if (SetSceneTool.TOOL_NAME.equals(tc.toolName())
                        && !result.containsKey("error")) {
                    emitSceneChange(result);
                } else if (ShowAdsbRadarTool.TOOL_NAME.equals(tc.toolName())
                        && !result.containsKey("error")) {
                    emitAdsbRadar();
                }
                eventSink.accept(
                        new AgentSessionEvent.ToolCallCompletedEvent(
                                tc.toolCallId(), tc.toolName(), result));
                toolHistory.add(
                        new LlmRequest.ToolCallPair(
                                tc.toolCallId(), tc.toolName(), tc.input(), result));
            }
            toolAccumMs += toMs(System.nanoTime() - toolRoundStart);
        }

        long stageCMs = hadToolCalls ? toolAccumMs : -1L;
        long stageDMs = hadToolCalls ? llm2AccumMs : -1L;

        // TTS: strip markdown and synthesize the accumulated response text
        String text = MarkdownStripper.strip(responseText.toString());
        if (!text.isEmpty() && ttsAdapter.capabilities().available()) {
            synthesizeSpeech(text, stageAMs, stageBMs, stageCMs, stageDMs);
        } else {
            logPartialLatency(stageAMs, stageBMs, stageCMs, stageDMs);
            transitionState(AgentState.IDLE);
        }
    }

    /**
     * Handles a single LLM event during streaming.
     *
     * @param event the LLM event
     * @param textAccumulator accumulates text tokens for TTS
     * @param toolCallAccumulator collects ToolCallRequest events for the current turn
     */
    void handleLlmEvent(
            LlmEvent event,
            StringBuilder textAccumulator,
            List<LlmEvent.ToolCallRequest> toolCallAccumulator) {
        switch (event) {
            case LlmEvent.TokenDelta tokenDelta -> {
                eventSink.accept(new AgentSessionEvent.LlmTokenEvent(tokenDelta.delta()));
                textAccumulator.append(tokenDelta.delta());
            }
            case LlmEvent.ToolCallRequest req -> {
                log.info(
                        "tool.request: id={}, name={}", req.toolCallId(), req.toolName());
                toolCallAccumulator.add(req);
            }
            case LlmEvent.ToolCallResult res ->
                    log.info("tool.result.forwarded: id={}", res.toolCallId());
            case LlmEvent.Stop stop ->
                    log.info("llm.complete: output_tokens={}", stop.outputTokens());
        }
    }

    /**
     * Synthesizes the response text via TTS, streams Opus audio frames to the client, and logs
     * all per-stage and total latency for the completed turn.
     *
     * <p>Emits AudioStreamStart, zero or more AudioChunk binary events, then AudioStreamEnd.
     * Transitions state to SPEAKING before synthesis and back to IDLE afterward.
     *
     * @param text the text to synthesize
     * @param stageAMs Stage A (STT) latency in milliseconds
     * @param stageBMs Stage B (LLM-1) latency in milliseconds
     * @param stageCMs Stage C (Tool) cumulative latency in milliseconds, or -1 if no tool used
     * @param stageDMs Stage D (LLM-2) cumulative latency in milliseconds, or -1 if no tool used
     */
    private void synthesizeSpeech(
            String text, long stageAMs, long stageBMs, long stageCMs, long stageDMs) {
        transitionState(AgentState.SPEAKING);

        String streamId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        // Use 16000 Hz for Opus (standard Piper TTS output rate after resampling)
        int sampleRate = 16000;

        eventSink.accept(
                new AgentSessionEvent.AudioStreamStartEvent(streamId, "opus", sampleRate));
        log.info("tts.stream.start: streamId={}", streamId);

        long ttsStartNano = System.nanoTime();
        // Single-element arrays allow mutation from within the lambda below
        long[] firstChunkNano = {0};
        long[] firstSentNano = {0};
        String endReason = "normal";

        try {
            ttsAdapter.speak(
                    text,
                    ttsEvent -> {
                        switch (ttsEvent) {
                            case TtsEvent.Chunk chunk -> {
                                if (firstChunkNano[0] == 0) {
                                    firstChunkNano[0] = System.nanoTime();
                                }
                                eventSink.accept(
                                        new AgentSessionEvent.AudioChunkEvent(chunk.audio()));
                                if (firstSentNano[0] == 0) {
                                    firstSentNano[0] = System.nanoTime();
                                }
                            }
                            case TtsEvent.Completed completed ->
                                    log.info(
                                            "tts.synthesis.done: duration_ms={}",
                                            completed.totalDurationMs());
                        }
                    });
        } catch (Exception e) {
            log.error("tts.stream.error: streamId={}, error={}", streamId, e.getMessage());
            endReason = "error";
        }

        long ttsEndNano = System.nanoTime();
        long stageEMs = toMs(ttsEndNano - ttsStartNano);
        long stageFMs = firstChunkNano[0] > 0 ? toMs(firstSentNano[0] - firstChunkNano[0]) : 0;
        long stageTMs =
                firstChunkNano[0] > 0
                        ? toMs(firstSentNano[0] - sttStartNano)
                        : toMs(ttsEndNano - sttStartNano);

        log.info(
                "tts.stream.end: streamId={}, elapsed_ms={}",
                streamId,
                toMs(ttsEndNano - ttsStartNano));
        logLatency(stageAMs, stageBMs, stageCMs, stageDMs, stageEMs, stageFMs, stageTMs);
        eventSink.accept(new AgentSessionEvent.AudioStreamEndEvent(streamId, endReason));
        transitionState(AgentState.IDLE);
    }

    /**
     * Logs per-stage and total latency for a completed agent turn (all stages including TTS).
     *
     * <p>Stages C and D are logged only when a tool was invoked ({@code stageCMs >= 0}). The
     * summary line always appears last and includes all stage values so a single grep captures
     * the full turn breakdown.
     *
     * @param stageAMs Stage A (STT) latency in milliseconds
     * @param stageBMs Stage B (LLM-1) latency in milliseconds
     * @param stageCMs Stage C (Tool) cumulative latency, or -1 if no tool was invoked
     * @param stageDMs Stage D (LLM-2) cumulative latency, or -1 if no tool was invoked
     * @param stageEMs Stage E (TTS) synthesis latency in milliseconds
     * @param stageFMs Stage F (Send) first-audio-byte latency in milliseconds
     * @param stageTMs Stage T (Total) end-to-end latency from SpeechEnded to first audio sent
     */
    private void logLatency(
            long stageAMs,
            long stageBMs,
            long stageCMs,
            long stageDMs,
            long stageEMs,
            long stageFMs,
            long stageTMs) {
        log.info("[LATENCY] Stage A (STT): {} ms", stageAMs);
        log.info("[LATENCY] Stage B (LLM-1): {} ms", stageBMs);
        if (stageCMs >= 0) {
            log.info("[LATENCY] Stage C (Tool): {} ms", stageCMs);
            log.info("[LATENCY] Stage D (LLM-2): {} ms", stageDMs);
        }
        log.info("[LATENCY] Stage E (TTS): {} ms", stageEMs);
        log.info("[LATENCY] Stage F (Send): {} ms", stageFMs);

        String stagesSummary;
        if (stageCMs >= 0) {
            stagesSummary =
                    String.format(
                            "A=%d B=%d C=%d D=%d E=%d F=%d",
                            stageAMs, stageBMs, stageCMs, stageDMs, stageEMs, stageFMs);
        } else {
            stagesSummary =
                    String.format(
                            "A=%d B=%d C=N/A D=N/A E=%d F=%d",
                            stageAMs, stageBMs, stageEMs, stageFMs);
        }
        log.info(
                "[LATENCY] Total (SpeechEnded \u2192 FirstAudio): {} ms | Stages: {}",
                stageTMs,
                stagesSummary);
    }

    /**
     * Logs partial turn latency when TTS synthesis was skipped (TTS unavailable or empty response
     * text after markdown stripping). Stages E, F, and T are not applicable.
     *
     * @param stageAMs Stage A (STT) latency in milliseconds
     * @param stageBMs Stage B (LLM-1) latency in milliseconds
     * @param stageCMs Stage C (Tool) cumulative latency, or -1 if no tool was invoked
     * @param stageDMs Stage D (LLM-2) cumulative latency, or -1 if no tool was invoked
     */
    private void logPartialLatency(
            long stageAMs, long stageBMs, long stageCMs, long stageDMs) {
        log.info("[LATENCY] Stage A (STT): {} ms", stageAMs);
        log.info("[LATENCY] Stage B (LLM-1): {} ms", stageBMs);
        if (stageCMs >= 0) {
            log.info("[LATENCY] Stage C (Tool): {} ms", stageCMs);
            log.info("[LATENCY] Stage D (LLM-2): {} ms", stageDMs);
        }
    }

    /**
     * Converts a nanosecond duration to milliseconds.
     *
     * @param nanos the duration in nanoseconds
     * @return the duration in milliseconds
     */
    private static long toMs(long nanos) {
        return nanos / 1_000_000L;
    }

    /**
     * Builds the tool definitions for the current turn.
     *
     * @return the list of tool definitions to pass to the LLM
     */
    private List<ToolDefinition> buildToolDefinitions() {
        return List.of(
                new ToolDefinition(
                        "get_current_weather",
                        "Get current real-time weather for a city or location. Always use this"
                                + " for weather questions. Returns temperature (°F), conditions,"
                                + " wind speed.",
                        Map.of(
                                "type", "object",
                                "properties",
                                        Map.of(
                                                "city",
                                                Map.of(
                                                        "type",
                                                        "string",
                                                        "description",
                                                        "City name, e.g. Dallas")),
                                "required", List.of("city"))),
                new ToolDefinition(
                        "focus_map",
                        "Pan and zoom the world map to a geographic location. Call this whenever"
                                + " the user mentions or asks about a specific place — city,"
                                + " region, country, or landmark. Choose zoom based on scope:"
                                + " 'city' for a specific city or landmark, 'region' for a"
                                + " state/prefecture/province, 'country' for an entire country,"
                                + " 'continent' for a continental region. Default to 'city' for"
                                + " specific locations.",
                        Map.of(
                                "type", "object",
                                "properties",
                                        Map.of(
                                                "location",
                                                Map.of(
                                                        "type", "string",
                                                        "description",
                                                        "Place name to geocode and focus on,"
                                                                + " e.g. 'Tokyo', 'Okinawa"
                                                                + " Prefecture', 'Japan'"),
                                                "zoom",
                                                Map.of(
                                                        "type", "string",
                                                        "enum",
                                                        List.of("continent", "country",
                                                                "region", "city"),
                                                        "description",
                                                        "Zoom level: continent, country,"
                                                                + " region (state/province),"
                                                                + " or city")),
                                "required", List.of("location"))),
                new ToolDefinition(
                        "set_scene",
                        "Switch the background scene to match the current conversation topic."
                                + " Call this immediately, with NO text output beforehand, when"
                                + " the conversation shifts to a topic that maps to a built-in"
                                + " scene: 'globe' for geography, weather, or location topics;"
                                + " 'space' for astronomy or space topics. Call with"
                                + " scene_id='dashboard' when returning to general topics.",
                        Map.of(
                                "type", "object",
                                "properties",
                                        Map.of(
                                                "scene_id",
                                                Map.of(
                                                        "type", "string",
                                                        "description",
                                                        "Scene identifier: 'globe', 'space',"
                                                                + " or 'dashboard'"),
                                                "params",
                                                Map.of(
                                                        "type", "object",
                                                        "description",
                                                        "Optional scene-specific parameters"),
                                                "transition",
                                                Map.of(
                                                        "type", "string",
                                                        "enum",
                                                        List.of("crossfade", "slide",
                                                                "instant"),
                                                        "description",
                                                        "Transition animation, default:"
                                                                + " crossfade")),
                                "required", List.of("scene_id"))),
                new ToolDefinition(
                        "show_adsb_radar",
                        "Display a live ADS-B radar showing real-time aircraft positions on the"
                                + " radar scene. Call this when the user asks to see live aircraft,"
                                + " flight traffic, planes in the sky, or air traffic radar.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "required", List.of())));
    }

    private void transitionState(AgentState newState) {
        log.info("agent.state: {} \u2192 {}", currentState.wireValue(), newState.wireValue());
        currentState = newState;
        eventSink.accept(new AgentSessionEvent.StateChangeEvent(newState));
    }
}
