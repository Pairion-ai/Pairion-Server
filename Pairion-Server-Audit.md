# Pairion-Server — Codebase Audit

**Audit Date:** 2026-04-21T22:36:01Z
**Branch:** main
**Commit:** fe11ccacedae7f1047d23ac3968ec3232db41f67 feat: configurable Piper TTS speech rate via lengthScale (PS-TTS-001)
**Auditor:** Claude Code (Automated)
**Purpose:** Zero-context reference for AI-assisted development
**Audit File:** Pairion-Server-Audit.md
**Scorecard:** Pairion-Server-Scorecard.md
**OpenAPI Spec:** Pairion-Server-OpenAPI.yaml (generated separately)

> This audit is the source of truth for the Pairion-Server codebase structure, entities, services, and configuration.
> The OpenAPI spec (Pairion-Server-OpenAPI.yaml) is the source of truth for all endpoints, DTOs, and API contracts.
> An AI reading this audit + the OpenAPI spec should be able to generate accurate code
> changes, new features, tests, and fixes without filesystem access.

---
## Section 1: Project Identity

```
Project Name:        Pairion-Server
Repository URL:      (local — no remote URL in pom.xml)
Primary Language:    Java 21 + Spring Boot 3.4.4
Build Tool:          Maven (multi-module, pom parent)
Current Branch:      main
Latest Commit:       fe11ccacedae7f1047d23ac3968ec3232db41f67 feat: configurable Piper TTS speech rate via lengthScale (PS-TTS-001)
Audit Timestamp:     2026-04-21T22:36:01Z
```

Multi-module Maven project with 9 modules: pairion-native-whisper, pairion-native-piper, pairion-core, pairion-adapters, pairion-household, pairion-memory, pairion-skills, pairion-agent, pairion-gateway. Spring Boot app entry point is pairion-gateway.

---
## Section 2: Directory Structure

```
./Architecture.md
./asyncapi.yaml
./CHANGELOG.md
./CLAUDE.md
./config/checkstyle/checkstyle.xml
./config/checkstyle/suppressions.xml
./CONVENTIONS.md
./openapi.yaml
./pom.xml (parent)
./pairion-native-whisper/  — jextract-generated whisper.cpp FFM bindings
./pairion-native-piper/    — jextract-generated Piper TTS FFM bindings
./pairion-core/            — Domain types, WebSocket message types, LLM/STT/TTS types, utilities
./pairion-adapters/        — Adapter SPI interfaces + implementations (Anthropic, OpenAI-compat, Piper, Whisper.cpp, Opus)
./pairion-household/       — Household/User/voice registry (stub module, package-info only)
./pairion-memory/          — Episodic/semantic memory (stub module, package-info only)
./pairion-skills/          — MCP client, skill registry (stub module, package-info only)
./pairion-agent/           — Agent session orchestrator, SOUL prompt provider, tools, markdown stripper
./pairion-gateway/         — Spring Boot app; REST controllers, WebSocket handler, config, startup
```

Single Maven project, multi-module layout. Source under `src/main/java` per module. Spring Boot application lives in pairion-gateway. pairion-household, pairion-memory, and pairion-skills are stub modules (package-info only).

---
## Section 3: Build & Dependency Manifest

### Parent POM (pom.xml)
- Spring Boot Parent: 3.4.4
- Java: 21 (preview enabled)
- ArchUnit: 1.3.0
- Spotless: 2.43.0 (Google Java Format 1.22.0, AOSP style)
- JaCoCo: 0.8.12 (100% line + branch coverage enforced; specific exclusions for native/generated code)
- Checkstyle: 10.21.4 (config/checkstyle/checkstyle.xml; failOnViolation=true)

**Global dependencies (all modules):**

| Dependency | Version | Purpose |
|---|---|---|
| slf4j-api | (Spring Boot managed) | Logging facade |
| junit-jupiter | (Spring Boot managed) | Test framework |
| assertj-core | (Spring Boot managed) | Fluent assertions |
| mockito-core | (Spring Boot managed) | Mocking |

**pairion-core dependencies:**

| Dependency | Version | Purpose |
|---|---|---|
| jackson-databind | (Spring Boot managed) | JSON serialization |
| jackson-annotations | (Spring Boot managed) | JSON annotations |

