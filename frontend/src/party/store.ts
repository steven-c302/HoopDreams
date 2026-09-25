import { useEffect, useState, useSyncExternalStore } from "react";
import type { HostState, MomentEnvelope, PublicState } from "./contract.gen";
import { getSocket } from "./socket";

export type Moment = MomentEnvelope["moment"];

let state: PublicState | null = null;
let hostState: HostState | null = null;
let offsetMs = 0;
let connected = false;
let wired = false;
const listeners = new Set<() => void>();
const momentListeners = new Set<(moment: Moment) => void>();

function notify(): void {
  for (const listener of listeners) listener();
}

function wire(): void {
  if (wired) return;
  wired = true;
  const socket = getSocket();
  connected = socket.connected;
  socket.on("connect", () => {
    connected = true;
    notify();
  });
  socket.on("disconnect", () => {
    connected = false;
    notify();
  });
  socket.on("state", (next: PublicState) => {
    state = next;
    offsetMs = next.now - Date.now();
    notify();
  });
  socket.on("host_state", (next: HostState) => {
    hostState = next;
    notify();
  });
  socket.on("moment", (moment: Moment) => {
    for (const listener of momentListeners) listener(moment);
  });
}

function subscribe(listener: () => void): () => void {
  wire();
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function usePartyState(): PublicState | null {
  return useSyncExternalStore(subscribe, () => state);
}

export function useHostState(): HostState | null {
  return useSyncExternalStore(subscribe, () => hostState);
}

export function useConnected(): boolean {
  return useSyncExternalStore(subscribe, () => connected);
}

export function onMoment(listener: (moment: Moment) => void): () => void {
  wire();
  momentListeners.add(listener);
  return () => {
    momentListeners.delete(listener);
  };
}

/** The server's clock, so countdowns agree across phones and the TV. */
export function serverNow(): number {
  return Date.now() + offsetMs;
}

export function useNow(intervalMs = 1000): number {
  const [now, setNow] = useState(serverNow);
  useEffect(() => {
    const id = setInterval(() => setNow(serverNow()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs]);
  return now;
}
