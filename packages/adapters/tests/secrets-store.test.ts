import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtempSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { FileSecretsStore, ensureDevToken } from '../src/index.js';

describe('FileSecretsStore', () => {
  let tempDir: string;
  let store: FileSecretsStore;

  beforeEach(() => {
    tempDir = mkdtempSync(join(tmpdir(), 'pairion-test-'));
    store = new FileSecretsStore(tempDir);
  });

  afterEach(() => {
    rmSync(tempDir, { recursive: true, force: true });
  });

  it('returns undefined for a missing secret', () => {
    expect(store.get('nonexistent')).toBeUndefined();
  });

  it('sets and retrieves a secret', () => {
    store.set('api-key', 'secret-value');
    expect(store.get('api-key')).toBe('secret-value');
  });

  it('overwrites an existing secret', () => {
    store.set('key', 'v1');
    store.set('key', 'v2');
    expect(store.get('key')).toBe('v2');
  });

  it('deletes a secret', () => {
    store.set('key', 'val');
    store.delete('key');
    expect(store.get('key')).toBeUndefined();
  });

  it('delete is a no-op for missing secret', () => {
    expect(() => store.delete('missing')).not.toThrow();
  });
});

describe('FileSecretsStore default directory', () => {
  it('uses ~/.pairion when no baseDir provided', () => {
    const store = new FileSecretsStore();
    // Just verify it doesn't throw — we don't want to write to ~
    expect(store.get('nonexistent-key-that-should-not-exist')).toBeUndefined();
  });
});

describe('ensureDevToken', () => {
  let tempDir: string;
  let store: FileSecretsStore;

  beforeEach(() => {
    tempDir = mkdtempSync(join(tmpdir(), 'pairion-test-'));
    store = new FileSecretsStore(tempDir);
  });

  afterEach(() => {
    rmSync(tempDir, { recursive: true, force: true });
  });

  it('generates a token if none exists', () => {
    const token = ensureDevToken(store);
    expect(token).toBeDefined();
    expect(token.length).toBe(64); // 32 bytes = 64 hex chars
  });

  it('returns the same token on subsequent calls', () => {
    const token1 = ensureDevToken(store);
    const token2 = ensureDevToken(store);
    expect(token1).toBe(token2);
  });

  it('writes the token to device.token', () => {
    const token = ensureDevToken(store);
    expect(store.get('device.token')).toBe(token);
  });
});