**pairion-adapters dependencies:**

| Dependency | Version | Purpose |
|---|---|---|
| pairion-core | 0.1.0-SNAPSHOT | Domain types |
| pairion-native-whisper | 0.1.0-SNAPSHOT | Whisper.cpp FFM bindings |
| pairion-native-piper | 0.1.0-SNAPSHOT | Piper TTS FFM bindings |
| anthropic-java | 2.25.0 | Anthropic Java SDK (restricted to llm.anthropic package) |
| concentus | 1.0.2 | Pure-Java Opus encoder/decoder |
| spring-context | (Spring Boot managed) | @Component, @ConditionalOnProperty |
| spring-boot-autoconfigure | (Spring Boot managed) | Conditional beans |

**pairion-gateway dependencies:**

| Dependency | Version | Purpose |
|---|---|---|
| pairion-core, pairion-adapters, pairion-household, pairion-memory, pairion-skills, pairion-agent | 0.1.0-SNAPSHOT | Internal modules |
| spring-boot-starter-web | (Spring Boot managed) | REST endpoints |
| spring-boot-starter-websocket | (Spring Boot managed) | WebSocket |
| logback-classic | (Spring Boot managed) | Logging implementation |
| logstash-logback-encoder | 8.0 | Structured JSON logging |
| archunit-junit5 | 1.3.0 | Architecture tests (test scope) |
| spring-boot-starter-test | (Spring Boot managed) | Test support |

**pairion-agent dependencies:**
pairion-core, pairion-household, pairion-memory, pairion-skills, pairion-adapters, spring-context; logback-classic (test scope).

**Build commands:**
```
Build:    mvn clean compile -DskipTests
Test:     mvn test
Verify:   mvn verify  (enforces JaCoCo 100% coverage + Checkstyle)
Run:      mvn spring-boot:run -pl pairion-gateway
Package:  mvn clean package -pl pairion-gateway -am
```

---
## Section 4: Configuration & Infrastructure Summary

**File:** `pairion-gateway/src/main/resources/application.yml`
- Server port: **18789**
- Virtual threads: enabled (`spring.threads.virtual.enabled=true`)
- WebSocket buffer: binary=1MB, text=256KB
- LLM adapter: `pairion.adapters.llm=openaicompat` (default config points to LM Studio at localhost:1234)
- Anthropic adapter: activated via `pairion.adapters.llm=anthropic`; requires `ANTHROPIC_API_KEY` env var
- TTS: Piper voice=en_GB-alan-medium, length-scale=0.85
- Logging: root=INFO, com.pairion=DEBUG; file at `${user.home}/Pairion/logs/pairion.log`; rolling 10MB max, 7 days history

**File:** `pairion-gateway/src/main/resources/logback-spring.xml`
- Console + rolling file appenders
- Pattern: `%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n`
- Rolling: daily + 10MB size trigger, 30-day history, 1GB total cap
- com.pairion package at DEBUG level

**File:** `pairion-gateway/src/test/resources/application.yml`
- Test-specific config (read separately in Section 11)

**Connection map:**
```
Database:       None
Cache:          None
Message Broker: None
External APIs:  LLM backends (configurable — LM Studio/Ollama at localhost:1234 or Anthropic cloud)
                Open-Meteo weather API (HTTP, via OpenMeteoWeatherTool)
Cloud Services: Anthropic API (when llm=anthropic) — requires ANTHROPIC_API_KEY
```

**CI/CD:** None detected (no .github/workflows, Jenkinsfile, or .gitlab-ci.yml).

**Environment map:**
```
ANTHROPIC_API_KEY — required when pairion.adapters.llm=anthropic
PAIRION_NATIVE_TESTS — set to "1" to enable native library integration tests
```

---
## Section 5: Startup & Runtime Behavior

**Entry point:** `com.pairion.gateway.PairionServerApplication`
- `@SpringBootApplication(scanBasePackages = "com.pairion")`
- `main()` calls `SpringApplication.run(PairionServerApplication.class, args)`
- Port: 18789; virtual threads enabled

