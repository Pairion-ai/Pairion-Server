package com.pairion.gateway.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairion.adapters.llm.spi.LlmAdapter;
import com.pairion.adapters.stt.spi.SttAdapter;
import com.pairion.agent.session.AgentSession;
import com.pairion.agent.session.AgentSessionEvent;
import com.pairion.agent.soul.SoulPromptProvider;
import com.pairion.core.ws.AgentStateChange;
import com.pairion.core.ws.AudioStreamStart;
import com.pairion.core.ws.DeviceIdentify;
import com.pairion.core.ws.HeartbeatPing;
import com.pairion.core.ws.HeartbeatPong;
import com.pairion.core.ws.LlmTokenStream;
import com.pairion.core.ws.SessionOpened;
import com.pairion.core.ws.SpeechEnded;
import com.pairion.core.ws.TranscriptFinal;
import com.pairion.core.ws.TranscriptPartial;
import com.pairion.core.ws.WebSocketMessage;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * Handles WebSocket connections for the Pairion real-time protocol.
 *
 * <p>Processes JSON text frames as {@link WebSocketMessage} envelopes using Jackson polymorphic
 * deserialization. Binary frames are routed to the agent session's STT pipeline for audio
 * processing. Each connected client gets an {@link AgentSession} that manages the turn loop.
 */
