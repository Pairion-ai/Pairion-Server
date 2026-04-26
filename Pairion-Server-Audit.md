# Pairion-Server — Codebase Audit

**Generated:** 2026-04-26T00:00:00Z
**Commit:** 0e62e76817d5b320a62230925b36611258bb66c9
**Branch:** main
**Template:** Codebase-Audit-Template.md

---

---

## 1. Project Identity

```
Project Name:          Pairion Server
Repository URL:        ~/Documents/GitHub/Pairion-Server
Primary Language:      Java 21 (Spring Boot 3.4.4)
Language/Runtime:      OpenJDK 21.0.10 LTS (preview features enabled: --enable-preview)
Build Tool:            Apache Maven 3.9.12
Package Manager:       Maven (Central)
Current Branch:        main
Latest Commit Hash:    0e62e76817d5b320a62230925b36611258bb66c9
Latest Commit Message: feat: WeatherCurrent overlay data adapter with Open-Meteo (PX-WXCUR-001)
Audit Timestamp:       2026-04-26T00:00:00Z
```

Ambient, voice-first, household-scale AI presence server. Multi-module Maven project (9 modules). No database; no message broker. All state is in-memory, per WebSocket session.

---

## 2. Directory Structure

Multi-module Maven project with 9 modules: 2 native-binding modules and 7 Java modules.

```
pairion-server/
├── pom.xml                            root aggregator POM
├── CLAUDE.md / CONVENTIONS.md / Architecture.md
├── openapi.yaml / asyncapi.yaml       API contracts
├── config/checkstyle/                 Checkstyle rules + suppressions
│
├── pairion-native-whisper/            jextract-generated FFM bindings for whisper.cpp
│   └── src/main/java/com/pairion/nativelib/whisper/
│       ├── NativeLibraryLoader.java
│       └── (generated: WhisperBindings, RuntimeHelper, whisper_context_params, whisper_full_params)
│
├── pairion-native-piper/              jextract-generated FFM bindings for Piper TTS
│   └── src/main/java/com/pairion/nativelib/piper/   (generated bindings)
│
├── pairion-core/                      domain types (sealed interfaces, records, enums)
│   └── src/main/java/com/pairion/core/
│       ├── agent/AgentState.java
│       ├── llm/{LlmEvent, LlmRequest, LlmCapabilities, ToolDefinition}.java
│       ├── stt/{SttEvent, SttCapabilities}.java
│       ├── tts/{TtsEvent, TtsCapabilities}.java
│       ├── util/ModelDownloader.java
│       └── ws/  (28 WebSocket message types — sealed interface WebSocketMessage)
│
├── pairion-adapters/                  adapter SPI + implementations
│   └── src/main/java/com/pairion/adapters/
│       ├── audio/opus/                Opus encode/decode (Concentus + native)
│       ├── data/adsb/                 ADS-B: AdsbDataAdapter, AdsbEnrichmentService, HttpAdsbDataClient
│       ├── data/weatherradar/         Weather radar: WeatherRadarDataAdapter, HttpWeatherRadarDataClient
│       ├── data/weathercurrent/       Current weather: WeatherCurrentDataAdapter, HttpWeatherCurrentDataClient
│       ├── embedding/spi/             EmbeddingAdapter SPI (no implementation yet)
│       ├── llm/anthropic/             AnthropicLlmAdapter + DefaultAnthropicClientWrapper
│       ├── llm/openaicompat/          OpenAiCompatLlmAdapter + SSE parser
│       ├── llm/spi/                   LlmAdapter SPI
│       ├── stt/spi/                   SttAdapter SPI
│       ├── stt/whispercpp/            WhisperCppSttAdapter + DefaultWhisperCppNative
│       ├── tts/piper/                 PiperTtsAdapter + DefaultPiperTtsNative
│       ├── tts/spi/                   TtsAdapter SPI
│       ├── vad/spi/                   VadAdapter SPI (no implementation)
│       ├── vectorstore/spi/           VectorStoreAdapter SPI (no implementation)
│       ├── voiceid/spi/               VoiceIdAdapter SPI (no implementation)
│       └── wake/spi/                  WakeAdapter SPI (no implementation)
│
├── pairion-household/                 placeholder module (package-info.java only)
├── pairion-memory/                    placeholder module (package-info.java only)
├── pairion-skills/                    placeholder module (package-info.java only)
│
├── pairion-agent/                     turn loop, tools, SOUL prompt
│   └── src/main/java/com/pairion/agent/
│       ├── session/{AgentSession, AgentSessionEvent}.java
│       ├── soul/{SoulPromptProvider, DefaultSoulPromptProvider}.java
│       ├── tools/{AgentTool, ToolDispatcher}.java
│       ├── tools/layer/{AddOverlayTool, ClearOverlaysTool, RemoveOverlayTool, SetBackgroundTool}.java
│       ├── tools/map/MapFocusTool.java
│       ├── tools/scene/ShowAdsbRadarTool.java
│       ├── tools/weather/OpenMeteoWeatherTool.java
│       └── util/MarkdownStripper.java
│
└── pairion-gateway/                   Spring Boot entry point
    └── src/main/java/com/pairion/gateway/
        ├── PairionServerApplication.java
        ├── config/{WebSocketConfig, ApiKeyRedactionFilter}.java
        ├── rest/{AdapterController, HealthController, HouseholdController,
        │         LogController, MemoryController, SkillController}.java
        ├── startup/ModelStartupService.java
        └── ws/PairionWebSocketHandler.java
```

---

## 3. Build & Dependency Manifest

**Build file:** `pom.xml` (root) + module-level `pom.xml` per module.

### Root-level global dependencies (all modules inherit):
| Dependency | Version | Purpose |
|---|---|---|
| slf4j-api | (Spring Boot managed) | Logging facade |
| junit-jupiter | (Spring Boot managed) | Unit testing |
| assertj-core | (Spring Boot managed) | Fluent assertions |
| mockito-core | (Spring Boot managed) | Mocking |

### pairion-adapters dependencies:
| Dependency | Version | Purpose |
|---|---|---|
| anthropic-java | 2.25.0 | Anthropic Claude SDK (Anthropic LLM adapter) |
| concentus | 1.0.2 | Pure-Java Opus codec (encode/decode audio) |
| spring-context | (managed) | Spring DI (@Component, @ConditionalOnProperty) |
| spring-boot-autoconfigure | (managed) | Conditional bean creation |

### pairion-agent dependencies:
| Dependency | Version | Purpose |
|---|---|---|
| pairion-core | 0.1.0-SNAPSHOT | Domain types |
| pairion-adapters | 0.1.0-SNAPSHOT | Adapter SPIs |
| spring-context | (managed) | DI |
| jackson-databind | (Spring Boot managed) | JSON parsing in tools |

