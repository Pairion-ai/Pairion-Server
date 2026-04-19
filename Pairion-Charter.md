# Pairion Charter

**Version:** 2.2
**Status:** Foundational
**Last updated:** 2026-04-18

---

## 1. What Pairion Is

Pairion is an ambient, voice-first, household-scale AI presence. A family of people speaks; Pairion recognizes who is speaking, remembers them across conversations, and helps them — through a cinematic heads-up display (HUD) on their Mac/PC/phone and, in later milestones, through ambient audio Nodes placed in rooms throughout the house.

It is a single-household, self-hosted, source-available open project. It is not a SaaS product. It runs in your home, talks to you, knows your family, and keeps your data local.

---

## 2. What Pairion Is Not

- Not a messaging bot. Not a chat overlay. Not a browser extension.
- Not a cloud SaaS. There is no central Pairion server. Your data never leaves your house by default.
- Not a voice-assistant-as-SDK for third-party products. Pairion is the product.
- Not single-user. Pairion is household-first from the foundation.

---

## 3. The Five Pillars

### 3.1 Household, Not Account
Pairion is installed into a home, not onto an account. Owner / Member / Minor / Guest are first-class identity types. Every voice heard is classified into one of these roles before any reasoning happens.

### 3.2 Voice First, Cinematic Second, Text Third
Primary interaction is spoken conversation. Secondary surface is a cinematic HUD — a full-screen canvas that feels like the interface from a great sci-fi film, not a corporate SaaS dashboard. Text is tertiary.

### 3.3 Speaker-Aware By Architecture
Every voice interaction is keyed to an identified speaker. Per-user partitioning of memory, preferences, and permissions is a hard invariant at the data layer.

### 3.4 Local-Capable, Adapter-Agnostic, Frontier-Capable
Every model — LLM, TTS, STT, wake-word, VAD, voice-ID, embeddings, vector store — is accessed through an adapter interface. Pairion supports both **frontier cloud LLMs** (Anthropic Claude, OpenAI, xAI, Google) and **local offline LLMs** (Ollama, LM Studio, llama.cpp, any OpenAI-compatible self-hosted endpoint) as first-class citizens. A household can run fully offline with local models, fully frontier-cloud with Claude, or mixed per-user (Mom uses Claude; Dad uses Llama 3.3 locally). Users can swap any adapter.

### 3.5 Self-Hosted Forever
Runs in the household. No forced cloud. No required account. No telemetry.

---

## 4. Signature Experiences

- **Household Presence** — Pairion knows your family and recognizes who is speaking.
- **The Always-There HUD** — Cinematic full-screen surface that wakes and rests.
- **The British Voice** — Default male British RP, warm, a character.
- **Cross-Room Continuity** (M6+) — Follow the user from room to room.
- **The Speaker-Aware Turn** — Two users in one room, distinct memories and permissions.
- **The Cold-Start Ritual** (M2) — First meeting feels like meeting a character.
- **Skill Authoring Through Conversation** (M11) — No YAML, just a conversation.
- **One-Command Install** — `brew install pairion && pairion init`.
- **Under-Breath Acks** (M4) — Interrupt mid-sentence; Pairion says "mm" and yields.
- **The Action Queue** — Visible ordered list of pending proactive behaviors.
- **Guest Handling** — Unfamiliar voice handled distinctly; Owner notified.

---

## 5. Household Model

- **Owner** — Exactly one. Full permissions. Can add/remove Members, configure adapters, approve skills.
- **Member** — Equal peer. Full conversational capabilities. No admin powers.
- **Minor** — Restricted peer. Configured limits (topics, skills, external integrations).
- **Guest** — Unrecognized voice. Transient. Limited. Owner-notified.

Per-user partitioning of memory, preferences, and skill permissions is a **hard invariant** enforced at the data layer.

---

## 6. The Three Surfaces