@Component
public class PairionWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PairionWebSocketHandler.class);
    private static final String SERVER_VERSION = "0.2.0";

    private final ObjectMapper objectMapper;
    private final SttAdapter sttAdapter;
    private final LlmAdapter llmAdapter;
    private final SoulPromptProvider soulProvider;
    private final Map<String, AgentSession> sessions = new ConcurrentHashMap<>();

    /**
     * Constructs the handler with dependencies for agent session management.
     *
     * @param objectMapper the Jackson mapper for WebSocket envelope serialization
     * @param sttAdapter the speech-to-text adapter
     * @param llmAdapter the LLM adapter
     * @param soulProvider the SOUL prompt provider
     */
    public PairionWebSocketHandler(
            ObjectMapper objectMapper,
            SttAdapter sttAdapter,
            LlmAdapter llmAdapter,
            SoulPromptProvider soulProvider) {
        this.objectMapper = objectMapper;
        this.sttAdapter = sttAdapter;
        this.llmAdapter = llmAdapter;
        this.soulProvider = soulProvider;
    }

    /**
     * Called after a WebSocket connection is established.
     *
     * @param session the new WebSocket session
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connection established: sessionId={}", session.getId());
    }

    /**
     * Handles inbound JSON text frames by deserializing them into {@link WebSocketMessage}
     * instances and dispatching to the appropriate handler method.
     *
     * @param session the WebSocket session
     * @param message the inbound text message
     * @throws Exception if message processing fails
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws Exception {
        String payload = message.getPayload();
        log.debug(
                "Received text frame: sessionId={}, length={}", session.getId(), payload.length());

        WebSocketMessage wsMessage = objectMapper.readValue(payload, WebSocketMessage.class);

        switch (wsMessage) {
            case DeviceIdentify identify -> handleDeviceIdentify(session, identify);
            case HeartbeatPing ping -> handleHeartbeatPing(session, ping);
            case AudioStreamStart streamStart -> handleAudioStreamStart(session, streamStart);
            case SpeechEnded speechEnded -> handleSpeechEnded(session);
            default ->
                    log.info(
                            "Received unhandled message type: type={}, sessionId={}",
                            wsMessage.type(),
                            session.getId());
        }
    }

    /**
     * Handles inbound binary frames by routing them to the agent session's audio pipeline.
     *
     * @param session the WebSocket session
     * @param message the inbound binary message
     */
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        log.debug(
                "Received binary frame: sessionId={}, size={} bytes",
                session.getId(),
                message.getPayloadLength());

        AgentSession agentSession = sessions.get(session.getId());
        if (agentSession != null) {
            byte[] data = new byte[message.getPayloadLength()];
            message.getPayload().get(data);
            agentSession.onAudioChunk(data);
        }
    }

    /**
     * Called after a WebSocket connection is closed. Cleans up the agent session.
     *
     * @param session the closed WebSocket session
     * @param status the close status
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket connection closed: sessionId={}, status={}", session.getId(), status);
        sessions.remove(session.getId());
    }

    /**
     * Handles a {@link DeviceIdentify} message by creating an agent session and sending a {@link
     * SessionOpened} response.
     *
     * @param session the WebSocket session
     * @param identify the device identification message
     * @throws Exception if sending the response fails
     */
    void handleDeviceIdentify(WebSocketSession session, DeviceIdentify identify) throws Exception {
        log.info(
                "Device identified: deviceId={}, clientVersion={}",
                identify.deviceId(),
                identify.clientVersion());

        MDC.put("sessionId", session.getId());

        AgentSession agentSession =
                new AgentSession(
                        session.getId(),
                        sttAdapter,
                        llmAdapter,
                        soulProvider,
                        event -> sendAgentEvent(session, event));
        sessions.put(session.getId(), agentSession);

        SessionOpened response =
                new SessionOpened(SessionOpened.TYPE, UUID.randomUUID().toString(), SERVER_VERSION);

        String json = objectMapper.writeValueAsString(response);
        session.sendMessage(new TextMessage(json));
    }

    /**
     * Handles a {@link HeartbeatPing} by sending a {@link HeartbeatPong} response.
     *
     * @param session the WebSocket session
     * @param ping the heartbeat ping message
     * @throws Exception if sending the response fails
     */
    void handleHeartbeatPing(WebSocketSession session, HeartbeatPing ping) throws Exception {
        HeartbeatPong pong = new HeartbeatPong(HeartbeatPong.TYPE, Instant.now().toString());
        String json = objectMapper.writeValueAsString(pong);
        session.sendMessage(new TextMessage(json));
    }

    /**
     * Handles an {@link AudioStreamStart} by delegating to the agent session.
     *
     * @param session the WebSocket session
     * @param streamStart the audio stream start message
     */
    void handleAudioStreamStart(WebSocketSession session, AudioStreamStart streamStart) {
        AgentSession agentSession = sessions.get(session.getId());
        if (agentSession != null) {
            agentSession.onAudioStreamStart(streamStart.streamId());
        }
    }

    /**
     * Handles a {@link SpeechEnded} by delegating to the agent session.
     *
     * @param session the WebSocket session
     */
    void handleSpeechEnded(WebSocketSession session) {
        AgentSession agentSession = sessions.get(session.getId());
        if (agentSession != null) {
            agentSession.onSpeechEnded();
        }
    }

    /**
     * Forwards an agent session event to the Client as a WebSocket text frame.
     *
     * @param session the WebSocket session
     * @param event the agent event to forward
     */
    void sendAgentEvent(WebSocketSession session, AgentSessionEvent event) {
        try {
            String json =
                    switch (event) {
                        case AgentSessionEvent.StateChangeEvent sc ->
                                objectMapper.writeValueAsString(
                                        new AgentStateChange(
                                                AgentStateChange.TYPE, sc.state().wireValue()));
                        case AgentSessionEvent.TranscriptPartialEvent tp ->
                                objectMapper.writeValueAsString(
                                        new TranscriptPartial(TranscriptPartial.TYPE, tp.text()));
                        case AgentSessionEvent.TranscriptFinalEvent tf ->
                                objectMapper.writeValueAsString(
                                        new TranscriptFinal(TranscriptFinal.TYPE, tf.text()));
                        case AgentSessionEvent.LlmTokenEvent lt ->
                                objectMapper.writeValueAsString(
                                        new LlmTokenStream(LlmTokenStream.TYPE, lt.delta()));
                    };
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("Failed to send agent event: sessionId={}", session.getId(), e);
        }
    }
}