### pairion-gateway dependencies:
| Dependency | Version | Purpose |
|---|---|---|
| spring-boot-starter-web | (managed) | REST controllers, Tomcat |
| spring-boot-starter-websocket | (managed) | WebSocket support |
| jackson-databind | (managed) | JSON serialization |
| archunit-core | 1.3.0 | Architecture enforcement tests |
| All pairion-* modules | 0.1.0-SNAPSHOT | Internal dependencies |

### Build plugins:
| Plugin | Version | Configuration |
|---|---|---|
| maven-compiler-plugin | (managed) | Java 21, --enable-preview, -Xlint:all |
| maven-surefire-plugin | (managed) | --enable-preview, --enable-native-access=ALL-UNNAMED |
| jacoco-maven-plugin | 0.8.12 | 100% LINE + BRANCH coverage enforced; 12 class exclusions for native/HTTP boundaries |
| spotless-maven-plugin | 2.43.0 | Google Java Format (AOSP style), remove unused imports |
| maven-checkstyle-plugin | 3.6.0 (checkstyle 10.21.4) | config/checkstyle/checkstyle.xml; fails on violation |

### Build commands:
```
Build:   mvn compile
Test:    mvn test
Verify:  mvn verify          (includes JaCoCo coverage check + Checkstyle)
Package: mvn package -DskipTests
Run:     mvn spring-boot:run -pl pairion-gateway
```

---

## 4. Configuration & Infrastructure Summary

### Config files:
**`pairion-gateway/src/main/resources/application.yml`**
- Server port: `18789`
- Tomcat WebSocket: max binary message 1 MB, max text message 256 KB
- Spring virtual threads: enabled
- LLM adapter selector: `pairion.adapters.llm` = `openaicompat` (default config; Anthropic is default bean when property absent)
- OpenAI-compatible adapter: `baseUrl=http://localhost:1234/v1`, `model=qwen2.5-14b-instruct-mlx`, `connectTimeoutSeconds=10`, `requestTimeoutSeconds=30`
- Anthropic adapter: model defaults to `claude-sonnet-4-6`, requires `ANTHROPIC_API_KEY` env var
- TTS/Piper voice: `en_GB-alan-medium`, `length-scale=0.85`
- ADS-B home: lat=33.814427, lon=-96.582106, radius=25nm, bbox configured, poll=10s
- ADS-B auth: `${OPENSKY_USERNAME:}` / `${OPENSKY_PASSWORD:}` (optional)
- Logging: root=INFO, `com.pairion`=DEBUG, file at `$HOME/Pairion/logs/pairion.log`, rolling 10MB/7-day

**`pairion-gateway/src/main/resources/logback-spring.xml`**
- Console + rolling file appenders; `com.pairion`=DEBUG, root=INFO
- Rolling policy: 10MB max file, 30-day history, 1GB total cap
- ApiKeyRedactionFilter registered as Logback TurboFilter (redacts `sk-ant-*` patterns before any appender sees them)

### Connection map:
```
Database:       None — all state is in-memory, per WebSocket session
Cache:          None — in-memory only (AdsbEnrichmentService uses ConcurrentHashMap with TTL)
Message Broker: None
External APIs (outbound HTTP):
  - Anthropic API (https://api.anthropic.com) — LLM inference (when llm=anthropic)
  - OpenAI-compatible API (http://localhost:1234/v1 by default) — LLM inference (when llm=openaicompat)
  - OpenSky Network (https://opensky-network.org/api/) — ADS-B state vectors, metadata, routes
  - RainViewer (https://api.rainviewer.com/public/weather-maps.json) — radar tile metadata
  - Open-Meteo geocoding (https://geocoding-api.open-meteo.com/v1/search) — city geocoding
  - Open-Meteo forecast (https://api.open-meteo.com/v1/forecast) — current weather
  - Hugging Face (https://huggingface.co) — Whisper model download on first run
  - Piper model CDN — TTS ONNX model download on first run
Cloud Services:  None
```

### CI/CD:
No `.github/workflows`, `Jenkinsfile`, or `.gitlab-ci.yml` detected in the repository.

---

## 5. Startup & Runtime Behavior

**Entry point:** `com.pairion.gateway.PairionServerApplication` (`@SpringBootApplication(scanBasePackages="com.pairion")`)

**Startup sequence:**
1. Spring Boot starts Tomcat on port 18789 with virtual threads enabled
2. Spring scans all `com.pairion.*` packages and autowires beans
3. `WebSocketConfig` registers `PairionWebSocketHandler` at `/ws/v1` with `setAllowedOrigins("*")`
4. `ModelStartupService` listens for `ApplicationReadyEvent` and then:
   - Spawns virtual thread `model-download-whisper-model` → downloads `ggml-small.en.bin` to `$PAIRION_HOME/models/whisper/` (idempotent, SHA-256 verified)
   - Spawns virtual thread `model-download-piper-model` → downloads `<voice>.onnx` + `.onnx.json` to `$PAIRION_HOME/models/tts/`
5. `AdsbDataAdapter`, `WeatherRadarDataAdapter`, `WeatherCurrentDataAdapter` beans are initialized but idle — polling starts per-session when activated by LLM tool call
6. LLM adapter bean activated conditionally: `AnthropicLlmAdapter` when `pairion.adapters.llm=anthropic` (or absent); `OpenAiCompatLlmAdapter` when `=openaicompat`

**Scheduled tasks / background workers:**
- `ModelStartupService`: one-shot virtual threads at startup for model downloads
- `AdsbDataAdapter`: per-session daemon thread pool (`adsb-poller`) at 10s interval (starts/stops with overlay activation)
- `WeatherRadarDataAdapter`: per-session daemon thread (`weather-radar-poller`) at 300s interval
- `WeatherCurrentDataAdapter`: one-shot virtual thread per request (`weather-current-fetch`)
- `AgentSession.clearScheduler`: single-thread scheduled executor (`map-clear-<sessionId>`) for 2-minute map auto-clear

**Health check:** `GET /v1/health` → `{"status": "healthy"}` (always 200, no dependency checks)

**WebSocket endpoint:** `ws://<host>:18789/ws/v1`

---

## 6. Data Model / Entity Layer

No JPA entities or database-backed models. All domain types are Java 21 records or sealed interfaces. No persistence layer.

---

### WebSocket Protocol Messages (`pairion-core/src/main/java/com/pairion/core/ws/`)

**`WebSocketMessage`** — sealed interface, Jackson polymorphic discriminator `type` field.

Permitted implementations (all are Java records unless noted):

