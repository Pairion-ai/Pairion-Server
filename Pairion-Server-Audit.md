# Pairion-Server — Codebase Audit

**Generated:** 2026-04-26T15:30:00Z
**Branch:** main
**Commit:** 94bc92ac33d4166c8915451bfaf2387050ab980c feat: auto-activate OSM background on session start with DFW default view (PS-OSM-001)

---
---

### 1. Project Identity

```
Project Name:      Pairion Server
Repository URL:    local — ~/Documents/GitHub/Pairion-Server
Primary Language:  Java 21 / Spring Boot 3.4.4
Language Version:  OpenJDK 21.0.10 2026-01-20 LTS (with --enable-preview)
Build Tool:        Apache Maven 3.9.12
Package Manager:   Maven (no separate package manager)
Current Branch:    main
Latest Commit:     94bc92ac33d4166c8915451bfaf2387050ab980c
Latest Message:    feat: auto-activate OSM background on session start with DFW default view (PS-OSM-001)
Audit Timestamp:   2026-04-26T15:30:00Z
```

---

### 2. Directory Structure

Multi-module Maven project. Source root is `src/main/java/com/pairion/` within each module. Nine modules under the project root.

```
pairion-server/                         ← root POM (packaging=pom)
├── pom.xml
├── CLAUDE.md, Architecture.md, CONVENTIONS.md, openapi.yaml, asyncapi.yaml
├── pairion-core/                       ← shared domain types (no Spring)
│   └── src/main/java/com/pairion/core/
│       ├── agent/   AgentState.java
│       ├── llm/     LlmCapabilities, LlmEvent, LlmRequest, ToolDefinition
│       ├── stt/     SttCapabilities, SttEvent
│       ├── tts/     TtsCapabilities, TtsEvent
│       ├── util/    ModelDownloader
│       └── ws/      WebSocketMessage + 28 record subtypes
├── pairion-adapters/                   ← SPI implementations
│   └── src/main/java/com/pairion/adapters/
│       ├── audio/opus/   OpusEncoder, OpusDecoder, Concentus wrappers
│       ├── data/adsb/    AdsbDataAdapter, HttpAdsbDataClient, AdsbEnrichmentService
│       ├── data/weatherradar/  WeatherRadarDataAdapter, HttpWeatherRadarDataClient
│       ├── embedding/spi/  EmbeddingAdapter (interface only)
│       ├── llm/anthropic/  AnthropicLlmAdapter, DefaultAnthropicClientWrapper
│       ├── llm/openaicompat/  OpenAiCompatLlmAdapter, SSE parser
│       ├── llm/spi/     LlmAdapter (interface)
│       ├── stt/spi/     SttAdapter (interface)
│       ├── stt/whispercpp/  WhisperCppSttAdapter, DefaultWhisperCppNative
│       ├── tts/piper/   PiperTtsAdapter, DefaultPiperTtsNative
│       ├── tts/spi/     TtsAdapter (interface)
│       ├── vad/spi/     VadAdapter (interface)
│       ├── vectorstore/spi/  VectorStoreAdapter (interface)
│       ├── voiceid/spi/  VoiceIdAdapter (interface)
│       └── wake/spi/    WakeAdapter (interface)
├── pairion-agent/                      ← turn loop + tool definitions
│   └── src/main/java/com/pairion/agent/
│       ├── session/  AgentSession, AgentSessionEvent
│       ├── soul/     SoulPromptProvider, DefaultSoulPromptProvider
│       ├── tools/    AgentTool, ToolDispatcher
│       │   ├── layer/  SetBackgroundTool, AddOverlayTool, RemoveOverlayTool, ClearOverlaysTool
│       │   ├── map/    MapFocusTool
│       │   ├── scene/  ShowAdsbRadarTool
│       │   └── weather/  OpenMeteoWeatherTool
│       └── util/     MarkdownStripper
├── pairion-gateway/                    ← Spring Boot entry point + controllers
│   └── src/main/java/com/pairion/gateway/
│       ├── config/   WebSocketConfig, ApiKeyRedactionFilter
│       ├── rest/     HealthController, AdapterController, HouseholdController,
│       │             LogController, MemoryController, SkillController
│       ├── startup/  ModelStartupService
│       └── ws/       PairionWebSocketHandler
├── pairion-household/                  ← placeholder module (package-info only)
├── pairion-memory/                     ← placeholder module (package-info only)
├── pairion-skills/                     ← placeholder module (package-info only)
├── pairion-native-whisper/             ← jextract-generated Whisper JNI bindings
└── pairion-native-piper/               ← CMake-built Piper TTS native library
```

---

### 3. Build & Dependency Manifest

File: `/pom.xml` (root), plus per-module `pom.xml` files in each of the 9 modules.

