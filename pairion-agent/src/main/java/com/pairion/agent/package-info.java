/**
 * Agent orchestrator module for Pairion Server.
 *
 * <p>Owns the turn loop: receives STT transcripts, constructs LLM prompts with SOUL and memory
 * context, dispatches tool calls, streams TTS output, and manages agent state transitions.
 */
package com.pairion.agent;