| Type discriminator | Class | Direction | Key fields |
|---|---|---|---|
| `DeviceIdentify` | `DeviceIdentify` | Client→Server | `deviceId: String`, `clientVersion: String` |
| `SessionOpened` | `SessionOpened` | Server→Client | `sessionId: String`, `serverVersion: String` |
| `SessionClosed` | `SessionClosed` | Server→Client | (none) |
| `HeartbeatPing` | `HeartbeatPing` | Client→Server | `timestamp: String` |
| `HeartbeatPong` | `HeartbeatPong` | Server→Client | `timestamp: String` |
| `Error` | `ErrorMessage` | Server→Client | `code: String`, `message: String` |
| `AgentStateChange` | `AgentStateChange` | Server→Client | `state: String` (wire value) |
| `WakeWordDetected` | `WakeWordDetected` | Server→Client | (none) |
| `AudioStreamStart` | `AudioStreamStart` | Both | `streamId: String`, `codec: String`, `sampleRate: int` |
| `SpeechEnded` | `SpeechEnded` | Client→Server | (none) |
| `AudioStreamEnd` | `AudioStreamEnd` | Server→Client | `streamId: String`, `reason: String` |
| `TextMessage` | `TextMessage` | Client→Server | `text: String` |
| `TranscriptPartial` | `TranscriptPartial` | Server→Client | `text: String` |
| `TranscriptFinal` | `TranscriptFinal` | Server→Client | `text: String` |
| `LlmTokenStream` | `LlmTokenStream` | Server→Client | `delta: String` |
| `ToolCallStarted` | `ToolCallStarted` | Server→Client | `toolCallId: String`, `toolName: String`, `input: Map<String,Object>` |
| `ToolCallCompleted` | `ToolCallCompleted` | Server→Client | `toolCallId: String`, `output: Map<String,Object>` |
| `UnderBreathAck` | `UnderBreathAck` | Server→Client | (none) |
| `MapFocus` | `MapFocus` | Server→Client | `lat: double`, `lon: double`, `label: String`, `zoom: String` |
| `MapClear` | `MapClear` | Server→Client | (none) |
| `ConversationEnded` | `ConversationEnded` | Server→Client | (none) |
| `BackgroundChange` | `BackgroundChange` | Server→Client | `backgroundId: String`, `params: Map<String,Object>`, `transition: String` |
| `OverlayAdd` | `OverlayAdd` | Server→Client | `overlayId: String`, `params: Map<String,Object>` |
| `OverlayRemove` | `OverlayRemove` | Server→Client | `overlayId: String` |
| `OverlayClear` | `OverlayClear` | Server→Client | (none) |
| `SceneDataPush` | `SceneDataPush` | Server→Client | `modelId: String`, `data: Object` |

---

### LLM Domain Types (`pairion-core/src/main/java/com/pairion/core/llm/`)

**`LlmRequest`** — record
- `systemPrompt: String`, `userMessage: String`, `toolDefinitions: List<ToolDefinition>`, `model: String` (nullable), `toolCallHistory: List<ToolCallPair>`
- Nested record: `ToolCallPair(toolCallId, toolName, toolInput, toolOutput)`
- Static factory: `LlmRequest.simple(systemPrompt, userMessage)`

**`LlmEvent`** — sealed interface
- `TokenDelta(delta: String)`
- `ToolCallRequest(toolCallId: String, toolName: String, input: Map<String,Object>)`
- `ToolCallResult(toolCallId: String, output: Map<String,Object>)`
- `Stop(outputTokens: int)`

**`LlmCapabilities`** — record: `available: boolean`, `streaming: boolean`, `toolUse: boolean`
- Static factory: `LlmCapabilities.unavailable()`

**`ToolDefinition`** — record: `name: String`, `description: String`, `inputSchema: Map<String,Object>`

---

### STT/TTS Domain Types (`pairion-core/src/main/java/com/pairion/core/stt|tts/`)

**`SttEvent`** — sealed interface: `Partial(text: String)`, `Final(text: String, audioDurationMs: long)`
**`SttCapabilities`** — record: `available: boolean`, `streaming: boolean`; static `unavailable()`
**`TtsEvent`** — sealed interface: `Chunk(audio: byte[], isOpus: boolean)`, `Completed(totalDurationMs: long)`
**`TtsCapabilities`** — record: `available: boolean`, `streaming: boolean`; static `unavailable()`

---

### Data Adapter Models (`pairion-adapters/src/main/java/com/pairion/adapters/data/`)

**`AdsbAircraft`** — record with `@JsonInclude(NON_NULL)`
- Fields: `icao24: String`, `callsign: String`, `lat: Double`, `lon: Double`, `altitudeFt: Double`, `speedKnots: Double`, `trackDeg: Double`, `verticalRateFpm: Double`, `onGround: boolean`, `registration: String`, `aircraftType: String`, `origin: String`, `destination: String`

**`WeatherRadarSnapshot`** — record with `@JsonInclude(NON_NULL)`
- Fields: `host: String`, `frames: List<WeatherRadarFrame>`, `latestPath: String`, `tileSize: int`, `colorScheme: int`, `options: String`

**`WeatherCurrentSnapshot`** — record with `@JsonInclude(NON_NULL)`, imperial units
- Fields: `city: String`, `temperatureF: double`, `feelsLikeF: double`, `highF: double`, `lowF: double`, `humidity: int`, `windSpeedMph: double`, `windDirectionDeg: int`, `conditions: String`, `precipitationIn: double`, `pressureMb: double`

---

### Agent Session Events (`pairion-agent/src/main/java/com/pairion/agent/session/AgentSessionEvent`)

Sealed interface with 17 permitted record types: `StateChangeEvent`, `TranscriptPartialEvent`, `TranscriptFinalEvent`, `LlmTokenEvent`, `ToolCallStartedEvent`, `ToolCallCompletedEvent`, `AudioStreamStartEvent`, `AudioChunkEvent`, `AudioStreamEndEvent`, `MapFocusEvent`, `MapClearEvent`, `ConversationEndedEvent`, `BackgroundChangeEvent`, `OverlayAddEvent`, `OverlayRemoveEvent`, `OverlayClearEvent`, `SceneDataPushEvent`.

---

## 7. Enum / Constant Inventory

**`AgentState`** (`pairion-core/src/main/java/com/pairion/core/agent/AgentState.java`)
- Values: `IDLE("idle")`, `LISTENING("listening")`, `THINKING("thinking")`, `SPEAKING("speaking")`
- Has wire value: YES — `wireValue()` returns the lowercase string for WebSocket messages
- Serialization: custom wire value (not ordinal or name), used in `AgentStateChange` WS message
- Used in: `AgentSession`, `AgentSessionEvent.StateChangeEvent`, `AgentStateChange` WS message

No other enums detected. All other domain states use sealed interface records (LlmEvent, SttEvent, TtsEvent, WebSocketMessage subtypes).

