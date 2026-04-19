# Pairion Milestones

**Version:** 2.2
**Status:** Foundational

The milestone ladder. Each milestone has a goal, deliverables, acceptance criteria, and a demo brief (what a user would see). Milestones are sequenced; later ones assume earlier ones.

**Node milestones (M6–M8) are deferred until M5 completes.** This document lists them but we do not build them now.

**Post-launch work (M13–M15) — Pairion-NodeServer with Wyoming protocol compatibility — is deferred until M12 completes.** Documented at the end of this file so architectural decisions during M0–M12 anticipate it.

---

## M0 — Walking Skeleton

**Goal:** Both repos exist, build, deploy, run tests, and a Client can establish a WebSocket session with a Server that acknowledges it. Nothing voice-related yet.

**Deliverables:**

- `Pairion-Server` scaffolded: Maven project, Spring Boot 3.4+, Java 21, package structure per Architecture §3
- `Pairion-Client` scaffolded: Qt 6.6+ CMake project, C++20, QML, package structure per Architecture §2
- REST endpoints that conform to `openapi.yaml` — all return stub responses (201 Created or 501 Not Implemented as appropriate)
- WebSocket endpoint at `/ws/v1` accepts a connection, negotiates identify (DeviceIdentify → SessionOpened), handles heartbeat (HeartbeatPing ↔ HeartbeatPong)
- Client's state machine: Connecting → Connected → Disconnected → Reconnecting (exponential backoff)
- Structured logging both sides (SLF4J+Logback Server; Qt `QLoggingCategory` with custom message handler Client)
- `ANTHROPIC_API_KEY` env var read at Server startup; warning if absent (not fatal in M0)
- 100% test coverage on all public classes/methods
- Both repos on `main` branch on GitHub, committed, pushed, no open pre-commit issues
- Server cold-start < 6 s

**Acceptance criteria:**

- Server: `mvn clean verify` passes with 100% coverage, Javadoc on every public class + method
- Client: `cmake --build` compiles with zero warnings at strict flags; `ctest` passes; coverage is 100% line on `src/`; Doxygen-style docs on every public class + method
- Running `mvn spring-boot:run` starts the Server listening on port 18789
- Running the built Qt binary (via `cmake --build --preset macos-debug` then launching the bundle) opens the Client which connects and shows "Connected"
- The Client window shows a debug-state panel with connection status, server version, device ID
- Both repos have `README.md`, `CHANGELOG.md`, `CONVENTIONS.md`, `Architecture.md` at the root

**Demo brief:** User opens Pairion Client. Window shows "Connected". That's it. No voice yet. But it's the foundation everything else stands on.

---

## M1 — First Voice

**Goal:** Full round-trip voice interaction. User says *"Hey Jarvis, what's the weather in Dallas?"*; Pairion replies in a British voice. Wake-to-first-TTS-byte under 700 ms on M4 Max.

**Deliverables:**

**Server:**
- Anthropic LLM adapter (streaming, tool use) using the official Java SDK
- Piper TTS adapter using ONNX Runtime Java binding; British male voice (specific voice chosen at implementation, e.g., `en_GB-alan-medium`)
- whisper.cpp STT adapter using Java 21 FFM API (native C++ via Metal on Apple Silicon)
- Weather MCP skill bundled; invokes Open-Meteo (no API key)
- Agent orchestrator that loops: STT → LLM (with tools) → TTS, streaming both directions
- Latency instrumentation at every stage (wake.detected, stt.first_partial, stt.final, llm.first_token, llm.done, tts.first_chunk, playback.first)

**Client:**
- Microphone capture via QtMultimedia `QAudioSource`; 16 kHz mono PCM
- Opus encoding of captured audio for transmission (libopus via CMake dependency)
- openWakeWord integration via ONNX Runtime C++ API — "Hey Jarvis" placeholder model
- Silero VAD integration — 800 ms silence triggers end-of-speech
- Full WebSocket pipeline: wake → AudioStreamStart → streamed AudioChunkIn binary frames → SpeechEnded
- Inbound: TranscriptPartial/Final rendering, AgentStateChange updates, AudioChunkOut binary frames decoded and played
- Audio playback via QtMultimedia `QAudioSink` with a jitter buffer (40-60 ms target)
- Voice state indicator in HUD (idle / listening / thinking / speaking) via QML bound to C++ state singleton

**Acceptance criteria:**

- End-to-end: saying "Hey Jarvis, what's the weather in Dallas?" produces an audible British-voice reply with correct forecast, round-trip < 700 ms on M4 Max
- Every model download is SHA-256 verified on first run; expected hashes committed
- Both sides 100% test coverage
- 30-60 second demo video recorded to `docs/demos/m1-first-voice.mov` showing full round-trip

