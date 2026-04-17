# CONVENTIONS.md — Pairion-Server

This document is authoritative for engineering discipline on `Pairion-Server`.
Every contributor (human and AI) reads it before touching code. Every Claude Code prompt assumes these conventions.

---

## 1. Project Standards (Pairion-Wide)

These standards apply across every Pairion repository and are reproduced verbatim in `Pairion-Client/CONVENTIONS.md` and `Pairion-Node/CONVENTIONS.md`.

### 1.1 Development philosophy

This is an AI-first codebase. AI writes 100% of production code. Development velocity is measured in AI-first benchmarks (proven track record of 200K+ lines of code per hour across prior projects). Traditional software development assumptions — sprint timelines, backlogs, phased severity-based deferrals — do not apply.

- **Never phase work by severity or priority.** Every fix, feature, or task is completed in a single pass. There is no cost/time justification for deferral. If it's worth doing, it's done now.
- **Never offer traditional time estimates** in PR descriptions, architecture discussions, or project planning. Effort is a function of prompt iteration, not calendar weeks.
- **No "next sprint" framing.** No "backlog" framing.

### 1.2 Source-of-truth hierarchy

When documents conflict, later beats earlier in this order:

1. `openapi.yaml`
2. `asyncapi.yaml`
3. `Architecture.md`
4. `Pairion-Charter.md`
5. Any Claude Code prompt
6. Prior conversation context

**Never assume, infer, or guess about a codebase.** Before generating any code, tests, fixes, prompts, or recommendations, verified source-of-truth documents must be consulted. Both Claude (conversational) and Claude Code (filesystem-capable) work from the same verified source — never from memory, conversation context, or inference.

### 1.3 Tests ship with code, always

- **Unit tests and integration tests ship in the same pass as the feature or fix.** No follow-up tickets for tests. Ever.
- **100% code coverage is mandatory, not aspirational.** Enforced by local verification during task execution.
- Tests validate behavior from public API, not internal implementation details.
- Test fixtures and harnesses are first-class code — same conventions, same review bar.

### 1.4 Documentation ships with code, always

- **Every class, every module, every public function has a documentation comment.** (Excluding DTOs, entities, and generated code.)
- TypeScript/JavaScript uses TSDoc/JSDoc. Java uses Javadoc. Dart uses DartDoc. C#/.NET uses XML Doc Comments. Rust uses RustDoc.
- Documentation ships in the same pass as the code. Not a follow-up.
- Auto-generated API docs publish on every release.

### 1.5 Centralized logging

- **All software projects must have centralized logging.**
- Structured, not free-text. Log level, subsystem, correlation id, session/user id where relevant.
- Client and Node logs forward to the Server's centralized log store.
- No `console.log`, no `println!` in production code paths. Use the project's logger.

### 1.6 Database migrations

- **Never use Flyway during development.** Flyway's lifecycle creates significant delays during restart-heavy development cycles.
- Use a lightweight migration runner during development (Hibernate for Java/Spring projects; a TypeScript migration runner for this project).
- Flyway adoption happens only when moving to production. That hardening is post-v1.

### 1.7 Build tooling

- **Never Gradle. Always Maven** (for Java projects — not applicable to this Node project, but applies if this convention is adopted for any Java module).
- This project uses **pnpm** for JavaScript/TypeScript and **Cargo** for Rust. No `npm`, no `yarn`, no Gradle, no Bazel in this repo.

### 1.8 Password policy

- **Development passwords:** minimal requirements. Short, simple, memorable. The goal is fast repeated testing without friction.
- **Production passwords:** strong (length, special characters, numbers, etc.). The shift happens when moving to production hardening, not before.

### 1.9 Claude Code prompt format

All prompts to Claude Code are `.md` file artifacts. Every prompt:

- **Begins with a STOP directive** reading the required source-of-truth files:
  > STOP: Before writing ANY code, read these files completely:
  > 1. `~/Documents/Github/<project folder>/openapi.yaml` — The OpenAPI spec defines every field name, type, enum value, and endpoint path. Your code must match it exactly.
  > 2. `~/Documents/Github/<project folder>/<project name>-Audit.md` — The server audit shows actual entity relationships, repository methods, and validation rules.
  > 3. `~/Documents/Github/<project folder>/<project name>-Architecture.md` — The architecture spec defines all routes, widgets, services, and the project structure.
  > Do not rely on the descriptions in this prompt alone. If this prompt conflicts with the source files, the source files win.
  >
  > If any of these files are missing, STOP and ask for them before proceeding.