### Key string constants:
- `PairionWebSocketHandler.SERVER_VERSION = "0.3.0"`
- `WebSocketMessage` subtypes each define `TYPE` static String constants (e.g. `SessionOpened.TYPE`)
- `MapFocusTool.TOOL_NAME = "focus_map"`, `SetBackgroundTool.TOOL_NAME = "set_background"`, etc.
- `AdsbDataAdapter.METRES_TO_FEET = 3.28084`, `MS_TO_KNOTS = 1.94384`, `MS_TO_FPM = 196.850`
- `AdsbEnrichmentService.METADATA_TTL_MS = 3600000` (1 hour), `ROUTE_TTL_MS = 1800000` (30 min)
- `AdsbEnrichmentService.METADATA_RATE_LIMIT_MS = 500`, `ROUTE_RATE_LIMIT_MS = 500` (2 req/sec each)
- `WhisperCppSttAdapter.PARTIAL_THROTTLE_MS = 200`
- `PiperTtsAdapter.OPUS_SAMPLE_RATE = 16000`

---

## 8. Data Access / Repository Layer

No repository layer. There is no database. The project uses in-memory data structures only.

Data access is handled at the adapter layer via boundary interfaces:

**`AdsbDataClient`** (interface) — boundary for OpenSky Network HTTP calls
- `fetchStates(lamin, lomin, lamax, lomax): List<List<Object>>` — aircraft state vectors
- `fetchMetadata(icao24): Optional<AircraftMetadata>` — registration + type code
- `fetchRoute(callsign): Optional<RouteInfo>` — departure/destination airports
- Implemented by: `HttpAdsbDataClient` (JaCoCo excluded — makes real HTTP calls)

**`WeatherRadarDataClient`** (interface) — boundary for RainViewer API
- `fetchSnapshot(): WeatherRadarSnapshot`
- Implemented by: `HttpWeatherRadarDataClient` (JaCoCo excluded)

**`WeatherCurrentDataClient`** (interface) — boundary for Open-Meteo API
- `fetchSnapshot(city): WeatherCurrentSnapshot`
- Implemented by: `HttpWeatherCurrentDataClient` (JaCoCo excluded)

**In-memory state:**
- `PairionWebSocketHandler.sessions: ConcurrentHashMap<String, AgentSession>` — active sessions keyed by WS session ID
- `AdsbEnrichmentService.metadataCache: ConcurrentHashMap<String, CachedEntry<Optional<AircraftMetadata>>>` — TTL-cached metadata keyed by icao24
- `AdsbEnrichmentService.routeCache: ConcurrentHashMap<String, CachedEntry<Optional<RouteInfo>>>` — TTL-cached route info keyed by callsign
- `WhisperSttSession.pcmChunks: List<byte[]>` — PCM accumulator per audio stream

---

## 9. Service / Business Logic Layer — Full Method Signatures

---

### `AgentSession` (`pairion-agent/src/main/java/com/pairion/agent/session/AgentSession.java`)

Dependencies: `SttAdapter`, `LlmAdapter`, `TtsAdapter`, `SoulPromptProvider`, `ToolDispatcher`, `AdsbDataAdapter` (nullable), `WeatherRadarDataAdapter` (nullable), `WeatherCurrentDataAdapter` (nullable), `Consumer<AgentSessionEvent>` (event sink)

Public Methods:
- `AgentSession(sessionId, sttAdapter, llmAdapter, ttsAdapter, soulProvider, toolDispatcher, adsbDataAdapter, weatherRadarDataAdapter, weatherCurrentDataAdapter, eventSink)` — constructor
  - Purpose: Initializes per-session agent with all adapter dependencies and a scheduler for map auto-clear.
- `onAudioStreamStart(streamId: String): void`
  - Purpose: Allocates OpusDecoder and STT session, transitions state to LISTENING.
- `onAudioChunk(frameData: byte[]): void`
  - Purpose: Opus-decodes binary frame and feeds PCM to active STT session.
- `onSpeechEnded(): void`
  - Purpose: Records Stage A start time and finalizes STT session, triggering transcript.
- `currentState(): AgentState`
  - Purpose: Returns current agent processing state.
- `close(): void`
  - Purpose: Shuts down map-clear scheduler, stops ADS-B and weather radar polling. Call on WS close.
- `activateDefaultOsmView(): void`
  - Purpose: Emits BackgroundChangeEvent for OSM background centred on DFW (lat=32.86, lon=-97.04, zoom=10). Called on DeviceIdentify.
- `activateAdsbRadar(): void`
  - Purpose: Emits VFR background + ADS-B overlay events and starts AdsbDataAdapter polling.

Package-private methods (for testing):
- `emitTimedMapClear(): void` — emits MapClearEvent; extracted from scheduler lambda for testability
- `handleSttEvent(SttEvent): void` — routes STT events, triggers onTranscriptFinal on Final
- `handleLlmEvent(LlmEvent, StringBuilder, List<LlmEvent.ToolCallRequest>): void` — routes LLM stream events

Private methods:
- `onTranscriptFinal(transcript: String, stageAMs: long): void` — full turn loop: multi-round LLM + tool dispatch + TTS
- `buildToolDefinitions(): List<ToolDefinition>` — constructs the 7 tool definitions
- `synthesizeSpeech(text, stageAMs, stageBMs, stageCMs, stageDMs): void` — TTS + audio frame emission + latency logging
- `emitMapFocus(result: Map<String,Object>): void`
- `emitMapFocusFromWeather(result: Map<String,Object>): void`
- `emitBackgroundChange(result: Map<String,Object>): void`
- `emitOverlayAdd(result: Map<String,Object>): void`
- `emitOverlayRemove(result: Map<String,Object>): void`
- `emitOverlayClear(): void`
- `transitionState(AgentState): void`
- `isMapClearPhrase(transcript: String): boolean`
- `isConversationEndPhrase(transcript: String): boolean`
- `rescheduleClear(): void`, `cancelClear(): void`
- `logLatency(stageAMs, stageBMs, stageCMs, stageDMs, stageEMs, stageFMs, stageTMs): void`
- `logPartialLatency(stageAMs, stageBMs, stageCMs, stageDMs): void`
- `toMs(nanos: long): long` — nanoseconds → milliseconds

---

### `ToolDispatcher` (`pairion-agent/src/main/java/com/pairion/agent/tools/ToolDispatcher.java`)

Dependencies: `List<AgentTool>` (Spring list injection — all AgentTool beans)

Public Methods:
- `ToolDispatcher(tools: List<AgentTool>)`
- `dispatch(toolName: String, input: Map<String,Object>): Map<String,Object>`
  - Purpose: Routes LLM tool call by name to matching AgentTool; returns structured error if unknown.

---

### `AdsbDataAdapter` (`pairion-adapters/src/main/java/com/pairion/adapters/data/adsb/AdsbDataAdapter.java`)

