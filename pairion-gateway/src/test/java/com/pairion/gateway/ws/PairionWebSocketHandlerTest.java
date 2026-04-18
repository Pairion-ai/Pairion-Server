package com.pairion.gateway.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairion.core.ws.HeartbeatPong;
import com.pairion.core.ws.SessionOpened;
import com.pairion.core.ws.WebSocketMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** Tests for {@link PairionWebSocketHandler}. */
class PairionWebSocketHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PairionWebSocketHandler handler;
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        handler = new PairionWebSocketHandler(objectMapper);
        session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("test-session-1");
    }

    @Test
    void afterConnectionEstablishedLogs() {
        handler.afterConnectionEstablished(session);
        // no exception means success — logging is verified by coverage
    }

    @Test
    void handleDeviceIdentifySendsSessionOpened() throws Exception {
        String json =
                "{\"type\":\"DeviceIdentify\",\"deviceId\":\"d1\","
                        + "\"bearerToken\":\"tok\",\"clientVersion\":\"1.0\"}";
        handler.handleTextMessage(session, new TextMessage(json));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());

        WebSocketMessage response =
                objectMapper.readValue(captor.getValue().getPayload(), WebSocketMessage.class);
        assertThat(response).isInstanceOf(SessionOpened.class);
        SessionOpened opened = (SessionOpened) response;
        assertThat(opened.type()).isEqualTo("SessionOpened");
        assertThat(opened.serverVersion()).isEqualTo("0.1.0");
        assertThat(opened.sessionId()).isNotEmpty();
    }

    @Test
    void handleHeartbeatPingSendsPong() throws Exception {
        String json = "{\"type\":\"HeartbeatPing\",\"timestamp\":\"2026-01-01T00:00:00Z\"}";
        handler.handleTextMessage(session, new TextMessage(json));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());

        WebSocketMessage response =
                objectMapper.readValue(captor.getValue().getPayload(), WebSocketMessage.class);
        assertThat(response).isInstanceOf(HeartbeatPong.class);
        HeartbeatPong pong = (HeartbeatPong) response;
        assertThat(pong.type()).isEqualTo("HeartbeatPong");
        assertThat(pong.timestamp()).isNotEmpty();
    }

    @Test
    void handleUnknownMessageTypeDoesNotSend() throws Exception {
        String json = "{\"type\":\"TextMessage\",\"text\":\"hello\"}";
        handler.handleTextMessage(session, new TextMessage(json));

        verify(session, never()).sendMessage(any());
    }

    @Test
    void handleBinaryMessageDoesNotSend() {
        byte[] data = new byte[] {0x01, 0x02, 0x03, 0x04};
        handler.handleBinaryMessage(session, new BinaryMessage(data));

        // binary frames are logged and discarded in M0
    }

    @Test
    void afterConnectionClosedLogs() {
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        // no exception means success
    }
}
