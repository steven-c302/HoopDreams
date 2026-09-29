import { describe, expect, it, vi } from "vitest";
import { Outbox, type KeyValueStore, type OutboxResult, type Sender } from "./outbox";

class MemoryStore implements KeyValueStore {
  data = new Map<string, string>();
  getItem(key: string) {
    return this.data.get(key) ?? null;
  }
  setItem(key: string, value: string) {
    this.data.set(key, value);
  }
}

function setup(send: Sender) {
  const store = new MemoryStore();
  const results: OutboxResult[] = [];
  const outbox = new Outbox({ store, key: "k", send, onResult: (r) => results.push(r), retryMs: 60_000 });
  return { store, results, outbox };
}

const shot = (requestId: string) => ({ requestId, drinkerIds: ["p1"] });

describe("Outbox", () => {
  it("persists an intent before sending it", async () => {
    const send = vi.fn<Sender>();
    const { store, outbox } = setup(send);
    await outbox.push("shot:log", shot("r1"));
    expect(send).not.toHaveBeenCalled(); // starts paused until the phone has resumed its session
    expect(JSON.parse(store.getItem("k")!)).toHaveLength(1);
  });

  it("sends once resumed and drops the item on an ok ack", async () => {
    const send = vi.fn<Sender>().mockResolvedValue({ ok: true, shotIds: ["s1"] });
    const { outbox, results } = setup(send);
    await outbox.push("shot:log", shot("r1"));
    await outbox.resume();
    expect(send).toHaveBeenCalledWith("shot:log", shot("r1"));
    expect(outbox.snapshot()).toEqual([]);
    expect(results[0].ack.ok).toBe(true);
  });

  it("keeps an unanswered intent and resends the same requestId", async () => {
    const send = vi
      .fn<Sender>()
      .mockResolvedValueOnce({ ok: false, error: "No answer", retryable: true })
      .mockResolvedValueOnce({ ok: true });
    const { outbox, results } = setup(send);
    await outbox.resume();
    await outbox.push("shot:log", shot("r1"));
    expect(outbox.snapshot()).toHaveLength(1);
    expect(results).toEqual([]);
    await outbox.flush();
    expect(send).toHaveBeenNthCalledWith(2, "shot:log", shot("r1"));
    expect(outbox.snapshot()).toEqual([]);
    outbox.dispose();
  });

  it("reports a refused intent and moves on to the next", async () => {
    const send = vi.fn<Sender>().mockResolvedValueOnce({ ok: false, error: "Too late" }).mockResolvedValueOnce({ ok: true });
    const { outbox, results } = setup(send);
    await outbox.push("shot:log", shot("r1"));
    await outbox.push("shot:log", shot("r2"));
    await outbox.resume();
    expect(results.map((r) => r.ack.ok)).toEqual([false, true]);
    expect(outbox.snapshot()).toEqual([]);
  });

  it("restores unsent intents after a reload", async () => {
    const first = setup(vi.fn<Sender>());
    await first.outbox.push("shot:log", shot("r1"));
    const reloaded = new Outbox({ store: first.store, key: "k", send: vi.fn<Sender>(), onResult: () => {} });
    expect(reloaded.snapshot().map((i) => i.requestId)).toEqual(["r1"]);
  });

  it("notifies subscribers when the queue changes", async () => {
    const { outbox } = setup(vi.fn<Sender>());
    const listener = vi.fn();
    outbox.subscribe(listener);
    await outbox.push("shot:log", shot("r1"));
    expect(listener).toHaveBeenCalledTimes(1);
  });
});