Dependencies: `AdsbDataClient`, `AdsbEnrichmentService`, `@Value` bbox coords + poll interval

Public Methods:
- `startPolling(sink: Consumer<List<AdsbAircraft>>): void` (synchronized) — starts daemon poller at configured interval
- `stopPolling(): void` (synchronized) — cancels poller

Package-private:
- `poll(): void` — fetches states, parses, enriches, delivers to sink
- `parseState(state: List<Object>): AdsbAircraft` — parses OpenSky state vector array

Constants: `METRES_TO_FEET=3.28084`, `MS_TO_KNOTS=1.94384`, `MS_TO_FPM=196.850`

---

### `AdsbEnrichmentService` (`pairion-adapters/src/main/java/com/pairion/adapters/data/adsb/AdsbEnrichmentService.java`)

Dependencies: `AdsbDataClient`, `Clock`

Public Methods:
- `enrich(aircraft: AdsbAircraft): AdsbAircraft`
  - Purpose: Enriches with registration/type (metadata cache, 1h TTL) and origin/destination (route cache, 30min TTL). Rate-limited at 2 req/sec per type. Thread-safe.

Package-private:
- `getCachedMetadata(icao24: String): Optional<AircraftMetadata>`
- `getCachedRoute(callsign: String): Optional<RouteInfo>`
- `acquireMetadataRateLimit(): boolean`
- `acquireRouteRateLimit(): boolean`

---

### `WeatherRadarDataAdapter` (`pairion-adapters/src/main/java/com/pairion/adapters/data/weatherradar/WeatherRadarDataAdapter.java`)

Dependencies: `WeatherRadarDataClient`, `@Value` poll interval (default 300s)

Public Methods:
- `startPolling(sink: Consumer<WeatherRadarSnapshot>): void` (synchronized)
- `stopPolling(): void` (synchronized)

Package-private: `poll(): void`

---

### `WeatherCurrentDataAdapter` (`pairion-adapters/src/main/java/com/pairion/adapters/data/weathercurrent/WeatherCurrentDataAdapter.java`)

Dependencies: `WeatherCurrentDataClient`

Public Methods:
- `start(city: String, sink: Consumer<WeatherCurrentSnapshot>): void`
  - Purpose: Kicks off one-shot virtual thread fetch; does not block caller.

Package-private: `fetch(city: String, sink: Consumer<WeatherCurrentSnapshot>): void`

---

### `ModelStartupService` (`pairion-gateway/src/main/java/com/pairion/gateway/startup/ModelStartupService.java`)

Dependencies: `@Value piperVoice`, `ModelDownloader`

Public Methods:
- `onApplicationReady(): void` — `@EventListener(ApplicationReadyEvent.class)` — schedules model downloads on virtual threads

Package-private:
- `downloadWhisperModel(): void`
- `downloadPiperModel(): void`
- `resolveModelPath(pairionHome: String, category: String, filename: String): Path`

---

### `ModelDownloader` (`pairion-core/src/main/java/com/pairion/core/util/ModelDownloader.java`)

Public Methods:
- `download(url: String, targetPath: Path, expectedSha256: String): boolean`
  - Purpose: Downloads file with SHA-256 verification; idempotent (skips if exists).

Package-private:
- `writeWithProgress(in: InputStream, target: Path, totalBytes: long): void`
- `computeSha256(path: Path): String`
- `getDigest(algorithm: String): MessageDigest`

---

### LLM Adapters

**`AnthropicLlmAdapter`** (`@ConditionalOnProperty(llm=anthropic, matchIfMissing=true)`)
- `name(): String` → "anthropic"
- `capabilities(): LlmCapabilities`
- `generate(request: LlmRequest, eventConsumer: Consumer<LlmEvent>): void`
  - Calls `AnthropicClientWrapper.streamCompletion(...)`, emits TokenDelta/ToolCallRequest/Stop events

**`OpenAiCompatLlmAdapter`** (`@ConditionalOnProperty(llm=openaicompat)`)
- `name(): String` → "openaicompat"
- `capabilities(): LlmCapabilities`
- `generate(request: LlmRequest, eventConsumer: Consumer<LlmEvent>): void`
  - Calls `OpenAiCompatClientWrapper.streamCompletion(...)` with configured baseUrl, apiKey, model

---

### STT Adapter

**`WhisperCppSttAdapter`** (`@ConditionalOnProperty(stt=whispercpp, matchIfMissing=true)` + `@ConditionalOnBean(WhisperCppNative.class)`)
- `name(): String` → "whispercpp"
- `capabilities(): SttCapabilities`
- `createSession(eventConsumer: Consumer<SttEvent>): SttSession` → returns `WhisperSttSession`

Inner class `WhisperSttSession`:
- `feedAudio(pcmData: byte[]): void` — accumulates PCM, throttled partial transcripts (200ms)
- `finalizeStream(): void` — full Whisper transcription → SttEvent.Final
- `pcmToFloat(pcm: byte[]): float[]` — 16-bit LE PCM → float32 [-1,1]

---

### TTS Adapter

**`PiperTtsAdapter`** (`@ConditionalOnProperty(tts=piper, matchIfMissing=true)` + `@ConditionalOnBean(PiperTtsNative.class)`)
- `name(): String` → "piper"
- `capabilities(): TtsCapabilities`
- `speak(text: String, eventConsumer: Consumer<TtsEvent>): void`
  - Synthesizes via Piper native, resamples to 16 kHz, Opus-encodes in 20ms frames (320 samples), emits TtsEvent.Chunk + TtsEvent.Completed

Private: `encodeChunk(...)`, `resample(pcm, fromRate, toRate): byte[]`

---

### SOUL Prompt

**`DefaultSoulPromptProvider`** (`@Component`, implements `SoulPromptProvider`)
- `getSystemPrompt(sessionId: String): String`
  - Returns hardcoded placeholder system prompt (SOUL milestone is deferred). Prompt enforces English-only, no markdown, concise speech, all tool-calling rules.

---

## 10. Controller / Handler / Route Layer — Method Signatures Only

---

### `PairionWebSocketHandler` (`pairion-gateway/src/main/java/com/pairion/gateway/ws/PairionWebSocketHandler.java`)

WebSocket endpoint: `/ws/v1` (raw WebSocket, not STOMP)
Dependencies: `ObjectMapper`, `SttAdapter` (@Nullable), `LlmAdapter`, `TtsAdapter` (@Nullable), `SoulPromptProvider`, `ToolDispatcher`, `AdsbDataAdapter` (@Nullable), `WeatherRadarDataAdapter` (@Nullable), `WeatherCurrentDataAdapter` (@Nullable)

