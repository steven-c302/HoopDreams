import { useEffect, useMemo, useRef, useSyncExternalStore } from "react";
import { Outbox, type KeyValueStore, type OutboxResult } from "../party/outbox";
import { call } from "../party/socket";

const memory = new Map<string, string>();
const safeStore: KeyValueStore = {
  getItem(key) {
    try {
      return localStorage.getItem(key);
    } catch {
      return memory.get(key) ?? null;
    }
  },
  setItem(key, value) {
    try {
      localStorage.setItem(key, value);
    } catch {
      memory.set(key, value);
    }
  },
};

export function useOutbox(playerId: string, onResult: (result: OutboxResult) => void) {
  const onResultRef = useRef(onResult);
  useEffect(() => {
    onResultRef.current = onResult;
  }, [onResult]);
  const outbox = useMemo(
    () =>
      new Outbox({
        store: safeStore,
        key: `hoop.outbox.${playerId}`,
        send: (event, payload) => call<Record<string, unknown>>(event, payload),
        onResult: (result) => onResultRef.current(result),
      }),
    [playerId],
  );
  useEffect(() => () => outbox.dispose(), [outbox]);
  const pending = useSyncExternalStore(outbox.subscribe, outbox.snapshot);
  return { outbox, pending };
}
