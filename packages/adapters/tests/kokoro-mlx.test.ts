import { describe, it, expect, vi, beforeEach } from 'vitest';
import { KokoroMLXTTSProvider } from '../src/implementations/tts/kokoro-mlx.js';
import { EventEmitter } from 'node:events';

vi.mock('node:child_process', () => ({
  spawn: vi.fn(() => {
    const proc = new EventEmitter() as any;
    proc.stdout = new EventEmitter();
    proc.stderr = new EventEmitter();

    setTimeout(() => {
      const pcm = Buffer.alloc(960, 0);
      proc.stdout.emit('data', pcm);
      proc.emit('close', 0);
    }, 10);

    return proc;
  }),
}));

describe('KokoroMLXTTSProvider', () => {
  let provider: KokoroMLXTTSProvider;

  beforeEach(() => {
    provider = new KokoroMLXTTSProvider({ pythonScriptPath: '/mock/kokoro.py' });
  });

  it('has providerId "kokoro-mlx"', () => {
    expect(provider.providerId).toBe('kokoro-mlx');
  });

  it('has streaming-tts capability', () => {
    expect(provider.capabilities.some((c) => c.id === 'streaming-tts')).toBe(true);
  });

  it('synthesize() yields audio chunks', async () => {
    const iterable = await provider.synthesize('Hello world');
    const chunks: Uint8Array[] = [];
    for await (const chunk of iterable) {
      chunks.push(chunk);
    }
    expect(chunks.length).toBeGreaterThan(0);
    expect(chunks[0]).toBeInstanceOf(Uint8Array);
  });

  it('synthesize() respects voice option', async () => {
    const { spawn } = await import('node:child_process');
    const iterable = await provider.synthesize('Test', { voiceId: 'af_bella' });
    for await (const _chunk of iterable) { /* consume */ }
    expect(spawn).toHaveBeenCalledWith('python3', expect.arrayContaining(['af_bella']));
  });

  it('synthesize() rejects on process error', async () => {
    const { spawn } = await import('node:child_process');
    (spawn as any).mockReturnValueOnce((() => {
      const proc = new EventEmitter() as any;
      proc.stdout = new EventEmitter();
      proc.stderr = new EventEmitter();
      setTimeout(() => {
        proc.stderr.emit('data', Buffer.from('model not found'));
        proc.emit('close', 1);
      }, 10);
      return proc;
    })());

    await expect(provider.synthesize('fail')).rejects.toThrow('TTS process exited');
  });

  it('synthesize() rejects on spawn error', async () => {
    const { spawn } = await import('node:child_process');
    (spawn as any).mockReturnValueOnce((() => {
      const proc = new EventEmitter() as any;
      proc.stdout = new EventEmitter();
      proc.stderr = new EventEmitter();
      setTimeout(() => {
        proc.emit('error', new Error('ENOENT'));
      }, 10);
      return proc;
    })());

    await expect(provider.synthesize('fail')).rejects.toThrow('ENOENT');
  });
});
