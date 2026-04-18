/**
 * whisper.cpp STT adapter implementation.
 *
 * <p>Uses Java 21 Foreign Function and Memory (FFM) API to call whisper.cpp directly — no Python,
 * no subprocess. The native library is loaded from {@code $PAIRION_HOME/native/} (defaulting to
 * {@code ~/.pairion/native/}) or the system library path.
 *
 * <p>The STT model ({@code ggml-small.en.bin}) is downloaded on first run with SHA-256 verification
 * and cached to {@code $PAIRION_HOME/models/whisper/}.
 */
package com.pairion.adapters.stt.whispercpp;