| Dependency | Version | Purpose |
|---|---|---|
| spring-boot-starter-parent | 3.4.4 | Spring Boot BOM and parent |
| spring-boot-starter-web | (via parent) | REST controllers, embedded Tomcat |
| spring-boot-starter-websocket | (via parent) | WebSocket support |
| spring-boot-starter-logging / logback | (via parent) | Centralized structured logging |
| com.anthropic:sdk | (resolved via anthropic module pom) | Anthropic Claude Java SDK |
| com.fasterxml.jackson | (via parent) | JSON serialization/deserialization |
| concentus | (adapters pom) | Pure-Java Opus codec (no native library) |
| slf4j-api | (managed) | Logging facade |
| junit-jupiter | (test scope) | JUnit 5 unit tests |
| assertj-core | (test scope) | Fluent assertions |
| mockito-core | (test scope) | Mocking framework |
| archunit | 1.3.0 | Architecture enforcement rules |
| jacoco-maven-plugin | 0.8.12 | Code coverage with 100% enforcement |
| spotless-maven-plugin | 2.43.0 | Code formatting (Google Java Format AOSP style) |
| maven-checkstyle-plugin | 3.6.0 | Static analysis / style enforcement |
| checkstyle | 10.21.4 | Checkstyle rules engine |

Build commands:
```
Build:    mvn compile
Test:     mvn test
Verify:   mvn verify          (includes jacoco check + checkstyle)
Run:      mvn spring-boot:run -pl pairion-gateway
Package:  mvn package
```

Compiler flags: `--enable-preview` (Java 21 preview features active), `-Xlint:all`.
Maven Surefire: `--enable-preview --enable-native-access=ALL-UNNAMED` for JNI test support.

---

### 4. Configuration & Infrastructure Summary

**`pairion-gateway/src/main/resources/application.yml`**
Port 18789; virtual threads enabled. Selects LLM adapter via `pairion.adapters.llm` (default: `anthropic`). OpenAI-compat base URL `http://localhost:1234/v1`. Piper voice `en_GB-alan-medium`. ADS-B bounding box centred ~DFW. Log file at `~/Pairion/logs/pairion.log` with 10MB rolling, 7-day retention.

**`pairion-gateway/src/main/resources/logback-spring.xml`**
Configures `ApiKeyRedactionFilter` as a Logback TurboFilter to deny any log event matching `sk-ant-[A-Za-z0-9_-]+` before it reaches any appender.

**`pairion-gateway/src/test/resources/application.yml`**
Test overrides: disables STT/TTS native adapters, uses stub LLM adapter.

**`config/checkstyle/checkstyle.xml`** and **`config/checkstyle/suppressions.xml`**
Checkstyle rules enforced at `verify` phase on main source only (tests excluded).

**Connection map:**
```
Database:        None — no persistence layer currently active
Cache:           In-memory only (ConcurrentHashMap in AdsbEnrichmentService)
Message Broker:  None
External APIs:
  - https://api.anthropic.com (LLM — Anthropic Claude, via ANTHROPIC_API_KEY)
  - http://localhost:1234/v1   (LLM — local OpenAI-compatible, e.g. LM Studio)
  - https://opensky-network.org/api/states/all (ADS-B aircraft data)
  - https://geocoding-api.open-meteo.com/v1/search (geocoding for focus_map and weather)
  - https://api.open-meteo.com/v1/forecast (weather data)
  - https://api.rainviewer.com/public/weather-maps.json (weather radar tile metadata)
Cloud Services:  None
```

**CI/CD:** No `.github/workflows`, Jenkinsfile, or other CI pipeline config detected at project root. None configured.

---

### 5. Startup & Runtime Behavior

**Entry point:** `com.pairion.gateway.PairionServerApplication` — `@SpringBootApplication(scanBasePackages = "com.pairion")`. Launches Spring via `SpringApplication.run()`.

**Startup sequence:**
1. Spring DI context loads all modules under `com.pairion` package scan.
2. `WebSocketConfig` registers `PairionWebSocketHandler` at `/ws/v1` (all origins).
3. Adapter selection: `@ConditionalOnProperty` wires either `AnthropicLlmAdapter` (default) or `OpenAiCompatLlmAdapter` depending on `pairion.adapters.llm` value.
4. STT and TTS adapters wire via `@ConditionalOnProperty`; both report as unavailable without native libraries.
5. `ModelStartupService.onApplicationReady()` fires after context ready; spawns two virtual threads to download Whisper STT model and Piper TTS voice ONNX model. Downloads are idempotent (skip if already present); SHA-256 verified.
6. No database migrations — no persistence layer.
7. No seed data.

**Scheduled tasks:**
- `AdsbDataAdapter`: single-thread `ScheduledExecutorService` running at `pairion.data.adsb.poll-interval-seconds` (default: 10s) while ADS-B radar is active per session.
- `WeatherRadarDataAdapter`: single-thread `ScheduledExecutorService` at `pairion.data.weatherradar.poll-interval-seconds` (default: 300s) while weather radar overlay is active.
- `AgentSession.clearScheduler`: per-session daemon thread scheduling 2-minute map-clear timer.

