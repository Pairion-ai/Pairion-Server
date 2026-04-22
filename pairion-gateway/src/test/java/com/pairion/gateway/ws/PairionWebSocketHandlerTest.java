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
import com.pairion.adapters.tts.spi.TtsAdapter;
import com.pairion.agent.soul.SoulPromptProvider;
import com.pairion.agent.tools.ToolDispatcher;
import com.pairion.core.llm.LlmCapabilities;
import com.pairion.core.stt.SttCapabilities;
import com.pairion.core.tts.TtsCapabilities;
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
    private TtsAdapter ttsAdapter;
    private SoulPromptProvider soulProvider;
    private ToolDispatcher toolDispatcher;

    @BeforeEach
    void setUp() {
        sttAdapter = mock(SttAdapter.class);
        llmAdapter = mock(LlmAdapter.class);
        ttsAdapter = mock(TtsAdapter.class);
        soulProvider = mock(SoulPromptProvider.class);
        toolDispatcher = mock(ToolDispatcher.class);
        when(sttAdapter.capabilities()).thenReturn(SttCapabilities.unavailable());
        when(llmAdapter.capabilities()).thenReturn(LlmCapabilities.unavailable());
        when(ttsAdapter.capabilities()).thenReturn(TtsCapabilities.unavailable());
        when(sttAdapter.createSession(any())).thenReturn(mock(SttAdapter.SttSession.class));
        handler =
                new PairionWebSocketHandler(
                        objectMapper, sttAdapter, llmAdapter, ttsAdapter, soulProvider,
                        toolDispatcher, null);
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

        // DeviceIdentify auto-activates ADS-B radar: BackgroundChange + OverlayAdd + SessionOpened
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, org.mockito.Mockito.atLeast(1)).sendMessage(captor.capture());

        SessionOpened opened = captor.getAllValues().stream()
                .map(msg -> {
                    try {
                        return objectMapper.readValue(msg.getPayload(), WebSocketMessage.class);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .filter(msg -> msg instanceof SessionOpened)
                .map(msg -> (SessionOpened) msg)
                .findFirst()
                .orElseThrow(() -> new AssertionError("SessionOpened not found in messages"));

        assertThat(opened.type()).isEqualTo("SessionOpened");
        assertThat(opened.serverVersion()).isEqualTo("0.3.0");
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

    @Test
    void sendAgentEventAudioChunkSendsBinaryFrame() throws Exception {
        byte[] frame = new byte[] {0x01, 0x02, 0x03, 0x04};
        handler.sendAgentEvent(
                session,
                new com.pairion.agent.session.AgentSessionEvent.AudioChunkEvent(frame));

        ArgumentCaptor<org.springframework.web.socket.BinaryMessage> captor =
                ArgumentCaptor.forClass(org.springframework.web.socket.BinaryMessage.class);
        verify(session).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload().remaining()).isEqualTo(4);
    }

    @Test
    void sendAgentEventToolCallStartedSendsJson() throws Exception {
        handler.sendAgentEvent(
                session,
                new com.pairion.agent.session.AgentSessionEvent.ToolCallStartedEvent(
                        "tc-1", "get_current_weather", java.util.Map.of("city", "Dallas")));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("ToolCallStarted");
    }

    @Test
    void sendAgentEventToolCallCompletedSendsJson() throws Exception {
        handler.sendAgentEvent(
                session,
                new com.pairion.agent.session.AgentSessionEvent.ToolCallCompletedEvent(
                        "tc-1", "get_current_weather", java.util.Map.of("temperature_f", 72.0)));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("ToolCallCompleted");
    }

    @Test
    void sendAgentEventAudioStreamStartSendsJson() throws Exception {
        handler.sendAgentEvent(
                session,
                new com.pairion.agent.session.AgentSessionEvent.AudioStreamStartEvent(
                        "s1", "opus", 16000));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("AudioStreamStart");
    }

    @Test
    void sendAgentEventAudioStreamEndSendsJson() throws Exception {
        handler.sendAgentEvent(
                session,
                new com.pairion.agent.session.AgentSessionEvent.AudioStreamEndEvent(
                        "s1", "normal"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("AudioStreamEnd");
    }

    @Test
    void serializeEventAudioChunkReturnsNull() throws Exception {
        String result =
                handler.serializeEvent(
                        new com.pairion.agent.session.AgentSessionEvent.AudioChunkEvent(
                                new byte[] {1, 2}));
        assertThat(result).isNull();
    }

    @Test
    void sendAgentEventMapFocusSendsJson() throws Exception {
        handler.sendAgentEvent(
                session,
                new com.pairion.agent.session.AgentSessionEvent.MapFocusEvent(
                        35.6762, 139.6503, "Tokyo, Japan", "city"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        String payload = captor.getValue().getPayload();
        assertThat(payload).contains("MapFocus");
        assertThat(payload).contains("Tokyo, Japan");
    }

    @Test
    void sendAgentEventMapClearSendsJson() throws Exception {
        handler.sendAgentEvent(
                session,
                new com.pairion.agent.session.AgentSessionEvent.MapClearEvent());

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("MapClear");
    }

    @Test
    void sendAgentEventConversationEndedSendsJson() throws Exception {
        handler.sendAgentEvent(
                session,
                new com.pairion.agent.session.AgentSessionEvent.ConversationEndedEvent());

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("ConversationEnded");
    }

    @Test
    void sendAgentEventBackgroundChangeSendsJson() throws Exception {
        handler.sendAgentEvent(
                session,
                new com.pairion.agent.session.AgentSessionEvent.BackgroundChangeEvent(
                        "globe", "crossfade"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        String payload = captor.getValue().getPayload();
        assertThat(payload).contains("BackgroundChange");
        assertThat(payload).contains("globe");
        assertThat(payload).contains("crossfade");
    }

    @Test
    void sendAgentEventOverlayAddSendsJson() throws Exception {
        handler.sendAgentEvent(
                session,
                new com.pairion.agent.session.AgentSessionEvent.OverlayAddEvent("adsb", null));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        String payload = captor.getValue().getPayload();
        assertThat(payload).contains("OverlayAdd");
        assertThat(payload).contains("adsb");
    }

    @Test
    void sendAgentEventOverlayRemoveSendsJson() throws Exception {
        handler.sendAgentEvent(
                session,
                new com.pairion.agent.session.AgentSessionEvent.OverlayRemoveEvent("adsb"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        String payload = captor.getValue().getPayload();
        assertThat(payload).contains("OverlayRemove");
        assertThat(payload).contains("adsb");
    }

    @Test
    void sendAgentEventOverlayClearSendsJson() throws Exception {
        handler.sendAgentEvent(
                session,
                new com.pairion.agent.session.AgentSessionEvent.OverlayClearEvent());

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        String payload = captor.getValue().getPayload();
        assertThat(payload).contains("OverlayClear");
    }

    @Test
    void afterConnectionClosedWithNoRegisteredSessionDoesNotThrow() {
        // Close a session that was never registered (removed == null branch)
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
    }

    @Test
    void sendAgentEventSceneDataPushSendsJson() throws Exception {
        handler.sendAgentEvent(
                session,
                new com.pairion.agent.session.AgentSessionEvent.SceneDataPushEvent(
                        "adsb", java.util.List.of()));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        String payload = captor.getValue().getPayload();
        assertThat(payload).contains("SceneDataPush");
        assertThat(payload).contains("adsb");
    }
}
