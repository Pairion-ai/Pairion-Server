# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.0.0-alpha.1] - 2026-04-17

### Added

- **M0 Walking Skeleton** — Full monorepo structure with 13 `@pairion/*` packages
- **@pairion/core** — Branded identifier types (`UserId`, `SessionId`, `NodeId`, `DeviceId`, etc.), typed event bus, error classes with machine-readable codes, pino logger, lightweight TypeScript migration runner
- **@pairion/gateway** — Fastify HTTP server on port 18789, `ws` WebSocket server at `/ws/v1`, bearer-token auth middleware, request correlation ids
  - `GET /health` — Liveness probe
  - `GET /version` — Server version and build info
  - `GET /v1/status` — Subsystem readiness report
  - All other OpenAPI routes registered with HTTP 501 responses
  - WebSocket: `DeviceIdentify`/`NodeIdentify` → `IdentifyAck`, `HeartbeatPing` → `HeartbeatPong`, `LogForward` processed, all other message types → `Error` with `server.not_implemented`
- **@pairion/adapters** — Seven adapter interfaces (`LLMProvider`, `TTSProvider`, `STTProvider`, `WakeWordProvider`, `VoiceIdProvider`, `EmbeddingProvider`, `VectorStore`), adapter registry, `SecretsStore` with `FileSecretsStore` implementation, `ensureDevToken()` for dev-mode auth
- **@pairion/logs** — Centralized log sink accepting `LogForward` messages from Clients/Nodes
- **Scaffold packages** — `agent`, `household`, `speaker-id`, `memory`, `skills`, `node-mgmt`, `proactive`, `actions`, `authoring` — type definitions and structural exports, no runtime logic
- **CI pipeline** — GitHub Actions workflow running lint, typecheck, build, test, coverage check
- **Root tooling** — pnpm monorepo, TypeScript strict mode, ESLint with custom rules (no `console.log`, no vendor SDK imports outside adapters), Prettier, Vitest with 100% coverage enforcement
- **Dev token** — Auto-generated at `~/.pairion/device.token` on first server start
- **Repository meta** — `README.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `LICENSE`, `CHANGELOG.md`
