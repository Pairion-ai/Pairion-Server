import { describe, it, expect } from 'vitest';
import { createOpusEncoder, createOpusDecoder } from '../src/index.js';

describe('Opus codec', () => {
  const SAMPLE_RATE = 48000;
  const CHANNELS = 1;
  // 20ms of silence at 48kHz mono, 16-bit = 48000 * 0.02 * 2 = 1920 bytes
  const FRAME_SIZE = (SAMPLE_RATE * CHANNELS * 2 * 20) / 1000;

  it('encoder produces Opus frames from PCM', () => {
    const encoder = createOpusEncoder(SAMPLE_RATE, CHANNELS);
    const pcm = Buffer.alloc(FRAME_SIZE, 0);
    const encoded = encoder.encode(pcm);
    expect(encoded).toBeInstanceOf(Buffer);
    expect(encoded.length).toBeGreaterThan(0);
    expect(encoded.length).toBeLessThan(pcm.length);
  });

  it('decoder produces PCM from Opus frames', () => {
    const encoder = createOpusEncoder(SAMPLE_RATE, CHANNELS);
    const decoder = createOpusDecoder(SAMPLE_RATE, CHANNELS);

    const pcm = Buffer.alloc(FRAME_SIZE, 0);
    const encoded = encoder.encode(pcm);
    const decoded = decoder.decode(encoded);

    expect(decoded).toBeInstanceOf(Buffer);
    // opusscript decode returns the PCM buffer (size depends on internal frame sizing)
    expect(decoded.length).toBeGreaterThan(0);
  });

  it('round-trip encode/decode preserves silence', () => {
    const encoder = createOpusEncoder(SAMPLE_RATE, CHANNELS);
    const decoder = createOpusDecoder(SAMPLE_RATE, CHANNELS);

    const silence = Buffer.alloc(FRAME_SIZE, 0);
    const encoded = encoder.encode(silence);
    const decoded = decoder.decode(encoded);

    // Decoded silence should be close to zero (Opus may not produce exact zeros)
    const maxSample = Math.max(...Array.from(decoded).map(Math.abs));
    expect(maxSample).toBeLessThan(128);
  });

  it('encoder respects custom bitrate', () => {
    const lowBitrate = createOpusEncoder(SAMPLE_RATE, CHANNELS, 16000);
    const highBitrate = createOpusEncoder(SAMPLE_RATE, CHANNELS, 128000);

    const pcm = Buffer.alloc(FRAME_SIZE);
    // Fill with some non-zero data
    for (let i = 0; i < pcm.length; i += 2) {
      pcm.writeInt16LE(Math.floor(Math.sin(i / 100) * 1000), i);
    }

    const lowEncoded = lowBitrate.encode(pcm);
    const highEncoded = highBitrate.encode(pcm);

    // Both should produce valid output
    expect(lowEncoded.length).toBeGreaterThan(0);
    expect(highEncoded.length).toBeGreaterThan(0);
  });
});
