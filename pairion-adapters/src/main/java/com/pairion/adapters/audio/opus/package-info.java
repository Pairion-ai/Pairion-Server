/**
 * Opus audio decoding utility for Pairion Server.
 *
 * <p>Decodes Opus-encoded audio frames from the Client into raw 16 kHz mono PCM for the STT
 * adapter. This is a utility package, not a capability adapter — Opus decoding is a transport
 * concern, not an external service with alternative implementations. No SPI interface is required.
 *
 * <p>Uses Concentus (pure-Java Opus implementation) to avoid native library dependencies for audio
 * decoding. Native libopus via FFM could be substituted for performance if needed in future
 * milestones.
 */
package com.pairion.adapters.audio.opus;
