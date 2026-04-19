# Pairion-Server Architecture

**Version:** 2.2
**Status:** Foundational
**Stack:** Java 21 + Spring Boot 3.4+ + Maven

---

## 1. Purpose

The Server is the household brain. One JVM process per household. Owns user identity, voice identity, memory, skills, orchestration, and adapter configuration.

## 2. Top-Level Shape

```
Pairion-Server/  (single Maven project, multi-module)
├── pom.xml                         (parent POM)
├── pairion-core/                   (domain types, interfaces, utilities)
├── pairion-adapters/               (adapter implementations — vendor SDK imports live here and nowhere else)
├── pairion-household/              (Household, User, voice registry)
├── pairion-memory/                 (episodic + semantic memory; per-user partitioning)
├── pairion-skills/                 (MCP client, skill registry, skill invoker)
├── pairion-agent/                  (orchestrator, turn loop, SOUL)
├── pairion-gateway/                (REST + WebSocket endpoints; Spring Boot application)
├── openapi.yaml                    (REST contract — source of truth)
├── asyncapi.yaml                   (WebSocket contract — source of truth)
├── Architecture.md                 (this file, in the repo root)
├── CONVENTIONS.md
├── README.md
└── CHANGELOG.md
```

## 3. Packages

- `com.pairion.core` — Domain types, event bus, utilities. No framework annotations.
- `com.pairion.adapters.*` — Adapter implementations. **Only place vendor SDKs may be imported.**
  - `com.pairion.adapters.llm` — LLM adapters. Two first-party implementations cover virtually the entire ecosystem:
    - `com.pairion.adapters.llm.anthropic` — Native Anthropic Java SDK. For Claude.
    - `com.pairion.adapters.llm.openaicompat` — Generic OpenAI-compatible HTTP adapter. Used for **every** backend speaking OpenAI's Chat Completions format, including: OpenAI, xAI (Grok), Google (Gemini), Groq, Together AI, Fireworks, DeepSeek, Ollama (local), LM Studio (local), llamafile (local), Jan (local), LocalAI (local), vLLM (local), Text Generation Inference (local), SGLang (local), and any self-hosted OpenAI-compatible endpoint. The only configuration difference between calling OpenAI and calling local Ollama is the `baseUrl` property.
  - `com.pairion.adapters.tts` — TTS adapters (Piper, etc.)
  - `com.pairion.adapters.stt` — STT adapters (whisper.cpp via FFM, etc.)
  - `com.pairion.adapters.wake` — Wake-word adapters (openWakeWord)
  - `com.pairion.adapters.vad` — VAD adapters (Silero)
  - `com.pairion.adapters.voiceid` — Voice-ID adapters (SpeechBrain ECAPA)
  - `com.pairion.adapters.embedding` — Embedding adapters (bge-m3, nomic-embed-text)
  - `com.pairion.adapters.vectorstore` — Vector store adapters (Qdrant, LanceDB)
- `com.pairion.household` — Household, User, voice registry, role enforcement
- `com.pairion.memory` — Episodic store, semantic store, preference extraction
- `com.pairion.skills` — MCP client (JSON-RPC over stdio), skill registry, skill invoker
- `com.pairion.agent` — Agent orchestrator, turn loop, SOUL prompt management
- `com.pairion.gateway` — REST controllers, WebSocket handlers, Spring Boot entry point

## 4. The Adapter Discipline

Every external capability is a Java interface in `com.pairion.adapters.*.spi`. Implementations are in a subpackage. Only implementation subpackages may import vendor SDKs.

Example:
```java
// com/pairion/adapters/llm/spi/LLMAdapter.java
public interface LLMAdapter {
    Capabilities capabilities();
    Flux<LLMEvent> generate(LLMRequest request);  // streaming via Reactor
}

// com/pairion/adapters/llm/anthropic/AnthropicLLMAdapter.java
@Component
@ConditionalOnProperty(name = "pairion.adapters.llm", havingValue = "anthropic")
public class AnthropicLLMAdapter implements LLMAdapter {
    // ONLY place com.anthropic.* imports may appear
}
```

Build enforcement: an ArchUnit test fails the build if a vendor SDK is imported outside its adapter package.

## 5. Virtual Threads

`spring.threads.virtual.enabled=true` in `application.yml`. Every incoming REST request and every WebSocket message handler runs on a virtual thread. Blocking IO in handlers is acceptable and preferred — no async/await, no Reactor, no callback hell, no thread-pool tuning.

Exception: streaming responses (LLM streaming, audio streaming) use Reactor `Flux` because they are inherently reactive. The adapter layer hides this from most of the codebase.

## 6. WebSocket — Raw Binary

`@Configuration class WebSocketConfig implements WebSocketConfigurer`.

- Handler: `PairionWebSocketHandler extends BinaryWebSocketHandler` (not `TextWebSocketHandler`)
- Text messages (JSON envelopes) carried in `TextMessage` frames
- Audio binary payloads carried in `BinaryMessage` frames
- Buffer sizes **must** be explicitly configured (Tomcat default is 8 KB; inadequate for audio):
  ```yaml
  server:
    tomcat:
      websocket:
        max-binary-message-buffer-size: 1MB
        max-text-message-buffer-size: 256KB
  ```
- Session correlation: every binary frame includes a 4-byte stream ID prefix; routing by stream ID to the correct in-flight agent turn

## 7. Session Lifecycle

