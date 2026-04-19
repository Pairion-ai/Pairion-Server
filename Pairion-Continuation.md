# Pairion Continuation

**Version:** 2.2
**Purpose:** Hand-off document for picking up the project in a new chat session.

---

## 1. What Pairion Is

Ambient, voice-first, household-scale AI presence. Household members speak; Pairion recognizes who is speaking, remembers them, helps them. Cinematic HUD. Self-hosted. Source-available, non-commercial.

**Scope rule:** Pairion = 100% functional equivalency with OpenClaw + 100% functional equivalency with Home Assistant Assist + all of Pairion's unique signature experiences (cinematic HUD, speaker-aware identity with voice-ID, cold-start ritual, under-breath acks, conversational skill authoring, LLM-native reasoning).

Full product vision: `Pairion-Charter.md`.

## 2. Current Stack (v2.2 Architecture)

**Pairion-Server:** Java 21 + Spring Boot 3.4+ + Maven. WebSocket raw binary. Virtual threads.
**Pairion-Client:** Qt 6.6+ + C++ 20 + QML (Qt Quick). Targeting macOS, Windows, Linux.
**Pairion-Mobile:** *Future separate Flutter project, iOS + Android, speaks same WebSocket protocol.* Deferred.
**Pairion-Node:** Deferred until M5 complete. Will be C/C++ on Raspberry Pi (capable tier). Thin tier (ESP32) via Wyoming protocol through future Pairion-NodeServer.
**Pairion-NodeServer:** *Future separate Spring Boot service (M13+).* Wyoming-protocol compatible on downstream side; supports Home Assistant Voice PE and any Wyoming-compatible hardware out of the box.

**Prior stack was Node/TypeScript + Rust/Tauri + Rust. Rejected on 2026-04-18 due to:**
- Ecosystem friction (release-candidate crates, Tauri v2 lifecycle quirks)
- Debug-ability issues (DevTools disabled by default in Tauri builds)
- User has 12+ successful projects on Java + Flutter-family stacks; Rust was unfamiliar territory

**v1 architecture archived** — old repos deleted, fresh start on 2026-04-18.

## 3. Decision Log (v2.0)

- **Frontier LLM:** Anthropic Claude via `com.anthropic:anthropic-java:2.25.x` (MIT) — default
- **LLM adapter families:** Two first-party adapters — `anthropic` (native SDK) and `openaicompat` (generic OpenAI-compatible HTTP). The `openaicompat` adapter covers OpenAI, xAI, Google, Groq, DeepSeek, Ollama (local), LM Studio (local), llamafile (local), vLLM (local), and any OpenAI-compatible backend. Users can run fully offline with local LLMs, fully frontier-cloud, or mixed per-user post-M5.
- **STT:** whisper.cpp via Java 21 Foreign Function & Memory API
- **TTS:** Piper TTS via ONNX Runtime Java binding (British voice default)
- **Wake word (Client):** openWakeWord ONNX via Java ONNX Runtime; pre-trained "hey jarvis" model as M1 placeholder
- **VAD (Client):** Silero VAD ONNX
- **Voice ID (M5):** SpeechBrain ECAPA-TDNN ONNX
- **Embeddings:** bge-m3 or nomic-embed-text via ONNX
- **Vector store:** Qdrant (embedded) or LanceDB
- **Memory structured:** SQLite via HikariCP, per-user schemas, Hibernate (development), Flyway (M12 production)
- **Secrets (development):** `ANTHROPIC_API_KEY` env var only (no Keychain)
- **CI:** None during development; added at M12
- **WebSocket:** raw `BinaryWebSocketHandler` (not STOMP); Tomcat buffer sizes must be explicitly configured (default 8KB is too small)
- **Wyoming protocol compatibility:** Committed as first-class goal for future Pairion-NodeServer. Pairion does not design or build thin-Node hardware — supports existing Home Assistant Voice PE and ESPHome-based DIY nodes out of the box.
- **Positioning:** Pairion is NOT first mover in open-source household voice AI. Home Assistant Assist exists; OpenClaw exists. Pairion is the union of both plus signature experiences neither provides. The scope rule (100% equivalency with both + signature experiences) is binding.

## 4. Milestone Position (as of session end 2026-04-18)

M0 → starting from scratch with v2.0 stack. Both PS-001 and PC-001 delivered.

Previous v1 project reached M1 code-complete (after 4 Client prompts) before stack switch.

## 5. Foundational Documents

All in repo roots (Pairion-Server/ or Pairion-Client/ as applicable):
- `Pairion-Charter.md` (lives in Server repo; referenced by both)
- `Pairion-Milestones.md` (Server repo)
- `Pairion-Roadmap.md` (Server repo)
- `openapi.yaml` (Server repo)
- `asyncapi.yaml` (Server repo)
- `Architecture.md` (each repo has its own)
- `CONVENTIONS.md` (each repo has its own)

## 6. User

- GitHub: `aallard`
- Machine: MacBook Pro M4 Max, 48 GB
- Location: Dallas, Texas
- Standing preferences: Maven not Gradle; Hibernate in development, Flyway in production; env vars not Keychain in development; minimal password complexity in development; 100% coverage mandatory; 100% AI-generated code; no CI during development phase; prompts are .md files; prompts never contain code

## 7. Naming Convention

Short names for conventional files one-per-repo: `Architecture.md`, `CONVENTIONS.md`, `README.md`, `CHANGELOG.md`, `openapi.yaml`, `asyncapi.yaml`.

Prefixed names for cross-repo discoverable docs: `Pairion-Charter.md`, `Pairion-Milestones.md`, `Pairion-Roadmap.md`, `Pairion-Continuation.md`.

## 8. Honest Notes

This is version 2 of the project. Version 1 ran aground on stack friction and multiple remediation cycles on the Client. Explicit user directive on 2026-04-18: *"Going forward, you will not longer be giving me anything other than what I ask for."* Obey literally.

Priority list going forward:
- Claude verifies before asserting (research-before-prompt is now standing practice)
- Claude does not propose options when the user has given a directive
- Claude does not ask ranges of questions when one will do
- Prompts are tight, scoped, honest about what the agent can and cannot verify from its context
- When a bug appears, it gets a small targeted fix prompt, not a restart

## 9. Immediate Next Step

Run PS-001 and PC-001 in Claude Code. Both are M0 walking-skeleton prompts. Server goes first (Client's WebSocket integration tests need Server to exist).
