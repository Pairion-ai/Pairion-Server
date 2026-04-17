/**
 * Whisper-MLX STT adapter.
 *
 * @remarks
 * Implements `STTProvider` using Whisper via MLX through a Python subprocess.
 * Collects audio chunks, writes them to a temp file, invokes the Python
 * Whisper script, and yields transcript events.
 *
 * Requires Apple Silicon and Python 3.10+ with `mlx-whisper` installed.
 */

import { spawn } from 'node:child_process';
import { writeFileSync, unlinkSync, mkdtempSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import type { STTProvider, TranscriptEvent, CapabilityDescriptor } from '../../interfaces.js';
import { createSubsystemLogger } from '@pairion/core';

const log = createSubsystemLogger('adapter-whisper-stt');

/** Configuration for the Whisper-MLX STT adapter. */
export interface WhisperMLXConfig {
  /** Path to the Python STT script. */
  readonly pythonScriptPath?: string | undefined;
  /** Whisper model size (default: small). */
  readonly model?: string | undefined;
  /** Expected sample rate of input audio (default: 16000). */
  readonly sampleRate?: number | undefined;
}

/**
 * Whisper-MLX STT provider implementation.
 */
export class WhisperMLXSTTProvider implements STTProvider {
  readonly providerId = 'whisper-mlx';
  readonly capabilities: readonly CapabilityDescriptor[] = [
    { id: 'offline-stt', label: 'Offline Speech-to-Text' },
  ];

  private readonly scriptPath: string;
  private readonly model: string;
  private readonly sampleRate: number;

  /**
   * Creates a Whisper-MLX STT provider.
   *
   * @param config - STT configuration.
   */
  constructor(config?: WhisperMLXConfig) {
    this.scriptPath = config?.pythonScriptPath ??
      join(import.meta.dirname, '../../../scripts/whisper_stt.py');
    this.model = config?.model ?? 'small';
    this.sampleRate = config?.sampleRate ?? 16000;
    log.info({ model: this.model, sampleRate: this.sampleRate }, 'Whisper-MLX STT initialized');
  }

  /** @inheritdoc */
  async *transcribe(audio: AsyncIterable<Uint8Array>): AsyncIterable<TranscriptEvent> {
    const startTime = Date.now();
    log.info('Starting STT transcription');

    // Collect all audio chunks into a single buffer
    const buffers: Buffer[] = [];
    for await (const chunk of audio) {
      buffers.push(Buffer.from(chunk));
    }
    const audioBuffer = Buffer.concat(buffers);

    if (audioBuffer.length === 0) {
      yield { isFinal: true, text: '', confidence: 0 };
      return;
    }

    // Write to temp file as raw PCM
    const tempDir = mkdtempSync(join(tmpdir(), 'pairion-stt-'));
    const tempPath = join(tempDir, 'audio.raw');
    writeFileSync(tempPath, audioBuffer);

    try {
      const result = await this.runWhisper(tempPath);
      const elapsed = Date.now() - startTime;
      log.info({ elapsed, textLength: result.length }, 'STT transcription complete');

      // Emit partial then final
      yield { isFinal: false, text: result, confidence: 0.9 };
      yield { isFinal: true, text: result, confidence: 0.95 };
    } finally {
      /* v8 ignore next 2 -- cleanup errors are intentionally swallowed */
      try { unlinkSync(tempPath); } catch { /* ignore cleanup errors */ }
      try { const { rmdirSync } = await import('node:fs'); rmdirSync(tempDir); } catch { /* ignore */ }
    }
  }

  /**
   * Invokes the Python Whisper script on an audio file.
   *
   * @param audioPath - Path to the raw PCM audio file.
   * @returns The transcribed text.
   */
  private runWhisper(audioPath: string): Promise<string> {
    return new Promise((resolve, reject) => {
      const proc = spawn('python3', [
        this.scriptPath,
        '--audio', audioPath,
        '--model', this.model,
        '--sample-rate', String(this.sampleRate),
      ]);

      let stdout = '';
      let stderr = '';

      proc.stdout.on('data', (data: Buffer) => {
        stdout += data.toString();
      });

      proc.stderr.on('data', (data: Buffer) => {
        stderr += data.toString();
      });

      proc.on('close', (code) => {
        if (code !== 0) {
          log.error({ code, stderr }, 'Whisper process failed');
          reject(new Error(`Whisper exited with code ${code}: ${stderr}`));
          return;
        }
        try {
          const result = JSON.parse(stdout) as { text: string };
          resolve(result.text.trim());
        } catch {
          // If not JSON, treat raw output as transcript
          resolve(stdout.trim());
        }
      });

      proc.on('error', reject);
    });
  }
}
