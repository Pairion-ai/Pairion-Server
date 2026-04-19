# Pairion-Server — Codebase Audit

**Audit Date:** 2026-04-19T17:10:00Z
**Branch:** main
**Commit:** d234e200274a57731eedd8f4c19138b880d512ce fix: correct model ID, add TTS SPI, weather tool scaffolding, logging overhaul
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
## 1. Project Identity

```
Project Name:         Pairion Server
Repository URL:       (local — no remote URL in config)
Primary Language:     Java 21
Framework:            Spring Boot 3.4.4
Build Tool:           Maven (multi-module, parent pom)
Current Branch:       main
Latest Commit Hash:   d234e200274a57731eedd8f4c19138b880d512ce
Latest Commit Msg:    fix: correct model ID, add TTS SPI, weather tool scaffolding, logging overhaul
Audit Timestamp:      2026-04-19T17:10:00Z
```

## 2. Directory Structure

```
./pom.xml                                            ← root multi-module POM
./Architecture.md                                    ← architectural spec
./asyncapi.yaml                                      ← WebSocket protocol spec
./openapi.yaml                                       ← REST API spec
./CONVENTIONS.md
./config/checkstyle/checkstyle.xml
./config/checkstyle/suppressions.xml

pairion-native-whisper/                              ← builds whisper.cpp from source, bundles lib
  pom.xml
  src/main/java/com/pairion/nativelib/whisper/
    NativeLibraryLoader.java                         ← classpath extraction of native lib
    WhisperBindings.java                             ← jextract-generated FFM bindings (excluded)
    whisper_context_params.java                      ← jextract struct (excluded)
    whisper_full_params.java                         ← jextract struct (excluded)

pairion-core/                                        ← domain types, events, utilities
  src/main/java/com/pairion/core/
    agent/AgentState.java                            ← enum: IDLE, LISTENING, THINKING, SPEAKING
    llm/LlmRequest.java                              ← record: LLM generation request
    llm/LlmEvent.java                                ← sealed: TokenDelta, ToolCallRequest, ToolCallResult, Stop
    llm/LlmCapabilities.java                         ← record: adapter capability descriptor
    llm/ToolDefinition.java                          ← record: LLM tool definition
    stt/SttCapabilities.java                         ← record: STT capability descriptor
    stt/SttEvent.java                                ← sealed: Partial, Final
    tts/TtsCapabilities.java                         ← record: TTS capability descriptor
    tts/TtsEvent.java                                ← sealed: Chunk, Completed
    util/ModelDownloader.java                        ← SHA-256 verified model file downloader
    ws/WebSocketMessage.java                         ← sealed: all 20 WS envelope types
    ws/[19 message record types]

pairion-adapters/                                    ← SPI interfaces + vendor implementations
  src/main/java/com/pairion/adapters/
    audio/opus/
      OpusDecoder.java                               ← strips 4-byte prefix, decodes Opus→PCM
      OpusDecoderNative.java                         ← interface: decodeFrame, reset
      ConcentusOpusDecoder.java                      ← Concentus pure-Java implementation
    llm/spi/LlmAdapter.java                          ← SPI interface
    llm/anthropic/
      AnthropicLlmAdapter.java                       ← Spring @Component, ConditionalOnProperty
      AnthropicClientWrapper.java                    ← internal boundary interface
      DefaultAnthropicClientWrapper.java             ← production SDK impl (excluded from coverage)
    stt/spi/SttAdapter.java                          ← SPI interface + SttSession inner interface
    stt/whispercpp/
      WhisperCppNative.java                          ← internal boundary interface
      WhisperCppSttAdapter.java                      ← Spring @Component, ConditionalOnBean
      DefaultWhisperCppNative.java                   ← FFM-based production impl
      LibraryLoader.java                             ← functional interface for test injection
    tts/spi/TtsAdapter.java                          ← SPI interface (no impl yet)
    vad/spi/VadAdapter.java                          ← SPI stub (name() only)
    embedding/spi/EmbeddingAdapter.java              ← SPI stub (name() only)
    vectorstore/spi/VectorStoreAdapter.java          ← SPI stub (name() only)
    voiceid/spi/VoiceIdAdapter.java                  ← SPI stub (name() only)
    wake/spi/WakeAdapter.java                        ← SPI stub (name() only)

pairion-agent/                                       ← turn loop, session management, SOUL
  src/main/java/com/pairion/agent/
    session/AgentSession.java                        ← per-connection turn loop orchestrator
    session/AgentSessionEvent.java                   ← sealed: StateChangeEvent, TranscriptPartialEvent, TranscriptFinalEvent, LlmTokenEvent
    soul/SoulPromptProvider.java                     ← interface: getSystemPrompt(sessionId)
    soul/DefaultSoulPromptProvider.java              ← placeholder hardcoded SOUL prompt

pairion-gateway/                                     ← Spring Boot app entry point
  src/main/java/com/pairion/gateway/
    PairionServerApplication.java                    ← @SpringBootApplication main class
    config/
      WebSocketConfig.java                           ← registers /ws/v1 handler
      ApiKeyStartupCheck.java                        ← warns if ANTHROPIC_API_KEY missing
      ApiKeyRedactionFilter.java                     ← Logback TurboFilter, denies sk-ant-* logs
    rest/
      HealthController.java                          ← GET /v1/health, GET /v1/version
      AdapterController.java                         ← GET /v1/adapters, GET /v1/adapters/{cat}/{name}
      HouseholdController.java                       ← GET/POST/DELETE /v1/household/...
      LogController.java                             ← POST /v1/logs
      MemoryController.java                          ← GET /v1/memory/episodes
      SkillController.java                           ← GET /v1/skills, GET /v1/skills/{id}
    ws/PairionWebSocketHandler.java                  ← AbstractWebSocketHandler at /ws/v1
  src/main/resources/
    application.yml
    logback-spring.xml
  src/test/resources/
    application.yml

pairion-household/                                   ← placeholder module (package-info only)
pairion-memory/                                      ← placeholder module (package-info only)
pairion-skills/                                      ← placeholder module (package-info only)
```

