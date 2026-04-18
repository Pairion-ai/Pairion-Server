package com.pairion.agent.session;

import com.pairion.core.agent.AgentState;

/**
 * Sealed interface for events emitted by an {@link AgentSession} to be forwarded to the Client via
 * WebSocket.
 */
public sealed interface AgentSessionEvent
        permits AgentSessionEvent.StateChangeEvent,
                AgentSessionEvent.TranscriptPartialEvent,
                AgentSessionEvent.TranscriptFinalEvent,
                AgentSessionEvent.LlmTokenEvent {

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
}
