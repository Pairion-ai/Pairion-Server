import { describe, it, expect, vi } from 'vitest';
import { EventBus } from '../src/index.js';

describe('EventBus', () => {
  it('emits and receives events via on()', () => {
    const bus = new EventBus();
    const handler = vi.fn();
    bus.on('system.ready', handler);
    bus.emit('system.ready');
    expect(handler).toHaveBeenCalledOnce();
  });

  it('supports once() — handler fires only once', () => {
    const bus = new EventBus();
    const handler = vi.fn();
    bus.once('system.ready', handler);
    bus.emit('system.ready');
    bus.emit('system.ready');
    expect(handler).toHaveBeenCalledOnce();
  });

  it('supports off() — unsubscribe', () => {
    const bus = new EventBus();
    const handler = vi.fn();
    bus.on('system.shutdown', handler);
    bus.off('system.shutdown', handler);
    bus.emit('system.shutdown');
    expect(handler).not.toHaveBeenCalled();
  });

  it('removeAllListeners clears everything', () => {
    const bus = new EventBus();
    const handler = vi.fn();
    bus.on('system.ready', handler);
    bus.removeAllListeners();
    bus.emit('system.ready');
    expect(handler).not.toHaveBeenCalled();
  });
});
