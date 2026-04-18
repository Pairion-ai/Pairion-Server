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

This compiles all modules, runs all tests, enforces 100% code coverage, and runs Checkstyle (Javadoc enforcement).

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
| `PAIRION_HOME` | No (defaults to `~/.pairion`) | Base directory for native libs and models |
| `PAIRION_NATIVE_TESTS` | No | Set to `1` to run integration tests requiring whisper.cpp + API key |

### whisper.cpp Setup

The STT adapter requires the whisper.cpp shared library and model:

1. Build whisper.cpp with Metal acceleration (see [whisper.cpp README](https://github.com/ggerganov/whisper.cpp))
2. Copy `libwhisper.dylib` (macOS) or `libwhisper.so` (Linux) to `~/.pairion/native/`
3. Download `ggml-small.en.bin` from Hugging Face and place in `~/.pairion/models/whisper/`

The server starts normally without whisper.cpp — STT is simply unavailable with a clear log message.

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
