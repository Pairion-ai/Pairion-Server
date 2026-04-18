# Pairion Charter

**Version:** 2.0
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

### 3.4 Local-Capable, Adapter-Agnostic
Every model — LLM, TTS, STT, wake-word, VAD, voice-ID, embeddings, vector store — is accessed through an adapter interface. Defaults are local where possible; cloud-backed where necessary. Users can swap any adapter.

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
│   │ (Flutter)│     │   (Java 21 +        │ │
│   │  per Mac │     │    Spring Boot)     │ │
│   │  HUD     │     │                     │ │
│   └──────────┘     │   • Gateway         │ │
│                    │   • User registry   │ │
│   ┌──────────┐     │   • Voice IDs       │ │
│   │  Client  │◀───▶│   • Memory         │ │
│   │ (Flutter)│     │   • Agent orchestr.│ │
│   │  per Mac │     │   • Skill registry │ │
│   │  HUD     │     │   • Adapter layer  │ │
│   └──────────┘     └─────────────────────┘ │
│                                             │
│   ┌──────────┐     ┌──────────┐ (later)    │
│   │ Node Pi  │◀───▶│ Node Pi  │            │
│   │ C/C++    │     │ C/C++    │            │
│   └──────────┘     └──────────┘            │
│                                             │
└─────────────────────────────────────────────┘
```

**Pairion-Server** — Java 21 + Spring Boot. One JVM process per household. Owns identity, voice, memory, skills, orchestration, adapter configuration.

**Pairion-Client** — Flutter (Dart). Native binaries per platform (macOS, Windows, Linux, iOS, Android, web). Cinematic HUD, local wake-word, mic capture, audio playback. Thin — all intelligence on Server.

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
| Client runtime | Flutter 3.x (Dart) | Cross-platform; Skia/Impeller-powered rendering; excellent DevTools |
| Client audio I/O | `record` (mic) + `just_audio` (playback) | Cross-platform; native backends per platform |
| Client state | Riverpod | Modern, testable, well-maintained |
| Client HUD rendering | Flutter `CustomPainter` + `flutter_shaders` | Frame-perfect animations via Skia/Impeller |
| Default LLM | Anthropic Claude via `com.anthropic:anthropic-java:2.25.x` | Best tool-use; first-party MIT-licensed SDK |
| Alternative LLMs | OpenAI, xAI, Google, Ollama, LM Studio, any OpenAI-compatible | Via `LLMAdapter` interface |
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
| Logging | SLF4J + Logback structured JSON (Server); Dart `logging` package with log-forwarding (Client) | Per CONVENTIONS |
| Testing | JUnit 5 + AssertJ + Mockito (Server); `flutter_test` + `mocktail` (Client) | 100% coverage required |
| Docs | Javadoc (Server); DartDoc (Client) | 100% public-surface coverage |

---

## 8. Adapter Architecture

Every external capability goes through a Java interface in `com.pairion.adapters.*`. No Spring service outside that package may import a vendor SDK. No Flutter code depends on any specific cloud service.

Adapters implement capability interfaces, register via Spring auto-configuration, can be swapped at runtime (LLM per-user; STT/TTS household-wide). Adapter licenses are surfaced in Settings.

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

## 13. Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Voice-ID precision fails 99% at M5 | Medium | High | Pivot plan in Milestones §M5 |
| Java ML ecosystem lags Python for newest models | High | Medium | Adapter layer; wrap native helpers if needed; Piper + whisper.cpp suffice for M1-M5 |
| Java 21 FFM platform quirks | Medium | Low | Encapsulated in single adapter module |
| Flutter audio macOS rough edges | Low | Medium | `record` is mature; Swift platform channel fallback |
| Spring Boot 8 KB WebSocket buffer trap | High | Medium | Explicitly configured; documented in CONVENTIONS |