**Health check:** `GET /v1/health` → `{"status": "healthy"}` (HealthController).

---

### 6. Data Model / Entity Layer

No JPA entities or database persistence. Data is carried in Java records (immutable value types). Key domain records:

```
=== AdsbAircraft (file: pairion-adapters/.../data/adsb/AdsbAircraft.java) ===
Storage: In-memory (pushed via WebSocket — no DB persistence)
Type: Java record
@JsonInclude(JsonInclude.Include.NON_NULL)

Fields:
  - icao24: String        (ICAO 24-bit address hex)
  - callsign: String      (nullable — trimmed flight callsign)
  - lat: Double           (nullable — decimal degrees)
  - lon: Double           (nullable — decimal degrees)
  - altitudeFt: Double    (nullable — barometric altitude, converted from metres)
  - speedKnots: Double    (nullable — converted from m/s)
  - trackDeg: Double      (nullable — true track degrees)
  - verticalRateFpm: Double (nullable — converted from m/s)
  - onGround: boolean
  - registration: String  (nullable — enriched via OpenSky metadata API)
  - aircraftType: String  (nullable — ICAO type code, enriched)
  - origin: String        (nullable — departure airport ICAO, enriched)
  - destination: String   (nullable — destination airport ICAO, enriched)

Audit Fields: none (ephemeral snapshot)
Relationships: none
```

```
=== WeatherRadarSnapshot (file: pairion-adapters/.../data/weatherradar/WeatherRadarSnapshot.java) ===
Storage: In-memory (pushed via WebSocket)
Type: Java record
@JsonInclude(JsonInclude.Include.NON_NULL)

Fields:
  - host: String          (RainViewer tile cache host URL)
  - frames: List<WeatherRadarFrame>
  - latestPath: String    (path of most recent frame)
  - tileSize: int         (always 256)
  - colorScheme: int      (1–8, default 4 = TurboMap)
  - options: String       ("1_1" = smooth + snow)
```

```
=== WeatherRadarFrame (file: pairion-adapters/.../data/weatherradar/WeatherRadarFrame.java) ===
Type: Java record
Fields:
  - time: long            (Unix epoch seconds)
  - path: String          (tile URL path fragment)
```

```
=== AgentSessionEvent subtypes (file: pairion-agent/.../session/AgentSessionEvent.java) ===
Sealed interface — 17 record subtypes carrying all WebSocket-bound events:
StateChangeEvent, TranscriptPartialEvent, TranscriptFinalEvent, LlmTokenEvent,
ToolCallStartedEvent, ToolCallCompletedEvent, AudioStreamStartEvent, AudioChunkEvent,
AudioStreamEndEvent, MapFocusEvent, MapClearEvent, ConversationEndedEvent,
BackgroundChangeEvent, OverlayAddEvent, OverlayRemoveEvent, OverlayClearEvent, SceneDataPushEvent
```

---

### 7. Enum / Constant Inventory

```
=== AgentState (file: pairion-core/.../core/agent/AgentState.java) ===
Values: IDLE("idle"), LISTENING("listening"), THINKING("thinking"), SPEAKING("speaking")
Used in: AgentSession (state machine), AgentSessionEvent.StateChangeEvent, AgentStateChange (ws record)
Has display label: YES (wireValue() returns lowercase string for protocol use)
Serialization: custom wireValue() string via enum constructor; e.g. IDLE → "idle"
```

No other enums. LLM event types and WebSocket message types use sealed interfaces with record subtypes rather than enums.

---

### 8. Data Access / Repository Layer

No repository or DAO layer exists. There is no database. Data access is done via HTTP clients (external APIs) with an in-memory cache for ADS-B enrichment.

**HTTP client boundaries (SPI pattern):**

```
=== AdsbDataClient (file: pairion-adapters/.../data/adsb/AdsbDataClient.java) ===
Interface — production impl: HttpAdsbDataClient (@Component, package-private)
Methods:
  - fetchStates(lamin, lomin, lamax, lomax: double): List<List<Object>>
    (calls https://opensky-network.org/api/states/all?lamin=...&lomin=...&lamax=...&lomax=...)
  - fetchMetadata(icao24: String): Optional<AircraftMetadata>
    (calls OpenSky aircraft metadata API)
  - fetchRoute(callsign: String): Optional<RouteInfo>
    (calls OpenSky route API)
Nested records: AircraftMetadata(registration, typecode), RouteInfo(departureAirport, destinationAirport)
HTTP Basic Auth via opensky-username/opensky-password config properties.
```

```
=== WeatherRadarDataClient (file: pairion-adapters/.../data/weatherradar/WeatherRadarDataClient.java) ===
Interface — production impl: HttpWeatherRadarDataClient (@Component, package-private)
Methods:
  - fetchSnapshot(): WeatherRadarSnapshot
    (calls https://api.rainviewer.com/public/weather-maps.json, no auth)
```

