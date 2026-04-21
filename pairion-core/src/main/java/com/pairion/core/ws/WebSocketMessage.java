package com.pairion.core.ws;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed interface representing all JSON WebSocket envelopes in the Pairion protocol.
 *
 * <p>The {@code type} field in the JSON payload is the polymorphic discriminator used by Jackson
 * for deserialization. Binary frames (audio chunks) are handled separately and are not part of this
 * hierarchy.
 *
 * @see <a href="../../../../../../asyncapi.yaml">asyncapi.yaml</a>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = DeviceIdentify.class, name = "DeviceIdentify"),
    @JsonSubTypes.Type(value = SessionOpened.class, name = "SessionOpened"),
    @JsonSubTypes.Type(value = SessionClosed.class, name = "SessionClosed"),
    @JsonSubTypes.Type(value = HeartbeatPing.class, name = "HeartbeatPing"),
    @JsonSubTypes.Type(value = HeartbeatPong.class, name = "HeartbeatPong"),
    @JsonSubTypes.Type(value = ErrorMessage.class, name = "Error"),
    @JsonSubTypes.Type(value = AgentStateChange.class, name = "AgentStateChange"),
    @JsonSubTypes.Type(value = WakeWordDetected.class, name = "WakeWordDetected"),
    @JsonSubTypes.Type(value = AudioStreamStart.class, name = "AudioStreamStart"),
    @JsonSubTypes.Type(value = SpeechEnded.class, name = "SpeechEnded"),
    @JsonSubTypes.Type(value = AudioStreamEnd.class, name = "AudioStreamEnd"),
    @JsonSubTypes.Type(value = TextMessage.class, name = "TextMessage"),
    @JsonSubTypes.Type(value = TranscriptPartial.class, name = "TranscriptPartial"),
    @JsonSubTypes.Type(value = TranscriptFinal.class, name = "TranscriptFinal"),
    @JsonSubTypes.Type(value = LlmTokenStream.class, name = "LlmTokenStream"),
    @JsonSubTypes.Type(value = ToolCallStarted.class, name = "ToolCallStarted"),
    @JsonSubTypes.Type(value = ToolCallCompleted.class, name = "ToolCallCompleted"),
    @JsonSubTypes.Type(value = UnderBreathAck.class, name = "UnderBreathAck"),
    @JsonSubTypes.Type(value = MapFocus.class, name = "MapFocus"),
    @JsonSubTypes.Type(value = MapClear.class, name = "MapClear"),
})
public sealed interface WebSocketMessage
        permits DeviceIdentify,
                SessionOpened,
                SessionClosed,
                HeartbeatPing,
                HeartbeatPong,
                ErrorMessage,
                AgentStateChange,
                WakeWordDetected,
                AudioStreamStart,
                SpeechEnded,
                AudioStreamEnd,
                TextMessage,
                TranscriptPartial,
                TranscriptFinal,
                LlmTokenStream,
                ToolCallStarted,
                ToolCallCompleted,
                UnderBreathAck,
                MapFocus,
                MapClear {

    /**
     * Returns the protocol-level type discriminator for this message.
     *
     * @return the type string as defined in asyncapi.yaml
     */
    String type();
}
