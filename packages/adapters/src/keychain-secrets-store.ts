/**
 * macOS Keychain-backed SecretsStore implementation.
 *
 * @remarks
 * Uses the macOS `security` CLI to read/write secrets from the system
 * Keychain. Falls back gracefully if the security command is unavailable
 * (non-macOS). API keys are stored under the service name `pairion.adapters`.
 */

import { execSync } from 'node:child_process';
import type { SecretsStore } from './secrets-store.js';
import { createSubsystemLogger } from '@pairion/core';

const log = createSubsystemLogger('keychain');

/**
 * macOS Keychain secrets store.
 */
export class KeychainSecretsStore implements SecretsStore {
  private readonly serviceName: string;

  /**
   * Creates a Keychain-backed secrets store.
   *
   * @param serviceName - Keychain service name prefix (default: `pairion`).
   */
  constructor(serviceName: string = 'pairion') {
    this.serviceName = serviceName;
  }

  /** @inheritdoc */
  get(name: string): string | undefined {
    try {
      const result = execSync(
        `security find-generic-password -a "${this.serviceName}" -s "${name}" -w 2>/dev/null`,
        { encoding: 'utf-8', stdio: ['pipe', 'pipe', 'pipe'] },
      );
      return result.trim();
    } catch {
      log.debug({ name }, 'Secret not found in Keychain');
      return undefined;
    }
  }

  /** @inheritdoc */
  set(name: string, value: string): void {
    try {
      // Delete existing entry first (ignore errors)
      try {
        execSync(
          `security delete-generic-password -a "${this.serviceName}" -s "${name}" 2>/dev/null`,
          { stdio: ['pipe', 'pipe', 'pipe'] },
        );
      } catch { /* entry may not exist */ }

      execSync(
        `security add-generic-password -a "${this.serviceName}" -s "${name}" -w "${value}"`,
        { stdio: ['pipe', 'pipe', 'pipe'] },
      );
      log.info({ name }, 'Secret stored in Keychain');
    } catch (err) {
      log.error({ name, err }, 'Failed to store secret in Keychain');
      throw new Error(`Failed to store secret "${name}" in Keychain`, { cause: err });
    }
  }

  /** @inheritdoc */
  delete(name: string): void {
    try {
      execSync(
        `security delete-generic-password -a "${this.serviceName}" -s "${name}" 2>/dev/null`,
        { stdio: ['pipe', 'pipe', 'pipe'] },
      );
    } catch {
      // Ignore — entry may not exist
    }
  }
}
