/**
 * SessionManager — Manages active voice sessions.
 *
 * @remarks
 * Creates sessions, resolves adapters from the registry, constructs turn
 * loops, and manages session lifecycle. Conversation history lives on the
 * Session object in memory (no persistence in M1).
 */

import { randomUUID } from 'node:crypto';
import { createSubsystemLogger, type EventBus } from '@pairion/core';
import type { LLMProvider, STTProvider, TTSProvider, AdapterRegistry } from '@pairion/adapters';
import type { SkillInvoker, SkillRegistry } from '@pairion/skills';
import type { LLMMessage } from '@pairion/adapters';
import type { SessionEmitter } from './session-emitter.js';
import { processTurn, type TurnSummary } from './turn-loop.js';
import type { AgentConfig } from './index.js';

const log = createSubsystemLogger('session-manager');

/** An active voice session. */
export interface Session {
  /** Unique session id. */
  readonly id: string;
  /** Device that initiated the session. */
  readonly deviceId: string;
  /** Conversation history for context within this session. */
  readonly conversationHistory: LLMMessage[];
  /** When the session was created. */
  readonly createdAt: Date;
}

/**
 * Manages active voice sessions and orchestrates turn processing.
 */
export class SessionManager {
  private readonly sessions = new Map<string, Session>();
  private readonly registry: AdapterRegistry;
  private readonly skillRegistry: SkillRegistry;
  private readonly skillInvoker: SkillInvoker;
  private readonly eventBus: EventBus;
  private readonly config: AgentConfig;
  private readonly systemPrompt: string;

  /**
   * Creates a SessionManager.
   *
   * @param deps - Dependencies for session management.
   */
  constructor(deps: {
    registry: AdapterRegistry;
    skillRegistry: SkillRegistry;
    skillInvoker: SkillInvoker;
    eventBus: EventBus;
    config: AgentConfig;
    systemPrompt: string;
  }) {
    this.registry = deps.registry;
    this.skillRegistry = deps.skillRegistry;
    this.skillInvoker = deps.skillInvoker;
    this.eventBus = deps.eventBus;
    this.config = deps.config;
    this.systemPrompt = deps.systemPrompt;
  }

  /**
   * Creates a new session.
   *
   * @param deviceId - The device that initiated the session.
   * @param emitter - The emitter for sending events back to the Client.
   * @returns The created session id.
   */
  createSession(deviceId: string, emitter: SessionEmitter): string {
    const sessionId = randomUUID();
    const session: Session = {
      id: sessionId,
      deviceId,
      conversationHistory: [],
      createdAt: new Date(),
    };
    this.sessions.set(sessionId, session);
    this.eventBus.emit('session.opened', { sessionId, deviceId });
    emitter.sendSessionOpened(sessionId, '__dev_user__');
    log.info({ sessionId, deviceId }, 'Session created');
    return sessionId;
  }

  /**
   * Processes a turn within a session.
   *
   * @param sessionId - The session to process the turn in.
   * @param audio - Async iterable of audio chunks from the Client.
   * @param emitter - The emitter for sending events back.
   * @returns The turn summary, or undefined if session not found.
   */
  async processTurn(
    sessionId: string,
    audio: AsyncIterable<Uint8Array>,
    emitter: SessionEmitter,
  ): Promise<TurnSummary | undefined> {
    const session = this.sessions.get(sessionId);
    if (!session) {
      log.warn({ sessionId }, 'Session not found');
      return undefined;
    }

    const llm = this.registry.get('llm', 'anthropic') as LLMProvider | undefined;
    const stt = this.registry.get('stt', 'whisper-mlx') as STTProvider | undefined;
    const tts = this.registry.get('tts', 'kokoro-mlx') as TTSProvider | undefined;

    if (!llm || !stt || !tts) {
      log.error({ sessionId, hasLlm: !!llm, hasStt: !!stt, hasTts: !!tts }, 'Required adapters not available');
      return undefined;
    }

    const summary = await processTurn({
      sessionId,
      audio,
      llm,
      stt,
      tts,
      skillInvoker: this.skillInvoker,
      skillRegistry: this.skillRegistry,
      emitter,
      systemPrompt: this.systemPrompt,
      conversationHistory: session.conversationHistory,
      config: this.config,
    });

    this.eventBus.emit('session.turn.completed', { sessionId, durationMs: summary.totalMs });
    return summary;
  }

  /**
   * Closes a session.
   *
   * @param sessionId - The session to close.
   * @param reason - The reason for closing.
   */
  closeSession(sessionId: string, reason: string = 'normal'): void {
    const session = this.sessions.get(sessionId);
    if (session) {
      this.sessions.delete(sessionId);
      this.eventBus.emit('session.closed', { sessionId, reason });
      log.info({ sessionId, reason }, 'Session closed');
    }
  }

  /**
   * Gets a session by id.
   *
   * @param sessionId - The session id.
   * @returns The session, or undefined if not found.
   */
  getSession(sessionId: string): Session | undefined {
    return this.sessions.get(sessionId);
  }

  /** Returns the number of active sessions. */
  get activeCount(): number {
    return this.sessions.size;
  }
}