Multi-module Maven project with 8 modules. Spring Boot app is `pairion-gateway`. Modules `pairion-household`, `pairion-memory`, and `pairion-skills` are scaffolded but contain only `package-info.java`. All source code is under `src/main/java/com/pairion/`.

## 3. Build & Dependency Manifest

### Root POM (`pom.xml`)
- Parent: `spring-boot-starter-parent:3.4.4`
- GroupId: `com.pairion`, ArtifactId: `pairion-server`, Version: `0.1.0-SNAPSHOT`
- Java: 21 with `--enable-preview` and `--enable-native-access=ALL-UNNAMED`

### Global Dependencies (all modules inherit)
| Dependency | Version | Purpose |
|---|---|---|
| `org.slf4j:slf4j-api` | (Spring Boot BOM) | Logging facade |
| `org.junit.jupiter:junit-jupiter` | (Spring Boot BOM) | Unit testing |
| `org.assertj:assertj-core` | (Spring Boot BOM) | Assertion library |
| `org.mockito:mockito-core` | (Spring Boot BOM) | Mocking framework |

### Module: `pairion-native-whisper`
| Dependency | Version | Purpose |
|---|---|---|
| `exec-maven-plugin` | 3.5.0 | Clones/builds whisper.cpp v1.8.4 from source |
| `build-helper-maven-plugin` | 3.6.0 | Adds jextract generated sources to compile path |
| jextract (tool) | 21-jextract+1-2 | Generates Java FFM bindings from whisper.h |

Build: clones `github.com/ggerganov/whisper.cpp` tag `v1.8.4`, builds with CMake (Metal ON, shared lib), generates bindings via jextract. Output: `native/darwin-aarch64/libwhisper.dylib` as classpath resource.

### Module: `pairion-core`
| Dependency | Version | Purpose |
|---|---|---|
| `com.fasterxml.jackson.core:jackson-databind` | (Spring Boot BOM) | JSON serialization for WS messages |
| `com.fasterxml.jackson.core:jackson-annotations` | (Spring Boot BOM) | @JsonSubTypes, @JsonTypeInfo |