In-memory enrichment cache in `AdsbEnrichmentService`: `ConcurrentHashMap` keyed by icao24 (metadata) and callsign (routes), with 1-hour and 30-minute TTLs respectively. Rate-limited at 2 calls/sec each bucket.

---

### 9. Service / Business Logic Layer — Full Method Signatures

```
=== AgentSession (file: pairion-agent/.../session/AgentSession.java) ===
Dependencies: SttAdapter, LlmAdapter, TtsAdapter, SoulPromptProvider, ToolDispatcher,
              AdsbDataAdapter, WeatherRadarDataAdapter, Consumer<AgentSessionEvent>
Lifecycle: one instance per WebSocket session; NOT a Spring bean (constructed in handler)

Public Methods:
  - onAudioStreamStart(streamId: String): void
    Purpose: Allocates Opus decoder and STT session; transitions state to LISTENING
    Calls: OpusDecoder.create(), sttAdapter.createSession()
    Transactional: NO

  - onAudioChunk(frameData: byte[]): void
    Purpose: Decodes Opus audio and feeds PCM to STT session
    Calls: opusDecoder.decode(), sttSession.feedAudio()
    Transactional: NO

  - onSpeechEnded(): void
    Purpose: Finalizes STT stream; triggers transcript → LLM → TTS turn loop
    Calls: sttSession.finalizeStream()
    Transactional: NO

  - currentState(): AgentState
    Purpose: Returns current state enum value

  - close(): void
    Purpose: Cancels map-clear timer, shuts down scheduler, stops polling adapters
    Calls: adsbDataAdapter.stopPolling(), weatherRadarDataAdapter.stopPolling()
    Transactional: NO

  - activateDefaultOsmView(): void
    Purpose: Emits BackgroundChangeEvent("osm") with DFW center at zoom 10 on session start
    Calls: eventSink.accept()

  - activateAdsbRadar(): void
    Purpose: Emits BackgroundChange("vfr") + OverlayAdd("adsb") and starts ADS-B polling
    Calls: adsbDataAdapter.startPolling()

Package-private / Internal Methods (signatures only):
  handleSttEvent(event: SttEvent): void
  handleLlmEvent(event: LlmEvent, textAccumulator: StringBuilder, toolCallAccumulator: List<ToolCallRequest>): void
  emitTimedMapClear(): void
  onTranscriptFinal(transcript: String, stageAMs: long): void
  synthesizeSpeech(text: String, stageAMs/B/C/D: long): void
  buildToolDefinitions(): List<ToolDefinition>
  emitMapFocus, emitMapFocusFromWeather, emitBackgroundChange, emitOverlayAdd,
  emitOverlayRemove, emitOverlayClear, emitAdsbRadar (all take Map<String,Object> result)
```

```
=== ToolDispatcher (file: pairion-agent/.../tools/ToolDispatcher.java) ===
Dependencies: List<AgentTool> (Spring list injection — all @Component AgentTool beans)

Public Methods:
  - dispatch(toolName: String, input: Map<String,Object>): Map<String,Object>
    Purpose: Routes tool call to matching AgentTool by name; returns error map if unknown
    Calls: AgentTool.execute(input)
    Throws/Returns errors: never throws — returns {error: "unknown_tool"} or {error: "tool_execution_failed"}
    Transactional: NO
```

```
=== AdsbDataAdapter (file: pairion-adapters/.../data/adsb/AdsbDataAdapter.java) ===
Dependencies: AdsbDataClient, AdsbEnrichmentService; config @Value properties for bbox/interval

Public Methods:
  - startPolling(sink: Consumer<List<AdsbAircraft>>): void [synchronized]
    Purpose: Starts scheduled polling; replaces any existing consumer
    Calls: AdsbDataClient.fetchStates(), AdsbEnrichmentService.enrich()
    Transactional: NO
  - stopPolling(): void [synchronized]
    Purpose: Cancels future, shuts down scheduler, clears sink
    Transactional: NO

Package-private:
  poll(): void
  parseState(state: List<Object>): AdsbAircraft (returns null if no position)
```

```
=== AdsbEnrichmentService (file: pairion-adapters/.../data/adsb/AdsbEnrichmentService.java) ===
Dependencies: AdsbDataClient, Clock (for TTL and rate-limit decisions)

Public Methods:
  - enrich(aircraft: AdsbAircraft): AdsbAircraft
    Purpose: Enriches aircraft with registration/type/route from cache or rate-limited API calls
    Calls: AdsbDataClient.fetchMetadata(), AdsbDataClient.fetchRoute()
    Transactional: NO

Package-private:
  getCachedMetadata(icao24: String): Optional<AircraftMetadata>
  getCachedRoute(callsign: String): Optional<RouteInfo>
  acquireMetadataRateLimit(): boolean (CAS on AtomicLong)
  acquireRouteRateLimit(): boolean
```