- **Ends with:** *"Compile, Run, Test, Commit, Push to Github."*
- **Includes a report template** that Claude Code fills in with a summary of work performed, ending with the Git commit hash.

**Prompts do not contain code.** This includes implementation code, test code, configuration snippets, YAML, shell commands, and code examples. Prompts direct Claude Code with goals, constraints, and instructions — never with code. Claude Code has direct filesystem access and must read actual source files before producing any output.

---

## 2. Pairion-Server Stack Conventions

### 2.1 Runtime and tooling

- **Node.js 24 LTS.** Pinned via `.nvmrc` and enforced by `engines` in `package.json`.
- **TypeScript strict mode.** Every `tsconfig.json` extends a root `tsconfig.base.json` with `"strict": true`, `"noUncheckedIndexedAccess": true`, `"exactOptionalPropertyTypes": true`.
- **pnpm monorepo.** Workspaces configured via `pnpm-workspace.yaml`. No alternative package managers. Lockfile (`pnpm-lock.yaml`) is committed and CI fails on uncommitted lockfile changes.
- **ESM-only.** No CommonJS in new code. Every `package.json` declares `"type": "module"`.

### 2.2 Package structure

- All packages live under `packages/` following the `@pairion/*` naming scheme.
- Every package has identical internal structure: `src/`, `tests/`, `package.json`, `tsconfig.json`, `README.md`.
- Cross-package imports use workspace protocol (`"@pairion/core": "workspace:*"`). Never reach into another package's `dist/` or `src/` directly.
- Every public export is re-exported from the package's `src/index.ts`. Consumers import from the package name, never deep-import from internal paths.

### 2.3 HTTP and WebSocket

- **Fastify** for HTTP. Selected for its mature OpenAPI plugin ecosystem and Node 24 performance profile.
- **`@fastify/swagger` + `@fastify/type-provider-typebox`** (or equivalent) for OpenAPI-driven route registration. Routes derive their types from the spec; CI fails if the spec and code drift.
- **`ws`** library for WebSocket. Not `socket.io` — we need raw binary frame control for Opus audio and message-type discrimination per AsyncAPI.
- **Every WS message is validated** against the AsyncAPI schema before dispatch.

### 2.4 Logging

- **pino** as the logger. Configured once in `@pairion/core`, imported everywhere.
- Structured JSON logs. Each log entry has: `level`, `time`, `subsystem`, `msg`, and optional `requestId`, `sessionId`, `userId`, `nodeId`, `deviceId`.
- Log level set via `PAIRION_LOG_LEVEL` env var; defaults to `info` in production, `debug` in development.
- **`console.log` is banned** in `packages/*/src/`. ESLint rule enforces.

### 2.5 Data access

- **SQLite via `better-sqlite3`** for primary persistence. Synchronous API matches our session-orchestration model.
- **LanceDB** for vector storage. Accessed only through the `@pairion/memory` facade.
- **Migrations** live under each package's `migrations/` directory. A lightweight TypeScript migration runner (in `@pairion/core`) applies them at startup in dependency order. **No Flyway during development** per §1.6.
- **Per-user partitioning is enforced at the data layer.** Every facade method that reads user-scoped data takes `userId` as its first argument. CI schema linter rejects PRs with user-scoped tables missing a `user_id` column.

### 2.6 Adapter discipline

- **Vendor SDKs are imported only in `@pairion/adapters`.** Any other package importing `@anthropic-ai/sdk`, `openai`, or similar is a CI failure.
- Every adapter implements the relevant interface (`LLMProvider`, `TTSProvider`, etc.) and declares a capability descriptor.
- New adapters: create a directory under `packages/adapters/src/implementations/<kind>/<n>/`, implement the interface, register via `register.ts`, ship tests. No core code changes.

### 2.7 Secrets

- Secrets (API keys, bearer tokens) **never appear in**: source code, SQLite, LanceDB, configuration files in the repo, logs, error messages.
- Secrets live in: macOS Keychain (primary), encrypted local file (non-macOS fallback).
- Access via the `SecretsStore` abstraction in `@pairion/adapters`.

### 2.8 Testing