**On startup (ApplicationReadyEvent):** `ModelStartupService.onApplicationReady()`
- Spawns two virtual threads:
  1. `model-download-whisper` — downloads `ggml-small.en.bin` to `$PAIRION_HOME/models/whisper/` (fallback: `~/.pairion/models/whisper/`). SHA-256 verified. Idempotent (skips if exists).
  2. `model-download-piper` — downloads `<voice>.onnx` + `<voice>.onnx.json` to `$PAIRION_HOME/models/tts/`. SHA-256 verified. Idempotent.
- Server accepts requests immediately; downloads are non-blocking

**WebSocket config:** `WebSocketConfig` registers `PairionWebSocketHandler` at `/ws/v1` with `setAllowedOrigins("*")`.

**Scheduled tasks:** None detected.

**Health check:** `GET /v1/health` → `HealthController` returns 200 OK (see Section 10).

**`PAIRION_HOME` env var:** Controls model storage directory. Defaults to `~/.pairion`.

---
## Section 6: Entity / Data Model Layer

This project has **no JPA entities or relational database**. Domain types are Java records.

### Core Domain Records (pairion-core)

=== LlmRequest (record) ===
Fields: systemPrompt (String), userMessage (String), toolDefinitions (List<ToolDefinition>), model (String, nullable), toolCallHistory (List<ToolCallPair>)
Nested record: ToolCallPair(toolCallId, toolName, toolInput Map<String,Object>, toolOutput Map<String,Object>)
Factory: LlmRequest.simple(systemPrompt, userMessage) — no tools, null model

=== LlmEvent (sealed interface) ===
Permits: TokenDelta(delta String), ToolCallRequest(toolCallId, toolName, input Map<String,Object>), ToolCallResult(toolCallId, output Map<String,Object>), Stop(outputTokens int)

=== LlmCapabilities (record) ===
Fields: available (boolean), supportsToolUse (boolean), supportsStreaming (boolean)
Factory: LlmCapabilities.unavailable()

=== ToolDefinition (record) ===
Fields: name (String), description (String), inputSchema (Map<String,Object>)

=== SttEvent (sealed interface) ===
Permits: Partial(text String), Final(text String, durationMs long)

=== SttCapabilities (record) ===
Fields: available (boolean), supportsStreaming (boolean)
Factory: SttCapabilities.unavailable()

=== TtsEvent (sealed interface) ===
Permits: Chunk(audio byte[], isOpus boolean), Completed(totalDurationMs long)

=== TtsCapabilities (record) ===
Fields: available (boolean), supportsStreaming (boolean)
Factory: TtsCapabilities.unavailable()

=== ModelDownloader (utility class) ===
Public methods: download(String url, Path targetPath, String expectedSha256): boolean
Package-private: writeWithProgress(InputStream, Path, long): void; computeSha256(Path): String; getDigest(String): static MessageDigest

### WebSocket Message Types (pairion-core, com.pairion.core.ws)

All are Java records implementing sealed `WebSocketMessage` interface. Jackson polymorphic deserialization via `@JsonTypeInfo(use=NAME, property="type")` and `@JsonSubTypes`.

| Type | Direction | Fields |
|---|---|---|
| DeviceIdentify | C→S | type, deviceId, bearerToken, clientVersion |
| SessionOpened | S→C | type, sessionId, serverVersion |
| SessionClosed | S→C | type, reason |
| HeartbeatPing | C→S | type, timestamp |
| HeartbeatPong | S→C | type, timestamp |
| ErrorMessage | S→C | type, code, message |
| AgentStateChange | S→C | type, state (idle/listening/thinking/speaking) |
| WakeWordDetected | C→S | type, timestamp, confidence (Double, nullable) |
| AudioStreamStart | Bidir | type, streamId, codec, sampleRate |
| SpeechEnded | C→S | type, streamId |
| AudioStreamEnd | Bidir | type, streamId, reason |
| TextMessage | C→S | type, text |
| TranscriptPartial | S→C | type, text |
| TranscriptFinal | S→C | type, text |
| LlmTokenStream | S→C | type, delta |
| ToolCallStarted | S→C | type, toolCallId, toolName, input (Map) |
| ToolCallCompleted | S→C | type, toolCallId, output (Map) |
| UnderBreathAck | S→C | type, acknowledgementType (nullable) |
| MapFocus | S→C | type, lat (double), lon (double), label, zoom |
| MapClear | S→C | type |
| ConversationEnded | S→C | type |