1. Client opens WS connection to `/ws/v1`
2. Client sends `DeviceIdentify` (JSON text frame)
3. Server validates device bearer token (Keychain-free in development; env-var-provided secret)
4. Server sends `SessionOpened`
5. Heartbeat loop runs continuously (HeartbeatPing/Pong)
6. When agent turn is active:
   - Client: `WakeWordDetected` → `AudioStreamStart` → streamed `AudioChunkIn` binary → `SpeechEnded`
   - Server: `TranscriptPartial` → `TranscriptFinal` → `AgentStateChange(thinking)` → `LlmTokenStream` → `ToolCallStarted` → `ToolCallCompleted` → `AgentStateChange(speaking)` → `AudioStreamStart` → streamed `AudioChunkOut` binary → `AudioStreamEnd`
7. On disconnect: session closed, in-flight turn cancelled gracefully

## 8. Agent Orchestrator

`com.pairion.agent.AgentOrchestrator` — one instance per active session.

Turn loop:
1. Receive STT stream → emit partial/final transcripts
2. On final transcript: construct LLM prompt (SOUL + memory context + tool catalog + transcript)
3. Stream LLM response; on tool-use request: dispatch to `SkillInvoker`, inject result, continue
4. On LLM completion: stream response to TTS adapter
5. Stream TTS output to Client as binary frames
6. Write turn to episodic memory; extract preferences if any
7. Emit state transitions at every stage

## 9. Security / Secrets

- `ANTHROPIC_API_KEY` environment variable is the sole source of the Anthropic key in development.
- No Keychain integration in development.
- Production (M12): OS-native secret store integration added.
- No secrets in source. No secrets in logs. An SLF4J turbo filter redacts anything matching the pattern `sk-ant-[A-Za-z0-9_-]+`.

## 10. Logging

- SLF4J over Logback
- JSON structured output
- Correlation ID (session ID) propagated via MDC
- Latency instrumentation: every agent-turn stage logs start and end with elapsed-ms
- Log forwarding from Client: Client POSTs to `/v1/logs` periodically; Server forwards to its own logger

## 11. Testing

- JUnit 5 + AssertJ + Mockito
- 100% line and branch coverage (Maven Surefire + JaCoCo; `mvn verify` enforces)
- ArchUnit tests enforce:
  - No vendor SDK import outside adapter packages
  - No `System.out.println` anywhere in `src/main/java`
  - Every public class has Javadoc
- WireMock for HTTP-level adapter tests (Anthropic mocked, Open-Meteo mocked)
- Contract tests for adapters: each adapter satisfies its interface contract against a mock backend

## 12. Relationship to OpenClaw

Pairion adopts several architectural patterns pioneered by OpenClaw (MIT, single-user messaging-channel AI assistant): `SOUL.md` persona convention, `SKILL.md` skill-directory convention, gateway daemon concept. Pairion is not a fork; patterns are independently implemented with credit.

Per Charter §14 (Project Scope), Pairion must provide **100% functional equivalency with OpenClaw** — every capability OpenClaw provides its users, Pairion provides its household. Messaging-channel integrations (WhatsApp, Slack, iMessage, Discord) are supported as optional skills. The Pairion difference is household-scale, speaker-aware, voice-first, cinematic — all layered on top of OpenClaw-equivalent agent-and-skill functionality.

## 13. Future: Pairion-NodeServer and Wyoming Protocol Compatibility

Not in scope for current work. Documented here so the adapter and protocol design anticipates it.

**Pairion-NodeServer** will be a separate Spring Boot service (M13+) that offloads wake-word, VAD, Opus encode/decode, and speaker-ID processing from thin-endpoint hardware. It sits between thin Nodes and Pairion-Server, multiplexing N thin sessions into a single upstream Pairion-AsyncAPI session per active speaker.

**Wyoming protocol compatibility** is a first-class goal for Pairion-NodeServer. The Wyoming protocol — originated by the Home Assistant project — is the de-facto open protocol for thin voice endpoints. By speaking Wyoming on the downstream side, Pairion-NodeServer works out-of-the-box with:

- Home Assistant Voice Preview Edition hardware ($60 retail, ESP32-S3, fully open firmware)
- ESPHome-based DIY voice nodes (ESP32-S3 + microphone + speaker, ~$20 in parts)
- Any third-party Wyoming-compatible hardware

Pairion does not design or build thin-Node hardware. The Home Assistant community has already solved that problem well; Pairion benefits from the entire open voice-hardware ecosystem their work catalyzed.

### Design implications that affect current work

To keep Wyoming-compatibility cheap to add later, current design must respect these invariants:

- **Session IDs are opaque strings**, not tied to specific transport paths
- **Device identification is decoupled from user identification** — NodeServer proxies multiple devices with different speaker identities over a shared upstream session
- **Binary audio frames include a stream ID prefix** (already spec'd in `asyncapi.yaml`) so multiplexing is trivial
- **Speaker-ID adapter is invokable at either tier** — runs in Pairion-Server when no NodeServer is in the path; runs in NodeServer when thin Nodes are deployed. Same adapter interface, different deployment.

These are cheap design choices now. Expensive to retrofit. The current `asyncapi.yaml` and adapter interfaces already satisfy them.

## 14. Relationship to Home Assistant Assist

Per Charter §14 (Project Scope), Pairion must provide **100% functional equivalency with Home Assistant Assist** — every capability Home Assistant's voice pipeline provides (local-first wake/STT/TTS, smart-home integration, Wyoming-compatible hardware support) Pairion provides. The Pairion difference is the cinematic HUD, persona, speaker-aware household identity, cold-start ritual, under-breath acks, conversational skill authoring, and LLM-native reasoning — none of which Home Assistant Assist provides.

Pairion and Home Assistant Assist are not competing; they solve different problems on overlapping infrastructure. Home Assistant is a smart-home platform with a voice interface. Pairion is a household AI presence that controls the smart home as one of many capabilities.
