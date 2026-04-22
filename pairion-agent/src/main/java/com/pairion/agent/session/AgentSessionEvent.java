package com.pairion.agent.session;

import com.pairion.core.agent.AgentState;
import java.util.Map;

/**
 * Sealed interface for events emitted by an {@link AgentSession} to be forwarded to the Client via
 * WebSocket.
 */
public sealed interface AgentSessionEvent
        permits AgentSessionEvent.StateChangeEvent,
                AgentSessionEvent.TranscriptPartialEvent,
                AgentSessionEvent.TranscriptFinalEvent,
                AgentSessionEvent.LlmTokenEvent,
                AgentSessionEvent.ToolCallStartedEvent,
                AgentSessionEvent.ToolCallCompletedEvent,
                AgentSessionEvent.AudioStreamStartEvent,
                AgentSessionEvent.AudioChunkEvent,
                AgentSessionEvent.AudioStreamEndEvent,
                AgentSessionEvent.MapFocusEvent,
                AgentSessionEvent.MapClearEvent,
                AgentSessionEvent.ConversationEndedEvent,
                AgentSessionEvent.SceneChangeEvent {

    /**
     * Agent state transition event.
     *
     * @param state the new agent state
     */
    record StateChangeEvent(AgentState state) implements AgentSessionEvent {}

    /**
     * Partial STT transcript event.
     *
     * @param text the partial transcript
     */
    record TranscriptPartialEvent(String text) implements AgentSessionEvent {}

    /**
     * Final STT transcript event.
     *
     * @param text the final transcript
     */
    record TranscriptFinalEvent(String text) implements AgentSessionEvent {}

    /**
     * Streamed LLM token delta event.
     *
     * @param delta the token text
     */
    record LlmTokenEvent(String delta) implements AgentSessionEvent {}

    /**
     * Notifies the client that a tool call has been initiated by the LLM.
     *
     * @param toolCallId unique identifier for this tool call
     * @param toolName name of the tool being invoked
     * @param input the input parameters passed to the tool
     */
    record ToolCallStartedEvent(String toolCallId, String toolName, Map<String, Object> input)
            implements AgentSessionEvent {}

    /**
     * Notifies the client that a tool call has completed.
     *
     * @param toolCallId unique identifier for this tool call
     * @param toolName name of the tool that was invoked
     * @param output the output returned by the tool
     */
    record ToolCallCompletedEvent(
            String toolCallId, String toolName, Map<String, Object> output)
            implements AgentSessionEvent {}

    /**
     * Notifies the client that a TTS audio stream is starting.
     *
     * @param streamId the audio stream identifier
     * @param codec the codec (always {@code "opus"})
     * @param sampleRate the sample rate in Hz
     */
    record AudioStreamStartEvent(String streamId, String codec, int sampleRate)
            implements AgentSessionEvent {}

    /**
     * A binary Opus audio frame with 4-byte stream ID prefix for WebSocket transmission.
     *
     * @param frameData the raw frame bytes (4-byte stream ID prefix + Opus payload)
     */
    record AudioChunkEvent(byte[] frameData) implements AgentSessionEvent {}

    /**
     * Notifies the client that a TTS audio stream has ended.
     *
     * @param streamId the audio stream identifier
     * @param reason the end reason ({@code "normal"}, {@code "interrupted"}, or {@code "error"})
     */
    record AudioStreamEndEvent(String streamId, String reason) implements AgentSessionEvent {}

    /**
     * Commands the client to pan and zoom the world map to a specific geographic location.
     *
     * @param lat latitude in decimal degrees
     * @param lon longitude in decimal degrees
     * @param label human-readable display label (e.g. "Tokyo, Japan")
     * @param zoom zoom level: {@code continent}, {@code country}, {@code region}, or {@code city}
     */
    record MapFocusEvent(double lat, double lon, String label, String zoom)
            implements AgentSessionEvent {}

    /**
     * Commands the client to clear the current map focus and resume globe auto-scroll.
     *
     * <p>Emitted after a 2-minute idle timeout since the last {@link MapFocusEvent}, or when the
     * user utters an ending phrase such as "Go back" or "that's all".
     */
    record MapClearEvent() implements AgentSessionEvent {}

    /**
     * Commands the client to exit conversation mode and return to wake-word listening.
     *
     * <p>Emitted when the user utters a dismissal phrase such as "that's all" or "goodbye".
     */
    record ConversationEndedEvent() implements AgentSessionEvent {}

    /**
     * Commands the client to switch the active background scene.
     *
     * <p>Emitted when the LLM calls the {@code set_scene} tool. The client {@code SceneManager}
     * dynamically loads the target scene and starts the transition animation.
     *
     * @param sceneId the identifier of the scene to activate (e.g. {@code "globe"},
     *     {@code "space"}, {@code "dashboard"})
     * @param params optional scene-specific parameters from the LLM tool call
     * @param transition transition animation: {@code "crossfade"}, {@code "slide"}, or
     *     {@code "instant"}
     */
    record SceneChangeEvent(String sceneId, Map<String, Object> params, String transition)
            implements AgentSessionEvent {}
}