---
## Section 7: Enum Inventory

=== AgentState (com.pairion.core.agent) ===
Values: IDLE("idle"), LISTENING("listening"), THINKING("thinking"), SPEAKING("speaking")
Used in: AgentStateChange WS message, AgentSession
Has display label: YES — wireValue() returns the JSON wire string

No other enums detected in src/main/java.

---
## Section 8: Repository Layer

**No JPA repositories.** This project does not use a relational database or Spring Data. There is no repository layer.

---
## Section 9: Service Layer — Full Method Signatures

### Adapter SPI Interfaces (pairion-adapters)

=== LlmAdapter (interface) ===
- name(): String
- capabilities(): LlmCapabilities
- generate(LlmRequest request, Consumer<LlmEvent> eventConsumer): void

=== AnthropicLlmAdapter implements LlmAdapter ===
Activated: @ConditionalOnProperty(name="pairion.adapters.llm", havingValue="anthropic", matchIfMissing=true)
Injects: AnthropicClientWrapper, @Value("${pairion.adapters.llm.anthropic.model:claude-sonnet-4-6}") String
- name(): String → "anthropic"
- capabilities(): LlmCapabilities — delegates to clientWrapper.isAvailable()
- generate(LlmRequest, Consumer<LlmEvent>): void — calls clientWrapper.streamCompletion(); logs first-token-ms, total-ms via MDC sessionId

=== OpenAiCompatLlmAdapter implements LlmAdapter ===
Activated: @ConditionalOnProperty(name="pairion.adapters.llm", havingValue="openaicompat")
Injects: OpenAiCompatClientWrapper, @Value baseUrl/model/apiKey
- name(): String → "openaicompat"
- capabilities(): LlmCapabilities — delegates to clientWrapper.isAvailable()
- generate(LlmRequest, Consumer<LlmEvent>): void — passes baseUrl, apiKey, model, system, user, tools, toolHistory; logs first-token-ms, total-ms

=== SttAdapter (interface) ===
- name(): String
- capabilities(): SttCapabilities
- createSession(Consumer<SttEvent> eventConsumer): SttSession
- SttSession.feedAudio(byte[] pcmData): void
- SttSession.finalizeStream(): void

=== WhisperCppSttAdapter implements SttAdapter ===
Activated: @ConditionalOnProperty(name="pairion.adapters.stt", havingValue="whispercpp", matchIfMissing=true) + @ConditionalOnBean(WhisperCppNative.class)
Injects: WhisperCppNative
- name(): String → "whispercpp"
- capabilities(): SttCapabilities — delegates to nativeImpl.isAvailable()
- createSession(Consumer<SttEvent>): SttSession → WhisperSttSession
  WhisperSttSession.feedAudio(byte[]): accumulates PCM; emits Partial at 200ms throttle interval
  WhisperSttSession.finalizeStream(): runs full transcription via nativeImpl.transcribe(); emits SttEvent.Final

=== TtsAdapter (interface) ===
- name(): String
- capabilities(): TtsCapabilities
- speak(String text, Consumer<TtsEvent> eventConsumer): void

=== PiperTtsAdapter implements TtsAdapter ===
Activated: @ConditionalOnProperty(name="pairion.adapters.tts", havingValue="piper", matchIfMissing=true) + @ConditionalOnBean(PiperTtsNative.class)
Injects: PiperTtsNative
- name(): String → "piper"
- capabilities(): TtsCapabilities
- speak(String text, Consumer<TtsEvent>): void — calls piperNative.synthesize(); resamples to 16 kHz; Opus-encodes with 4-byte stream ID prefix; emits TtsEvent.Chunk(isOpus=true) per frame; emits TtsEvent.Completed

### Agent Services (pairion-agent)

