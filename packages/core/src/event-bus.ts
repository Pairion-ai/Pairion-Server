/**
 * Typed event bus for cross-cutting pub/sub signals within the Server process.
 *
 * @remarks
 * Packages communicate asynchronous events (session opened, speaker identified,
 * proactive fired, etc.) through this bus. The bus is typed: only events
 * declared in {@link PairionEvents} can be emitted or subscribed to.
 */

import { EventEmitter } from 'node:events';

/**
 * Map of event names to their payload types.
 *
 * @remarks
 * Extend this interface in future milestones as new event types are added.
 * M0 declares only the structural types; concrete events are added as
 * packages ship real functionality.
 */
export interface PairionEvents {
  /** A generic system-level event for startup/shutdown signals. */
  'system.ready': void;
  /** A generic system-level shutdown event. */
  'system.shutdown': void;
}

/** Callback type for a specific event. */
type EventCallback<T> = T extends void ? () => void : (payload: T) => void;

/**
 * A strongly-typed event bus backed by Node.js `EventEmitter`.
 *
 * @remarks
 * Ensures that only events declared in {@link PairionEvents} can be emitted
 * or subscribed to, with correct payload types.
 */
export class EventBus {
  private readonly emitter = new EventEmitter();

  /**
   * Subscribe to an event.
   *
   * @param event - The event name.
   * @param callback - The handler function.
   */
  on<K extends keyof PairionEvents>(event: K, callback: EventCallback<PairionEvents[K]>): void {
    this.emitter.on(event as string, callback as (...args: unknown[]) => void);
  }

  /**
   * Subscribe to an event, firing only once.
   *
   * @param event - The event name.
   * @param callback - The handler function.
   */
  once<K extends keyof PairionEvents>(event: K, callback: EventCallback<PairionEvents[K]>): void {
    this.emitter.once(event as string, callback as (...args: unknown[]) => void);
  }

  /**
   * Unsubscribe from an event.
   *
   * @param event - The event name.
   * @param callback - The handler to remove.
   */
  off<K extends keyof PairionEvents>(event: K, callback: EventCallback<PairionEvents[K]>): void {
    this.emitter.off(event as string, callback as (...args: unknown[]) => void);
  }

  /**
   * Emit an event to all subscribers.
   *
   * @param event - The event name.
   * @param args - The event payload (omitted for void events).
   */
  emit<K extends keyof PairionEvents>(
    ...args: PairionEvents[K] extends void ? [event: K] : [event: K, payload: PairionEvents[K]]
  ): void {
    const [event, payload] = args;
    this.emitter.emit(event as string, payload);
  }

  /** Remove all listeners for all events. */
  removeAllListeners(): void {
    this.emitter.removeAllListeners();
  }
}