Methods:
- `afterConnectionEstablished(session: WebSocketSession): void` → logs connection
- `handleTextMessage(session, message: TextMessage): void` → deserializes + dispatches to DeviceIdentify/HeartbeatPing/AudioStreamStart/SpeechEnded handlers
- `handleBinaryMessage(session, message: BinaryMessage): void` → routes to `agentSession.onAudioChunk()`
- `afterConnectionClosed(session, status: CloseStatus): void` → removes session, calls `agentSession.close()`
- `handleDeviceIdentify(session, identify: DeviceIdentify): void throws Exception` → creates AgentSession, calls `activateDefaultOsmView()`, sends SessionOpened
- `handleHeartbeatPing(session, ping: HeartbeatPing): void throws Exception` → sends HeartbeatPong
- `handleAudioStreamStart(session, streamStart: AudioStreamStart): void` → delegates to agentSession
- `handleSpeechEnded(session: WebSocketSession): void` → delegates to agentSession
- `sendAgentEvent(session, event: AgentSessionEvent): void` → AudioChunkEvent → binary frame; all others → JSON text frame
- `serializeEvent(event: AgentSessionEvent): String throws Exception` → maps AgentSessionEvent subtypes to WS message JSON

---

### `HealthController` (`pairion-gateway/src/main/java/com/pairion/gateway/rest/HealthController.java`)

Base path: `/v1`
- `getHealth(): ResponseEntity<Map<String,String>>` — `GET /v1/health`
- `getVersion(): ResponseEntity<Map<String,String>>` — `GET /v1/version`

---

### `AdapterController` (`pairion-gateway/src/main/java/com/pairion/gateway/rest/AdapterController.java`)

Base path: `/v1/adapters` — **STUB responses in M0**
- `listAdapters(): ResponseEntity<List<Object>>` — `GET /v1/adapters` → empty list
- `getAdapter(category, name): ResponseEntity<Map<String,Object>>` — `GET /v1/adapters/{category}/{name}` → stub

---

### `HouseholdController` (`pairion-gateway/src/main/java/com/pairion/gateway/rest/HouseholdController.java`)

Base path: `/v1/household` — **STUB responses in M0**
- `getHousehold(): ResponseEntity<Map<String,Object>>` — `GET /v1/household`
- `listUsers(): ResponseEntity<List<Object>>` — `GET /v1/household/users`
- `createUser(body: Map<String,Object>): ResponseEntity<Map<String,Object>>` — `POST /v1/household/users` → 201
- `getUser(userId): ResponseEntity<Map<String,Object>>` — `GET /v1/household/users/{userId}`
- `deleteUser(userId): ResponseEntity<Void>` — `DELETE /v1/household/users/{userId}` → 204

---

### `MemoryController` (`pairion-gateway/src/main/java/com/pairion/gateway/rest/MemoryController.java`)

Base path: `/v1/memory` — **STUB responses in M0**
- `listEpisodes(userId: String, limit: int): ResponseEntity<List<Object>>` — `GET /v1/memory/episodes` → empty list

---

### `SkillController` (`pairion-gateway/src/main/java/com/pairion/gateway/rest/SkillController.java`)

Base path: `/v1/skills` — **STUB responses in M0**
- `listSkills(): ResponseEntity<List<Object>>` — `GET /v1/skills` → empty list
- `getSkill(skillId): ResponseEntity<Map<String,Object>>` — `GET /v1/skills/{skillId}` → stub

---

### `LogController` (`pairion-gateway/src/main/java/com/pairion/gateway/rest/LogController.java`)

Base path: `/v1/logs`
- `postLogs(records: List<Map<String,Object>>): ResponseEntity<Void>` — `POST /v1/logs` → 204; forwards each record through Logback at INFO level

---

## 11. Security Configuration

No Spring Security dependency detected. The server has no authentication or authorization layer.

```
Authentication:    None
Token issuer:      N/A
Password hashing:  N/A

Public endpoints (all unauthenticated):
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

Protected endpoints: None — all endpoints are open

CORS: WebSocket handler registered with setAllowedOrigins("*") — all origins permitted
      No global CORS filter configured for REST endpoints.

CSRF: Disabled (no Spring Security; stateless WebSocket protocol)

Rate limiting: None implemented
```

**Security mitigations present:**
- `ApiKeyRedactionFilter` (Logback TurboFilter): redacts `sk-ant-[A-Za-z0-9_-]+` patterns from all log messages before any appender

---

## 12. Custom Security Components

**`ApiKeyRedactionFilter`** (`pairion-gateway/src/main/java/com/pairion/gateway/config/ApiKeyRedactionFilter.java`)
- Type: Logback TurboFilter (not an HTTP filter)
- Purpose: Prevents Anthropic API key values from appearing in any log appender output
- Pattern matched: `sk-ant-[A-Za-z0-9_-]+`
- Applied to: format string and all message parameters
- Action on match: `FilterReply.DENY` — event blocked from all appenders
- Registered in: `logback-spring.xml` (confirmed via logback config)

No custom HTTP authentication middleware, Spring Security filters, or JWT validators. No `UserLookupService` — the application has no concept of authenticated users at runtime.

---

## 13. Exception / Error Handling

No `@ControllerAdvice` or global exception handler is present. Error handling is localized:

**REST layer:**
- Spring Boot's default `BasicErrorController` handles unhandled exceptions → standard Spring error response format
- No custom error response format is defined
- Controllers return `ResponseEntity` with explicit status codes; no exceptions are thrown

**WebSocket layer:**
- `PairionWebSocketHandler.handleTextMessage()` — Jackson deserialization errors propagate to Spring's WS exception handler (no override)
- `PairionWebSocketHandler.sendAgentEvent()` — try/catch logs `log.error("Failed to send agent event: ...")` and swallows the exception
- `AgentSession.synthesizeSpeech()` — try/catch on TTS, logs error, sets `endReason = "error"`, emits AudioStreamEnd

**Adapter / tool layer:**
- `ToolDispatcher.dispatch()` — try/catch: returns `Map.of("error", "tool_execution_failed", "message", e.getMessage())`; unknown tool returns `Map.of("error", "unknown_tool", ...)`
- `AdsbDataAdapter.poll()` — try/catch logs `log.warn("adsb.poll.error: ...")` and returns silently
- `WeatherRadarDataAdapter.poll()` — same pattern
- `WeatherCurrentDataAdapter.fetch()` — try/catch logs `log.warn("weather.current.fetch.error: ...")` and does NOT call sink

**Standard error response format (Spring Boot default):**
```json
{
  "timestamp": "...",
  "status": 500,
  "error": "Internal Server Error",
  "path": "/v1/..."
}
```

**Gap:** No `@ControllerAdvice` — REST error responses are non-uniform Spring Boot defaults.

---

## 14. Mappers / Data Transformation

No dedicated mapper framework (no MapStruct, no AutoMapper). All data transformation is manual.

**Transformations present:**