- **Vitest** for unit and integration tests. Configured at the root with coverage enforcement.
- **Coverage threshold: 100% of lines, branches, functions on every package's public surface.** Internals may be exercised only indirectly. Untested paths fail CI.
- **Playwright** (server-side) for end-to-end tests that exercise the REST + WS surface.
- **Contract tests** — tests that verify adapter implementations actually satisfy their interfaces. Run against every implementation.
- **CI benchmarks** — latency tests for voice round-trip, barge-in, memory recall, voice-ID precision/recall. Each has a pass/fail threshold; regressions fail the build.
- **Cross-user leak suite** — synthesized two-user scenarios that exercise every user-scoped code path. Must pass 100% per Charter §12.1.

### 2.9 Documentation

- **TSDoc** on every class and public function. No exceptions for "obvious" code.
- **JSDoc** in `.js` config files or build scripts if any.
- Doc comments describe *behavior* and *contract*, not implementation. "Returns the User for this Device" — not "Does a SELECT on devices and users tables."
- **`@pairion/*` package READMEs** summarize the package's purpose and public API. Auto-updated where possible; reviewed for accuracy on every release.
- API docs published via TypeDoc on every release.

### 2.10 Linting and formatting

- **ESLint** with `@typescript-eslint` strict rules.
- **Prettier** for formatting, integrated with ESLint.
- Custom rules enforce project invariants: "no vendor SDK imports outside `@pairion/adapters`", "user-scoped data access must pass `userId`", "no `console.log`".
- **CI fails on lint errors and unformatted code.** No exceptions.

### 2.11 Git and PR conventions

- **Trunk-based.** `main` is always green. Feature branches are short-lived.
- Commits use Conventional Commits format: `feat(skills): add aws-bill skill`, `fix(agent): prevent tool-call retry storm`, `docs(memory): clarify partitioning invariant`.
- **Every PR links the Claude Code prompt** that generated the change, in the PR description.
- **Local verification runs during every task** — lint, type-check, build, test, coverage threshold, schema linter, cross-user leak suite, benchmarks. Claude Code executes the full verification pipeline as part of every task.
- **No GitHub Actions CI is configured.** Do not re-add CI workflows unless a prompt explicitly directs it.

### 2.12 CHANGELOG

- `CHANGELOG.md` is updated in every PR that ships user-visible or API-surface changes.
- Follows Keep a Changelog conventions: sections for Added / Changed / Deprecated / Removed / Fixed / Security.
- OpenAPI / AsyncAPI version bumps are noted in CHANGELOG with migration notes.

---

## 3. Invariants (from `Architecture.md` §16)

These are architectural laws. Violating them is a red-flag in code review.

1. **No package imports a vendor SDK directly.** Vendor integration happens only in `@pairion/adapters`.
2. **Every user-scoped data access is partitioned at the data layer.** No raw queries. No "admin" escape hatch outside the audited Owner path.
3. **Every package has 100% test coverage on its public surface.**
4. **Every class and public function has a TSDoc comment.**
5. **All logs are structured.** No `console.log`. No stringly-typed log levels.
6. **AsyncAPI and OpenAPI are authoritative.** If code contradicts spec, code is wrong.
7. **No first-party telemetry.**
8. **Secrets never touch SQLite.**
9. **Voice embeddings never leave the Household.**
10. **The agent never talks to a vendor without going through the adapter layer.**

---

## 4. Local Verification Pipeline (`pnpm ci:all`)

**Verification runs locally during task execution** — Claude Code runs tests, lints, typechecks, and coverage as part of every task. No GitHub Actions CI is configured. Do not re-add CI workflows unless a prompt explicitly directs it.

Every task runs, in order:

1. `pnpm install --frozen-lockfile`
2. `pnpm lint`
3. `pnpm typecheck`
4. `pnpm build` (TypeScript project references)
5. `pnpm test` (unit + integration)
6. `pnpm test:contract` (adapter contract tests)
7. `pnpm test:leak` (cross-user leak suite)
8. `pnpm test:bench` (latency benchmarks)
9. `pnpm coverage:check` (enforce 100% thresholds)
10. `pnpm schema:lint` (custom schema linter for partitioning invariant)

Failure at any step fails the task. No step is skippable.

---

## 5. When In Doubt

Read, in order: this document, `Pairion-Charter.md`, `Architecture.md`, `openapi.yaml`, `asyncapi.yaml`. If still uncertain, the code you're about to write is probably wrong — stop and ask.