```
=== WeatherRadarDataAdapter (file: pairion-adapters/.../data/weatherradar/WeatherRadarDataAdapter.java) ===
Dependencies: WeatherRadarDataClient, pollIntervalSeconds from config

Public Methods:
  - startPolling(sink: Consumer<WeatherRadarSnapshot>): void [synchronized]
    Purpose: Starts scheduled polling of RainViewer API
    Transactional: NO
  - stopPolling(): void [synchronized]
    Purpose: Cancels future, shuts down scheduler

Package-private:
  poll(): void
```

```
=== DefaultSoulPromptProvider (file: pairion-agent/.../soul/DefaultSoulPromptProvider.java) ===
Dependencies: none (placeholder implementation)

Public Methods:
  - getSystemPrompt(sessionId: String): String
    Purpose: Returns hardcoded SOUL system prompt (placeholder — full SOUL is a later milestone)
    NOTE: Documented as placeholder throughout; BLOCKING technical debt — see Section 21
```

```
=== ModelStartupService (file: pairion-gateway/.../startup/ModelStartupService.java) ===
Dependencies: piperVoice (@Value), ModelDownloader

Public Methods:
  - onApplicationReady(): void [@EventListener(ApplicationReadyEvent.class)]
    Purpose: Triggers idempotent model downloads on virtual threads
    Calls: ModelDownloader.download() for Whisper and Piper models

Package-private:
  downloadWhisperModel(): void
  downloadPiperModel(): void
  resolveModelPath(pairionHome: String, category: String, filename: String): Path
```

```
=== OpenMeteoWeatherTool (file: pairion-agent/.../tools/weather/OpenMeteoWeatherTool.java) ===
Implements: AgentTool

Public Methods:
  - name(): String → "get_current_weather"
  - execute(input: Map<String,Object>): Map<String,Object>
    Purpose: Geocodes city via Open-Meteo geocoding API, then fetches current conditions
    External calls: geocoding-api.open-meteo.com, api.open-meteo.com (no API key required)
    Returns: {city, temperature_f, conditions, wind_speed_mph, latitude, longitude}

Package-private static:
  describeWeatherCode(code: int): String (WMO code → human-readable string)
```

```
=== MapFocusTool (file: pairion-agent/.../tools/map/MapFocusTool.java) ===
Implements: AgentTool

Public Methods:
  - name(): String → "focus_map"
  - execute(input: Map<String,Object>): Map<String,Object>
    Purpose: Geocodes location via Open-Meteo geocoding API and returns lat/lon/label/zoom
    External calls: geocoding-api.open-meteo.com
    Returns: {lat, lon, label, zoom, status} or {error, message}
```

```
=== SetBackgroundTool / AddOverlayTool / RemoveOverlayTool / ClearOverlaysTool / ShowAdsbRadarTool ===
All implement: AgentTool
All are pure result-map builders — no external HTTP calls.
  SetBackgroundTool.execute()  → validates background_id, returns {status, background_id, transition}
  AddOverlayTool.execute()     → validates overlay_id, returns {status, overlay_id[, params]}
  RemoveOverlayTool.execute()  → validates overlay_id, returns {status, overlay_id}
  ClearOverlaysTool.execute()  → returns {status: "overlays_cleared"}
  ShowAdsbRadarTool.execute()  → returns {status: "adsb_radar_activated", background_id: "vfr", overlay_id: "adsb"}
```

```
=== AnthropicLlmAdapter (file: pairion-adapters/.../llm/anthropic/AnthropicLlmAdapter.java) ===
Dependencies: AnthropicClientWrapper, defaultModel (@Value)
@ConditionalOnProperty(name = "pairion.adapters.llm", havingValue = "anthropic", matchIfMissing = true)

Public Methods:
  - name(): String → "anthropic"
  - capabilities(): LlmCapabilities (reflects API key availability)
  - generate(request: LlmRequest, eventConsumer: Consumer<LlmEvent>): void
    Purpose: Calls Anthropic Messages API, emits streaming LlmEvent tokens/tool calls/stop
    Calls: AnthropicClientWrapper.streamCompletion()
    Transactional: NO
```

---

### 10. Controller / Handler / Route Layer — Method Signatures Only

```
=== HealthController (file: pairion-gateway/.../rest/HealthController.java) ===
Base Path: /v1
Dependencies: none

Endpoints:
  - getHealth() → ResponseEntity<Map<String,String>> (inline response)
  - getVersion() → ResponseEntity<Map<String,String>> (inline response)
```

```
=== AdapterController (file: pairion-gateway/.../rest/AdapterController.java) ===
Base Path: /v1/adapters
Dependencies: none (stub implementation — M0)

Endpoints:
  - listAdapters() → ResponseEntity<List<Object>>   (returns empty list)
  - getAdapter(category, name) → ResponseEntity<Map<String,Object>> (returns stub map)
```

