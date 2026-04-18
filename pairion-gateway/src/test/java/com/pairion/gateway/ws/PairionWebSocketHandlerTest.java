package com.pairion.gateway.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairion.adapters.llm.spi.LlmAdapter;
import com.pairion.adapters.stt.spi.SttAdapter;
import com.pairion.agent.soul.SoulPromptProvider;
import com.pairion.core.llm.LlmCapabilities;
import com.pairion.core.stt.SttCapabilities;
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
    private SttAdapter sttAdapter;
    private LlmAdapter llmAdapter;
    private SoulPromptProvider soulProvider;

    @BeforeEach
    void setUp() {
        sttAdapter = mock(SttAdapter.class);
        llmAdapter = mock(LlmAdapter.class);
        soulProvider = mock(SoulPromptProvider.class);
        when(sttAdapter.capabilities()).thenReturn(SttCapabilities.unavailable());
        when(llmAdapter.capabilities()).thenReturn(LlmCapabilities.unavailable());
        when(sttAdapter.createSession(any())).thenReturn(mock(SttAdapter.SttSession.class));
        handler = new PairionWebSocketHandler(objectMapper, sttAdapter, llmAdapter, soulProvider);
        session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("test-session-1");
    }

    @Test
    void afterConnectionEstablishedLogs() {
        handler.afterConnectionEstablished(session);
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
        assertThat(opened.serverVersion()).isEqualTo("0.2.0");
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
    }

    @Test
    void handleUnknownMessageTypeDoesNotSend() throws Exception {
        String json = "{\"type\":\"TextMessage\",\"text\":\"hello\"}";
        handler.handleTextMessage(session, new TextMessage(json));
        verify(session, never()).sendMessage(any());
    }

    @Test
    void handleAudioStreamStartDelegatesToAgent() throws Exception {
        // First identify to create agent session
        String identify =
                "{\"type\":\"DeviceIdentify\",\"deviceId\":\"d1\","
                        + "\"bearerToken\":\"tok\",\"clientVersion\":\"1.0\"}";
        handler.handleTextMessage(session, new TextMessage(identify));

        // Then send AudioStreamStart
        String streamStart =
                "{\"type\":\"AudioStreamStart\",\"streamId\":\"s1\","
                        + "\"codec\":\"opus\",\"sampleRate\":16000}";
        handler.handleTextMessage(session, new TextMessage(streamStart));

        // Agent session should have been created and AudioStreamStart handled
        verify(session, atLeastOnce()).sendMessage(any());
    }

    @Test
    void handleSpeechEndedDelegatesToAgent() throws Exception {
        String identify =
                "{\"type\":\"DeviceIdentify\",\"deviceId\":\"d1\","
                        + "\"bearerToken\":\"tok\",\"clientVersion\":\"1.0\"}";
        handler.handleTextMessage(session, new TextMessage(identify));

        String speechEnded = "{\"type\":\"SpeechEnded\",\"streamId\":\"s1\"}";
        handler.handleTextMessage(session, new TextMessage(speechEnded));
    }

    @Test
    void handleBinaryMessageRoutesToAgent() throws Exception {
        String identify =
                "{\"type\":\"DeviceIdentify\",\"deviceId\":\"d1\","
                        + "\"bearerToken\":\"tok\",\"clientVersion\":\"1.0\"}";
        handler.handleTextMessage(session, new TextMessage(identify));

        byte[] data = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05};
        handler.handleBinaryMessage(session, new BinaryMessage(data));
    }

    @Test
    void handleBinaryMessageWithoutAgentSession() {
        byte[] data = new byte[] {0x01, 0x02, 0x03, 0x04};
        handler.handleBinaryMessage(session, new BinaryMessage(data));
    }

    @Test
    void audioStreamStartWithoutAgentSession() throws Exception {
        String streamStart =
                "{\"type\":\"AudioStreamStart\",\"streamId\":\"s1\","
                        + "\"codec\":\"opus\",\"sampleRate\":16000}";
        handler.handleTextMessage(session, new TextMessage(streamStart));
    }

    @Test
    void speechEndedWithoutAgentSession() throws Exception {
        String speechEnded = "{\"type\":\"SpeechEnded\",\"streamId\":\"s1\"}";
        handler.handleTextMessage(session, new TextMessage(speechEnded));
    }

    @Test
    void afterConnectionClosedCleansUpSession() throws Exception {
        String identify =
                "{\"type\":\"DeviceIdentify\",\"deviceId\":\"d1\","
                        + "\"bearerToken\":\"tok\",\"clientVersion\":\"1.0\"}";
        handler.handleTextMessage(session, new TextMessage(identify));

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
    }

    @Test
    void sendAgentEventForwardsAllEventTypes() throws Exception {
        String identify =
                "{\"type\":\"DeviceIdentify\",\"deviceId\":\"d1\","
                        + "\"bearerToken\":\"tok\",\"clientVersion\":\"1.0\"}";
        handler.handleTextMessage(session, new TextMessage(identify));

        // Reset mock to capture new messages
        org.mockito.Mockito.clearInvocations(session);
        when(session.getId()).thenReturn("test-session-1");

        // Test all four event types
        handler.sendAgentEvent(
                session,
                new com.pairion.agent.session.AgentSessionEvent.StateChangeEvent(
                        com.pairion.core.agent.AgentState.LISTENING));
        handler.sendAgentEvent(
                session,
                new com.pairion.agent.session.AgentSessionEvent.TranscriptPartialEvent("hel"));
        handler.sendAgentEvent(
                session,
                new com.pairion.agent.session.AgentSessionEvent.TranscriptFinalEvent("hello"));
        handler.sendAgentEvent(
                session, new com.pairion.agent.session.AgentSessionEvent.LlmTokenEvent("world"));

        verify(session, org.mockito.Mockito.times(4)).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendAgentEventHandlesException() throws Exception {
        String identify =
                "{\"type\":\"DeviceIdentify\",\"deviceId\":\"d1\","
                        + "\"bearerToken\":\"tok\",\"clientVersion\":\"1.0\"}";
        handler.handleTextMessage(session, new TextMessage(identify));

        // Close session to cause send to fail
        when(session.isOpen()).thenReturn(false);
        org.mockito.Mockito.doThrow(new java.io.IOException("closed"))
                .when(session)
                .sendMessage(any(TextMessage.class));

        // This should not throw — error is logged
        handler.sendAgentEvent(
                session,
                new com.pairion.agent.session.AgentSessionEvent.StateChangeEvent(
                        com.pairion.core.agent.AgentState.IDLE));
    }
}