- `AdsbDataAdapter.parseState(List<Object>): AdsbAircraft` — OpenSky state vector (positional array) → AdsbAircraft record; unit conversions: metres→feet, m/s→knots, m/s→fpm; coordinates rounded to 5 decimal places
- `PiperTtsAdapter.resample(byte[], fromRate, toRate): byte[]` — linear interpolation PCM resampling from Piper native rate to 16 kHz
- `WhisperSttSession.pcmToFloat(byte[]): float[]` — 16-bit signed LE PCM → float32 normalized to [-1,1]
- `PairionWebSocketHandler.serializeEvent(AgentSessionEvent): String` — AgentSessionEvent sealed type → WS protocol JSON via switch + ObjectMapper.writeValueAsString()
- `MarkdownStripper.strip(String): String` — LLM markdown text → TTS-safe plain prose (regex pipeline: links, headings, blockquotes, bold, italic, backticks, whitespace collapse)

**Jackson configuration:** Default Spring Boot ObjectMapper; `@JsonTypeInfo`/`@JsonSubTypes` on `WebSocketMessage` for polymorphic deserialization; `@JsonInclude(NON_NULL)` on `AdsbAircraft`, `WeatherRadarSnapshot`, `WeatherCurrentSnapshot`

---

## 15. Utility Modules & Shared Components

---

### `MarkdownStripper` (`pairion-agent/src/main/java/com/pairion/agent/util/MarkdownStripper.java`)

Pure utility class (final, no-arg constructor private).

Functions:
- `strip(text: String): String` — strips markdown formatting for TTS synthesis
  - Removes: links `[text](url)→text`, `---`/`***` rules, `#` headings, `>` blockquotes, `**` bold, `*` italic, `` ` `` backticks; collapses whitespace; trims; null/blank → ""
  - Used by: `AgentSession.onTranscriptFinal()`

---

### `ModelDownloader` (`pairion-core/src/main/java/com/pairion/core/util/ModelDownloader.java`)

- `download(url: String, targetPath: Path, expectedSha256: String): boolean`
  - Idempotent (skips if file exists); SHA-256 verified; logs progress at 10% intervals
  - Used by: `ModelStartupService`
- `writeWithProgress(InputStream, Path, long): void`
- `computeSha256(Path): String`
- `getDigest(algorithm: String): MessageDigest`

---

### `NativeLibraryLoader` (`pairion-native-whisper/src/main/java/com/pairion/nativelib/whisper/NativeLibraryLoader.java`)

Loads whisper.cpp native shared library from classpath or `$PAIRION_HOME/lib/`. Used by `DefaultWhisperCppNative`.

---

### `LibraryLoader` (STT + TTS — two copies)

- `pairion-adapters/src/main/java/com/pairion/adapters/stt/whispercpp/LibraryLoader.java`
- `pairion-adapters/src/main/java/com/pairion/adapters/tts/piper/LibraryLoader.java`

Interface boundary injected into `DefaultWhisperCppNative` / `DefaultPiperTtsNative` for testability of library loading logic.

---

### SPI Placeholder Interfaces (no implementations)

| Interface | Package | Status |
|---|---|---|
| `EmbeddingAdapter` | adapters.embedding.spi | SPI only — no implementation |
| `VadAdapter` | adapters.vad.spi | SPI only — no implementation |
| `VectorStoreAdapter` | adapters.vectorstore.spi | SPI only — no implementation |
| `VoiceIdAdapter` | adapters.voiceid.spi | SPI only — no implementation |
| `WakeAdapter` | adapters.wake.spi | SPI only — no implementation |

These are future-milestone SPIs; no beans are registered for them.

---

## 16. Database Schema (Live)

**No database.** The application uses no SQL or NoSQL database. All state is in-memory and session-scoped.

```
Database: None
Persistence: None
Schema: N/A
ORM: None (no Hibernate, no JPA, no Flyway)
```

All domain state is held in:
- `ConcurrentHashMap<String, AgentSession>` — active WS sessions (PairionWebSocketHandler)
- `ConcurrentHashMap` caches in `AdsbEnrichmentService` — TTL-gated metadata and route lookups
- `List<byte[]>` PCM accumulator in `WhisperSttSession` — per audio stream, discarded on finalize

---

## 17. Message Broker Configuration

**No message broker.** No AMQP, Kafka, NATS, SQS, or any other message broker is used. All inter-component communication is synchronous in-process Java method calls or `Consumer<T>` callbacks.

The WebSocket protocol (`/ws/v1`) is the only message-passing mechanism, and it operates directly over Spring WebSocket sessions.

---

## 18. Cache Layer

**No distributed or managed cache.** In-process TTL caching only.

**`AdsbEnrichmentService` — in-memory TTL caches:**
```
Cache: metadataCache (ConcurrentHashMap<String, CachedEntry<Optional<AircraftMetadata>>>)
  Key:    icao24 (hex string)
  TTL:    1 hour (METADATA_TTL_MS = 3600000)
  Write:  on fetchMetadata() success (positive and negative sentinels cached)
  Evict:  TTL expiry on next access (lazy eviction)
  Used by: AdsbEnrichmentService.enrich()

Cache: routeCache (ConcurrentHashMap<String, CachedEntry<Optional<RouteInfo>>>)
  Key:    callsign (trimmed)
  TTL:    30 minutes (ROUTE_TTL_MS = 1800000)
  Write:  on fetchRoute() success
  Evict:  TTL expiry on next access
  Used by: AdsbEnrichmentService.enrich()
```

Both caches grow unboundedly (no maximum size); in practice bounded by airspace traffic during active sessions. No Spring `@Cacheable` or Caffeine/EhCache is used.

---

## 19. Environment Variable Inventory

| Variable | Used In | Default | Required in Prod |
|---|---|---|---|
| `ANTHROPIC_API_KEY` | `DefaultAnthropicClientWrapper` (SDK auto-reads) | (none) | YES (if llm=anthropic) |
| `OPENSKY_USERNAME` | `application.yml` → `HttpAdsbDataClient` | "" (empty) | NO — increases rate limits if set |
| `OPENSKY_PASSWORD` | `application.yml` → `HttpAdsbDataClient` | "" (empty) | NO |
| `PAIRION_HOME` | `ModelStartupService.resolveModelPath()`, `NativeLibraryLoader` | `~/.pairion` | NO — fallback to `~/.pairion` |
| `pairion.adapters.llm` | `application.yml` (Spring property) | `anthropic` (bean default) | NO — defaults to Anthropic |
| `pairion.adapters.openaicompat.baseUrl` | `application.yml` | `http://localhost:1234/v1` | NO |
| `pairion.adapters.openaicompat.model` | `application.yml` | `gpt-4o-mini` | NO |
| `pairion.adapters.openaicompat.apiKey` | `application.yml` | "" | NO |
| `pairion.adapters.llm.anthropic.model` | `AnthropicLlmAdapter @Value` | `claude-sonnet-4-6` | NO |

