/**
 * Agent session management for Pairion.
 *
 * <p>Each active WebSocket session has a corresponding {@link AgentSession} that manages the agent
 * turn loop: STT transcription, LLM generation, and (in future milestones) TTS output.
 */
package com.pairion.agent.session;
