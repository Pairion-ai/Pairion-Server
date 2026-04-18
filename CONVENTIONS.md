# Pairion-Server Engineering Conventions

## 0. User Preferences (Binding — Applied to All Work)

Always be my sparring partner. Look for blind spots in my theories, proposals, ideas, weak arguments, look for missing data. I want the tightest most correct and most winning results and mostly for for you to not be a sycophant. If my idea or suggestion is correct, then strong agreement is warranted and requested. But if not, I need to see alternate ideas, better ideas, better outcomes. Your goal is not to make me happy, but to help me achieve greater accuracy and success in all facets of our interaction.

In addition to the above, for anything relating to software development please additionally apply the following:

I am an AI-first developer producing code at 500x traditional speed. AI writes 100% of production code. Never give traditional time estimates, never phase work by severity/priority, never suggest "next sprint" or "backlog." Every fix, feature, or task is completed in a single pass — there is no cost/time justification for deferral. When estimating effort, use proven AI-first benchmarks: we've done over 200k lines of code per hour. Traditional software development assumptions do not apply to my workflow.

NEVER assume, infer, or guess about any codebase. You have no filesystem access. Before generating any code, tests, fixes, prompts, or recommendations that touch a codebase, you MUST request: (1) A current comprehensive audit that was produced using our Claude Audit Template for every project/codebase involved, and (2) The OpenAPI.yaml (also created by the audit template) for every service involved, so you understand the full REST API surface. If the work touches multiple projects, request audits and OpenAPI specs for ALL of them — never leave any out. Do not proceed until you have these. When I provide these files, also ask for their filesystem paths so you can reference them in any Claude Code prompts you generate. Both you and Claude Code must work from the same verified source of truth — never from memory, conversation context, or inference.

All code — features, fixes, remediations, anything — ships with tests in the same pass. 100% code coverage is mandatory, not aspirational. This includes both unit and integration tests. All tests are never a follow-up task. We never use Flyway during development. Flyway can cause significant delays when stopping and restarting services, which is typical during development. We only use Flyway when we move a project into production. Before that, we use Hibernate. Never use Gradle, we ALWAYS use Maven. Likewise, for production we would normally require strong passwords (length, special characters, numbers, etc), but during development, when repeatedly testing, we want minimal requirements to make logins fast and easy.

All code must have documentation comments on every class/module and every public method/function (excluding DTOs, entities, and generated code). Java uses Javadoc, TypeScript/JavaScript uses TSDoc/JSDoc, Dart uses DartDoc, C#/.NET uses XML Doc Comments. Documentation ships in the same pass as the code. All software projects must have centralized logging.

All prompts to Claude Code must be .md file artifacts. Every prompt must begin with: "STOP: Before writing ANY code, read these files completely: 1. ~/Documents/Github/<project folder>/openapi.yaml — The OpenAPI spec defines every field name, type, enum value, and endpoint path. Your code must match it exactly. 2. ~/Documents/Github/<project folder>/<project name>-Audit.md — The server audit shows actual entity relationships, repository methods, and validation rules. 3. ~/Documents/Github/<project folder>/<project name>-Architecture.md — The architecture spec defines all routes, widgets, services, and the project structure. Do not rely on the descriptions in this prompt alone. If this prompt conflicts with the source files, the source files win." If any of these files are missing, STOP, and ask for them before proceeding.

Each prompt must end with: "Compile, Run, Test, Commit, Push to Github". Your prompts will also end with a template that Claude Code will use as a report format to give back the summary of the work it performed during the prompt. That report template will include the Git commit hash.

Claude never writes code of any kind in any prompt. This includes implementation code, test code, configuration snippets, YAML, shell commands, and code examples. Prompts direct Claude Code with goals, constraints, and instructions — never with code. Claude Code has direct filesystem access and must read actual source files before producing any output. If achieving a goal requires code, the prompt tells Claude Code what to accomplish and what files to read, not how to write it.

---

## 1. Repository Stack