```
           HOUSEHOLD
┌─────────────────────────────────────────────┐
│                                             │
│   ┌──────────┐     ┌─────────────────────┐ │
│   │  Client  │◀───▶│   PAIRION-SERVER    │ │
│   │  (Qt 6,  │     │   (Java 21 +        │ │
│   │  C++/QML)│     │    Spring Boot)     │ │
│   │  per Mac │     │                     │ │
│   │/PC/Linux │     │   • Gateway         │ │
│   │  HUD     │     │   • User registry   │ │
│   └──────────┘     │   • Voice IDs       │ │
│                    │   • Memory          │ │
│   ┌──────────┐     │   • Agent orchestr. │ │
│   │  Client  │◀───▶│   • Skill registry  │ │
│   │  (Qt 6)  │     │   • Adapter layer   │ │
│   │ per host │     └─────────────────────┘ │
│   │  HUD     │                              │
│   └──────────┘                              │
│                                             │
│   ┌──────────┐  ┌──────────┐  (future)      │
│   │  Mobile  │  │  Mobile  │                │
│   │ (Flutter)│  │ (Flutter)│                │
│   │ iOS/Andr.│  │ iOS/Andr.│                │
│   └──────────┘  └──────────┘                │
│                                             │
│   ┌──────────┐  ┌──────────┐  (later)       │
│   │ Node Pi  │  │ Node Pi  │                │
│   │ C/C++    │  │ C/C++    │                │
│   └──────────┘  └──────────┘                │
│                                             │
└─────────────────────────────────────────────┘
```

**Pairion-Server** — Java 21 + Spring Boot. One JVM process per household. Owns identity, voice, memory, skills, orchestration, adapter configuration.

**Pairion-Client** — Qt 6 (C++ / QML). Native binaries for macOS, Windows, Linux. Cinematic HUD, local wake-word, mic capture, audio playback. Thin — all intelligence on Server.

**Pairion-Mobile** — *Future separate Flutter project for iOS and Android.* Speaks the same WebSocket protocol as the desktop Client. Deferred until Server + desktop Client are mature.

**Pairion-Node** — *Deferred until Server + Client are working properly (M5 complete).* Raspberry Pi, C/C++. Not in scope for current work.

---

## 7. Technology Stack