```
=== HouseholdController (file: pairion-gateway/.../rest/HouseholdController.java) ===
Base Path: /v1/household
Dependencies: none (stub implementation — M0)

Endpoints:
  - getHousehold() → ResponseEntity<Map<String,Object>>
  - listUsers() → ResponseEntity<List<Object>>
  - createUser(@RequestBody Map) → ResponseEntity<Map<String,Object>> (201 Created)
  - getUser(userId) → ResponseEntity<Map<String,Object>>
  - deleteUser(userId) → ResponseEntity<Void> (204 No Content)
```

```
=== LogController (file: pairion-gateway/.../rest/LogController.java) ===
Base Path: /v1/logs
Dependencies: SLF4J Logger

Endpoints:
  - postLogs(@RequestBody List<Map<String,Object>>) → ResponseEntity<Void> (204)
    (forwards client log records through server Logback pipeline)
```

```
=== MemoryController (file: pairion-gateway/.../rest/MemoryController.java) ===
Base Path: /v1/memory
Dependencies: none (stub — M0)

Endpoints:
  - listEpisodes(userId, limit=50) → ResponseEntity<List<Object>> (returns empty list)
```

```
=== SkillController (file: pairion-gateway/.../rest/SkillController.java) ===
Base Path: /v1/skills
Dependencies: none (stub — M0)

Endpoints:
  - listSkills() → ResponseEntity<List<Object>>
  - getSkill(skillId) → ResponseEntity<Map<String,Object>> (returns stub)
```

```
=== PairionWebSocketHandler (file: pairion-gateway/.../ws/PairionWebSocketHandler.java) ===
Base Path: /ws/v1
Extends: AbstractWebSocketHandler
Dependencies: ObjectMapper, SttAdapter, LlmAdapter, TtsAdapter, SoulPromptProvider,
              ToolDispatcher, AdsbDataAdapter, WeatherRadarDataAdapter

Endpoints/Handlers:
  - afterConnectionEstablished(session) → logs connection
  - handleTextMessage(session, message) → dispatches to DeviceIdentify, HeartbeatPing,
    AudioStreamStart, SpeechEnded handlers
  - handleBinaryMessage(session, message) → routes to agentSession.onAudioChunk()
  - afterConnectionClosed(session, status) → cleans up agentSession

Package-private helpers (tested directly):
  handleDeviceIdentify(), handleHeartbeatPing(), handleAudioStreamStart(), handleSpeechEnded()
  sendAgentEvent(), serializeEvent()
```

---

### 11. Security Configuration

```
Authentication: None — no authentication or authorization layer.
Token issuer/validator: N/A
Password hashing: N/A

Public endpoints (no auth required):
  - ALL endpoints are public — no authentication configured.
  - GET  /v1/health
  - GET  /v1/version
  - GET  /v1/adapters
  - GET  /v1/adapters/{category}/{name}
  - GET  /v1/household
  - GET  /v1/household/users
  - POST /v1/household/users
  - GET  /v1/household/users/{userId}
  - DELETE /v1/household/users/{userId}
  - GET  /v1/memory/episodes
  - GET  /v1/skills
  - GET  /v1/skills/{skillId}
  - POST /v1/logs
  - WS   /ws/v1

Protected endpoints: None

CORS: setAllowedOrigins("*") on /ws/v1 — no CORS restriction configured.

CSRF: Disabled (not configured). Spring Security is not on the classpath.

Rate limiting: None at API level. AdsbEnrichmentService has internal rate limiting
               for OpenSky API calls (2 calls/sec per bucket). No HTTP-level rate limiting.
```

**OBSERVATION:** No authentication or authorization is implemented. This is expected for M0/M1 development milestone on a LAN-only device (household AI), but must be addressed before any network-exposed deployment. Flag for architect review.

---

### 12. Custom Security Components

```
=== ApiKeyRedactionFilter (file: pairion-gateway/.../config/ApiKeyRedactionFilter.java) ===
Type: Logback TurboFilter (registered in logback-spring.xml)
Purpose: Prevents Anthropic API key patterns from reaching any log appender
Extracts credentials from: log message format string and parameters
Validates via: Pattern.compile("sk-ant-[A-Za-z0-9_\\-]+") regex match
Sets user context: NO — denies the log event before any appender processes it
```

No HTTP authentication filter, JWT validator, session guard, or authorization interceptor exists. The project uses no Spring Security configuration.

---

### 13. Exception / Error Handling

No global `@ControllerAdvice` or `@ExceptionHandler` exists. Error handling is ad-hoc at the call site.

