package com.pairion.agent.session;

import com.pairion.adapters.audio.opus.OpusDecoder;
import com.pairion.adapters.llm.spi.LlmAdapter;
import com.pairion.adapters.stt.spi.SttAdapter;
import com.pairion.adapters.tts.spi.TtsAdapter;
import com.pairion.agent.soul.SoulPromptProvider;
import com.pairion.agent.tools.ToolDispatcher;
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
    private final Consumer<AgentSessionEvent> eventSink;

    private OpusDecoder opusDecoder;
    private SttAdapter.SttSession sttSession;
    private AgentState currentState = AgentState.IDLE;

    /**
     * Constructs an agent session.
     *
     * @param sessionId the WebSocket session ID
     * @param sttAdapter the speech-to-text adapter (may be null if STT unavailable)
     * @param llmAdapter the LLM adapter
     * @param ttsAdapter the text-to-speech adapter (may be null if TTS unavailable)
     * @param soulProvider the SOUL prompt provider
     * @param toolDispatcher the tool dispatcher for LLM tool calls
     * @param eventSink consumer receiving agent events to forward to the Client
     */
    public AgentSession(
            String sessionId,
            SttAdapter sttAdapter,
            LlmAdapter llmAdapter,
            TtsAdapter ttsAdapter,
            SoulPromptProvider soulProvider,
            ToolDispatcher toolDispatcher,
            Consumer<AgentSessionEvent> eventSink) {
        this.sessionId = sessionId;
        this.sttAdapter = sttAdapter;
        this.llmAdapter = llmAdapter;
        this.ttsAdapter = ttsAdapter;
        this.soulProvider = soulProvider;
        this.toolDispatcher = toolDispatcher;
        this.eventSink = eventSink;
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

    /** Handles an inbound SpeechEnded message. Finalizes the STT session. */
    public void onSpeechEnded() {
        MDC.put("sessionId", sessionId);
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
                eventSink.accept(
                        new AgentSessionEvent.TranscriptFinalEvent(finalEvent.text()));
                onTranscriptFinal(finalEvent.text());
            }
        }
    }

    /**
     * Runs the full turn: multi-turn LLM tool loop followed by TTS synthesis.
     *
     * <p>Transitions: thinking → (tool rounds) → speaking → idle
     *
     * @param transcript the final STT transcript
     */
    private void onTranscriptFinal(String transcript) {
        transitionState(AgentState.THINKING);

        String systemPrompt = soulProvider.getSystemPrompt(sessionId);
        List<ToolDefinition> toolDefinitions = buildToolDefinitions();

        // Multi-turn tool dispatch loop
        List<LlmRequest.ToolCallPair> toolHistory = new ArrayList<>();
        StringBuilder responseText = new StringBuilder();
        int rounds = 0;

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

            llmAdapter.generate(
                    request,
                    event -> handleLlmEvent(event, responseText, pendingToolCalls));

            if (pendingToolCalls.isEmpty()) {
                // LLM finished with text — no more tool calls
                break;
            }

            // Execute each tool call and add to history
            for (LlmEvent.ToolCallRequest tc : pendingToolCalls) {
                eventSink.accept(
                        new AgentSessionEvent.ToolCallStartedEvent(
                                tc.toolCallId(), tc.toolName(), tc.input()));
                Map<String, Object> result =
                        toolDispatcher.dispatch(tc.toolName(), tc.input());
                eventSink.accept(
                        new AgentSessionEvent.ToolCallCompletedEvent(
                                tc.toolCallId(), tc.toolName(), result));
                toolHistory.add(
                        new LlmRequest.ToolCallPair(
                                tc.toolCallId(), tc.toolName(), tc.input(), result));
            }
        }

        // TTS: synthesize the accumulated response text
        String text = responseText.toString().trim();
        if (!text.isEmpty() && ttsAdapter.capabilities().available()) {
            synthesizeSpeech(text);
        } else {
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
     * Synthesizes the response text via TTS and streams Opus audio frames to the client.
     *
     * <p>Emits AudioStreamStart, zero or more AudioChunk binary events, then AudioStreamEnd.
     * Transitions state to SPEAKING before synthesis and back to IDLE afterward.
     *
     * @param text the text to synthesize
     */
    private void synthesizeSpeech(String text) {
        transitionState(AgentState.SPEAKING);

        String streamId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        // Use 16000 Hz for Opus (standard Piper TTS output rate after resampling)
        int sampleRate = 16000;

        eventSink.accept(
                new AgentSessionEvent.AudioStreamStartEvent(streamId, "opus", sampleRate));
        log.info("tts.stream.start: streamId={}", streamId);

        long startMs = System.currentTimeMillis();
        String endReason = "normal";

        try {
            ttsAdapter.speak(
                    text,
                    ttsEvent -> {
                        switch (ttsEvent) {
                            case TtsEvent.Chunk chunk ->
                                    eventSink.accept(
                                            new AgentSessionEvent.AudioChunkEvent(chunk.audio()));
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

        long totalMs = System.currentTimeMillis() - startMs;
        log.info("tts.stream.end: streamId={}, elapsed_ms={}", streamId, totalMs);
        eventSink.accept(new AgentSessionEvent.AudioStreamEndEvent(streamId, endReason));
        transitionState(AgentState.IDLE);
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
                                "required", List.of("city"))));
    }

    private void transitionState(AgentState newState) {
        log.info("agent.state: {} → {}", currentState.wireValue(), newState.wireValue());
        currentState = newState;
        eventSink.accept(new AgentSessionEvent.StateChangeEvent(newState));
    }
}