| Layer | Choice | Rationale |
|---|---|---|
| Server runtime | Java 21 + Spring Boot 3.4+ | Mature enterprise framework; virtual threads; official Anthropic Java SDK |
| Server build | Maven | User's standing convention |
| Server transport | REST (OpenAPI) + raw binary WebSocket (AsyncAPI) | REST for CRUD/config; WebSocket for real-time events and audio streaming |
| WebSocket framework | Spring `WebSocketHandler` (raw, not STOMP) | STOMP adds pub/sub we don't need; raw binary fits audio streaming |
| Concurrency | Java 21 virtual threads | `spring.threads.virtual.enabled=true`; eliminates async/await complexity |
| Client runtime | Qt 6.6+ + C++ 20 + QML (Qt Quick) | Mature desktop-first framework; frame-perfect scene graph; cross-platform via Qt's graphics abstraction; LGPL v3 compatible with Pairion's license |
| Client audio I/O | QtMultimedia (`QAudioSource` + `QAudioSink`) + libopus | Cross-platform, native backends per platform (AVFoundation on Apple, MediaFoundation on Windows, PulseAudio/PipeWire on Linux) |
| Client state | Qt QObject singletons with `Q_PROPERTY` bindings | Native Qt pattern; automatic QML binding via property system |
| Client HUD rendering | QML + Qt Quick Scene Graph + `ShaderEffect` / `MultiEffect` / custom `QSGRenderNode` | Hardware-accelerated via QRhi (Metal / Vulkan / D3D11); 60 FPS trivially achievable |
| Client build | CMake 3.25+ with Qt 6 CMake integration + Conan or vcpkg for non-Qt deps | Qt-standard build system |
| Client secrets | QtKeychain (cross-platform Keychain / Credential Vault / Secret Service wrapper) | LGPL; one abstraction across all three platforms |
| Mobile (future) | Flutter (separate Pairion-Mobile project, deferred) | Better mobile story than Qt open-source; same WebSocket protocol |
| Frontier LLM — default | Anthropic Claude via `com.anthropic:anthropic-java:2.25.x` | Best tool-use; first-party MIT-licensed SDK; streaming; tool calling |
| Frontier LLM — alternatives | OpenAI, xAI (Grok), Google (Gemini), Groq, Together, Fireworks, DeepSeek, any OpenAI-compatible cloud endpoint | Via generic `OpenAICompatibleLLMAdapter` — one adapter, dozens of backends |
| Local LLM — primary path | Ollama (recommended) on localhost via OpenAI-compatible REST API | Zero native-code complexity; trivial install (`brew install ollama`); active development; Apple Silicon accelerated via llama.cpp; covers Llama 3.3, Qwen 2.5, Mistral, Phi, DeepSeek-R1, many more |
| Local LLM — alternatives | LM Studio, llamafile, Jan, LocalAI, vLLM, any OpenAI-compatible self-hosted backend | Same `OpenAICompatibleLLMAdapter` works for all |
| Local LLM — future direct path | llama.cpp via Java 21 FFM (no subprocess) | Future optional adapter for maximum performance / minimum overhead |
| Default TTS | Piper TTS (ONNX) via ONNX Runtime Java binding | Local, fast, natural British voices, Apple Silicon accelerated |
| Default STT | whisper.cpp via Java 21 Foreign Function & Memory (FFM) API | Native C++ on Apple Silicon via Metal; no Python runtime |
| Wake word | openWakeWord ONNX via Java ONNX Runtime (Client-side) | Pre-trained models CC BY-NC-SA (compatible with Pairion) |
| VAD | Silero VAD ONNX via Java ONNX Runtime | Industry standard; tiny; fast |
| Voice ID | SpeechBrain ECAPA-TDNN ONNX via Java ONNX Runtime | Proven; ONNX-exportable |
| Embeddings | `bge-m3` or `nomic-embed-text` ONNX via Java ONNX Runtime | No cloud dependency |
| Vector store | Qdrant (embedded) or LanceDB | Java client first-class |
| Structured memory | SQLite via HikariCP, with per-user schemas | Embedded; zero ops |
| Schema management | Hibernate `hbm2ddl.auto=update` (development); Flyway (production, M12) | Per user's standing convention |
| Secrets (development) | `ANTHROPIC_API_KEY` environment variable | Per user's standing convention |
| Skills | MCP servers, invoked via custom Java JSON-RPC-over-stdio client | Industry standard |
| Logging | SLF4J + Logback structured JSON (Server); Qt `QLoggingCategory` + custom message handler with log-forwarding (Client) | Per CONVENTIONS |
| Testing | JUnit 5 + AssertJ + Mockito (Server); Qt Test + QtQuickTest + mock `QWebSocketServer` harness (Client) | 100% coverage required |
| Docs | Javadoc (Server); Doxygen-style comments (Client) | 100% public-surface coverage |

---

## 8. Adapter Architecture

Every external capability goes through a Java interface in `com.pairion.adapters.*`. No Spring service outside that package may import a vendor SDK. No Client code depends on any specific cloud service.

Adapters implement capability interfaces, register via Spring auto-configuration, can be swapped at runtime. Adapter licenses are surfaced in Settings.

### 8.1 LLM Adapter Families

Pairion ships with two first-party LLM adapters that between them cover virtually the entire LLM ecosystem:

