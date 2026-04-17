/**
 * A push/pull async queue implementing `AsyncIterable<T>`.
 *
 * @remarks
 * Used to bridge push-based data sources (WebSocket audio frames) to
 * pull-based consumers (STT adapter's `AsyncIterable<Uint8Array>` input).
 * The gateway pushes audio chunks; the STT adapter pulls them.
 *
 * @typeParam T - The type of items in the queue.
 */
export class AsyncIterableQueue<T> implements AsyncIterable<T> {
  private readonly buffer: T[] = [];
  private readonly waiters: Array<(result: IteratorResult<T>) => void> = [];
  private done = false;

  /**
   * Push an item into the queue.
   *
   * @param item - The item to enqueue.
   * @throws If the queue has already been ended.
   */
  push(item: T): void {
    if (this.done) {
      throw new Error('Cannot push to an ended queue');
    }
    const waiter = this.waiters.shift();
    if (waiter) {
      waiter({ value: item, done: false });
    } else {
      this.buffer.push(item);
    }
  }

  /**
   * Signal that no more items will be pushed.
   */
  end(): void {
    this.done = true;
    for (const waiter of this.waiters) {
      waiter({ value: undefined as unknown as T, done: true });
    }
    this.waiters.length = 0;
  }

  /** Returns an async iterator over the queued items. */
  [Symbol.asyncIterator](): AsyncIterator<T> {
    return {
      next: (): Promise<IteratorResult<T>> => {
        const buffered = this.buffer.shift();
        if (buffered !== undefined) {
          return Promise.resolve({ value: buffered, done: false });
        }
        if (this.done) {
          return Promise.resolve({ value: undefined as unknown as T, done: true });
        }
        return new Promise<IteratorResult<T>>((resolve) => {
          this.waiters.push(resolve);
        });
      },
    };
  }
}