### Module: `pairion-adapters`
| Dependency | Version | Purpose |
|---|---|---|
| `com.anthropic:anthropic-java` | 2.25.0 | Anthropic Claude API SDK |
| `io.github.jaredmdobson:concentus` | 1.0.2 | Pure-Java Opus encoder/decoder |
| `org.springframework:spring-context` | (Boot BOM) | @Component, @ConditionalOnProperty |
| `org.springframework.boot:spring-boot-autoconfigure` | (Boot BOM) | @ConditionalOnBean |

### Module: `pairion-gateway`
| Dependency | Version | Purpose |
|---|---|---|
| `spring-boot-starter-web` | (Boot BOM) | Spring MVC, Tomcat |
| `spring-boot-starter-websocket` | (Boot BOM) | Raw WebSocket support |
| `ch.qos.logback:logback-classic` | (Boot BOM) | Logging implementation |
| `net.logstash.logback:logstash-logback-encoder` | 8.0 | JSON log formatting |
| `com.tngtech.archunit:archunit-junit5` | 1.3.0 | Architecture constraint testing |
| `spring-boot-starter-test` | (Boot BOM) | Spring test support |

### Build Plugins (global)
| Plugin | Version | Purpose |
|---|---|---|
| `maven-compiler-plugin` | (Boot BOM) | Java 21 + `--enable-preview` |
| `maven-surefire-plugin` | (Boot BOM) | Test runner with preview/native args |
| `jacoco-maven-plugin` | 0.8.12 | Coverage; enforces 100% LINE+BRANCH at bundle level |
| `spotless-maven-plugin` | 2.43.0 | Google Java Format (AOSP style) |
| `maven-checkstyle-plugin` | 3.6.0 | Checkstyle 10.21.4 with project config |
| `spring-boot-maven-plugin` | (Boot BOM) | Fat JAR packaging in pairion-gateway |

### JaCoCo Exclusions
- `com/pairion/nativelib/whisper/WhisperBindings.class` — jextract generated
- `com/pairion/nativelib/whisper/RuntimeHelper.class` — jextract generated
- `com/pairion/nativelib/whisper/constants*.class` — jextract generated
- `com/pairion/nativelib/whisper/whisper_context_params.class` — jextract struct
- `com/pairion/nativelib/whisper/whisper_full_params.class` — jextract struct
- `com/pairion/adapters/audio/opus/OpusDecoder.class` — catch(OpusException) unreachable
- `com/pairion/adapters/llm/anthropic/DefaultAnthropicClientWrapper.class` — requires real API key

### Build Commands
```
Build:   mvn clean compile -DskipTests
Test:    mvn test
Run:     mvn spring-boot:run -pl pairion-gateway
Package: mvn clean package
```

## 4. Configuration & Infrastructure Summary

### `pairion-gateway/src/main/resources/application.yml`
- Server port: **18789**
- WebSocket max binary message buffer: 1 MB, text: 256 KB
- Logging root: INFO, `com.pairion`: DEBUG
- Log file: `${user.home}/Pairion/logs/pairion.log`
- Log rolling: daily, max 10 MB/file, 100 MB total cap, 7-day history
- No profiles defined (single profile configuration)

### `pairion-gateway/src/main/resources/logback-spring.xml`
- Two appenders: CONSOLE and FILE (rolling)
- Pattern: `%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n` (plain text, not JSON)
- `com.pairion` logger at DEBUG; root at INFO
- `ApiKeyRedactionFilter` (TurboFilter) registered to deny logs containing `sk-ant-[A-Za-z0-9_-]+`
- Note: `logstash-logback-encoder` is a dependency but JSON layout is NOT wired in current config

### `pairion-gateway/src/test/resources/application.yml`
- Port: 0 (random)
- Virtual threads: enabled
- Banner: off
- `pairion.stt.native.enabled: false` — disables native whisper for tests

**Connection map:**
```
Database:        None (no persistence layer implemented)
Cache:           None
Message Broker:  None
External APIs:   Anthropic Messages API (api.anthropic.com via Anthropic Java SDK)
Cloud Services:  None
Model Downloads: huggingface.co/ggerganov/whisper.cpp (ggml-small.en.bin, on first run)
```

**CI/CD:** None detected in project root. (GitHub workflow files found in `pairion-native-whisper/target/whisper.cpp-src/.github/` are from the vendored whisper.cpp source, not Pairion CI.)