- **`com.pairion.adapters.llm.anthropic`** — Native Anthropic Java SDK. Default. Used for Claude Sonnet / Opus / Haiku.
- **`com.pairion.adapters.llm.openaicompat`** — Generic OpenAI-compatible HTTP adapter. Used for:
  - **Cloud frontier backends:** OpenAI, xAI (Grok), Google (Gemini), Groq, Together AI, Fireworks, DeepSeek, and any cloud backend speaking the OpenAI Chat Completions format
  - **Local offline backends:** Ollama, LM Studio, llamafile, Jan, LocalAI, vLLM, Text Generation Inference, SGLang, and any self-hosted backend speaking the OpenAI Chat Completions format

A single base URL in adapter configuration determines which backend is actually used. Pointing at `https://api.openai.com/v1` calls OpenAI; pointing at `http://localhost:11434/v1` calls Ollama.

### 8.2 Per-User LLM Selection

Once voice-ID is working (M5), each user in the household may be configured with a different LLM adapter. Mom's turns route to Claude; Dad's turns route to Llama 3.3 locally; the teenager's turns route to whatever Minor-safe model the Owner allows.

Pre-M5, LLM selection is household-wide (one default adapter serves all speakers).

### 8.3 Capability Reporting

Every adapter reports its `capabilities()` — supports streaming, supports tool use, supports vision input, max context window, typical first-token latency. The orchestrator uses these to downgrade gracefully when a local model cannot handle the same tool-use patterns a frontier model handles. A local Llama 3.3 7B that doesn't reliably handle tool calls will be routed to simpler single-turn interactions; the HUD explains the adapter's current operating mode in Settings.

### 8.4 Latency Expectations

Frontier cloud LLMs meet the 700ms wake-to-first-TTS-byte target. Local LLMs on M-series hardware do not — first-token latency on a 70B quantized local model can be 1–3 seconds depending on prompt length. Users opting into local LLMs are opting into degraded latency in exchange for privacy and offline capability. The HUD state machine (M2+) tolerates longer `thinking` states when the active adapter reports higher expected latency.

---

## 9. Quality Attributes

| Attribute | Target | Milestone |
|---|---|---|
| Wake-to-first-TTS-byte | < 700 ms on M4 Max | M1 |
| Barge-in interrupt | < 150 ms to under-breath ack | M4 |
| Voice-ID precision | ≥ 99% in real 4-person household | M5 |
| Memory recall | < 50 ms for top-5 semantic matches | M3 |
| HUD frame rate | 60 FPS sustained in all cinematic states | M2 |
| Client cold start | < 2 s to Connected | M1 |
| Server cold start | < 6 s to first WS accept | M0 |

---

## 10. Milestones (summary)

Full detail in `Pairion-Milestones.md`.

- **M0** — Walking skeleton. Both repos build, deploy, connect, run tests. No voice yet.
- **M1** — First Voice. Round-trip voice interaction end-to-end.
- **M2** — The HUD. Cinematic visuals, cold-start ritual, sound design.
- **M3** — Memory. Per-user episodic + semantic memory; preferences.
- **M4** — Barge-in. Interrupt mid-sentence, under-breath acks.
- **M5** — Voice-ID. Speaker identification; Household enforced; Guest handling.
- **M6** — *First Node (deferred).*
- **M7** — *Smart Node / offline (deferred).*
- **M8** — *Multi-Node (deferred).*
- **M9** — Proactive. Action queue, scheduled behaviors.
- **M10** — Computer Use. Screen awareness (opt-in).
- **M11** — Skill Authoring.
- **M12** — Launch. CI, installer, demo, public release.

---

## 11. Governance & Licensing

Source-available, non-commercial. MIT-style with a non-commercial clause. Commercial deployments require a commercial license.

**Model assets may carry their own licenses.** Non-commercial model licenses (CC BY-NC, CC BY-NC-SA) are acceptable as defaults when they align with Pairion's own non-commercial posture. The openWakeWord wake-phrase model ships CC BY-NC-SA 4.0 — compatible with Pairion's license and transparent to users.

