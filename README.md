# Pairion — Local Voice AI Platform

Pairion is a fully local, privacy-first voice AI platform. A wake-word-triggered pipeline captures speech, transcribes it, reasons over it with a local LLM, and speaks a response — all without sending audio or conversation data to the cloud. The only external calls are optional weather lookups.

---

## Repositories

| Repository | Stack | Role |
|---|---|---|
| [Pairion-Server](https://github.com/Pairion-ai/Pairion-Server) | Java 21, Spring Boot 3.4 | WebSocket gateway, STT, LLM, TTS, tool dispatch |
| [Pairion-Client](https://github.com/Pairion-ai/Pairion-Client) | C++20, Qt 6.5 | Wake detection, VAD, audio capture, playback, QML UI |

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        PAIRION CLIENT                           │
│                    (C++20 / Qt 6.5 / macOS)                     │
│                                                                 │
│  Mic → PairionAudioCapture (16 kHz PCM)                         │
│           │                                                     │
│           ├──▶ OpenWakeWord (3× ONNX)  ──▶ wake signal          │
│           └──▶ Silero VAD (1× ONNX)   ──▶ speech end signal     │
│                                                                 │
│  AudioSessionOrchestrator (state machine)                       │
│    Idle → AwaitingWake → Streaming → Idle                       │
│           │                                                     │
│           └──▶ PairionOpusEncoder → binary frames               │
│                                                                 │
│  PairionAudioPlayback ◀── Opus frames ◀── server TTS            │
└──────────────────────────────┬──────────────────────────────────┘
                               │  ws://localhost:18789/ws/v1
                               │  text:   JSON envelopes
                               │  binary: 4-byte streamId + Opus
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                        PAIRION SERVER                           │
│               (Java 21 / Spring Boot 3.4 / macOS)               │
│                                                                 │
│  PairionWebSocketHandler                                        │
│           │                                                     │
│           ▼                                                     │
│  AgentSession (per-connection turn loop)                        │
│    A. STT  — whisper.cpp (FFM) → TranscriptFinal                │
│    B. LLM  — OpenAI-compat adapter → token stream              │
│    C. Tool — ToolDispatcher → AgentTool impls (optional)        │
│    D. LLM  — second pass with tool results (max 5 rounds)       │
│    E. TTS  — Piper TTS (FFM) → Opus frames → client             │
│                                                                 │
│  REST: /v1/health  /v1/adapters  /v1/household  /v1/logs        │
└─────────────────────────────────────────────────────────────────┘
```

---

## Models & Dependencies

### Client-Side — 4 ONNX Models, All Local

All four models run on the dedicated `InferenceThread` via ONNX Runtime. On first launch they are downloaded from the server, SHA-256 verified, and cached to `~/Library/Application Support/Pairion/models/`.

| Solution | Model File | Purpose | Stage |
|---|---|---|---|
| openWakeWord | `melspectrogram.onnx` | Stage 1: PCM → mel spectrogram | 1 |
| openWakeWord | `embedding_model.onnx` | Stage 2: mel → embedding features | 2 |
| openWakeWord | `hey_jarvis_v0.1.onnx` | Stage 3: embedding → wake word score | 3 |
| Silero VAD v5 | `silero_vad.onnx` | Voice activity detection — speech start/end | — |

**openWakeWord data flow:** Raw PCM accumulates in a sliding buffer → every 1280 samples (80 ms) the mel model runs on the last 1760 samples → mel rows are appended to a rolling buffer → every 76 rows the embedding model produces a 96-element feature vector → embeddings accumulate → every 16 embeddings the classifier produces a wake score. A score above threshold (default 0.3, configurable) fires the wake signal with a pre-roll buffer of raw PCM.

### Server-Side — 3 Models, All Local

| Solution | Model / File | Purpose | Acceleration |
|---|---|---|---|
| whisper.cpp v1.8.4 | `ggml-small.en.bin` (~466 MB) | Speech-to-text, English | Metal (Apple Silicon) |
| LM Studio + Qwen | `qwen2.5-14b-instruct-mlx` | Reasoning, tool dispatch, response generation | MLX (Apple Silicon) |
| Piper TTS | `en_GB-alan-medium` (ONNX + config) | Text-to-speech, British male voice | CPU |

**Native integration:** whisper.cpp and Piper are bundled as native shared libraries (`pairion-native-whisper`, `pairion-native-piper` Maven modules) and called via Java 21 Foreign Function & Memory (FFM) API using `jextract`-generated bindings. No JNI, no JNA.

### Audio Codec

| Library | Purpose | Native dependency |
|---|---|---|
| Concentus (Java) | Opus encode/decode on server | None — pure Java |
| libopus (C) | Opus encode/decode on client | System library via pkg-config |

### External HTTP APIs

Everything runs locally except weather lookups.

| Service | Purpose | Auth required |
|---|---|---|
| Open-Meteo geocoding | City name → lat/lon | None |
| Open-Meteo forecast | lat/lon → current weather | None |

**Full inventory:** 7 models (4 ONNX on client, 3 on server) + 1 codec + 2 external HTTP APIs. No audio or conversation data ever leaves the device.

---

## Server

### Prerequisites

- Java 21+ (FFM API is a JDK 21 feature)
- Maven 3.9+
- LM Studio running on port 1234 with a model loaded
- macOS Apple Silicon (Metal-accelerated whisper.cpp; MLX-accelerated LLM via LM Studio)
- `ggml-small.en.bin` at `~/.pairion/models/whisper/ggml-small.en.bin`

### Build & Run

```bash
# Full build (compiles, tests, enforces 100% coverage, Checkstyle)
mvn clean verify

# Run
mvn -pl pairion-gateway spring-boot:run

# Or build JAR and run directly
mvn clean package -DskipTests
java -jar pairion-gateway/target/pairion-gateway-0.1.0-SNAPSHOT.jar
```

Server starts on port **18789**.

### Configuration

`pairion-gateway/src/main/resources/application.yml`:

```yaml
server:
  port: 18789
  tomcat:
    websocket:
      max-binary-message-buffer-size: 1048576   # 1 MB
      max-text-message-buffer-size: 262144       # 256 KB

spring:
  threads:
    virtual:
      enabled: true   # Java 21 virtual threads

pairion:
  adapters:
    llm: openaicompat              # or 'anthropic'
    openaicompat:
      baseUrl: http://localhost:1234/v1
      model: qwen2.5-14b-instruct-mlx
      apiKey: ""                   # blank for local LM Studio
      connectTimeoutSeconds: 10
      requestTimeoutSeconds: 30
    tts:
      piper:
        voice: en_GB-alan-medium

logging:
  level:
    root: INFO
    com.pairion: DEBUG
  file:
    name: ~/.Pairion/logs/pairion.log
```

**LLM adapter options:**

| `pairion.adapters.llm` | Backend | Notes |
|---|---|---|
| `openaicompat` (default) | LM Studio, Ollama, OpenAI, DeepSeek, etc. | Forces HTTP/1.1 for SSE compatibility |
| `anthropic` | Anthropic Claude API | Requires `ANTHROPIC_API_KEY` env var |

**Environment variables:**

| Variable | Required | Description |
|---|---|---|
| `ANTHROPIC_API_KEY` | No (warns if absent) | Required for `llm: anthropic` only |
| `PAIRION_HOME` | No (default: `~/.pairion`) | Base directory for native libs and models |
| `PAIRION_NATIVE_TESTS` | No | Set to `1` to run integration tests against real whisper.cpp |

### Module Structure

```
pairion-server/
├── pairion-native-whisper   # whisper.cpp v1.8.4, built with Metal, ARM64
├── pairion-native-piper     # Piper TTS native shared library
├── pairion-core             # Domain types: LLM/STT/TTS events, WS message hierarchy (sealed interfaces)
├── pairion-adapters         # Adapter implementations: whisper, piper, openaicompat, anthropic, opus
├── pairion-household        # User identity, voice registry, role enforcement
├── pairion-memory           # Episodic + semantic memory, preference extraction
├── pairion-skills           # MCP client (JSON-RPC over stdio), skill registry
├── pairion-agent            # Turn loop, tool dispatch, SOUL prompt, latency instrumentation
└── pairion-gateway          # Spring Boot entry point, WebSocket handler, REST API
```

### REST API

Base: `http://localhost:18789/v1`

| Method | Path | Purpose |
|---|---|---|
| GET | `/v1/health` | Liveness probe |
| GET | `/v1/version` | Server version |
| GET | `/v1/adapters` | List all registered adapters |
| GET | `/v1/adapters/{category}/{name}` | Adapter detail |
| GET | `/v1/household/users` | List users |
| POST | `/v1/household/users` | Create user |
| GET | `/v1/household/users/{id}` | Get user |
| DELETE | `/v1/household/users/{id}` | Delete user |
| GET | `/v1/skills` | List MCP skills |
| GET | `/v1/skills/{skillId}` | Skill detail |
| GET | `/v1/memory/episodes` | Episodic memory |
| POST | `/v1/logs` | Client log forwarding |

### Testing

```bash
mvn test

# Integration tests against real whisper.cpp + model file
PAIRION_NATIVE_TESTS=1 mvn verify -pl pairion-adapters

# Code formatting
mvn spotless:check
mvn spotless:apply
```

---

## Client

### Prerequisites

- macOS Apple Silicon
- Qt 6.5+
- CMake 3.25+ and Ninja
- libopus (via Homebrew or pkg-config)
- ONNX Runtime C++ SDK

### Build & Run

```bash
cmake --preset macos-arm64-debug
cmake --build build/macos-arm64-debug --target pairion
open build/macos-arm64-debug/pairion.app
```

On first launch the client requests microphone permission and downloads all 4 ONNX models from the server (server must be running).

### Source Structure

```
src/
├── core/        Constants, ONNX session wrapper, model downloader, device identity
├── protocol/    WS message structs, JSON envelope codec, binary frame codec
├── audio/       PairionAudioCapture, PairionAudioPlayback, Opus encoder/decoder, ring buffer
├── wake/        OpenWakewordDetector — 3-stage ONNX pipeline (mel → embedding → classifier)
├── vad/         SileroVad — ONNX-based voice activity detection
├── ws/          PairionWebSocketClient — connect, identify, heartbeat, reconnect w/ backoff
├── pipeline/    AudioSessionOrchestrator — state machine wiring all components
├── state/       ConnectionState — QML-exposed singleton (status, transcript, LLM response)
├── settings/    Settings — QML-exposed singleton (thresholds, audio device)
├── util/        Logger — batched log forwarding to server
└── main.cpp     Dependency wiring, thread setup, QML engine launch
```

### Threading Model

| Thread | Components |
|---|---|
| Main thread | Qt event loop, QML, WebSocket client, audio capture (AVFoundation requirement), orchestrator, playback |
| InferenceThread | OpenWakewordDetector, SileroVad |
| EncoderThread | PairionOpusEncoder |

Cross-thread signals use `Qt::QueuedConnection`. The orchestrator's `m_state` is accessed single-threaded — no locks required.

---

## WebSocket Protocol

**Endpoint:** `ws://localhost:18789/ws/v1`

**Frame types:**
- **Text** — JSON envelopes dispatched on a `type` field
- **Binary** — `[4-byte stream ID][Opus payload]`

### Session Flow

```
Client                                           Server
  │                                                │
  │──▶ DeviceIdentify (deviceId, bearerToken) ─────│
  │◀── SessionOpened (sessionId, serverVersion) ───│
  │                                                │
  │  [heartbeat every 15 s]                        │
  │──▶ HeartbeatPing ──────────────────────────────│
  │◀── HeartbeatPong ──────────────────────────────│
  │                                                │
  │  [wake word fires locally]                     │
  │──▶ WakeWordDetected (score)  ──────────────────│
  │──▶ AudioStreamStart (streamId) ────────────────│
  │──▶ [binary Opus frames] ───────────────────────│ ← STT running
  │──▶ SpeechEnded ────────────────────────────────│
  │──▶ AudioStreamEnd ─────────────────────────────│
  │                                                │
  │◀── TranscriptPartial (text) ───────────────────│
  │◀── TranscriptFinal (text) ─────────────────────│
  │◀── AgentStateChange ("thinking") ──────────────│ ← LLM running
  │◀── LlmTokenStream (delta) ─────────────────────│   (streaming)
  │◀── ToolCallStarted (toolName, input) ──────────│   (if tool used)
  │◀── ToolCallCompleted (result) ─────────────────│
  │◀── AgentStateChange ("speaking") ──────────────│ ← TTS running
  │◀── AudioStreamStart (streamId) ────────────────│
  │◀── [binary Opus frames] ───────────────────────│
  │◀── AudioStreamEnd ─────────────────────────────│
  │◀── AgentStateChange ("idle") ──────────────────│
```

**Reconnection:** Exponential backoff on disconnect — 1 s → 2 s → 4 s → 8 s → 15 s → 30 s (cap).

---

## Tool System

Tools are Spring `@Component` beans implementing the `AgentTool` SPI. The `ToolDispatcher` discovers all implementations at startup and routes LLM tool call requests to the matching bean. Results are appended to the message history and passed back to the LLM; this loop repeats up to 5 rounds per turn.

**Built-in tools:**

| Tool name | Class | Description |
|---|---|---|
| `get_current_weather` | `OpenMeteoWeatherTool` | Temperature (°F), weather code, wind speed for a given city |

---

## Audio Pipeline Detail

### Upstream (client → server)

```
Microphone (AVFoundation / macOS)
  └─▶ PairionAudioCapture: 16 kHz mono 16-bit PCM, 20 ms frames (640 bytes each)
        ├─▶ InferenceThread: OpenWakewordDetector (mel → embedding → classifier)
        ├─▶ InferenceThread: SileroVad (speech start / end)
        └─▶ EncoderThread: PairionOpusEncoder → Opus frames
               └─▶ WebSocket binary: [4-byte streamId][Opus payload]
```

### Downstream (server → client)

```
Piper TTS → PCM → Concentus Opus encoder
  └─▶ WebSocket binary: [4-byte streamId][Opus payload]
        └─▶ PairionAudioPlayback
               └─▶ PairionOpusDecoder: Opus → 48 kHz mono 16-bit PCM
                     └─▶ QAudioSink (5 s software buffer)
                           └─▶ Drain timer: flushes jitter buffer so long
                                           responses play to completion
```

**Loopback prevention:** When the playback engine emits `speakingStateChanged("speaking")`, the `AudioSessionOrchestrator` immediately ends the upstream mic stream, preventing the microphone from picking up speaker output.

---

## Running the Full Stack

```bash
# 1. Start LM Studio with qwen2.5-14b-instruct-mlx loaded on port 1234

# 2. Start Pairion Server
cd Pairion-Server/pairion-gateway
mvn spring-boot:run
# Wait for: "Started PairionServerApplication"

# 3. Launch Pairion Client
open Pairion-Client/build/macos-arm64-debug/pairion.app
```

### Stopping everything

```bash
pkill -x pairion
pkill -f PairionServerApplication
pkill -f pairion-gateway
```

### Logs

| Source | Location |
|---|---|
| Server | `~/.Pairion/logs/pairion.log` + stdout |
| Client | Forwarded to server via `POST /v1/logs`; also shown in in-app debug panel |

---

## Privacy

- No audio leaves the device — wake detection, STT, LLM, and TTS all run locally
- Wake word fires entirely on-device before any network activity
- The only outbound HTTP calls are to Open-Meteo for weather (no account required, no PII sent)
- No telemetry, no analytics, no cloud accounts required

---

## License

Pairion Source-Available License (Non-Commercial)
