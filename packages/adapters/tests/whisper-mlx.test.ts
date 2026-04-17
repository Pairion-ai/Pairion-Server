import { describe, it, expect, vi, beforeEach } from 'vitest';
import { WhisperMLXSTTProvider } from '../src/implementations/stt/whisper-mlx.js';
import { EventEmitter } from 'node:events';

vi.mock('node:child_process', () => ({
  spawn: vi.fn(() => {
    const proc = new EventEmitter() as any;
    proc.stdout = new EventEmitter();
    proc.stderr = new EventEmitter();

    setTimeout(() => {
      proc.stdout.emit('data', Buffer.from(JSON.stringify({ text: 'What is the weather?' })));
      proc.emit('close', 0);
    }, 10);

    return proc;
  }),
}));

describe('WhisperMLXSTTProvider', () => {
  let provider: WhisperMLXSTTProvider;

  beforeEach(() => {
    provider = new WhisperMLXSTTProvider({ pythonScriptPath: '/mock/whisper.py' });
  });

  it('has providerId "whisper-mlx"', () => {
    expect(provider.providerId).toBe('whisper-mlx');
  });

  it('transcribes audio to transcript events', async () => {
    async function* audioSource() {
      yield new Uint8Array(Buffer.alloc(3200, 0));
    }

    const events = [];
    for await (const event of provider.transcribe(audioSource())) {
      events.push(event);
    }

    expect(events.length).toBe(2);
    expect(events[0]?.isFinal).toBe(false);
    expect(events[1]?.isFinal).toBe(true);
    expect(events[1]?.text).toBe('What is the weather?');
  });

  it('handles empty audio', async () => {
    async function* emptyAudio() {
      // yield nothing
    }

    const events = [];
    for await (const event of provider.transcribe(emptyAudio())) {
      events.push(event);
    }

    expect(events.length).toBe(1);
    expect(events[0]?.isFinal).toBe(true);
    expect(events[0]?.text).toBe('');
  });

  it('handles non-JSON whisper output as raw text', async () => {
    const { spawn } = await import('node:child_process');
    (spawn as any).mockReturnValueOnce((() => {
      const proc = new EventEmitter() as any;
      proc.stdout = new EventEmitter();
      proc.stderr = new EventEmitter();
      setTimeout(() => {
        proc.stdout.emit('data', Buffer.from('plain text transcript'));
        proc.emit('close', 0);
      }, 10);
      return proc;
    })());

    async function* audio() {
      yield new Uint8Array(Buffer.alloc(100, 0));
    }

    const events = [];
    for await (const event of provider.transcribe(audio())) {
      events.push(event);
    }

    expect(events[1]?.text).toBe('plain text transcript');
  });

  it('rejects on process failure', async () => {
    const { spawn } = await import('node:child_process');
    (spawn as any).mockReturnValueOnce((() => {
      const proc = new EventEmitter() as any;
      proc.stdout = new EventEmitter();
      proc.stderr = new EventEmitter();
      setTimeout(() => {
        proc.stderr.emit('data', Buffer.from('model error'));
        proc.emit('close', 1);
      }, 10);
      return proc;
    })());

    async function* audio() {
      yield new Uint8Array(Buffer.alloc(100, 0));
    }

    const gen = provider.transcribe(audio());
    await expect(async () => {
      for await (const _e of gen) { /* consume */ }
    }).rejects.toThrow('Whisper exited');
  });
});
