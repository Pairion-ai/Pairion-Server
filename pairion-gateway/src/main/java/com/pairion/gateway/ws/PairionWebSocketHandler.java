package com.pairion.gateway.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairion.core.ws.DeviceIdentify;
import com.pairion.core.ws.HeartbeatPing;
import com.pairion.core.ws.HeartbeatPong;
import com.pairion.core.ws.SessionOpened;
import com.pairion.core.ws.WebSocketMessage;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * deserialization. Binary frames are logged and discarded in M0 (audio handling deferred to M1).
 */
@Component
public class PairionWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PairionWebSocketHandler.class);
    private static final String SERVER_VERSION = "0.1.0";

    private final ObjectMapper objectMapper;

    /**
     * Constructs the handler with the given Jackson ObjectMapper.
     *
     * @param objectMapper the Jackson mapper for WebSocket envelope serialization
     */
    public PairionWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
            default ->
                    log.info(
                            "Received unhandled message type: type={}, sessionId={}",
                            wsMessage.type(),
                            session.getId());
        }
    }

    /**
     * Handles inbound binary frames. In M0, binary frames are logged and discarded. Actual audio
     * processing is deferred to M1.
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
    }

    /**
     * Called after a WebSocket connection is closed.
     *
     * @param session the closed WebSocket session
     * @param status the close status
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket connection closed: sessionId={}, status={}", session.getId(), status);
    }

    /**
     * Handles a {@link DeviceIdentify} message by sending a {@link SessionOpened} response.
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
}
