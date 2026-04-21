package com.pairion.core.ws;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for the sealed {@link WebSocketMessage} hierarchy and Jackson polymorphic serialization.
 */
class WebSocketMessageTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deviceIdentifyRoundTrip() throws Exception {
        DeviceIdentify msg = new DeviceIdentify("DeviceIdentify", "dev-1", "token-abc", "1.0.0");
        String json = mapper.writeValueAsString(msg);
        WebSocketMessage deserialized = mapper.readValue(json, WebSocketMessage.class);
        assertThat(deserialized).isInstanceOf(DeviceIdentify.class);
        DeviceIdentify result = (DeviceIdentify) deserialized;
        assertThat(result.type()).isEqualTo("DeviceIdentify");
        assertThat(result.deviceId()).isEqualTo("dev-1");
        assertThat(result.bearerToken()).isEqualTo("token-abc");
        assertThat(result.clientVersion()).isEqualTo("1.0.0");
        assertThat(DeviceIdentify.TYPE).isEqualTo("DeviceIdentify");
    }

    @Test
    void sessionOpenedRoundTrip() throws Exception {
        SessionOpened msg = new SessionOpened("SessionOpened", "sess-1", "0.1.0");
        String json = mapper.writeValueAsString(msg);
        WebSocketMessage deserialized = mapper.readValue(json, WebSocketMessage.class);
        assertThat(deserialized).isInstanceOf(SessionOpened.class);
        SessionOpened result = (SessionOpened) deserialized;
        assertThat(result.type()).isEqualTo("SessionOpened");
        assertThat(result.sessionId()).isEqualTo("sess-1");
        assertThat(result.serverVersion()).isEqualTo("0.1.0");
        assertThat(SessionOpened.TYPE).isEqualTo("SessionOpened");
    }

    @Test
    void sessionClosedRoundTrip() throws Exception {
        SessionClosed msg = new SessionClosed("SessionClosed", "timeout");
        String json = mapper.writeValueAsString(msg);
        WebSocketMessage deserialized = mapper.readValue(json, WebSocketMessage.class);
        assertThat(deserialized).isInstanceOf(SessionClosed.class);
        SessionClosed result = (SessionClosed) deserialized;
        assertThat(result.type()).isEqualTo("SessionClosed");
        assertThat(result.reason()).isEqualTo("timeout");
        assertThat(SessionClosed.TYPE).isEqualTo("SessionClosed");
    }

    @Test
    void heartbeatPingRoundTrip() throws Exception {
        HeartbeatPing msg = new HeartbeatPing("HeartbeatPing", "2026-01-01T00:00:00Z");
        String json = mapper.writeValueAsString(msg);
        WebSocketMessage deserialized = mapper.readValue(json, WebSocketMessage.class);
        assertThat(deserialized).isInstanceOf(HeartbeatPing.class);
        HeartbeatPing result = (HeartbeatPing) deserialized;
        assertThat(result.type()).isEqualTo("HeartbeatPing");
        assertThat(result.timestamp()).isEqualTo("2026-01-01T00:00:00Z");
        assertThat(HeartbeatPing.TYPE).isEqualTo("HeartbeatPing");
    }

    @Test
    void heartbeatPongRoundTrip() throws Exception {
        HeartbeatPong msg = new HeartbeatPong("HeartbeatPong", "2026-01-01T00:00:01Z");
        String json = mapper.writeValueAsString(msg);
        WebSocketMessage deserialized = mapper.readValue(json, WebSocketMessage.class);
        assertThat(deserialized).isInstanceOf(HeartbeatPong.class);
        HeartbeatPong result = (HeartbeatPong) deserialized;
        assertThat(result.type()).isEqualTo("HeartbeatPong");
        assertThat(result.timestamp()).isEqualTo("2026-01-01T00:00:01Z");
        assertThat(HeartbeatPong.TYPE).isEqualTo("HeartbeatPong");
    }

    @Test
    void errorMessageRoundTrip() throws Exception {
        ErrorMessage msg = new ErrorMessage("Error", "AUTH_FAILED", "Invalid token");
        String json = mapper.writeValueAsString(msg);
        WebSocketMessage deserialized = mapper.readValue(json, WebSocketMessage.class);
        assertThat(deserialized).isInstanceOf(ErrorMessage.class);
        ErrorMessage result = (ErrorMessage) deserialized;
        assertThat(result.type()).isEqualTo("Error");
        assertThat(result.code()).isEqualTo("AUTH_FAILED");
        assertThat(result.message()).isEqualTo("Invalid token");
        assertThat(ErrorMessage.TYPE).isEqualTo("Error");
    }

    @Test
    void agentStateChangeRoundTrip() throws Exception {
        AgentStateChange msg = new AgentStateChange("AgentStateChange", "thinking");
        String json = mapper.writeValueAsString(msg);
        WebSocketMessage deserialized = mapper.readValue(json, WebSocketMessage.class);
        assertThat(deserialized).isInstanceOf(AgentStateChange.class);
        AgentStateChange result = (AgentStateChange) deserialized;
        assertThat(result.state()).isEqualTo("thinking");
        assertThat(AgentStateChange.TYPE).isEqualTo("AgentStateChange");
    }

    @Test
    void wakeWordDetectedRoundTrip() throws Exception {
        WakeWordDetected msg =
                new WakeWordDetected("WakeWordDetected", "2026-01-01T00:00:00Z", 0.95);
        String json = mapper.writeValueAsString(msg);
        WebSocketMessage deserialized = mapper.readValue(json, WebSocketMessage.class);
        assertThat(deserialized).isInstanceOf(WakeWordDetected.class);
        WakeWordDetected result = (WakeWordDetected) deserialized;
        assertThat(result.timestamp()).isEqualTo("2026-01-01T00:00:00Z");
        assertThat(result.confidence()).isEqualTo(0.95);
        assertThat(WakeWordDetected.TYPE).isEqualTo("WakeWordDetected");
    }

    @Test
    void wakeWordDetectedNullConfidence() throws Exception {
        WakeWordDetected msg =
                new WakeWordDetected("WakeWordDetected", "2026-01-01T00:00:00Z", null);
        String json = mapper.writeValueAsString(msg);
        WebSocketMessage deserialized = mapper.readValue(json, WebSocketMessage.class);
        assertThat(deserialized).isInstanceOf(WakeWordDetected.class);
        assertThat(((WakeWordDetected) deserialized).confidence()).isNull();
    }

    @Test
    void audioStreamStartRoundTrip() throws Exception {
        AudioStreamStart msg = new AudioStreamStart("AudioStreamStart", "stream-1", "opus", 16000);
        String json = mapper.writeValueAsString(msg);
        WebSocketMessage deserialized = mapper.readValue(json, WebSocketMessage.class);
        assertThat(deserialized).isInstanceOf(AudioStreamStart.class);
        AudioStreamStart result = (AudioStreamStart) deserialized;
        assertThat(result.streamId()).isEqualTo("stream-1");
        assertThat(result.codec()).isEqualTo("opus");
        assertThat(result.sampleRate()).isEqualTo(16000);
        assertThat(AudioStreamStart.TYPE).isEqualTo("AudioStreamStart");
    }

    @Test
    void speechEndedRoundTrip() throws Exception {
        SpeechEnded msg = new SpeechEnded("SpeechEnded", "stream-1");
        String json = mapper.writeValueAsString(msg);
        WebSocketMessage deserialized = mapper.readValue(json, WebSocketMessage.class);
        assertThat(deserialized).isInstanceOf(SpeechEnded.class);
        assertThat(((SpeechEnded) deserialized).streamId()).isEqualTo("stream-1");
        assertThat(SpeechEnded.TYPE).isEqualTo("SpeechEnded");
    }

    @Test
    void audioStreamEndRoundTrip() throws Exception {
        AudioStreamEnd msg = new AudioStreamEnd("AudioStreamEnd", "stream-1", "normal");
        String json = mapper.writeValueAsString(msg);
        WebSocketMessage deserialized = mapper.readValue(json, WebSocketMessage.class);
        assertThat(deserialized).isInstanceOf(AudioStreamEnd.class);
        AudioStreamEnd result = (AudioStreamEnd) deserialized;
        assertThat(result.streamId()).isEqualTo("stream-1");
        assertThat(result.reason()).isEqualTo("normal");
        assertThat(AudioStreamEnd.TYPE).isEqualTo("AudioStreamEnd");
    }

    @Test
    void textMessageRoundTrip() throws Exception {
        TextMessage msg = new TextMessage("TextMessage", "Hello Pairion");
        String json = mapper.writeValueAsString(msg);
        WebSocketMessage deserialized = mapper.readValue(json, WebSocketMessage.class);
        assertThat(deserialized).isInstanceOf(TextMessage.class);
        assertThat(((TextMessage) deserialized).text()).isEqualTo("Hello Pairion");
        assertThat(TextMessage.TYPE).isEqualTo("TextMessage");
    }

    @Test
    void transcriptPartialRoundTrip() throws Exception {
        TranscriptPartial msg = new TranscriptPartial("TranscriptPartial", "Hel");
        String json = mapper.writeValueAsString(msg);
        WebSocketMessage deserialized = mapper.readValue(json, WebSocketMessage.class);
        assertThat(deserialized).isInstanceOf(TranscriptPartial.class);
        assertThat(((TranscriptPartial) deserialized).text()).isEqualTo("Hel");
        assertThat(TranscriptPartial.TYPE).isEqualTo("TranscriptPartial");
    }

    @Test
    void transcriptFinalRoundTrip() throws Exception {
        TranscriptFinal msg = new TranscriptFinal("TranscriptFinal", "Hello");
        String json = mapper.writeValueAsString(msg);
        WebSocketMessage deserialized = mapper.readValue(json, WebSocketMessage.class);
        assertThat(deserialized).isInstanceOf(TranscriptFinal.class);
        assertThat(((TranscriptFinal) deserialized).text()).isEqualTo("Hello");
        assertThat(TranscriptFinal.TYPE).isEqualTo("TranscriptFinal");
    }

    @Test
    void llmTokenStreamRoundTrip() throws Exception {
        LlmTokenStream msg = new LlmTokenStream("LlmTokenStream", "token");
        String json = mapper.writeValueAsString(msg);
        WebSocketMessage deserialized = mapper.readValue(json, WebSocketMessage.class);
        assertThat(deserialized).isInstanceOf(LlmTokenStream.class);
        assertThat(((LlmTokenStream) deserialized).delta()).isEqualTo("token");
        assertThat(LlmTokenStream.TYPE).isEqualTo("LlmTokenStream");
    }

    @Test
    void toolCallStartedRoundTrip() throws Exception {
        ToolCallStarted msg =
                new ToolCallStarted("ToolCallStarted", "tc-1", "weather", Map.of("city", "London"));
        String json = mapper.writeValueAsString(msg);
        WebSocketMessage deserialized = mapper.readValue(json, WebSocketMessage.class);
        assertThat(deserialized).isInstanceOf(ToolCallStarted.class);
        ToolCallStarted result = (ToolCallStarted) deserialized;
        assertThat(result.toolCallId()).isEqualTo("tc-1");
        assertThat(result.toolName()).isEqualTo("weather");
        assertThat(result.input()).containsEntry("city", "London");
        assertThat(ToolCallStarted.TYPE).isEqualTo("ToolCallStarted");
    }

    @Test
    void toolCallCompletedRoundTrip() throws Exception {
        ToolCallCompleted msg =
                new ToolCallCompleted("ToolCallCompleted", "tc-1", Map.of("temp", 20));
        String json = mapper.writeValueAsString(msg);
        WebSocketMessage deserialized = mapper.readValue(json, WebSocketMessage.class);
        assertThat(deserialized).isInstanceOf(ToolCallCompleted.class);
        ToolCallCompleted result = (ToolCallCompleted) deserialized;
        assertThat(result.toolCallId()).isEqualTo("tc-1");
        assertThat(result.output()).containsEntry("temp", 20);
        assertThat(ToolCallCompleted.TYPE).isEqualTo("ToolCallCompleted");
    }

    @Test
    void underBreathAckRoundTrip() throws Exception {
        UnderBreathAck msg = new UnderBreathAck("UnderBreathAck", "mm");
        String json = mapper.writeValueAsString(msg);
        WebSocketMessage deserialized = mapper.readValue(json, WebSocketMessage.class);
        assertThat(deserialized).isInstanceOf(UnderBreathAck.class);
        UnderBreathAck result = (UnderBreathAck) deserialized;
        assertThat(result.acknowledgementType()).isEqualTo("mm");
        assertThat(UnderBreathAck.TYPE).isEqualTo("UnderBreathAck");
    }

    @Test
    void underBreathAckNullType() throws Exception {
        UnderBreathAck msg = new UnderBreathAck("UnderBreathAck", null);
        String json = mapper.writeValueAsString(msg);
        WebSocketMessage deserialized = mapper.readValue(json, WebSocketMessage.class);
        assertThat(((UnderBreathAck) deserialized).acknowledgementType()).isNull();
    }

    @Test
    void mapFocusRoundTrip() throws Exception {
        MapFocus msg = new MapFocus("MapFocus", 35.6762, 139.6503, "Tokyo, Japan", "city");
        String json = mapper.writeValueAsString(msg);
        WebSocketMessage deserialized = mapper.readValue(json, WebSocketMessage.class);
        assertThat(deserialized).isInstanceOf(MapFocus.class);
        MapFocus result = (MapFocus) deserialized;
        assertThat(result.type()).isEqualTo("MapFocus");
        assertThat(result.lat()).isEqualTo(35.6762);
        assertThat(result.lon()).isEqualTo(139.6503);
        assertThat(result.label()).isEqualTo("Tokyo, Japan");
        assertThat(result.zoom()).isEqualTo("city");
        assertThat(MapFocus.TYPE).isEqualTo("MapFocus");
    }

    @Test
    void mapClearRoundTrip() throws Exception {
        MapClear msg = new MapClear("MapClear");
        String json = mapper.writeValueAsString(msg);
        WebSocketMessage deserialized = mapper.readValue(json, WebSocketMessage.class);
        assertThat(deserialized).isInstanceOf(MapClear.class);
        assertThat(((MapClear) deserialized).type()).isEqualTo("MapClear");
        assertThat(MapClear.TYPE).isEqualTo("MapClear");
    }
}
