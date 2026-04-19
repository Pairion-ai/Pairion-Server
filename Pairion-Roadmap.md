# Pairion Roadmap

**Version:** 2.2
**Scope:** Two repos active (Pairion-Server, Pairion-Client). Node deferred. Pairion-NodeServer deferred to M13+.

## Task Series

- **PS-xxx** — Pairion-Server prompts
- **PC-xxx** — Pairion-Client prompts
- **PX-xxx** — Cross-cutting prompts (rarely needed)

## M0 — Walking Skeleton

- **PS-001** — Server bootstrap. Maven multi-module, Spring Boot, package structure, REST stubs conforming to OpenAPI, WebSocket handler with identify + heartbeat, Anthropic env var detection, 100% coverage, Javadoc, ArchUnit guards.
- **PC-001** — Client bootstrap. Qt 6.6+ CMake project, C++20, QML, package structure per Architecture, `QWebSocket`-based client with reconnect, connection-state UI, mock `QWebSocketServer` harness for tests, 100% coverage, Doxygen-style docs.

## M1 — First Voice

- **PS-002** — Server M1. Anthropic LLM adapter, Piper TTS adapter, whisper.cpp STT adapter (via FFM), weather MCP skill, agent orchestrator with turn loop, latency instrumentation.
- **PC-002** — Client M1. Audio capture via QtMultimedia `QAudioSource`, Opus encode (libopus), openWakeWord + Silero VAD via ONNX Runtime C++ API, playback via `QAudioSink` with jitter buffer, full pipeline orchestrator, basic voice-state HUD, integration tests against mock harness.

## M2 — The HUD

- **PS-003** — Server: OpenAI-compatible LLM adapter (`com.pairion.adapters.llm.openaicompat`) covering OpenAI, xAI, Groq, DeepSeek, Ollama, LM Studio, vLLM, any OpenAI-compatible backend; capability reporting; Settings endpoint for household LLM switching. Plus support for HUD events (minor additions to protocol, any Server-side state needed for HUD reactivity).
- **PC-003** — Client cinematic HUD. QML + Qt Quick Scene Graph, `ShaderEffect` / `MultiEffect` / custom `QSGRenderNode` items, cold-start ritual, sound design, 60 FPS verification via Qt Creator QML Profiler.

## M3 — Memory

- **PS-004** — Server memory subsystem. Episodic SQLite schema, semantic index via embedding adapter + Qdrant/LanceDB, preference extraction, per-user partitioning invariants.
- **PC-004** — Client memory browser UI.

## M4 — Barge-in

- **PS-005** — Server barge-in handling. TTS cancellation, LLM cancellation, interrupted-turn memory logging.
- **PC-005** — Client barge-in. VAD-during-playback, immediate AudioStreamEnd on detection, local under-breath ack cache.

## M5 — Voice ID *(Critical Risk)*

- **PS-006** — SpeechBrain ECAPA-TDNN voice-ID adapter, enrollment flow, classification-before-LLM, Guest detection.
- **PC-006** — Enrollment UI, Household settings UI.

## M6–M8 — Node milestones (deferred)

*Prompts will be written once M5 ships.*

## M9 — Proactive

- **PS-007** — Action queue, triggers, scheduler.
- **PC-007** — Action queue HUD surface.

## M10 — Computer Use

- **PS-008** — Screen-capture adapter, cursor-control adapter, screen-aware skills.
- **PC-008** — REC indicator, per-user opt-in UI, Swift platform channel for ScreenCaptureKit + Accessibility.

## M11 — Skill Authoring

- **PS-009** — Conversational skill-authoring state machine, MCP manifest generator, sandbox test execution.
- **PC-009** — Skill browser UI, authoring flow UI hooks.

## M12 — Launch Polish

- **PX-LAUNCH-001** — GitHub Actions CI for both repos.
- **PX-LAUNCH-002** — `brew install pairion` tap.
- **PX-LAUNCH-003** — Demo video production.

## M13–M15 — Post-Launch Future Work (deferred)

*Prompts will be written once M12 ships.*

- **PNS-xxx** — Pairion-NodeServer series: bootstrap (M13), thin-Node integration via Wyoming protocol (M14), mixed-fleet arbitration (M15). Prefix `PNS-` reserves the namespace now so the task-prefix scheme stays consistent.

## Notes

- Every prompt begins with STOP directive and ends with "Compile, Run, Test, Commit, Push to Github" + report template.
- Prompts never contain code. Claude Code reads source files and produces implementations.
- 100% coverage required every pass. Tests ship with code.
- No CI during development (M0–M11). Local verification only.