## 5. Startup & Runtime Behavior

**Entry point:** `com.pairion.gateway.PairionServerApplication.main(String[])`
- `@SpringBootApplication(scanBasePackages = "com.pairion")` — scans all 8 modules

**Startup sequence:**
1. Spring Boot auto-configuration starts on port 18789
2. `DefaultWhisperCppNative` constructor: attempts to load bundled `libwhisper.dylib` from classpath, resolves model path at `$PAIRION_HOME/models/whisper/ggml-small.en.bin` (or `~/.pairion/models/whisper/`). Logs WARN if library or model not found; does not block startup.
3. `AnthropicLlmAdapter` constructor: logs the configured model (`claude-sonnet-4-6`)
4. `DefaultAnthropicClientWrapper` constructor: reads `ANTHROPIC_API_KEY` env var; logs WARN if absent
5. `ApiKeyStartupCheck.checkApiKey()` (`@EventListener(ApplicationReadyEvent)`) — logs INFO if key present, WARN if absent
6. `WebSocketConfig.registerWebSocketHandlers()` — registers `PairionWebSocketHandler` at `/ws/v1` with `setAllowedOrigins("*")`

**Scheduled tasks:** None.

**Health check:** `GET /v1/health` → `{"status":"healthy"}` (always 200, not Spring Actuator).

**Background jobs:** Model download happens on first transcription request, not startup.

**Virtual threads:** `spring.threads.virtual.enabled: true` in test config only; not wired in main `application.yml`. Spring Boot 3.4.4 enables virtual threads by default when `spring.threads.virtual.enabled=true`.

## 6. Entity / Data Model Layer

No JPA entities exist. The project has no persistence layer at this milestone. Modules `pairion-household`, `pairion-memory`, and `pairion-skills` are scaffold-only.

**Domain records (pairion-core) — not persisted:**

```
=== LlmRequest (record) ===
Fields: String systemPrompt, String userMessage, List<ToolDefinition> toolDefinitions, String model (nullable)
Factory: LlmRequest.simple(systemPrompt, userMessage) → no tools, null model

=== ToolDefinition (record) ===
Fields: String name, String description, Map<String,Object> inputSchema

=== LlmCapabilities (record) ===
Fields: boolean available, boolean supportsToolUse, boolean supportsStreaming
Factory: LlmCapabilities.unavailable() → false/false/false

=== SttCapabilities (record) ===
Fields: boolean available, boolean supportsStreaming
Factory: SttCapabilities.unavailable() → false/false

=== TtsCapabilities (record) ===
Fields: boolean available, boolean supportsStreaming
Factory: TtsCapabilities.unavailable() → false/false

=== LlmEvent (sealed interface) ===
Permits: TokenDelta(String delta), ToolCallRequest(String toolCallId, String toolName, Map<String,Object> input),
         ToolCallResult(String toolCallId, Map<String,Object> output), Stop(int outputTokens)

=== SttEvent (sealed interface) ===
Permits: Partial(String text), Final(String text, long durationMs)

=== TtsEvent (sealed interface) ===
Permits: Chunk(byte[] audio, boolean isOpus), Completed(long totalDurationMs)
```

## 7. Enum Inventory

```
=== AgentState (com.pairion.core.agent) ===
Values: IDLE("idle"), LISTENING("listening"), THINKING("thinking"), SPEAKING("speaking")
Used in: AgentSession (currentState field), AgentSessionEvent.StateChangeEvent, AgentStateChange WS message
Has display label: YES — wireValue() method returns lowercase wire-format string
```

No other enums in production code. WebSocket message types use `String` constants (e.g., `AgentStateChange.TYPE = "AgentStateChange"`).

## 8. Repository Layer

No repository layer exists. The project has no database or persistence. Data access will be added in future milestones (pairion-household, pairion-memory, pairion-skills modules are currently empty scaffolds).

## 9. Service Layer — Full Method Signatures

No traditional `@Service` classes exist. Business logic is in `AgentSession` (plain class) and the adapter implementations. Key classes:

