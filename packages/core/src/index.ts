/**
 * @pairion/core — Shared vocabulary for the Pairion Server monorepo.
 *
 * @remarks
 * This package is imported by every other `@pairion/*` package and imports
 * nothing else inside the monorepo. It owns branded identifier types, the
 * typed event bus, error classes, the pino logger, and the migration runner.
 *
 * @packageDocumentation
 */

export {
  type UserId,
  type SessionId,
  type NodeId,
  type DeviceId,
  type SkillId,
  type AdapterId,
  type RuleId,
  type ActionId,
  createId,
} from './ids.js';

export {
  PairionError,
  NotImplementedError,
  UnauthorizedError,
  ForbiddenError,
  NotFoundError,
  BadRequestError,
  ConflictError,
} from './errors.js';

export { logger, createSubsystemLogger } from './logger.js';

export { EventBus, type PairionEvents } from './event-bus.js';

export { runMigrations, type Migration, type MigrationState } from './migration-runner.js';

export { AsyncIterableQueue } from './async-iterable-queue.js';

export { createOpusDecoder, createOpusEncoder } from './opus.js';
