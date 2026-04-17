import { describe, it, expect, vi, beforeEach } from 'vitest';

const mockExecSync = vi.fn();

vi.mock('node:child_process', () => ({
  execSync: (...args: unknown[]) => mockExecSync(...args),
}));

// Must import after mock setup
const { KeychainSecretsStore } = await import('../src/keychain-secrets-store.js');

describe('KeychainSecretsStore', () => {
  let store: KeychainSecretsStore;

  beforeEach(() => {
    vi.clearAllMocks();
    mockExecSync.mockImplementation((cmd: string) => {
      if (typeof cmd === 'string' && cmd.includes('find-generic-password') && cmd.includes('test-key')) {
        return 'test-value\n';
      }
      if (typeof cmd === 'string' && cmd.includes('find-generic-password')) {
        throw new Error('security: SecKeychainSearchCopyNext');
      }
      return '';
    });
    store = new KeychainSecretsStore('pairion');
  });

  it('gets a secret from keychain', () => {
    expect(store.get('test-key')).toBe('test-value');
  });

  it('returns undefined for missing secret', () => {
    expect(store.get('missing-key')).toBeUndefined();
  });

  it('sets a secret in keychain', () => {
    expect(() => store.set('new-key', 'new-value')).not.toThrow();
  });

  it('throws on keychain set failure', () => {
    mockExecSync.mockImplementation((cmd: string) => {
      if (typeof cmd === 'string' && cmd.includes('add-generic-password')) {
        throw new Error('security error');
      }
    });
    expect(() => store.set('fail-key', 'value')).toThrow('Failed to store secret');
  });

  it('deletes a secret from keychain', () => {
    expect(() => store.delete('any-key')).not.toThrow();
  });

  it('delete handles missing entry gracefully', () => {
    mockExecSync.mockImplementation(() => {
      throw new Error('not found');
    });
    expect(() => store.delete('missing')).not.toThrow();
  });
});
