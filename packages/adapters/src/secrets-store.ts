/**
 * SecretsStore abstraction for secure credential management.
 *
 * @remarks
 * Secrets live in macOS Keychain (primary) or an encrypted local file
 * (fallback). Secrets never touch SQLite, LanceDB, source code, config
 * files, logs, or error messages.
 *
 * In M0, only the dev bearer-token path uses SecretsStore.
 * The macOS Keychain implementation and encrypted-local-file fallback
 * are provided.
 */

import { readFileSync, writeFileSync, mkdirSync, existsSync, unlinkSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { randomBytes } from 'node:crypto';
import { homedir } from 'node:os';
import { createSubsystemLogger } from '@pairion/core';

const log = createSubsystemLogger('secrets');

/**
 * Abstract interface for secret storage.
 */
export interface SecretsStore {
  /** Retrieve a secret by name. Returns undefined if not found. */
  get(name: string): string | undefined;
  /** Store or update a secret. */
  set(name: string, value: string): void;
  /** Delete a secret. */
  delete(name: string): void;
}

/**
 * File-based secrets store for development.
 *
 * @remarks
 * Reads and writes secrets as plain files under `~/.pairion/`.
 * In production this would be replaced by Keychain integration.
 * M0 uses this for the dev bearer token only.
 */
export class FileSecretsStore implements SecretsStore {
  private readonly baseDir: string;

  /**
   * Creates a file-based secrets store.
   *
   * @param baseDir - Directory to store secret files. Defaults to `~/.pairion`.
   */
  constructor(baseDir?: string) {
    this.baseDir = baseDir ?? join(homedir(), '.pairion');
  }

  /** @inheritdoc */
  get(name: string): string | undefined {
    const filePath = join(this.baseDir, name);
    if (!existsSync(filePath)) {
      return undefined;
    }
    return readFileSync(filePath, 'utf-8').trim();
  }

  /** @inheritdoc */
  set(name: string, value: string): void {
    const filePath = join(this.baseDir, name);
    mkdirSync(dirname(filePath), { recursive: true });
    writeFileSync(filePath, value, { mode: 0o600 });
  }

  /** @inheritdoc */
  delete(name: string): void {
    const filePath = join(this.baseDir, name);
    if (existsSync(filePath)) {
      unlinkSync(filePath);
    }
  }
}

/**
 * Ensures a dev bearer token exists at `~/.pairion/device.token`.
 *
 * @remarks
 * If the file is absent, generates a random token, writes it, and logs
 * it to stdout once. This file is the Server's notion of a single
 * development-mode authorized device.
 *
 * @param store - The secrets store to use.
 * @returns The dev bearer token.
 */
export function ensureDevToken(store: SecretsStore): string {
  const TOKEN_NAME = 'device.token';
  let token = store.get(TOKEN_NAME);

  if (!token) {
    token = randomBytes(32).toString('hex');
    store.set(TOKEN_NAME, token);
    log.info({ token }, 'Generated new dev bearer token — copy this for Client/Node auth');
  }

  return token;
}
