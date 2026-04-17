import { describe, it, expect } from 'vitest';
import { logger, createSubsystemLogger } from '../src/index.js';

describe('logger', () => {
  it('exports a pino logger instance', () => {
    expect(logger).toBeDefined();
    expect(typeof logger.info).toBe('function');
    expect(typeof logger.error).toBe('function');
  });

  it('creates a child logger with subsystem binding', () => {
    const child = createSubsystemLogger('gateway');
    expect(child).toBeDefined();
    expect(typeof child.info).toBe('function');
  });

  it('creates a child logger with additional bindings', () => {
    const child = createSubsystemLogger('agent', { sessionId: 'abc' });
    expect(child).toBeDefined();
  });
});