=== AgentSession (com.pairion.agent.session) ===
Injects (constructor): sessionId, SttAdapter, LlmAdapter, TtsAdapter, SoulPromptProvider, ToolDispatcher, Consumer<AgentSessionEvent>
- onAudioStreamStart(String streamId): void — allocates OpusDecoder + SttSession; emits LISTENING state
- onAudioChunk(byte[] frameData): void — Opus decodes; feeds PCM to SttSession
- onSpeechEnded(): void — records sttStartNano; calls sttSession.finalizeStream()
- currentState(): AgentState
- close(): void — shuts down clearScheduler
- handleSttEvent(SttEvent): void [package-private] — routes Partial/Final; triggers onTranscriptFinal on Final
- handleLlmEvent(LlmEvent, StringBuilder, List<ToolCallRequest>): void [package-private]
- emitTimedMapClear(): void [package-private] — used by 2-min scheduler

Private turn loop: onTranscriptFinal() — checks map-clear/conversation-end phrases; transitions THINKING; multi-turn LLM/tool loop (max 5 rounds); MarkdownStripper.strip(); TTS synthesis; logs [LATENCY] stages A/B/C/D/E/F/T

=== ToolDispatcher (@Component) ===
Injects: List<AgentTool> (auto-discovered Spring beans)
- dispatch(String toolName, Map<String,Object> input): Map<String,Object>

=== OpenMeteoWeatherTool (@Component, implements AgentTool) ===
- name(): String → "get_current_weather"
- execute(Map<String,Object> input): Map<String,Object>
  Two HTTP calls: geocoding (open-meteo geocoding API) + forecast (open-meteo forecast API). 5-sec timeouts. Returns: city, temperature_f, conditions, wind_speed_mph, latitude, longitude.

=== MapFocusTool (@Component, implements AgentTool) ===
- name(): String → "focus_map"
- execute(Map<String,Object> input): Map<String,Object>
  One HTTP call: open-meteo geocoding. Returns: lat, lon, label, zoom, status.

=== DefaultSoulPromptProvider (@Component, implements SoulPromptProvider) ===
- getSystemPrompt(String sessionId): String — returns hardcoded "Jarvis" SOUL prompt (placeholder for M1)

### Gateway Services (pairion-gateway)

=== ModelStartupService (@Component) ===
Injects: @Value piperVoice, ModelDownloader
- onApplicationReady(): void [@EventListener(ApplicationReadyEvent)] — spawns virtual threads for whisper + piper model downloads
- downloadWhisperModel(): void [package-private]
- downloadPiperModel(): void [package-private]
- resolveModelPath(String, String, String): Path [package-private]

### Utility Classes (pairion-core)

=== ModelDownloader ===
- download(String url, Path targetPath, String expectedSha256): boolean
- writeWithProgress(InputStream, Path, long): void [package-private]
- computeSha256(Path): String [package-private]
- getDigest(String): static MessageDigest

=== MarkdownStripper (com.pairion.agent.util, final utility class) ===
- strip(String text): static String — strips markdown for TTS; handles links, headings, blockquotes, HR, bold, italic, backticks, whitespace

---
## Section 10: Controller / API Layer — Method Signatures Only

=== PairionWebSocketHandler (com.pairion.gateway.ws) ===
Handler: `AbstractWebSocketHandler`, registered at `/ws/v1`
Injects: ObjectMapper, SttAdapter (nullable), LlmAdapter, TtsAdapter (nullable), SoulPromptProvider, ToolDispatcher
Session map: ConcurrentHashMap<String, AgentSession>

Message dispatch:
- afterConnectionEstablished() → logs sessionId
- handleTextMessage() → deserializes WebSocketMessage → routes:
  - DeviceIdentify → handleDeviceIdentify() → creates AgentSession, sends SessionOpened
  - HeartbeatPing → handleHeartbeatPing() → sends HeartbeatPong
  - AudioStreamStart → handleAudioStreamStart() → agentSession.onAudioStreamStart()
  - SpeechEnded → handleSpeechEnded() → agentSession.onSpeechEnded()
- handleBinaryMessage() → agentSession.onAudioChunk(bytes)
- afterConnectionClosed() → removes AgentSession, calls agentSession.close()
- sendAgentEvent() [package-private] → AudioChunkEvent → BinaryMessage; all others → JSON TextMessage
- serializeEvent() [package-private] → maps AgentSessionEvent → WebSocketMessage JSON

=== HealthController ===
Base Path: /v1
- getHealth() → ResponseEntity<Map<String,String>> (status: "healthy")
- getVersion() → ResponseEntity<Map<String,String>> (version: "0.1.0", buildTime, gitCommit)

