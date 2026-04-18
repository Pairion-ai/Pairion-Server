# Changelog

All notable changes to Pairion Server will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.3] - Complete PS-002b remaining items

### Changed

- jextract 21-jextract+1-2 installed, FFM bindings generated from exact whisper.h of pinned v1.8.4 source
- Manual FFM bindings in `ffm/WhisperCpp.java` deleted; replaced by jextract-generated `com.pairion.nativelib.whisper.WhisperBindings`
- Metal-on-JVM-exit assertion fixed via shutdown hook that calls `whisper_free()` before GGML atexit runs
- `DefaultWhisperCppNative` JaCoCo class-level exclusion removed; class is now directly tested
- ArchUnit rule added: only `com.pairion.adapters.stt.whispercpp.**` may import from `com.pairion.nativelib.whisper.**`
- Install scripts (`install-jextract.sh`, `regenerate-bindings.sh`) committed
- `pairion-native-whisper/README.md` created with pinned versions, build flags, platform matrix

## [0.2.2] - Bundle whisper.cpp from source, eliminate ABI-drift segfault class

### Changed

- New `pairion-native-whisper` Maven module: clones whisper.cpp v1.8.4 from source, builds with `GGML_METAL=ON`, packages `libwhisper.dylib` as classpath resource
- Rewritten FFM bindings: use `_by_ref` API functions to obtain default params, then pass correct-size structs by value (48-byte context params, 304-byte full params — verified against compiled whisper.h)
- `DefaultWhisperCppNative` loads library from classpath via `NativeLibraryLoader` (temp-file extraction, `System.load`)
- `~/.pairion/native/` convention retired — no user-installed whisper.cpp required
- Updated `MODEL_SHA256` to `c6138d6d58ecc8322097e0f987c32f1be8bb0a18532a3f88f734d1bbf9c41e5d` (current HuggingFace value)
- Added `--enable-native-access=ALL-UNNAMED` to Spring Boot and Surefire JVM args

### Removed

- Hand-written FFM bindings placeholder script (`whispercpp-jextract.sh`)
- User-facing whisper.cpp installation instructions from README

## [0.2.1] - PS-002 Stabilization — vendor integrations made real

### Changed

- Upgraded `anthropic-java` from 2.5.0 to 2.25.0 (current GA, published 2026-03-19)
- Wired real Concentus Opus decoder (`io.github.jaredmdobson:concentus:1.0.2`) — inbound Opus frames now produce real PCM instead of silence
- Implemented real DefaultWhisperCppNative with Java 21 FFM bindings for whisper.cpp (library loading, context init, transcription)
- Manual FFM bindings committed under `pairion-adapters/src/main/java/.../ffm/` (jextract unavailable; jextract script committed for regeneration)
- Default LLM model configured as `claude-sonnet-4-6-20250514`
- Added `ModelDownloader` utility in pairion-core for SHA-256 verified model downloads with progress logging
- Added formal `NativeIntegrationTests` suite gated by `PAIRION_NATIVE_TESTS=1` environment variable
- Reduced JaCoCo blanket exclusions to justified native-dependent classes only (WhisperCpp FFM, DefaultAnthropicClientWrapper, DefaultWhisperCppNative, OpusDecoder.create catch)
- Checkstyle suppressions extended for FFM binding files
- Enabled `--enable-preview` for Java 21 FFM API support

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
