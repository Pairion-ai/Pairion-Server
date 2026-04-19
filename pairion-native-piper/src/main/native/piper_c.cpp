/**
 * piper_c.cpp — C wrapper implementation for the Piper TTS C++ API.
 *
 * Routes calls through the piper:: namespace. Compiled as part of the Piper
 * source tree so that it links against the same piper C++ translation units
 * without requiring a separate shared library build.
 *
 * Thread safety: piper_initialize / piper_terminate must be called from a
 * single thread. piper_synthesize may be called concurrently from different
 * threads if each call uses a different voice_handle.
 */

#include "piper_c.h"

#include <cassert>
#include <memory>
#include <vector>

// Piper public C++ header — located at src/cpp/piper.hpp in the piper repo.
// The CMake build ensures this is on the include path.
#include "piper.hpp"

// ── Module-level PiperConfig ─────────────────────────────────────────────────
// A single global PiperConfig owns eSpeak state; one instance per process.
static piper::PiperConfig g_piperConfig;
static bool g_initialized = false;

extern "C" {

int piper_initialize(const char* espeak_data_path) {
    if (g_initialized) return 0;
    g_piperConfig.eSpeakDataPath = std::string(espeak_data_path);
    try {
        piper::initialize(g_piperConfig);
        g_initialized = true;
        return 0;
    } catch (...) {
        return -1;
    }
}

piper_voice_handle_t piper_load_voice(
    const char* model_path,
    const char* model_config_path) {
    if (!g_initialized) return nullptr;
    try {
        auto* voice = new piper::Voice();
        std::optional<piper::SpeakerId> speakerId;
        piper::loadVoice(
            g_piperConfig,
            std::string(model_path),
            std::string(model_config_path),
            *voice,
            speakerId);
        return static_cast<piper_voice_handle_t>(voice);
    } catch (...) {
        return nullptr;
    }
}

int piper_synthesize(
    piper_voice_handle_t voice_handle,
    const char* text,
    piper_audio_callback_t callback,
    void* user_data) {
    if (!voice_handle || !text || !callback) return -1;
    auto* voice = static_cast<piper::Voice*>(voice_handle);
    try {
        std::vector<int16_t> audioBuffer;
        piper::SynthesisResult result;
        piper::textToAudio(
            g_piperConfig,
            *voice,
            std::string(text),
            audioBuffer,
            result,
            [&]() {
                // Called per sentence with samples accumulated so far
                if (!audioBuffer.empty()) {
                    callback(audioBuffer.data(),
                             audioBuffer.size(),
                             user_data);
                    audioBuffer.clear();
                }
            });
        // Flush any remaining samples after the last sentence
        if (!audioBuffer.empty()) {
            callback(audioBuffer.data(), audioBuffer.size(), user_data);
        }
        return 0;
    } catch (...) {
        return -1;
    }
}

void piper_free_voice(piper_voice_handle_t voice_handle) {
    if (!voice_handle) return;
    delete static_cast<piper::Voice*>(voice_handle);
}

void piper_terminate(void) {
    if (!g_initialized) return;
    piper::terminate(g_piperConfig);
    g_initialized = false;
}

} // extern "C"