```
=== AgentSession (com.pairion.agent.session) ===
Injects (constructor): String sessionId, SttAdapter sttAdapter, LlmAdapter llmAdapter,
                       SoulPromptProvider soulProvider, Consumer<AgentSessionEvent> eventSink

Public Methods:
  - onAudioStreamStart(String streamId): void
    Purpose: Allocates OpusDecoder + SttAdapter.SttSession, transitions to LISTENING
    Calls: OpusDecoder.create(), sttAdapter.createSession()
    Throws: none declared
    Transactional: NO

  - onAudioChunk(byte[] frameData): void
    Purpose: Decodes Opus frame and feeds PCM to STT session
    Calls: opusDecoder.decode(), sttSession.feedAudio()
    Throws: none declared
    Transactional: NO

  - onSpeechEnded(): void
    Purpose: Signals STT finalization
    Calls: sttSession.finalizeStream()
    Throws: none declared
    Transactional: NO

  - currentState(): AgentState
    Purpose: Returns current processing state
    Calls: (field access)
    Throws: none
    Transactional: NO

Package-private Methods:
  - handleSttEvent(SttEvent): void
  - handleLlmEvent(LlmEvent): void
  - onTranscriptFinal(String): void  [private — builds LlmRequest with weather tool, calls llmAdapter.generate()]
  - transitionState(AgentState): void  [private]

=== DefaultSoulPromptProvider (com.pairion.agent.soul) ===
Injects: none (Spring @Component)

Public Methods:
  - getSystemPrompt(String sessionId): String
    Purpose: Returns the hardcoded M1 placeholder SOUL prompt (references weather tool)
    Calls: none
    Throws: none
    Transactional: NO

=== AnthropicLlmAdapter (com.pairion.adapters.llm.anthropic) ===
Injects (constructor): AnthropicClientWrapper clientWrapper, @Value String defaultModel (claude-sonnet-4-6)
Condition: @ConditionalOnProperty(pairion.adapters.llm=anthropic, matchIfMissing=true)

Public Methods:
  - name(): String → "anthropic"
  - capabilities(): LlmCapabilities
    Purpose: Returns availability based on API key presence
    Calls: clientWrapper.isAvailable()
  - generate(LlmRequest request, Consumer<LlmEvent> eventConsumer): void
    Purpose: Streams completion from Anthropic, emits TokenDelta/Stop events with latency logging
    Calls: clientWrapper.streamCompletion(), MDC.get("sessionId")
    Throws: none (errors forwarded as LlmEvent.Stop)
    Transactional: NO

=== WhisperCppSttAdapter (com.pairion.adapters.stt.whispercpp) ===
Injects (constructor): WhisperCppNative nativeImpl
Condition: @ConditionalOnProperty(pairion.adapters.stt=whispercpp, matchIfMissing=true)
           @ConditionalOnBean(WhisperCppNative.class)

Public Methods:
  - name(): String → "whispercpp"
  - capabilities(): SttCapabilities
  - createSession(Consumer<SttEvent> eventConsumer): SttAdapter.SttSession
    Purpose: Returns a new WhisperSttSession
    Calls: new WhisperSttSession(nativeImpl, eventConsumer)

=== ModelDownloader (com.pairion.core.util) ===
Injects (constructor): HttpClient httpClient (or default)

Public Methods:
  - download(String url, Path targetPath, String expectedSha256): boolean
    Purpose: Downloads file with SHA-256 verification; skips if already present
    Calls: httpClient.send(), computeSha256(), Files.move()
    Throws: IOException, InterruptedException (caught internally)
    Transactional: NO

Package-private Methods: writeWithProgress(), computeSha256(), getDigest()
```

## 10. Controller / API Layer — Method Signatures Only

