# Changelog

All notable changes to Pairion Server will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.1] - Checkstyle with Javadoc Enforcement

### Added

- Checkstyle Maven plugin bound to `verify` phase across all modules
- `config/checkstyle/checkstyle.xml` with Javadoc enforcement rules (MissingJavadocType, MissingJavadocMethod, JavadocType, JavadocMethod)
- `config/checkstyle/suppressions.xml` suppressing checks on test sources, generated code, and package-info files
- Basic naming conventions (PackageName, TypeName, ConstantName) and import hygiene (AvoidStarImport, UnusedImports, RedundantImport)
- `mvn verify` now fails if any public class or method is missing Javadoc

## [0.1.0] - M0 Walking Skeleton

### Added

- Maven multi-module project structure with seven modules: core, adapters, household, memory, skills, agent, gateway
- Sealed `WebSocketMessage` interface hierarchy with all 18 message types from asyncapi.yaml
- Jackson polymorphic deserialization with `type` field as discriminator
- WebSocket endpoint at `/ws/v1` with DeviceIdentify/SessionOpened and HeartbeatPing/HeartbeatPong flows
- Binary frame acceptance (logged and discarded for M0)
- REST controllers for all 13 endpoints defined in openapi.yaml
- `/v1/health` returns actual `{"status":"healthy"}` response
- Stub responses for all other REST endpoints
- Virtual threads enabled (`spring.threads.virtual.enabled=true`)
- Server listens on port 18789
- WebSocket buffer sizes configured (1 MB binary, 256 KB text)
- SLF4J + Logback with JSON structured output via logstash-logback-encoder
- Logback turbo filter that redacts `sk-ant-*` API key patterns
- `ANTHROPIC_API_KEY` environment variable detection at startup (warn if absent, info if present)
- Adapter SPI interfaces for all eight categories: LLM, TTS, STT, wake, VAD, voice-ID, embedding, vector store
- ArchUnit tests: no vendor SDK outside adapters, no System.out in src/main
- 100% line and branch coverage enforced via JaCoCo
- Spotless with google-java-format (AOSP style)
- Javadoc on every public class, interface, and method
- `package-info.java` for every package