=== HouseholdController ===
Base Path: /v1/household
- getHousehold() → ResponseEntity<Map<String,Object>> (stub)
- listUsers() → ResponseEntity<List<Object>> (stub, empty)
- createUser(@RequestBody Map) → ResponseEntity<Map<String,Object>> (stub, 201 Created)
- getUser(@PathVariable userId) → ResponseEntity<Map<String,Object>> (stub)
- deleteUser(@PathVariable userId) → ResponseEntity<Void> (204 No Content)

=== LogController ===
Base Path: /v1/logs
- postLogs(@RequestBody List<Map<String,Object>>) → ResponseEntity<Void> (forwards to server logger, 204)

=== MemoryController ===
Base Path: /v1/memory
- listEpisodes(@RequestParam userId, @RequestParam(default=50) limit) → ResponseEntity<List<Object>> (stub, empty)

=== AdapterController ===
Base Path: /v1/adapters
- listAdapters() → ResponseEntity<List<Object>> (stub, empty)
- getAdapter(@PathVariable category, @PathVariable name) → ResponseEntity<Map<String,Object>> (stub)

=== SkillController ===
Base Path: /v1/skills
- listSkills() → ResponseEntity<List<Object>> (stub, empty)
- getSkill(@PathVariable skillId) → ResponseEntity<Map<String,Object>> (stub)

---
## Section 11: Security Configuration

```
Authentication:      None — no Spring Security dependency; no login/JWT/OAuth2
Token issuer:        N/A
Password encoder:    N/A

Public endpoints:    ALL (no authentication layer configured)

Protected endpoints: None (M1 milestone — auth is a future milestone)

CORS:               WebSocket: setAllowedOrigins("*") (all origins allowed)
                    REST: default Spring MVC (no CORS config)

CSRF:               Not applicable (no Spring Security)

Rate limiting:       None

API Key Redaction:   ApiKeyRedactionFilter (Logback TurboFilter)
                     — matches sk-ant-[A-Za-z0-9_-]+ in log messages
                     — returns FilterReply.DENY to prevent key from reaching appenders
```

**Note:** Device bearer token in `DeviceIdentify` message is received and logged but NOT validated (keychain-free in development per Architecture §9).

---
## Section 12: Custom Security Components

=== ApiKeyRedactionFilter (com.pairion.gateway.config) ===
Extends: TurboFilter (Logback)
Purpose: Redacts Anthropic API key patterns from log messages before any appender processes them
Pattern: `sk-ant-[A-Za-z0-9_\-]+`
Activation: Logback turbo filter; runs pre-appender on every log event
Sets SecurityContext: N/A
Behavior: DENY log events that match in format string or any param; NEUTRAL otherwise

No other custom security components (no JWT filter, no UserDetailsService — not yet implemented).

---
## Section 13: Exception Handling & Error Responses

**No @ControllerAdvice / GlobalExceptionHandler.** The project does not have a centralized REST exception handler. Controller stubs return hardcoded success responses; WebSocket errors are logged and result in partial error messages sent to the client.

WebSocket error handling:
- `PairionWebSocketHandler.sendAgentEvent()` catches Exception, logs it; does not propagate
- TTS errors in `AgentSession.synthesizeSpeech()` — caught; sets endReason="error"; AudioStreamEnd sent with reason="error"
- Tool errors in `ToolDispatcher.dispatch()` — caught; returns Map.of("error", "tool_execution_failed", "message", e.getMessage())
- Tool not found: returns Map.of("error", "unknown_tool", "tool", toolName)

**OBSERVATION:** No @ControllerAdvice for REST endpoints — unhandled exceptions will produce Spring Boot default error response (500 with /error redirect). This is a gap for future milestones.

---
## Section 14: Mappers / DTOs

**No MapStruct or ModelMapper.** All serialization is handled via Jackson directly on Java records with `@JsonProperty` annotations.

WebSocket message types ARE the DTOs — Java records implementing `WebSocketMessage`. Jackson uses `@JsonTypeInfo(use=NAME, property="type")` for polymorphic deserialization.

REST controllers return `Map<String, Object>` or `List<Object>` inline (stub responses only). No DTO classes defined.

---
## Section 15: Utility Classes & Shared Components