```
=== HealthController (com.pairion.gateway.rest) ===
Base Path: /v1
Injects: none

Endpoints:
  - getHealth() → returns {"status":"healthy"}
  - getVersion() → returns {"version","buildTime","gitCommit"}

=== AdapterController (com.pairion.gateway.rest) ===
Base Path: /v1/adapters
Injects: none

Endpoints:
  - listAdapters() → returns empty List (stub)
  - getAdapter(@PathVariable category, @PathVariable name) → returns stub Map

=== HouseholdController (com.pairion.gateway.rest) ===
Base Path: /v1/household
Injects: none

Endpoints:
  - getHousehold() → returns stub household Map
  - listUsers() → returns empty List (stub)
  - createUser(@RequestBody Map body) → returns new user Map with generated UUID (201)
  - getUser(@PathVariable userId) → returns stub user Map
  - deleteUser(@PathVariable userId) → returns 204 No Content

=== LogController (com.pairion.gateway.rest) ===
Base Path: /v1/logs
Injects: (Logger only)

Endpoints:
  - postLogs(@RequestBody List<Map> records) → forwards via log.info(), returns 204

=== MemoryController (com.pairion.gateway.rest) ===
Base Path: /v1/memory
Injects: none

Endpoints:
  - listEpisodes(@RequestParam userId, @RequestParam(defaultValue="50") limit) → returns empty List (stub)

=== SkillController (com.pairion.gateway.rest) ===
Base Path: /v1/skills
Injects: none

Endpoints:
  - listSkills() → returns empty List (stub)
  - getSkill(@PathVariable skillId) → returns stub skill Map

=== PairionWebSocketHandler (com.pairion.gateway.ws) ===
Endpoint: /ws/v1  (raw WebSocket, not STOMP)
Injects (constructor): ObjectMapper, SttAdapter (nullable), LlmAdapter, SoulPromptProvider

Session map: ConcurrentHashMap<String, AgentSession> (sessionId → AgentSession)

Public/package-private methods:
  - afterConnectionEstablished(session) → logs info
  - handleTextMessage(session, message) → polymorphic dispatch via sealed interface switch
  - handleBinaryMessage(session, message) → routes to agentSession.onAudioChunk()
  - afterConnectionClosed(session, status) → removes session from map
  - handleDeviceIdentify(session, identify) → creates AgentSession, sends SessionOpened
  - handleHeartbeatPing(session, ping) → sends HeartbeatPong
  - handleAudioStreamStart(session, streamStart) → delegates to agentSession
  - handleSpeechEnded(session) → delegates to agentSession
  - sendAgentEvent(session, event) → serializes AgentSessionEvent to WS text frame
```

## 11. Security Configuration

No Spring Security dependency. The project has no authentication/authorization framework at this milestone.

```
Authentication:    None (no Spring Security)
Token issuer:      None
Password encoder:  None
CORS:              WebSocket endpoint uses setAllowedOrigins("*") — unrestricted
CSRF:              N/A (no CSRF protection — WebSocket only, no forms)
Rate limiting:     None
```

**Security measures that DO exist:**
- `ApiKeyRedactionFilter` (Logback TurboFilter): prevents `sk-ant-[A-Za-z0-9_-]+` from appearing in any log output
- `ApiKeyStartupCheck`: warns at startup if `ANTHROPIC_API_KEY` is absent
- No auth on any REST or WebSocket endpoint

**Note:** `DeviceIdentify` WS message has a `bearerToken` field but it is not validated — it is only read for logging (ignored in handler).

## 12. Custom Security Components

```
=== ApiKeyRedactionFilter (com.pairion.gateway.config) ===
Extends: ch.qos.logback.classic.turbo.TurboFilter
Purpose: Denies any log event whose format string or parameters contain sk-ant-[A-Za-z0-9_-]+
Extracts token from: N/A (inspects log message content, not HTTP)
Registration: via logback-spring.xml <turboFilter> block (wired manually in logback config)
Returns: FilterReply.DENY if pattern matches, FilterReply.NEUTRAL otherwise

=== ApiKeyStartupCheck (com.pairion.gateway.config) ===
Extends: Spring @Component
Purpose: Reads ANTHROPIC_API_KEY from Spring Environment at ApplicationReadyEvent
Logs: INFO if key present, WARN if absent — non-blocking
```

No JWT filter, no UserDetailsService, no custom authentication provider.

## 13. Exception Handling & Error Responses

No `@ControllerAdvice` or global exception handler exists. Each controller method returns `ResponseEntity` with explicit status codes. Unhandled exceptions fall through to Spring Boot's default `/error` endpoint.

