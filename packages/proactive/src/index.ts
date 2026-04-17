/**
 * @pairion/proactive — Proactive rules engine and firing queue.
 *
 * @remarks
 * M0 scaffolding only. No runtime logic. The proactive engine evaluates
 * rules on a tick loop, enforces quiet hours and per-user scope, and
 * enqueues firings for delivery.
 *
 * @packageDocumentation
 */

/** Proactive rule trigger types. */
export type ProactiveTriggerKind = 'calendar' | 'task' | 'build' | 'cron' | 'memory-reminder';

/** Proactive action kinds. */
export type ProactiveActionKind = 'speak' | 'notify' | 'present-card' | 'run-skill';
