/**
 * @pairion/logs — Centralized log sink for the Pairion Server.
 *
 * @remarks
 * In M0, accepts `LogForward` WebSocket messages from Clients and Nodes
 * and writes them to the Server's own pino output. A SQLite-backed indexed
 * log store comes at a later milestone.
 *
 * @packageDocumentation
 */

import { createSubsystemLogger } from '@pairion/core';

const log = createSubsystemLogger('logs');

/** Log level values matching AsyncAPI LogForwardPayload. */
export type LogLevel = 'trace' | 'debug' | 'info' | 'warn' | 'error' | 'fatal';

/** A single forwarded log entry from a Client or Node. */
export interface ForwardedLogEntry {
  /** ISO timestamp from the originating device. */
  readonly timestamp: string;
  /** Log level. */
  readonly level: LogLevel;
  /** Originating subsystem name. */
  readonly subsystem: string;
  /** Log message text. */
  readonly message: string;
  /** Optional structured context. */
  readonly context?: Record<string, unknown> | undefined;
  /** Optional request correlation id. */
  readonly requestId?: string | undefined;
  /** Optional session correlation id. */
  readonly sessionId?: string | undefined;
}

/** A batch of forwarded log entries. */
export interface LogForwardBatch {
  /** Device id that forwarded the logs, if from a Client. */
  readonly sourceDeviceId?: string | undefined;
  /** Node id that forwarded the logs, if from a Node. */
  readonly sourceNodeId?: string | undefined;
  /** The forwarded log entries. */
  readonly entries: readonly ForwardedLogEntry[];
}

/**
 * Processes a batch of forwarded log entries by writing each to the
 * Server's pino output.
 *
 * @param batch - The log forward batch to process.
 */
export function processLogForward(batch: LogForwardBatch): void {
  const source = batch.sourceDeviceId ?? batch.sourceNodeId ?? 'unknown';

  for (const entry of batch.entries) {
    const pinoLevel = mapLevel(entry.level);
    log[pinoLevel](
      {
        source,
        remoteSubsystem: entry.subsystem,
        remoteTimestamp: entry.timestamp,
        ...(entry.requestId ? { requestId: entry.requestId } : {}),
        ...(entry.sessionId ? { sessionId: entry.sessionId } : {}),
        ...(entry.context ?? {}),
      },
      entry.message,
    );
  }
}

/**
 * Maps an AsyncAPI log level to a pino log method name.
 *
 * @param level - The log level from the forwarded entry.
 * @returns The corresponding pino method name.
 */
function mapLevel(level: LogLevel): 'trace' | 'debug' | 'info' | 'warn' | 'error' | 'fatal' {
  return level;
}
