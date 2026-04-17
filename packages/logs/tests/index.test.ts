import { describe, it, expect } from 'vitest';
import { processLogForward } from '../src/index.js';
import type { LogForwardBatch, LogLevel, ForwardedLogEntry } from '../src/index.js';

describe('@pairion/logs', () => {
  it('processes a log forward batch without throwing', () => {
    const batch: LogForwardBatch = {
      sourceDeviceId: 'device-1',
      entries: [
        {
          timestamp: new Date().toISOString(),
          level: 'info',
          subsystem: 'client-audio',
          message: 'Mic capture started',
        },
      ],
    };
    expect(() => processLogForward(batch)).not.toThrow();
  });

  it('handles a batch from a Node', () => {
    const batch: LogForwardBatch = {
      sourceNodeId: 'node-kitchen',
      entries: [
        {
          timestamp: new Date().toISOString(),
          level: 'debug',
          subsystem: 'node-led',
          message: 'LED state change',
          context: { animation: 'idle' },
        },
      ],
    };
    expect(() => processLogForward(batch)).not.toThrow();
  });

  it('handles an empty batch', () => {
    const batch: LogForwardBatch = { entries: [] };
    expect(() => processLogForward(batch)).not.toThrow();
  });

  it('handles entries with requestId and sessionId', () => {
    const batch: LogForwardBatch = {
      sourceDeviceId: 'd1',
      entries: [
        {
          timestamp: new Date().toISOString(),
          level: 'warn',
          subsystem: 'test',
          message: 'test warning',
          requestId: 'req-1',
          sessionId: 'sess-1',
        },
      ],
    };
    expect(() => processLogForward(batch)).not.toThrow();
  });

  it('handles all log levels', () => {
    const levels: LogLevel[] = ['trace', 'debug', 'info', 'warn', 'error', 'fatal'];
    for (const level of levels) {
      const entry: ForwardedLogEntry = {
        timestamp: new Date().toISOString(),
        level,
        subsystem: 'test',
        message: `Test ${level}`,
      };
      expect(() => processLogForward({ entries: [entry] })).not.toThrow();
    }
  });
});