=== MarkdownStripper (com.pairion.agent.util) ===
Final utility class, static-methods only.
- strip(String text): static String
  Applies: links→text, HR removal, heading removal, blockquote removal, bold removal, italic removal, backtick removal, whitespace collapse.
  Returns: empty string for null/blank input.
Used by: AgentSession.onTranscriptFinal() before TTS synthesis

=== ModelDownloader (com.pairion.core.util) ===
- download(String url, Path targetPath, String expectedSha256): boolean
  Downloads to temp file, SHA-256 verifies, atomically moves to target. Idempotent (skips if exists). Follows redirects.
- writeWithProgress(InputStream, Path, long): void [package-private] — logs at 10% intervals
- computeSha256(Path): String [package-private]
- getDigest(String): static MessageDigest [package-private]
Used by: ModelStartupService

=== OpusEncoder (com.pairion.adapters.audio.opus) ===
- create(int sampleRate): static OpusEncoder — factory using ConcentusOpusEncoder
- getFrameSize(): int — sampleRate * 20 / 1000 samples
- getSampleRate(): int
- encode(byte[] streamIdBytes, byte[] pcmFrame): byte[] — encodes and prepends 4-byte stream ID prefix
- reset(): void
Constants: CHANNELS=1, FRAME_DURATION_MS=20, STREAM_ID_PREFIX_LENGTH=4
Used by: PiperTtsAdapter

=== OpusDecoder (com.pairion.adapters.audio.opus) ===
- create(): static OpusDecoder — factory using ConcentusOpusDecoder
- extractStreamId(byte[] frame): String — reads first 4 bytes as UTF-8
- decode(byte[] frame): byte[] — strips 4-byte prefix, decodes Opus → 16-bit LE PCM
- reset(): void
Constants: SAMPLE_RATE=16000, CHANNELS=1, FRAME_DURATION_MS=20, FRAME_SIZE=320, STREAM_ID_PREFIX_LENGTH=4
Used by: AgentSession

=== ConcentusOpusEncoder implements OpusEncoderNative ===
Wraps Concentus (io.github.jaredmdobson:concentus:1.0.2)
- encodeFrame(byte[] pcmFrame): byte[] — 16-bit LE PCM → Opus frame
- getSampleRate(): int
- reset(): void

=== ConcentusOpusDecoder implements OpusDecoderNative ===
- decodeFrame(byte[] opusFrame): byte[] — Opus → 16-bit LE PCM at 16 kHz
- reset(): void

=== PiperTtsAdapter.resample() [static, package-private] ===
Resamples 16-bit LE PCM from fromRate → toRate using linear interpolation.
Used within PiperTtsAdapter.speak() when piperSampleRate != 16000.

---
## Section 16: Database Schema (Live)

**No database.** This project has no relational database, NoSQL store, or persistence layer configured. All state is in-memory (WebSocket session map in PairionWebSocketHandler). No schema to dump.

Memory and household persistence are future milestone work (pairion-memory and pairion-household modules are stubs).

---
## Section 17: Message Broker Configuration

No message broker detected. No RabbitMQ, Kafka, or SQS dependencies or annotations found anywhere in src/.

---

## Section 18: Cache Layer

No Redis or caching layer detected. No @Cacheable, @CacheEvict, CacheManager, or spring.cache/spring.redis configuration found anywhere in src/.

---
## Section 19: Environment Variable Inventory

| Variable | Used In | Default | Required in Prod |
|---|---|---|---|
| ANTHROPIC_API_KEY | DefaultAnthropicClientWrapper — API key for Anthropic calls | (none) | YES (when llm=anthropic) |
| PAIRION_HOME | ModelStartupService, DefaultWhisperCppNative, DefaultPiperTtsNative — model storage root | ~/.pairion | NO (defaults to ~/.pairion) |

**Spring @Value properties** (set via application.yml, not env vars):

