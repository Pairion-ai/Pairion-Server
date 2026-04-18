# pairion-native-whisper

Builds whisper.cpp from source and packages the native library + jextract-generated FFM bindings as a Maven artifact consumed by `pairion-adapters`.

## Pinned Version

| Component | Version | Source |
|---|---|---|
| whisper.cpp | v1.8.4 | `git clone --branch v1.8.4` (SHA: `9386f239401074690479731c1e41683fbbeac557`) |
| jextract | 21-jextract+1-2 | JDK 21 FFM preview (macOS x64, runs under Rosetta on ARM64) |

## Build Flags (macOS ARM64)

| Flag | Value | Justification |
|---|---|---|
| `GGML_METAL` | `ON` | Non-negotiable Metal acceleration on Apple Silicon |
| `BUILD_SHARED_LIBS` | `ON` | Required for FFM `System.load()` at runtime |
| `WHISPER_BUILD_EXAMPLES` | `OFF` | Not needed — only the library |
| `WHISPER_BUILD_TESTS` | `OFF` | Not needed — only the library |
| `CMAKE_BUILD_TYPE` | `Release` | Optimized for inference performance |

## Per-Platform Build Matrix

| Platform | Status | Notes |
|---|---|---|
| macOS ARM64 | **Built** | Primary development target |
| macOS x86_64 | Stub | M12 launch-polish |
| Linux x86_64 | Stub | M12 launch-polish |
| Linux ARM64 | Stub | M12 launch-polish |
| Windows x86_64 | Stub | M12 launch-polish |

## jextract Workflow

1. Install jextract: `./bin/install-jextract.sh`
2. Build whisper.cpp: `mvn generate-resources -pl pairion-native-whisper`
3. Generate bindings: `./bin/regenerate-bindings.sh` (or Maven does this automatically)

Bindings are regenerated on every `mvn clean install` from the pinned whisper.cpp source. They are NOT committed to git — regenerable provenance from pinned SHA + pinned jextract version is the correct model.

## JVM Requirements

Production deployments must include:
```
--enable-preview --enable-native-access=ALL-UNNAMED
```
