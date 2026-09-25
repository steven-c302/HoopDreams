import type { Ack } from "./socket";

export interface OutboxItem {
  requestId: string;
  event: string;
  payload: { requestId: string } & Record<string, unknown>;
  createdAt: number;
}

export interface KeyValueStore {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}

export type Sender = (event: string, payload: unknown) => Promise<Ack<Record<string, unknown>>>;

export interface OutboxResult {
  item: OutboxItem;
  ack: Ack<Record<string, unknown>>;
}

interface OutboxOptions {
  store: KeyValueStore;
  key: string;
  send: Sender;
  onResult: (result: OutboxResult) => void;
  retryMs?: number;
}

/**
 * Intents that must never be lost (shot logs). An item is persisted before it is sent and leaves the
 * queue only once the server answers. Resends reuse the requestId, which the server dedupes.
 * It starts paused: the phone resumes it once the server knows who this socket belongs to.
 */
export class Outbox {
  private readonly store: KeyValueStore;
  private readonly key: string;
  private readonly send: Sender;
  private readonly onResult: (result: OutboxResult) => void;
  private readonly retryMs: number;
  private readonly listeners = new Set<() => void>();
  private items: readonly OutboxItem[];
  private paused = true;
  private flushing = false;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(options: OutboxOptions) {
    this.store = options.store;
    this.key = options.key;
    this.send = options.send;
    this.onResult = options.onResult;
    this.retryMs = options.retryMs ?? 2000;
    this.items = this.read();
  }

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  snapshot = (): readonly OutboxItem[] => this.items;

  async push(event: string, payload: OutboxItem["payload"]): Promise<void> {
    this.write([...this.items, { requestId: payload.requestId, event, payload, createdAt: Date.now() }]);
    await this.flush();
  }

  pause(): void {
    this.paused = true;
  }

  async resume(): Promise<void> {
    this.paused = false;
    await this.flush();
  }

  async flush(): Promise<void> {
    if (this.paused || this.flushing) return;
    this.flushing = true;
    try {
      while (!this.paused && this.items.length > 0) {
        const item = this.items[0];
        const ack = await this.send(item.event, item.payload);
        if (!ack.ok && ack.retryable) {
          this.scheduleRetry();
          return;
        }
        this.write(this.items.filter((i) => i.requestId !== item.requestId));
        this.onResult({ item, ack });
      }
    } finally {
      this.flushing = false;
    }
  }

  dispose(): void {
    if (this.retryTimer) clearTimeout(this.retryTimer);
    this.retryTimer = null;
    this.paused = true;
  }

  private scheduleRetry(): void {
    if (this.retryTimer) return;
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null;
      void this.flush();
    }, this.retryMs);
  }

  private write(items: readonly OutboxItem[]): void {
    this.items = items;
    try {
      this.store.setItem(this.key, JSON.stringify(items));
    } catch {
      // Storage blocked or full: keep the queue in memory.
    }
    for (const listener of this.listeners) listener();
  }

  private read(): OutboxItem[] {
    try {
      const raw = this.store.getItem(this.key);
      return raw ? (JSON.parse(raw) as OutboxItem[]) : [];
    } catch {
      return [];
    }
  }
}