- **Language:** Java 21
- **Framework:** Spring Boot 3.4+
- **Build tool:** Maven (never Gradle, per user preference)
- **Test framework:** JUnit 5 + AssertJ + Mockito
- **Architectural-fitness tests:** ArchUnit
- **Schema tool (development):** Hibernate `hbm2ddl.auto=update` — Flyway only at production (M12)
- **Logging:** SLF4J over Logback (JSON structured output)
- **Concurrency model:** Virtual threads (`spring.threads.virtual.enabled=true`) for all handlers; Reactor `Flux` only inside adapter streaming paths

## 2. Documentation Requirements

- Javadoc on every public class, interface, and method (excluding DTOs, entities, and generated code per user preference)
- Package-level `package-info.java` with purpose summary for every package
- Javadoc is ASCII, terse, action-oriented. Every `@param` and `@return` documented.
- `README.md` at repo root with build, run, test instructions
- `Architecture.md` at repo root (source of truth for architecture)
- `CHANGELOG.md` at repo root, Keep A Changelog format

## 3. Coverage

- 100% line coverage required on every Maven module for tests to pass (enforced via JaCoCo in `mvn verify`)
- 100% branch coverage required (same enforcement)
- Every test runs in isolation; no test depends on another test's side effects
- Integration tests use `@SpringBootTest` with `TestContainers` where external dependencies are involved
- Contract tests exist for every adapter interface

## 4. Local Verification (no CI during development phase)

Every task runs the following and must pass before commit:
- `mvn clean verify` — compiles, tests, coverage checks, ArchUnit checks, Checkstyle (Javadoc enforcement)
- `mvn spotless:check` — formatting
- No warnings from `mvn compile`
- Checkstyle runs during `verify` phase and fails the build if any public class, interface, or method is missing Javadoc (excludes DTOs, entities, records, tests, and generated code)

CI (GitHub Actions) is added at M12 for launch polish only.

## 5. Code Style

- Package structure per Architecture §3
- No vendor SDK import outside adapter packages (ArchUnit-enforced)
- No `System.out.println` or `System.err.println` anywhere in `src/main/java` (ArchUnit-enforced)
- No business logic in controllers — controllers dispatch to services
- Constructor injection only (no field injection, no setter injection)
- Records over classes for DTOs
- Sealed interfaces for closed hierarchies (WebSocket message types, agent events, etc.)

## 6. Secrets

- Development: `ANTHROPIC_API_KEY` environment variable
- No Keychain integration in development
- Secrets never in source, tests, logs, or error messages
- An SLF4J turbo filter redacts patterns matching `sk-ant-[A-Za-z0-9_-]+`

## 7. Logging

- SLF4J + Logback, JSON structured output
- Every log entry carries session ID in MDC when inside a session context
- Latency instrumentation at every agent-turn stage (log start and end with elapsed-ms)
- Client log forwarding accepted at `POST /v1/logs`; records flow through Logback alongside Server logs

## 8. WebSocket Discipline

- Raw `BinaryWebSocketHandler` (not STOMP)
- Buffer sizes explicitly configured in `application.yml`:
  - `server.tomcat.websocket.max-binary-message-buffer-size: 1MB`
  - `server.tomcat.websocket.max-text-message-buffer-size: 256KB`
- Every binary frame routed via a 4-byte stream ID prefix

## 9. Passwords (for future user-auth features)

- Development: minimal requirements (any non-empty password accepted) for repeated-login friction avoidance
- Production (M12+): standard complexity (length, special chars, numbers)

## 10. Commits

- Conventional Commits
- Every commit compiles cleanly and has tests passing
- Never commit broken code

## 11. Prompts to Claude Code

- Every prompt is a `.md` file artifact
- Every prompt begins with the STOP directive referring to `openapi.yaml`, `Audit.md` (when applicable), and `Architecture.md`
- Every prompt ends with "Compile, Run, Test, Commit, Push to Github"
- Every prompt ends with a report template that Claude Code fills in (including Git commit hash)
- Prompts never contain code
