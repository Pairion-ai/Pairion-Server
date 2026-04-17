# @pairion/core

Shared vocabulary for the Pairion Server monorepo. Every other `@pairion/*` package imports this package; this package imports nothing else inside the monorepo.

## Public Surface

- **Branded identifiers** — `UserId`, `SessionId`, `NodeId`, `DeviceId`, `SkillId`, `AdapterId`, `RuleId`, `ActionId` and the `createId<T>()` factory.
- **Error classes** — `PairionError`, `NotImplementedError`, `UnauthorizedError`, `ForbiddenError`, `NotFoundError`, `BadRequestError`, `ConflictError`. All carry machine-readable codes matching the OpenAPI `Error` schema.
- **Logger** — `logger` (singleton pino instance) and `createSubsystemLogger()` for per-package child loggers.
- **Event bus** — `EventBus` class with typed pub/sub for cross-cutting signals.
- **Migration runner** — `runMigrations()` for the lightweight TypeScript migration runner used during development.
