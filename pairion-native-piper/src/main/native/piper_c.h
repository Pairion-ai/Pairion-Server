/**
 * piper_c.h — C wrapper around the Piper TTS C++ API.
 *
 * Provides a flat C API suitable for jextract FFM binding generation.
 * All functions use extern "C" linkage and opaque handles to avoid C++ ABI
 * dependencies in the generated Java bindings.
 *
 * Build: compiled as part of the piper source tree via CMake. The wrapper
 * implementation (piper_c.cpp) includes piper.hpp and routes calls through
 * the piper:: namespace.
 */

#ifndef PIPER_C_H
#define PIPER_C_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Opaque handle to a loaded Piper voice model.
 * Allocated by piper_load_voice(), freed by piper_free_voice().
 */
typedef void* piper_voice_handle_t;

/**
 * Audio callback invoked per synthesized sentence chunk.
 *
 * @param samples   pointer to 16-bit little-endian PCM samples
 * @param num_samples number of samples in this chunk
 * @param user_data opaque pointer passed through from piper_synthesize()
 */
typedef void (*piper_audio_callback_t)(
    const int16_t* samples,
    size_t num_samples,
    void* user_data);

/**
 * Initializes the Piper TTS engine.
 *
 * Must be called once before any other piper_ function.
 *
 * @param espeak_data_path path to the espeak-ng-data directory
 * @return 0 on success, non-zero on failure
 */
int piper_initialize(const char* espeak_data_path);

/**
 * Loads a Piper voice model from disk.
 *
 * @param model_path        path to the ONNX model file (.onnx)
 * @param model_config_path path to the model JSON config file (.onnx.json)
 * @return an opaque voice handle, or NULL on failure
 */
piper_voice_handle_t piper_load_voice(
    const char* model_path,
    const char* model_config_path);

/**
 * Synthesizes text to PCM audio, invoking the callback per sentence.
 *
 * @param voice_handle handle returned by piper_load_voice()
 * @param text         null-terminated UTF-8 text to synthesize
 * @param callback     called once per synthesized sentence with PCM samples
 * @param user_data    opaque pointer forwarded to every callback invocation
 * @return 0 on success, non-zero on failure
 */
int piper_synthesize(
    piper_voice_handle_t voice_handle,
    const char* text,
    piper_audio_callback_t callback,
    void* user_data);

/**
 * Sets the speech rate (length scale) for a loaded voice.
 *
 * A lower value speeds up speech; a higher value slows it down.
 * 1.0 is the default (normal speed). 0.85 produces approximately 15% faster speech.
 *
 * @param voice_handle  handle returned by piper_load_voice()
 * @param length_scale  speech rate multiplier (default 1.0; lower = faster)
 */
void piper_set_length_scale(piper_voice_handle_t voice_handle, float length_scale);

/**
 * Frees a loaded voice model and releases its resources.
 *
 * @param voice_handle handle returned by piper_load_voice()
 */
void piper_free_voice(piper_voice_handle_t voice_handle);

/**
 * Shuts down the Piper TTS engine.
 *
 * Must be called after all voice handles have been freed.
 */
void piper_terminate(void);

#ifdef __cplusplus
}
#endif

#endif /* PIPER_C_H */