### Privacy Invariants (non-negotiable)

- No first-party telemetry
- No account system external to the Household
- No cloud dependency in the default profile
- Adapter calls to external services are transparent and opt-in per profile
- Screen / audio capture requires explicit per-user activation with visible indicator

### The REC Invariant

When capturing audio or screen, a visible indicator on the Client HUD reflects it. No silent capture, ever. Enforced by UI invariant test.

---

## 12. Source of Truth Hierarchy

1. `Pairion-Charter.md` (this file) — product vision
2. `Pairion-Milestones.md` — milestone ladder
3. `openapi.yaml` — REST contract
4. `asyncapi.yaml` — WebSocket contract
5. `Pairion-Server-Architecture.md` / `Pairion-Client-Architecture.md` — per-repo architecture
6. `CONVENTIONS.md` (per repo) — engineering discipline
7. `Pairion-Roadmap.md` — sequenced tasks

Later files must conform to earlier files. If in doubt, earlier wins. If a prompt conflicts, source files win.

---

## 13. Ecosystem and Positioning

Pairion is not the first open-source household voice assistant. Two projects have done meaningful adjacent work, and understanding exactly where Pairion differs from each is essential to getting the product positioning right.

### 13.1 Home Assistant Assist (and Home Assistant Voice Preview Edition)

Home Assistant is the most mature open-source smart-home platform in the world. Their voice pipeline — "Home Assistant Assist" — ships on dedicated open hardware (Home Assistant Voice Preview Edition, $60, ESP32-S3 based, fully open firmware) and speaks a documented protocol called **Wyoming** for voice pipeline components (wake word, VAD, STT, intent, TTS).

The Wyoming ecosystem is real and growing. ESPHome-based DIY voice nodes work. Third-party hardware is emerging. Home Assistant Assist is legitimately local-first and legitimately private.

**Pairion's position relative to Home Assistant Assist:**

Pairion will adopt **Wyoming protocol compatibility** on the Pairion-NodeServer tier (M13+, future work). This means every Home Assistant Voice PE device, every ESPHome-based DIY voice node, and every Wyoming-compatible third-party device in the ecosystem works with Pairion out of the box. Instead of building thin-Node hardware, Pairion benefits from the entire open voice-hardware ecosystem that Home Assistant has catalyzed.

**Where Pairion differentiates from Home Assistant Assist:**

- **Cinematic HUD.** Home Assistant's UI is functional and well-crafted as a smart-home dashboard. It is not emotionally designed. There is no Jarvis-quality interface. Pairion is specifically architected around a full-screen cinematic surface that feels like the interface from a sci-fi film.
- **Persona.** Home Assistant Assist is generic and interchangeable. It responds to commands. Pairion is a character — the British voice, the under-breath acks, the conversational texture are architectural commitments, not cosmetics.
- **Household identity model with voice-ID.** Home Assistant has device-aware zones (this room, that speaker). Pairion has speaker-aware identity with per-user memory partitions (this person, their memories, their preferences, their permissions). Voice-ID is architectural in Pairion; it is not present in Home Assistant Assist.
- **Cold-start ritual, under-breath acks, signature experiences.** Home Assistant focuses on utility — turn the lights on, set a timer. Pairion focuses on presence — the first-time-meeting ritual, the interrupt-and-yield behavior, the ways Pairion acknowledges you as a known person rather than a command source.
- **Skill authoring via conversation.** Home Assistant has YAML automations and blueprint-driven configuration. Pairion has talking to the assistant about what you want it to be able to do, and having it generate the skill.
- **LLM integration as the core reasoning engine.** Home Assistant's LLM integration is recent and bolted on top of their intent-classification infrastructure. Pairion's reasoning is LLM-native from the architecture outward — the intent parser *is* Claude (or the configured LLM), not a pattern matcher that occasionally defers to an LLM.