| Property | Default | Used In |
|---|---|---|
| pairion.adapters.llm | (none — matchIfMissing=true→anthropic) | Adapter @ConditionalOnProperty selectors |
| pairion.adapters.llm.anthropic.model | claude-sonnet-4-6 | AnthropicLlmAdapter |
| pairion.adapters.openaicompat.baseUrl | http://localhost:1234/v1 | OpenAiCompatLlmAdapter |
| pairion.adapters.openaicompat.model | gpt-4o-mini | OpenAiCompatLlmAdapter |
| pairion.adapters.openaicompat.apiKey | (empty) | OpenAiCompatLlmAdapter |
| pairion.adapters.openaicompat.connectTimeoutSeconds | 10 | DefaultOpenAiCompatClientWrapper |
| pairion.adapters.openaicompat.requestTimeoutSeconds | 30 | DefaultOpenAiCompatClientWrapper |
| pairion.adapters.tts.piper.voice | en_GB-alan-medium | ModelStartupService, DefaultPiperTtsNative |
| pairion.adapters.tts.piper.length-scale | 1.0 | DefaultPiperTtsNative — controls speech rate |

---
## Section 20: Service Dependency Map

```
Pairion-Server → Depends On (external)
-----------------------------------------
LM Studio / Ollama:      http://localhost:1234/v1 (when llm=openaicompat; configurable)
Anthropic API:           https://api.anthropic.com (when llm=anthropic; ANTHROPIC_API_KEY required)
Open-Meteo Geocoding:    https://geocoding-api.open-meteo.com/v1/search
                         (MapFocusTool + OpenMeteoWeatherTool, no API key required)
Open-Meteo Forecast:     https://api.open-meteo.com/v1/forecast
                         (OpenMeteoWeatherTool, no API key required)
Hugging Face model hub:  (ModelDownloader download URLs for whisper/piper models — one-time on first run)
```

Standalone service — no inter-service HTTP dependencies at runtime beyond the above.

Downstream consumers:
- Pairion (iOS/macOS client) — connects to /ws/v1 WebSocket and REST /v1/* endpoints on port 18789
```

---
## Section 21: Known Technical Debt & Issues

### TODO/FIXME Scan
No TODO/FIXME/XXX/HACK markers found in src/main/.

### Stub/Placeholder Patterns Detected (by design — milestone stubs)

The following are **intentional M0/M1 milestone stubs**, documented in Javadoc as such. They are NOT incomplete code masquerading as complete — they are explicitly scoped stubs for future milestones.

| Issue | Location | Severity | Notes |
|---|---|---|---|
| Stub REST endpoints (return empty lists) | AdapterController, SkillController, MemoryController, HouseholdController | LOW | Documented as M0 stubs; real implementations are future milestone work |
| Placeholder SOUL prompt | DefaultSoulPromptProvider | LOW | Returns hardcoded "Jarvis" prompt; full SOUL with memory context is a later milestone |
| Device bearer token not validated | PairionWebSocketHandler.handleDeviceIdentify() | MEDIUM | Token received in DeviceIdentify but not checked; per Architecture §9 intentional for dev |
| No @ControllerAdvice for REST | pairion-gateway (no GlobalExceptionHandler) | MEDIUM | Spring Boot default /error handler used; unhandled exceptions produce 500s |
| No authentication layer | All REST and WebSocket endpoints | MEDIUM | By design for M1 dev phase; production auth is a later milestone |
| pairion-household, pairion-memory, pairion-skills modules | All three modules | LOW | Package-info only; no implementation yet |

### Observations (not blocking, informational)
- `pairion-agent/src/main/.../SoulPromptProvider.java` interface Javadoc explicitly says "the current implementation returns a hardcoded placeholder" — correctly documented
- `DefaultPiperTtsNative.java:272` references "Upcall stub" in a comment about the C FFM callback — this is a code comment about the FFM binding mechanism, not an incomplete implementation

---
## Section 22: Security Vulnerability Scan (Snyk)

Scan Date: 2026-04-21T22:36:01Z
Snyk CLI Version: 1.1303.0

### Dependency Vulnerabilities (Open Source)
Critical: 0
High: 0
Medium: 0
Low: 0
**RESULT: PASS — No known vulnerabilities in dependencies.**

### Code Vulnerabilities (SAST)
**RESULT: SKIPPED — Snyk Code returned HTTP 403 Forbidden (Snyk Code not enabled on this account/plan). Run `snyk code test` manually with appropriate credentials.**

### IaC Findings
No Dockerfile, docker-compose, or Terraform files present — IaC scan not applicable.

---
