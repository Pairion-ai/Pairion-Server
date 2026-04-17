import { describe, it, expect } from 'vitest';
import { AsyncIterableQueue } from '../src/index.js';

describe('AsyncIterableQueue', () => {
  it('yields pushed items in order', async () => {
    const queue = new AsyncIterableQueue<number>();
    queue.push(1);
    queue.push(2);
    queue.push(3);
    queue.end();

    const items: number[] = [];
    for await (const item of queue) {
      items.push(item);
    }
    expect(items).toEqual([1, 2, 3]);
  });

  it('waits for items when buffer is empty', async () => {
    const queue = new AsyncIterableQueue<string>();

    const promise = (async () => {
      const items: string[] = [];
      for await (const item of queue) {
        items.push(item);
      }
      return items;
    })();

    queue.push('a');
    queue.push('b');
    queue.end();

    const result = await promise;
    expect(result).toEqual(['a', 'b']);
  });

  it('returns done when ended with no items', async () => {
    const queue = new AsyncIterableQueue<number>();
    queue.end();

    const items: number[] = [];
    for await (const item of queue) {
      items.push(item);
    }
    expect(items).toEqual([]);
  });

  it('throws when pushing to an ended queue', () => {
    const queue = new AsyncIterableQueue<number>();
    queue.end();
    expect(() => queue.push(1)).toThrow('Cannot push to an ended queue');
  });

  it('resolves waiting consumers on end()', async () => {
    const queue = new AsyncIterableQueue<number>();
    const iter = queue[Symbol.asyncIterator]();

    const nextPromise = iter.next();
    queue.end();

    const result = await nextPromise;
    expect(result.done).toBe(true);
  });
});
