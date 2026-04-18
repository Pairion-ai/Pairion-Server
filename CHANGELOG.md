# Changelog

All notable changes to Pairion Server will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - M1 Part 1 — STT and LLM

### Added

- STT adapter (`com.pairion.adapters.stt.whispercpp`) with WhisperCppNative abstraction boundary for testability; searches `$PAIRION_HOME/native/` for shared library
- LLM adapter (`com.pairion.adapters.llm.anthropic`) using official Anthropic Java SDK 2.5.0; streams tokens via AnthropicClientWrapper abstraction
- Opus decoder (`com.pairion.adapters.audio.opus`) using pure-Java Concentus library for zero native-dep audio decoding
- Agent orchestrator (`com.pairion.agent.session.AgentSession`) implementing M1 Part 1 turn loop: AudioStreamStart → Opus decode → STT → TranscriptPartial/Final → AgentStateChange(thinking) → LLM streaming → LlmTokenStream → AgentStateChange(idle)
- SOUL prompt placeholder (`com.pairion.agent.soul.DefaultSoulPromptProvider`) returning hardcoded persona prompt
- Core domain types: `AgentState`, `LlmEvent` (sealed: TokenDelta, ToolCallRequest, ToolCallResult, Stop), `LlmRequest`, `LlmCapabilities`, `SttEvent` (sealed: Partial, Final), `SttCapabilities`, `ToolDefinition`
- Full SPI interfaces for `LlmAdapter` and `SttAdapter` with streaming methods
- `AgentSessionEvent` sealed hierarchy for event forwarding from agent to WebSocket
- Latency instrumentation: `llm.first_token_ms`, `llm.total_ms`, `stt.partial`, `stt.final_ms`, `audio.stream.start`, `speech.ended`
- MDC session-ID propagation in agent turn loop
- Tool-use plumbing scaffolded in LLM adapter (no real tools in PS-002)
- jextract regeneration script (`pairion-adapters/whispercpp-jextract.sh`)
- Default model: `claude-sonnet-4-6-20250514` configured in `application.yml`
- Server gracefully continues when whisper.cpp is absent (clear error log with install path)

## [0.1.1] - Checkstyle with Javadoc Enforcement

### Added

- Checkstyle Maven plugin bound to `verify` phase across all modules
- `config/checkstyle/checkstyle.xml` with Javadoc enforcement rules (MissingJavadocType, MissingJavadocMethod, JavadocType, JavadocMethod)
- `config/checkstyle/suppressions.xml` suppressing checks on test sources, generated code, and package-info files
- Basic naming conventions (PackageName, TypeName, ConstantName) and import hygiene (AvoidStarImport, UnusedImports, RedundantImport)
- `mvn verify` now fails if any public class or method is missing Javadoc

## [0.1.0] - M0 Walking Skeleton

### Added

- Maven multi-module project structure with seven modules: core, adapters, household, memory, skills, agent, gateway
- Sealed `WebSocketMessage` interface hierarchy with all 18 message types from asyncapi.yaml
- Jackson polymorphic deserialization with `type` field as discriminator
- WebSocket endpoint at `/ws/v1` with DeviceIdentify/SessionOpened and HeartbeatPing/HeartbeatPong flows
- Binary frame acceptance (logged and discarded for M0)
- REST controllers for all 13 endpoints defined in openapi.yaml
- `/v1/health` returns actual `{"status":"healthy"}` response
- Stub responses for all other REST endpoints
- Virtual threads enabled (`spring.threads.virtual.enabled=true`)
- Server listens on port 18789
- WebSocket buffer sizes configured (1 MB binary, 256 KB text)
- SLF4J + Logback with JSON structured output via logstash-logback-encoder
- Logback turbo filter that redacts `sk-ant-*` API key patterns
- `ANTHROPIC_API_KEY` environment variable detection at startup (warn if absent, info if present)
- Adapter SPI interfaces for all eight categories: LLM, TTS, STT, wake, VAD, voice-ID, embedding, vector store
- ArchUnit tests: no vendor SDK outside adapters, no System.out in src/main
- 100% line and branch coverage enforced via JaCoCo
- Spotless with google-java-format (AOSP style)
- Javadoc on every public class, interface, and method
- `package-info.java` for every package
