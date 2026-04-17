/**
 * Opus codec wrappers for audio encoding/decoding at the transport boundary.
 *
 * @remarks
 * Uses `opusscript` (pure JS) for Opus encode/decode. Both the gateway
 * (decoding inbound Client audio) and the TTS pipeline (encoding outbound
 * audio) need codec access, so it lives in `@pairion/core`.
 */

import OpusScript from 'opusscript';

/** Valid Opus sampling rates. */
type OpusSampleRate = 8000 | 12000 | 16000 | 24000 | 48000;

/**
 * Creates an Opus decoder.
 *
 * @param sampleRate - Sample rate in Hz (8000, 12000, 16000, 24000, or 48000).
 * @param channels - Number of audio channels (1 or 2).
 * @returns An object with a `decode` method that converts Opus frames to PCM.
 */
export function createOpusDecoder(
  sampleRate: OpusSampleRate,
  channels: number,
): { decode: (frame: Uint8Array) => Buffer } {
  const decoder = new OpusScript(sampleRate, channels, OpusScript.Application.AUDIO);
  return {
    decode(frame: Uint8Array): Buffer {
      return decoder.decode(Buffer.from(frame));
    },
  };
}

/**
 * Creates an Opus encoder.
 *
 * @param sampleRate - Sample rate in Hz (8000, 12000, 16000, 24000, or 48000).
 * @param channels - Number of audio channels (1 or 2).
 * @param bitrate - Target bitrate in bits/sec (default 64000).
 * @returns An object with an `encode` method that converts PCM to Opus frames.
 */
export function createOpusEncoder(
  sampleRate: OpusSampleRate,
  channels: number,
  bitrate: number = 64000,
): { encode: (pcm: Buffer) => Buffer } {
  const encoder = new OpusScript(sampleRate, channels, OpusScript.Application.AUDIO);
  encoder.setBitrate(bitrate);
  return {
    encode(pcm: Buffer): Buffer {
      return encoder.encode(pcm, (sampleRate * channels * 2 * 20) / 1000);
    },
  };
}