```
=== No GlobalErrorHandler ===
Mechanism: None — no centralized exception handler registered.

Per-component handling:
  - ToolDispatcher.dispatch():  catches Exception from tool.execute(), returns
    {error: "tool_execution_failed", message: e.getMessage()} structured map to LLM
  - AgentSession.synthesizeSpeech(): catches Exception from ttsAdapter.speak(),
    logs error, sets endReason="error", emits AudioStreamEndEvent
  - AdsbDataAdapter.poll():  catches Exception, logs warning, returns silently
  - AdsbEnrichmentService.fetchMetadata/fetchRoute():  catches Exception, logs warning, returns null
  - WeatherRadarDataAdapter.poll():  catches Exception, logs warning, returns silently
  - PairionWebSocketHandler.sendAgentEvent():  catches Exception, logs error

REST error format: No standard error response body — Spring Boot's default /error page applies.
WebSocket error format: No structured ErrorMessage sent on unhandled exceptions.
```

**OBSERVATION:** Missing `@ControllerAdvice` means REST errors return Spring Boot's default Whitelabel error page rather than a structured JSON body. This is acceptable at M0 for stub endpoints but should be addressed before real endpoint implementation.

---

### 14. Mappers / Data Transformation

No dedicated mapper layer. No MapStruct or similar framework.

Data transformation is done inline:
- **OpenSky state vector → AdsbAircraft**: `AdsbDataAdapter.parseState(List<Object>)` — positional array parsing with unit conversion (metres→feet, m/s→knots, m/s→fpm) and coordinate rounding.
- **AgentSessionEvent → WebSocket record**: `PairionWebSocketHandler.serializeEvent()` — sealed-interface switch mapping each event subtype to its corresponding `WebSocketMessage` record, then Jackson serializes to JSON.
- **LLM tool call → Map<String,Object>**: Each AgentTool returns a Map directly consumed by AgentSession for side-effect dispatch.
- **Jackson**: Used throughout for JSON serialization/deserialization. `WebSocketMessage` uses `@JsonTypeInfo`/`@JsonSubTypes` for polymorphic type discrimination on the `type` field.

---

### 15. Utility Modules & Shared Components

```
=== MarkdownStripper (file: pairion-agent/.../util/MarkdownStripper.java) ===
Functions:
  - strip(text: String): String — static utility; strips markdown for TTS synthesis
    Operations: link unwrapping, horizontal-rule removal, heading markers, blockquotes,
                bold (**), italic (*), backticks, whitespace collapse. Returns "" for null/blank.
Used by: AgentSession.onTranscriptFinal() (before TTS synthesis)
```

```
=== ModelDownloader (file: pairion-core/.../util/ModelDownloader.java) ===
Functions:
  - download(url: String, targetPath: Path, expectedSha256: String): boolean
    Downloads file if not already present; verifies SHA-256; returns true on success.
Used by: ModelStartupService (downloads Whisper and Piper models on startup)
```

```
=== LibraryLoader — two copies ===
  pairion-adapters/.../stt/whispercpp/LibraryLoader.java
  pairion-adapters/.../tts/piper/LibraryLoader.java
Both extract native library from classpath JAR to a temp directory and call System.load().
Used by: DefaultWhisperCppNative, DefaultPiperTtsNative respectively.
```

---

### 16. Database Schema (Live)

No database is configured or running. Pairion-Server has no persistence layer in the current milestone. All state is held in-memory per WebSocket session. The `pairion-household`, `pairion-memory`, and `pairion-skills` modules are placeholder stubs with no entity classes.

Database not available for live schema check — none configured.

---

### 17. Message Broker Configuration

No message broker detected. RabbitMQ, Kafka, NATS, SQS, or any other broker is absent from dependencies and configuration. Inter-component communication uses in-process Java consumers (`Consumer<T>` lambdas) and a single `ConcurrentHashMap` for WebSocket session management.

---

### 18. Cache Layer

No external cache (Redis, Memcached, Caffeine, EhCache) is configured.

In-memory caching in `AdsbEnrichmentService`:
```
Cache Provider: In-memory (ConcurrentHashMap, JVM heap)
Connection: N/A — in-process

Cache Regions/Keys:
  - metadataCache (ConcurrentHashMap<String, CachedEntry<Optional<AircraftMetadata>>>)
    TTL: 1 hour (METADATA_TTL_MS)
    Key pattern: icao24 hex string
    Used by: AdsbEnrichmentService.enrich()
  - routeCache (ConcurrentHashMap<String, CachedEntry<Optional<RouteInfo>>>)
    TTL: 30 minutes (ROUTE_TTL_MS)
    Key pattern: callsign string (trimmed)
    Used by: AdsbEnrichmentService.enrich()

Cache Operations:
  - Read-through on enrich() — cache miss triggers (rate-limited) API call
  - Negative sentinel caching — Optional.empty() is cached to avoid re-querying unknown aircraft
  - No explicit eviction (entries expire by TTL check on read; no background eviction)
  - No size bound — could grow unbounded over very long sessions
```

