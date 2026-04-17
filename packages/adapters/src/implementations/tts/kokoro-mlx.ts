/**
 * MLX-Audio Kokoro-82M TTS adapter.
 *
 * @remarks
 * Implements `TTSProvider` using MLX-Audio's Kokoro model via a Python
 * subprocess. Default voice: `bm_george` (British RP male). Produces
 * PCM audio chunks that the agent pipeline encodes to Opus for streaming.
 *
 * Requires Apple Silicon and Python 3.10+ with `mlx-audio` installed.
 */

import { spawn } from 'node:child_process';
import { join } from 'node:path';
import type {
  TTSProvider,
  TTSSynthesizeOptions,
  CapabilityDescriptor,
} from '../../interfaces.js';
import { createSubsystemLogger } from '@pairion/core';

const log = createSubsystemLogger('adapter-kokoro-tts');

/** Configuration for the Kokoro-MLX TTS adapter. */
export interface KokoroMLXConfig {
  /** Path to the Python TTS script. */
  readonly pythonScriptPath?: string | undefined;
  /** Default voice id (default: bm_george). */
  readonly defaultVoice?: string | undefined;
  /** Sample rate in Hz (default: 24000). */
  readonly sampleRate?: number | undefined;
}

/**
 * MLX-Audio Kokoro TTS provider implementation.
 */
export class KokoroMLXTTSProvider implements TTSProvider {
  readonly providerId = 'kokoro-mlx';
  readonly capabilities: readonly CapabilityDescriptor[] = [
    { id: 'streaming-tts', label: 'Streaming TTS' },
    { id: 'british-rp', label: 'British RP Voice' },
  ];

  private readonly scriptPath: string;
  private readonly defaultVoice: string;
  private readonly sampleRate: number;

  /**
   * Creates a Kokoro-MLX TTS provider.
   *
   * @param config - TTS configuration.
   */
  constructor(config?: KokoroMLXConfig) {
    this.scriptPath = config?.pythonScriptPath ??
      join(import.meta.dirname, '../../../scripts/kokoro_tts.py');
    this.defaultVoice = config?.defaultVoice ?? 'bm_george';
    this.sampleRate = config?.sampleRate ?? 24000;
    log.info({ voice: this.defaultVoice, sampleRate: this.sampleRate }, 'Kokoro-MLX TTS initialized');
  }

  /** @inheritdoc */
  async synthesize(text: string, options?: TTSSynthesizeOptions): Promise<AsyncIterable<Uint8Array>> {
    const voice = options?.voiceId ?? this.defaultVoice;
    const startTime = Date.now();

    log.info({ text: text.substring(0, 50), voice }, 'Starting TTS synthesis');

    const resultChunks = await new Promise<Uint8Array[]>((resolve, reject) => {
      const proc = spawn('python3', [
        this.scriptPath,
        '--text', text,
        '--voice', voice,
        '--sample-rate', String(this.sampleRate),
      ]);

      const chunks: Buffer[] = [];
      let error = '';

      proc.stdout.on('data', (data: Buffer) => {
        chunks.push(data);
      });

      proc.stderr.on('data', (data: Buffer) => {
        error += data.toString();
      });

      proc.on('close', (code) => {
        const elapsed = Date.now() - startTime;
        if (code !== 0) {
          log.error({ code, error, elapsed }, 'TTS synthesis failed');
          reject(new Error(`TTS process exited with code ${code}: ${error}`));
          return;
        }
        log.info({ elapsed, chunkCount: chunks.length }, 'TTS synthesis complete');
        resolve(chunks.map((c) => new Uint8Array(c)));
      });

      proc.on('error', reject);
    });

    return (async function* () {
      for (const chunk of resultChunks) {
        yield chunk;
      }
    })();
  }
}