Pairion and Home Assistant Assist are not competing; they are solving different problems. Home Assistant is a smart-home platform with a voice interface. Pairion is a household AI presence that happens to be able to control the smart home.

### 13.2 OpenClaw

OpenClaw (MIT licensed, actively maintained by Peter Steinberger and team) is a single-user, messaging-channel AI assistant. It pioneered several patterns Pairion adopts: the `SOUL.md` persona file convention, skills-as-directories with `SKILL.md` manifests, the gateway daemon concept.

**Pairion's position relative to OpenClaw:**

Pairion will achieve **100% functional equivalency with OpenClaw** — every meaningful capability OpenClaw provides to its users, Pairion provides to its household. The patterns Pairion adopts (SOUL, SKILL, Gateway) are explicitly credited. Messaging channel integrations (WhatsApp, Slack, iMessage, Discord) are supported as optional skills, off by default.

**Where Pairion differentiates from OpenClaw:**

- **Household vs. account.** OpenClaw is single-user and account-keyed. Pairion is household-first with Owner/Member/Minor/Guest roles.
- **Voice-first vs. text-first.** OpenClaw's primary surface is messaging channels. Pairion's primary surface is ambient voice with a cinematic HUD.
- **Speaker-aware.** OpenClaw has no concept of speaker identification — it's talking to one person through a text channel. Pairion knows who is speaking in every turn.
- **Multi-room ambient presence.** OpenClaw is wherever the user's messaging app is. Pairion is in the house, in every room, as a present entity.

### 13.3 Pairion in One Sentence

**Pairion is the union of Home Assistant Assist's local-first voice pipeline, OpenClaw's agent-and-skill architecture, and a suite of signature experiences (cinematic HUD, speaker-aware household identity, cold-start ritual, under-breath acks, conversational skill authoring, voice-first persona) that neither project provides.**

---

## 14. Project Scope — The Equivalency Baseline

Pairion's scope is defined by a simple rule:

> **Pairion must provide 100% functional equivalency with OpenClaw AND with Home Assistant Assist, plus every signature experience unique to Pairion.**

Any capability OpenClaw has (skills, personas, gateway, messaging-channel integration) Pairion must have. Any capability Home Assistant Assist has (local-first voice pipeline, Wyoming-compatible hardware, smart-home integration) Pairion must have. Any signature experience unique to Pairion (cinematic HUD, speaker-aware identity, cold-start ritual, conversational skill authoring, etc.) is the reason Pairion exists rather than contributing to one of the other projects.

This is not "three products stitched together." Pairion is architecturally coherent from day one — the adapter layer, the household identity model, the voice-first orchestration, the cinematic surface are designed as a single system. The equivalency rule is the scope check: if a feature of OpenClaw or Home Assistant Assist is missing at launch (M12), Pairion is not ready to launch.

---

## 15. Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Voice-ID precision fails 99% at M5 | Medium | High | Pivot plan in Milestones §M5 |
| Java ML ecosystem lags Python for newest models | High | Medium | Adapter layer; wrap native helpers if needed; Piper + whisper.cpp suffice for M1-M5 |
| Java 21 FFM platform quirks | Medium | Low | Encapsulated in single adapter module |
| Flutter mobile parity (when Pairion-Mobile is built) | Low | Low | Flutter's mobile story is mature; this is a future project, not current scope |
| Qt LGPL compliance for commercial deployments | Low | Medium | Qt commercial license obtainable if/when commercial deployments ship; documented in CONVENTIONS |
| Spring Boot 8 KB WebSocket buffer trap | High | Medium | Explicitly configured; documented in CONVENTIONS |
| Wyoming protocol drift (M13+) | Medium | Low | Wyoming is simple and well-documented; NodeServer's adapter layer isolates protocol concerns |
| Local LLM tool-use reliability varies by model | High | Medium | Adapter `capabilities()` method; orchestrator downgrades gracefully; HUD surfaces current adapter mode |