**Current behavior:**
- All controllers return explicit `ResponseEntity` with 200, 201, or 204 status
- WebSocket errors are caught in `PairionWebSocketHandler.sendAgentEvent()` and logged at ERROR
- LLM errors are forwarded to `LlmEvent.Stop` (no propagation to client as error frame)
- No standard error response body format defined

**Gap:** No `@ControllerAdvice` — unhandled exceptions return Spring Boot default error response (no custom shape).

## 14. Mappers / DTOs

No MapStruct or ModelMapper. No DTO classes. All REST response bodies are built as `Map<String, Object>` inline in controllers (M0 stubs). The WebSocket protocol uses sealed record types in `com.pairion.core.ws` directly — these are the wire-format DTOs and are serialized/deserialized by Jackson.

**WebSocket wire types (com.pairion.core.ws) — act as DTOs:**
All are Java records with `@JsonProperty` annotations. Full list: `DeviceIdentify`, `SessionOpened`, `SessionClosed`, `HeartbeatPing`, `HeartbeatPong`, `ErrorMessage`, `AgentStateChange`, `WakeWordDetected`, `AudioStreamStart`, `SpeechEnded`, `AudioStreamEnd`, `TextMessage`, `TranscriptPartial`, `TranscriptFinal`, `LlmTokenStream`, `ToolCallStarted`, `ToolCallCompleted`, `UnderBreathAck`.

## 15. Utility Classes & Shared Components

```
=== ModelDownloader (com.pairion.core.util) ===
Methods:
  - download(String url, Path targetPath, String expectedSha256): boolean
    Downloads file with SHA-256 hash verification; skips if exists; logs progress at 10% intervals
  - writeWithProgress(InputStream in, Path target, long totalBytes): void [package-private]
  - computeSha256(Path path): String [package-private]
  - getDigest(String algorithm): MessageDigest [package-private, static]
Used by: DefaultWhisperCppNative (indirectly via PAIRION_HOME model path; downloader not currently wired in)

=== OpusDecoder (com.pairion.adapters.audio.opus) ===
Constants: SAMPLE_RATE=16000, CHANNELS=1, FRAME_DURATION_MS=20, FRAME_SIZE=320, STREAM_ID_PREFIX_LENGTH=4
Methods:
  - create(): OpusDecoder [static factory — creates ConcentusOpusDecoder]
  - decode(byte[] frame): byte[] — strips 4-byte prefix, returns PCM bytes
  - extractStreamId(byte[] frame): String — reads first 4 bytes as UTF-8
  - reset(): void
Used by: AgentSession.onAudioStreamStart()

=== NativeLibraryLoader (com.pairion.nativelib.whisper) ===
Methods:
  - load(): boolean [static, synchronized] — extracts classpath lib to temp, calls System.load()
  - isLoaded(): boolean [static]
  - loadedPath(): String [static]
  - detectPlatform(): String [package-private, static] — returns "darwin-aarch64", "linux-x86_64", etc.
Used by: DefaultWhisperCppNative via LibraryLoader functional interface
```

## 16. DATABASE SCHEMA

No database. No ORM. No Flyway. No Hibernate. The project has no persistence layer at this milestone.

```
Database not available — no data store configured
```

## 17. MESSAGE BROKER DETECTION

No message broker detected.

```
Broker: None
```

No RabbitMQ, Kafka, SQS, or any async messaging dependency. All communication is synchronous (REST) or real-time (WebSocket).

## 18. CACHE DETECTION

No Redis or caching layer detected.

```
Cache Provider: None
```

No `@Cacheable`, `@CacheEvict`, Redis, Caffeine, or EhCache dependencies or annotations.

## 19. ENVIRONMENT VARIABLE INVENTORY

```
Variable              | Used In                                    | Default                    | Required in Prod
----------------------|--------------------------------------------|----------------------------|------------------
ANTHROPIC_API_KEY     | DefaultAnthropicClientWrapper (constructor)| None                       | YES — LLM unavailable without it
PAIRION_HOME          | DefaultWhisperCppNative (constructor)      | ~/.pairion                 | NO (defaults to ~/.pairion)
```

