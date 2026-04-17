import { describe, it, expect } from 'vitest';
import { OpenWakeWordProvider } from '../src/implementations/wake-word/openwakeword.js';

describe('OpenWakeWordProvider', () => {
  it('has providerId "openwakeword"', () => {
    const provider = new OpenWakeWordProvider();
    expect(provider.providerId).toBe('openwakeword');
  });

  it('returns default model path', () => {
    const provider = new OpenWakeWordProvider();
    expect(provider.getModelPath()).toContain('hey_pairion.onnx');
  });

  it('returns custom model path', () => {
    const provider = new OpenWakeWordProvider({ modelPath: '/custom/model.onnx' });
    expect(provider.getModelPath()).toBe('/custom/model.onnx');
  });

  it('returns default threshold', () => {
    const provider = new OpenWakeWordProvider();
    expect(provider.getThreshold()).toBe(0.5);
  });

  it('returns custom threshold', () => {
    const provider = new OpenWakeWordProvider({ threshold: 0.8 });
    expect(provider.getThreshold()).toBe(0.8);
  });

  it('has local-wake-word capability', () => {
    const provider = new OpenWakeWordProvider();
    expect(provider.capabilities.some((c) => c.id === 'local-wake-word')).toBe(true);
  });
});
