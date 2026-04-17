/**
 * openWakeWord adapter.
 *
 * @remarks
 * Implements `WakeWordProvider`. In M1 the Server side only exposes model
 * file location and threshold configuration — wake word detection itself
 * runs on the Client.
 */

import { join } from 'node:path';
import { homedir } from 'node:os';
import type { WakeWordProvider, CapabilityDescriptor } from '../../interfaces.js';

/** Configuration for the openWakeWord adapter. */
export interface OpenWakeWordConfig {
  /** Path to the .onnx model file. */
  readonly modelPath?: string | undefined;
  /** Detection confidence threshold (0-1, default: 0.5). */
  readonly threshold?: number | undefined;
}

/**
 * openWakeWord provider implementation.
 */
export class OpenWakeWordProvider implements WakeWordProvider {
  readonly providerId = 'openwakeword';
  readonly capabilities: readonly CapabilityDescriptor[] = [
    { id: 'local-wake-word', label: 'Local Wake Word Detection' },
  ];

  private readonly modelPath: string;
  private readonly threshold: number;

  /**
   * Creates an openWakeWord provider.
   *
   * @param config - Wake word configuration.
   */
  constructor(config?: OpenWakeWordConfig) {
    this.modelPath = config?.modelPath ??
      join(homedir(), '.pairion', 'models', 'hey_pairion.onnx');
    this.threshold = config?.threshold ?? 0.5;
  }

  /** @inheritdoc */
  getModelPath(): string {
    return this.modelPath;
  }

  /** @inheritdoc */
  getThreshold(): number {
    return this.threshold;
  }
}
