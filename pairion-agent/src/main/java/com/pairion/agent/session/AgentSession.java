package com.pairion.agent.session;

import com.pairion.adapters.audio.opus.OpusDecoder;
import com.pairion.adapters.llm.spi.LlmAdapter;
import com.pairion.adapters.stt.spi.SttAdapter;
import com.pairion.agent.soul.SoulPromptProvider;
import com.pairion.core.agent.AgentState;
import com.pairion.core.llm.LlmEvent;
import com.pairion.core.llm.LlmRequest;
import com.pairion.core.llm.ToolDefinition;
import com.pairion.core.stt.SttEvent;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Manages the agent turn loop for a single WebSocket session.
 *
 * <p>Lifecycle: one instance per active WebSocket session. Handles the M1 Part 1 turn loop:
 *
 * <ol>
 *   <li>AudioStreamStart → allocate STT session, emit AgentStateChange(listening)
 *   <li>AudioChunkIn binary → Opus decode → feed PCM to STT
 *   <li>SpeechEnded → finalize STT → emit TranscriptFinal
 *   <li>Emit AgentStateChange(thinking) → call LLM → stream tokens
 *   <li>LLM completion → emit AgentStateChange(idle)
 * </ol>
 */
public class AgentSession {

    private static final Logger log = LoggerFactory.getLogger(AgentSession.class);

    private final String sessionId;
    private final SttAdapter sttAdapter;
    private final LlmAdapter llmAdapter;
    private final SoulPromptProvider soulProvider;
    private final Consumer<AgentSessionEvent> eventSink;

    private OpusDecoder opusDecoder;
    private SttAdapter.SttSession sttSession;
    private AgentState currentState = AgentState.IDLE;

    /**
     * Constructs an agent session.
     *
     * @param sessionId the WebSocket session ID
     * @param sttAdapter the speech-to-text adapter
     * @param llmAdapter the LLM adapter
     * @param soulProvider the SOUL prompt provider
     * @param eventSink consumer receiving agent events to forward to the Client
     */
    public AgentSession(
            String sessionId,
            SttAdapter sttAdapter,
            LlmAdapter llmAdapter,
            SoulPromptProvider soulProvider,
            Consumer<AgentSessionEvent> eventSink) {
        this.sessionId = sessionId;
        this.sttAdapter = sttAdapter;
        this.llmAdapter = llmAdapter;
        this.soulProvider = soulProvider;
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
        sttSession = sttAdapter.createSession(event -> handleSttEvent(event));

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
                    eventSink.accept(new AgentSessionEvent.TranscriptPartialEvent(partial.text()));
            case SttEvent.Final finalEvent -> {
                eventSink.accept(new AgentSessionEvent.TranscriptFinalEvent(finalEvent.text()));
                onTranscriptFinal(finalEvent.text());
            }
        }
    }

    /**
     * Handles an LLM event by forwarding tokens and transitioning to idle on stop.
     *
     * @param event the LLM event
     */
    void handleLlmEvent(LlmEvent event) {
        switch (event) {
            case LlmEvent.TokenDelta tokenDelta ->
                    eventSink.accept(new AgentSessionEvent.LlmTokenEvent(tokenDelta.delta()));
            case LlmEvent.Stop stop -> {
                log.info("llm.complete: output_tokens={}", stop.outputTokens());
                transitionState(AgentState.IDLE);
            }
            case LlmEvent.ToolCallRequest req ->
                    log.info("Tool call: id={} name={} input={}", req.toolCallId(), req.toolName(), req.input());
            case LlmEvent.ToolCallResult res ->
                    log.info("Tool result: id={} output={}", res.toolCallId(), res.output());
        }
    }

    private void onTranscriptFinal(String transcript) {
        transitionState(AgentState.THINKING);

        String systemPrompt = soulProvider.getSystemPrompt(sessionId);
        ToolDefinition weatherTool = new ToolDefinition(
            "get_current_weather",
            "Get current real-time weather for a city or location. Always use this for weather questions. Returns temperature, conditions, wind.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "city", Map.of("type", "string", "description", "City name, e.g. Dallas")
                ),
                "required", List.of("city")
            )
        );
        LlmRequest request = new LlmRequest(systemPrompt, transcript, List.of(weatherTool), null);


        llmAdapter.generate(request, this::handleLlmEvent);
    }

    private void transitionState(AgentState newState) {
        log.info("agent.state: {} → {}", currentState.wireValue(), newState.wireValue());
        currentState = newState;
        eventSink.accept(new AgentSessionEvent.StateChangeEvent(newState));
    }
}