Spring `@Value` properties:
```
pairion.adapters.llm                          | AnthropicLlmAdapter @ConditionalOnProperty | "anthropic" (matchIfMissing=true)
pairion.adapters.llm.anthropic.model          | AnthropicLlmAdapter constructor            | "claude-sonnet-4-6"
pairion.adapters.stt                          | WhisperCppSttAdapter @ConditionalOnProperty| "whispercpp" (matchIfMissing=true)
pairion.stt.native.enabled                    | DefaultWhisperCppNative @ConditionalOnProperty | true (matchIfMissing=true)
```

## 20. SERVICE DEPENDENCY MAP

Standalone service — no inter-service dependencies.

**External dependencies:**
```
Anthropic API:        api.anthropic.com — called by DefaultAnthropicClientWrapper via anthropic-java SDK
                      Auth: ANTHROPIC_API_KEY environment variable
                      Protocol: HTTPS streaming (Messages API, /v1/messages)
                      Model: claude-sonnet-4-6 (configurable via pairion.adapters.llm.anthropic.model)

HuggingFace CDN:      huggingface.co/ggerganov/whisper.cpp — one-time model download
                      URL: resolve/main/ggml-small.en.bin
                      Auth: None (public)
                      Triggered: first transcription when model not present at $PAIRION_HOME/models/whisper/

whisper.cpp source:   github.com/ggerganov/whisper.cpp — cloned at build time (Maven initialize phase)
                      Tag: v1.8.4
```

**Downstream consumers:** None (no other services call Pairion Server at this milestone).

## 21. Known Technical Debt & Issues

**TODO/PLACEHOLDER/STUB SCAN:**
Grep results contain "stub" and "placeholder" in Javadoc comments only — not executable code gaps. All stub endpoints return well-formed (if empty/synthetic) responses, which is the intended M0 walking skeleton pattern. These are documented milestones, not incomplete code.

| Issue | Location | Severity | Notes |
|-------|----------|----------|-------|
| REST controllers return hardcoded stubs | AdapterController, HouseholdController, MemoryController, SkillController | MEDIUM | Intentional M0 walking skeleton; full impl is later milestones |
| DefaultSoulPromptProvider returns hardcoded prompt | pairion-agent/soul/DefaultSoulPromptProvider.java | MEDIUM | Intentional placeholder; SOUL system is later milestone |
| DeviceIdentify.bearerToken not validated | PairionWebSocketHandler.handleDeviceIdentify() | HIGH | Field exists in protocol but is never authenticated; no auth layer at all |
| No @ControllerAdvice / global exception handler | pairion-gateway/rest/ | MEDIUM | Unhandled exceptions return Spring Boot default error shape |
| logstash-logback-encoder not wired | pom.xml + logback-spring.xml | LOW | Dependency declared but JSON layout not configured in logback-spring.xml |
| No Spring Security | pairion-gateway | HIGH | All endpoints are open (intentional for M0; must be addressed before any network exposure) |
| pairion-household, pairion-memory, pairion-skills modules empty | three modules | MEDIUM | Scaffold only — package-info.java exists, no implementation |
| ModelDownloader not wired into Spring context | pairion-core/util/ModelDownloader.java | LOW | Class exists but not a @Component; whisper model download not automated on startup |
| Weather tool hardcoded in AgentSession | AgentSession.java:onTranscriptFinal() | MEDIUM | get_current_weather ToolDefinition is hardcoded inline; no tool call dispatch or actual weather fetch implemented |
| TTS SPI defined, no implementation | TtsAdapter.java | MEDIUM | Interface exists, no @Component implementation wired |
| virtual threads not enabled in main config | application.yml | LOW | spring.threads.virtual.enabled=true only in test config, not main application.yml |

## 22. Security Vulnerability Scan (Snyk)

Scan Date: 2026-04-19T17:10:00Z
Snyk CLI Version: 1.1303.0

### Dependency Vulnerabilities (Open Source)
**EXIT CODE: 0 — PASS**

Critical: 0
High:     0
Medium:   0
Low:      0
Total:    0 known vulnerabilities in dependencies.

### Code Vulnerabilities (SAST)
**EXIT CODE: 2 — Snyk Code scan not available** (authentication or feature flag issue; not a vulnerability finding)

Errors:   N/A (scan unavailable)
Warnings: N/A (scan unavailable)

### IaC Findings
N/A — No Dockerfile or docker-compose.yml present.