Note: Spring properties (via `application.yml`) are not environment variables but may be overridden via `SPRING_APPLICATION_JSON` or command-line args. The only true OS-level env vars the application reads directly are `ANTHROPIC_API_KEY`, `OPENSKY_USERNAME`, `OPENSKY_PASSWORD`, and `PAIRION_HOME`.

---

## 20. Service Dependency Map

Pairion Server is a standalone service with no inter-service dependencies. It does not call other internal microservices.

**Outbound external dependencies:**

```
Pairion-Server → External Services
───────────────────────────────────────────────────────
Anthropic API (https://api.anthropic.com)
  Auth: ANTHROPIC_API_KEY env var (Bearer token via SDK)
  Called by: AnthropicLlmAdapter → DefaultAnthropicClientWrapper
  Trigger: each LLM generate() call per turn

OpenAI-compatible API (http://localhost:1234/v1 default)
  Auth: optional API key (pairion.adapters.openaicompat.apiKey)
  Called by: OpenAiCompatLlmAdapter → DefaultOpenAiCompatClientWrapper
  Trigger: each LLM generate() call per turn

OpenSky Network (https://opensky-network.org/api/)
  Auth: optional HTTP Basic (OPENSKY_USERNAME/PASSWORD)
  Endpoints: /states/all (ADS-B), /aircraft/metadata/{icao24}, /routes
  Called by: HttpAdsbDataClient
  Trigger: every 10s when ADS-B overlay is active; metadata/route on enrich

RainViewer API (https://api.rainviewer.com/public/weather-maps.json)
  Auth: none
  Called by: HttpWeatherRadarDataClient
  Trigger: every 300s when weather radar overlay is active

Open-Meteo Geocoding (https://geocoding-api.open-meteo.com/v1/search)
  Auth: none
  Called by: MapFocusTool, OpenMeteoWeatherTool, HttpWeatherCurrentDataClient
  Trigger: on focus_map or get_current_weather tool call; weather_current overlay

Open-Meteo Forecast (https://api.open-meteo.com/v1/forecast)
  Auth: none
  Called by: OpenMeteoWeatherTool, HttpWeatherCurrentDataClient
  Trigger: on get_current_weather tool call; weather_current overlay

Model CDN (Hugging Face / Piper CDN)
  Auth: none
  Called by: ModelDownloader (via ModelStartupService)
  Trigger: startup only if models not present at $PAIRION_HOME/models/
```

**Downstream consumers (clients that call this service):**
- Pairion client application (mobile/desktop) via WebSocket `/ws/v1`
- Any HTTP client via REST `/v1/*`

---

## 21. Known Technical Debt & Issues

### TODO/Placeholder/Stub Scan Results

No `TODO`, `FIXME`, `XXX`, `HACK`, or `TEMPORARY` markers found in production source files.

Placeholder/stub patterns found:

| Issue | Location | Severity | Notes |
|---|---|---|---|
| SOUL prompt is a hardcoded placeholder | `DefaultSoulPromptProvider.java` | High | Class is explicitly named "Placeholder SOUL prompt provider for M1". Full SOUL (memory context, user preferences, persona depth) is a future milestone. The `sessionId` parameter is unused. |
| All household endpoints are stubs | `HouseholdController.java` | Medium | `getHousehold()`, `listUsers()`, `getUser()`, `createUser()`, `deleteUser()` all return synthetic in-memory data with random UUIDs. No persistence. |
| All memory endpoints are stubs | `MemoryController.java` | Medium | `listEpisodes()` returns empty list. No memory storage exists. |
| All skill endpoints are stubs | `SkillController.java` | Medium | `listSkills()`/`getSkill()` return hardcoded placeholder responses. |
| All adapter endpoints are stubs | `AdapterController.java` | Medium | `listAdapters()` returns empty list; `getAdapter()` returns static stub. |
| `pairion-household` module is empty | `pairion-household/` | Medium | Contains only `package-info.java`. No user identity, voice enrollment, or household management implementation. |
| `pairion-memory` module is empty | `pairion-memory/` | Medium | Contains only `package-info.java`. Episodic/semantic memory is a future milestone. |
| `pairion-skills` module is empty | `pairion-skills/` | Medium | Contains only `package-info.java`. MCP client is a future milestone. |
| 5 adapter SPIs have no implementations | `EmbeddingAdapter`, `VadAdapter`, `VectorStoreAdapter`, `VoiceIdAdapter`, `WakeAdapter` | Low | Future milestone SPIs; no beans registered. |
| ADS-B enrichment caches grow unboundedly | `AdsbEnrichmentService` | Low | No max size on `metadataCache` or `routeCache`. Bounded by traffic in practice, but no eviction cap. |
| No `@ControllerAdvice` for REST errors | `pairion-gateway` | Low | Spring Boot default error responses are returned for unhandled exceptions. Error format is not standardized. |
| No authentication on any endpoint | `pairion-gateway` | Low (dev) | No Spring Security. Acceptable in local development; must be addressed before any multi-tenant or production deployment. |
| `HealthController.getVersion()` returns `"development"` for gitCommit | `HealthController.java:41` | Low | Hardcoded string; should be injected from build metadata at package time. |
| `AdsbEnrichmentService` shared between sessions | `pairion-adapters` | Low | Enrichment caches are global (not session-scoped). Multiple simultaneous sessions sharing one ADS-B poller and one enrichment service may cause unexpected interactions. |

---

## 22. Security Vulnerability Scan (Snyk CLI)

**Snyk CLI version:** 1.1303.0
**Scan type:** Open Source dependency vulnerabilities (`snyk test`)
**Target:** Root `pom.xml` (multi-module Maven project)

```
=== SNYK OPEN SOURCE SCAN RESULTS ===
Critical vulnerabilities:  0
High vulnerabilities:      0
Medium vulnerabilities:    0
Low vulnerabilities:       0
Total unique CVEs:         0

Result: PASS — No known vulnerabilities in declared dependencies.
```

**Snyk Code (SAST):** Not run (requires Snyk Code license; not available in this environment).

**Manual security observations from audit:**
- `ANTHROPIC_API_KEY` is never logged (ApiKeyRedactionFilter blocks `sk-ant-*` patterns at Logback level)
- `OPENSKY_USERNAME`/`OPENSKY_PASSWORD` are injected as Spring properties; not referenced in source code except via `@Value` binding; no logging of credentials detected
- No hardcoded secrets found in source files
- WebSocket endpoint allows all origins (`setAllowedOrigins("*")`) — acceptable for local development; restrict in production
- No rate limiting on any endpoint (all open); acceptable for single-household local deployment
- No HTTPS enforcement in configuration — depends on reverse proxy in production
