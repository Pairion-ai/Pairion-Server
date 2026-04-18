# Pairion Server

Ambient, voice-first, household-scale AI presence — server component.

One JVM process per household. Owns user identity, voice identity, memory, skills, orchestration, and adapter configuration.

## Prerequisites

- Java 21+
- Maven 3.9+

## Build

```bash
mvn clean verify
```

This compiles all modules, runs all tests, and enforces 100% code coverage.

## Run

```bash
mvn -pl pairion-gateway spring-boot:run
```

Or build the JAR and run directly:

```bash
mvn clean package -DskipTests
java -jar pairion-gateway/target/pairion-gateway-0.1.0-SNAPSHOT.jar
```

The server starts on port **18789**.

## Configuration

| Environment Variable | Required | Description |
|---|---|---|
| `ANTHROPIC_API_KEY` | No (warn if absent) | Anthropic API key for LLM adapter |

## Endpoints

### REST (control plane)

All REST endpoints are prefixed with `/v1`.

| Method | Path | Description |
|---|---|---|
| GET | `/v1/health` | Liveness probe |
| GET | `/v1/version` | Server version |
| GET | `/v1/household` | Household summary |
| GET | `/v1/household/users` | List users |
| POST | `/v1/household/users` | Add user |
| GET | `/v1/household/users/{userId}` | Get user |
| DELETE | `/v1/household/users/{userId}` | Remove user |
| GET | `/v1/adapters` | List adapters |
| GET | `/v1/adapters/{category}/{name}` | Get adapter |
| GET | `/v1/skills` | List skills |
| GET | `/v1/skills/{skillId}` | Get skill |
| GET | `/v1/memory/episodes` | List episodes |
| POST | `/v1/logs` | Forward client logs |

### WebSocket (real-time)

Connect to `ws://localhost:18789/ws/v1`.

Protocol: JSON text frames for events, binary frames for audio.

## Test

```bash
mvn test
```

## Format

```bash
mvn spotless:check    # check formatting
mvn spotless:apply    # auto-fix formatting
```

## Project Structure

```
pairion-core/       — Domain types, WebSocket message hierarchy
pairion-adapters/   — Adapter SPI interfaces (vendor SDK boundary)
pairion-household/  — Household and user management
pairion-memory/     — Episodic and semantic memory
pairion-skills/     — MCP client and skill registry
pairion-agent/      — Agent orchestrator and turn loop
pairion-gateway/    — REST + WebSocket endpoints, Spring Boot entry point
```

## License

Pairion Source-Available License (Non-Commercial)