**Demo brief:** User says the phrase. Pairion replies in a British voice with the forecast. Under a second, end to end.

---

## M2 — The HUD

**Goal:** Pairion becomes visually alive. Cinematic full-screen canvas. Cold-start ritual. Sound design.

**Deliverables:**

**Server:**
- `com.pairion.adapters.llm.openaicompat` — generic OpenAI-compatible HTTP adapter. One adapter covering OpenAI, xAI, Groq, DeepSeek, Google Gemini (via OpenAI compatibility layer), Ollama, LM Studio, llamafile, Jan, LocalAI, vLLM, and any other OpenAI-compatible backend. Streaming, tool use, capability reporting (some local models can't do tool use reliably; adapter reports this).
- Settings UI endpoint to select active LLM adapter household-wide (per-user LLM routing lands at M5 when voice-ID exists)
- Contract tests against mock OpenAI-compatible server covering happy path, tool use, streaming cancellation, error handling

**Client:**
- Full-screen Qt application with cinematic canvas rendering via QML + Qt Quick Scene Graph; custom `ShaderEffect` / `MultiEffect` / `QSGRenderNode` items for complex shader work
- Distinct visual states: Resting, Listening (wake fired), Thinking, Speaking, Error
- 60 FPS sustained in all states on M4 Max
- Cold-start ritual animation (first-time-ever activation)
- Sound design: wake chime, completion sigh, error tone
- Transcript strip renders live partials and final
- HUD states are driven by C++ state singletons exposed to QML via `Q_PROPERTY`; state-transition logic unit-tested via Qt Test

**Acceptance criteria:**

- 60 FPS sustained (monitored via `QSG_RENDER_TIMING=1` and Qt Creator's QML Profiler)
- Cold-start ritual plays exactly once; subsequent launches skip it
- HUD state transitions tested as pure functions
- OpenAI-compatible adapter works against at least three backends verified in integration tests: local Ollama (Llama 3.3), OpenAI production API, and one other cloud backend (xAI, Groq, or DeepSeek)
- Household LLM switch in Settings functional: user can change from Claude (default) to Ollama-local and back without restart
- 30-60 second demo video showing cinematic HUD in action AND a separate clip showing LLM switching mid-session

**Demo brief:** User watches Pairion visually transition through listening, thinking, speaking — cinematic, frame-perfect, feels alive.

---

## M3 — Memory

**Goal:** Per-user episodic and semantic memory. Pairion remembers what you said yesterday.

**Deliverables:**

- Episodic memory: every agent turn is written to a per-user SQLite table with timestamp, transcript, LLM response, tool calls
- Semantic memory: each episode embedded via bge-m3 (local, ONNX) and indexed in Qdrant (embedded) or LanceDB
- Memory retrieval: on each LLM call, top-K semantically-relevant episodes injected into system prompt
- Preference capture: explicit user statements ("I prefer Celsius", "I drink my coffee black") extracted and stored as structured preferences
- Memory browser UI in Client (view, search, delete)
- Per-user partitioning enforced at data layer (no cross-user reads without auth)

**Acceptance criteria:**

- Memory recall latency < 50 ms for top-5 matches
- Cross-user isolation enforced by integration test
- Per-user partitioning invariant test passes
- 30-60 second demo showing memory recall across sessions

**Demo brief:** User says "I prefer Celsius." Next session, Pairion uses Celsius without being asked.

---

## M4 — Barge-in

**Goal:** Interrupt Pairion mid-sentence. Under-breath acknowledgements. < 150 ms response.

**Deliverables:**

- Client-side barge-in detection: VAD fires while TTS is playing → immediate AudioStreamEnd with reason=interrupted
- Server-side cancellation: TTS synthesis stopped mid-stream; LLM generation cancelled if in progress
- Under-breath ack library: Pairion says "mm", "sure", "alright" from a local cache < 150 ms after detected barge-in
- LLM context update: the interrupted turn is logged as "user interrupted at X characters"

**Acceptance criteria:**

- Barge-in latency measured and asserted < 150 ms wake-to-ack
- Interrupted turns correctly logged in episodic memory
- 30-60 second demo showing successful interrupt and continuation

**Demo brief:** Pairion starts explaining something long. User says "stop, actually—". Pairion says "mm" and yields within 150 ms.

---

## M5 — Voice ID *(Critical Risk)*

**Goal:** Pairion recognizes who is speaking. Owner / Member / Minor / Guest classification at ≥ 99% precision.

**Deliverables:**

- Voice enrollment flow: Cold-start ritual includes capturing 3-5 reference samples
- SpeechBrain ECAPA-TDNN (ONNX, Java binding) voice-ID adapter
- Speaker classification on every turn before LLM invocation
- Per-user memory partitioning honored end-to-end (turn is written to speaker's partition)
- Guest detection: unfamiliar voice → "Guest-1" session, Owner notified
- Settings UI for managing Household members

**Acceptance criteria:**

- Precision ≥ 99% in a real 4-person household test
- **If precision fails:** documented pivot plan (PIN-per-session, push-to-identify, or restricted capability mode) triggers and M5 ships with degraded but functional voice-ID
- Cross-user memory isolation verified by integration tests

**Demo brief:** Three family members take turns asking Pairion questions. Pairion addresses each by name, keeps memories distinct.

---

## M6-M8 — Node Milestones (DEFERRED)

Deferred until M5 complete. Brief descriptions only:

- **M6** — First Node (Raspberry Pi, C/C++, in-room ambient presence)
- **M7** — Smart Node (Pi AI HAT+ 2, offline degraded mode)
- **M8** — Multi-Node (arbitration, cross-room continuity)

Not in scope for current work.

---

## M9 — Proactive

**Goal:** Pairion initiates conversations at appropriate moments. Action queue is visible.

**Deliverables:**

- Action queue: ordered list of pending proactive behaviors
- Trigger types: time-based (scheduled), context-based (user arrived home), skill-based (calendar reminders)
- HUD surface for action queue (visible, interactive)
- User can reorder, approve, reject

**Acceptance criteria:**

- Proactive triggers fire on schedule / event; tested
- Queue operations (add, remove, reorder, approve, reject) all tested
- 30-60 second demo showing scheduled proactive nudge

---

## M10 — Computer Use

**Goal:** Pairion can see the user's screen and control the cursor (opt-in, per-user, with visible REC indicator).

**Deliverables:**

- Screen-capture adapter using macOS `ScreenCaptureKit` (integrated from C++ Client via Objective-C++ bridge file or macOS-specific Qt plugin)
- Cursor-control adapter via Accessibility API
- Claude Agent tools for screen reasoning + cursor control
- Opt-in per-user; enforced with REC indicator invariant
- Skills that use these tools (e.g., "summarize what's on my screen")

**Acceptance criteria:**

- REC indicator always visible when capturing (invariant test)
- Opt-out immediately stops capture
- Privacy review passed (no data leaves device unless user explicitly invokes cloud skill)

---

## M11 — Skill Authoring

**Goal:** User creates a new skill by talking to Pairion.

**Deliverables:**

- Skill-authoring conversational flow state machine
- Skills generated conform to MCP manifest format
- Sandbox test execution (exact sandbox pattern decided at implementation)
- Installed skills auto-registered with user's permission

**Acceptance criteria:**

- User can create, test, and install a working skill entirely through voice
- Sandbox prevents destructive operations during test

---

## M12 — Launch Polish

**Goal:** Pairion is publicly releasable. CI, installer, demo videos, docs, licensing all in place.

**Deliverables:**

- GitHub Actions CI for both repos
- `brew install pairion` tap
- `pairion init` setup wizard
- Demo video (90 seconds, polished) for the README
- Full public documentation
- Commercial licensing process documented

---

## M13+ — Post-Launch Future Work

Documented here so architectural decisions during M0–M12 anticipate this scope. Not committed work.

**M13 — Pairion-NodeServer bootstrap.** Separate Spring Boot service; downstream side speaks Wyoming protocol (Home Assistant voice pipeline protocol); upstream side speaks Pairion AsyncAPI to Pairion-Server. Enables Home Assistant Voice Preview Edition hardware and ESPHome-based DIY voice nodes to work with Pairion out of the box.

**M14 — First thin Node through NodeServer.** Verify Home Assistant Voice PE hardware connects, wake-word fires locally on the ESP32-S3, audio streams to NodeServer, NodeServer forwards to Pairion-Server, round-trip completes.

**M15 — Mixed fleet.** Capable Nodes (Pi 5 + AI HAT+) and thin Nodes (ESP32-S3 via NodeServer) coexist in the same household. Arbitration selects which endpoint answers. Cross-room continuity works regardless of endpoint tier.

---

## Deferred Scope Reminders

- **Node work (M6-M8)** parked until M5 complete and M5 demo recorded.
- **Pairion-NodeServer work (M13-M15)** parked until M12 launch complete.
- **Non-macOS Client platforms** land as natural Qt CMake cross-compile targets; acceptance at each milestone is macOS-only until M12 launch polish.
