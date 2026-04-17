/**
 * Centralized pino logger configured once and imported by every package.
 *
 * @remarks
 * Log level is controlled via the `PAIRION_LOG_LEVEL` environment variable.
 * Defaults to `debug` in development and `info` in production.
 */

import pino from 'pino';

/** The singleton pino logger instance for the entire Server process. */
export const logger: pino.Logger = pino({
  name: 'pairion',
  level: process.env['PAIRION_LOG_LEVEL'] ?? 'debug',
  timestamp: pino.stdTimeFunctions.isoTime,
});

/**
 * Creates a child logger scoped to a specific subsystem.
 *
 * @param subsystem - The subsystem name (e.g. `gateway`, `agent`, `memory`).
 * @param bindings - Additional key-value pairs to attach to every log entry.
 * @returns A child pino logger with the subsystem binding.
 */
export function createSubsystemLogger(
  subsystem: string,
  bindings?: Record<string, unknown>,
): pino.Logger {
  return logger.child({ subsystem, ...bindings });
}