---

### 19. Environment Variable Inventory

| Variable | Used In | Default | Required in Prod |
|----------|---------|---------|-----------------|
| `ANTHROPIC_API_KEY` | `DefaultAnthropicClientWrapper` (`System.getenv`) | none | YES (when llm=anthropic) |
| `PAIRION_HOME` | `ModelStartupService`, `DefaultPiperTtsNative`, `DefaultWhisperCppNative` (`System.getenv`) | `~/.pairion` | NO (falls back to home dir) |

**Config-property credentials (application.yml — NOT environment variables):**

| Property | Value | Issue |
|----------|-------|-------|
| `pairion.data.adsb.opensky-username` | `aallard` | Hardcoded in application.yml — CRITICAL |
| `pairion.data.adsb.opensky-password` | `Annabelle01*` | **Hardcoded plaintext password in application.yml** — CRITICAL |

**NOTE:** The OpenSky Network username and password are committed in plaintext to `application.yml`. This is a critical credential exposure. These must be moved to environment variables.

---

### 20. Service Dependency Map

```
Pairion-Server → Depends On (External)
-------------------------------------------
Anthropic Claude API:    api.anthropic.com:443 — LLM generation (ANTHROPIC_API_KEY)
LM Studio (optional):   localhost:1234/v1 — OpenAI-compatible LLM (when llm=openaicompat)
OpenSky Network:        opensky-network.org — ADS-B aircraft state vectors + metadata + routes
                        (HTTP Basic Auth: opensky-username/opensky-password)
Open-Meteo Geocoding:   geocoding-api.open-meteo.com — location name → lat/lon
Open-Meteo Forecast:    api.open-meteo.com — current weather conditions
RainViewer:             api.rainviewer.com — weather radar tile metadata
Hugging Face (startup): huggingface.co — Whisper STT model download (ggml-small.en.bin)
Rhasspy Piper (startup): huggingface.co — Piper TTS voice ONNX model download
```

Downstream Consumers: Client application (iOS/Android/Desktop) connects via WebSocket `/ws/v1` and REST `/v1/*`. No other services call Pairion-Server.

---

### 21. Known Technical Debt & Issues

**TODO/FIXME Scan Results:** No `TODO`, `FIXME`, or `XXX` markers detected in production source.

Placeholder/stub patterns found in production code (documented, intentional M0 stubs):

| Issue | Location | Severity | Notes |
|-------|----------|----------|-------|
| Hardcoded OpenSky password in application.yml | `pairion-gateway/src/main/resources/application.yml:52` | CRITICAL | `opensky-password: Annabelle01*` committed in plaintext. Must move to env var or secrets manager. |
| SOUL prompt is a placeholder | `pairion-agent/.../soul/DefaultSoulPromptProvider.java` | HIGH | Returns hardcoded prompt. Full SOUL with memory context, user preferences, and persona is deferred. All REST endpoints that depend on SOUL (household, memory, skills) are stubs. |
| No authentication or authorization | All REST and WebSocket endpoints | HIGH | Zero auth on any endpoint. Acceptable for LAN-only M0, but BLOCKING for any external exposure. |
| stub REST endpoints (AdapterController, HouseholdController, MemoryController, SkillController) | `pairion-gateway/.../rest/` | MEDIUM | All return empty lists or hardcoded stub maps. Placeholder for future milestones. |
| No global error handler (@ControllerAdvice) | pairion-gateway | MEDIUM | REST errors return Spring Boot default error page, not structured JSON. |
| No size bound on AdsbEnrichmentService in-memory cache | `AdsbEnrichmentService.java` | MEDIUM | metadataCache and routeCache can grow unbounded over long sessions; no LRU eviction. |
| pairion-household, pairion-memory, pairion-skills are empty placeholder modules | Multiple pom.xml and package-info.java files | LOW | No source beyond package-info.java. |
| CI/CD pipeline absent | Project root | LOW | No automated build, test, or deployment pipeline. |

---

### 22. Security Vulnerability Scan (Snyk)

```
Scan Date: 2026-04-26T15:30:00Z
Snyk CLI Version: 1.1303.0

### Dependency Vulnerabilities (Open Source)
Critical: 0
High: 0
Medium: 0
Low: 0
Total unique: 0

PASS — No known vulnerabilities in Maven dependencies.

### Code Vulnerabilities (SAST)
Snyk Code: SKIPPED — Snyk Code is not enabled for organization `aallard` (SNYK-CODE-0005).
Status: 403 Forbidden — plan limitation.

### IaC Findings
Not applicable — no Dockerfile, docker-compose, or Terraform files present.
```

**CRITICAL FINDING (manual, not Snyk):** OpenSky Network password (`Annabelle01*`) is hardcoded in `application.yml` and committed to the repository. This must be treated as a leaked credential regardless of whether Snyk flagged it.

