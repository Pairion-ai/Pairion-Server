package com.pairion.core.agent;

/**
 * Enumeration of agent processing states, matching the AgentStateChange WebSocket message.
 *
 * <p>State transitions follow the turn loop: idle → listening → thinking → speaking → idle.
 */
public enum AgentState {

    /** Agent is idle, awaiting user input. */
    IDLE("idle"),

    /** Agent is receiving and transcribing audio input. */
    LISTENING("listening"),

    /** Agent is processing the transcript via LLM. */
    THINKING("thinking"),

    /** Agent is streaming TTS audio output. */
    SPEAKING("speaking");

    private final String wireValue;

    AgentState(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the wire-format value as defined in asyncapi.yaml.
     *
     * @return the state string for WebSocket messages
     */
    public String wireValue() {
        return wireValue;
    }
}
