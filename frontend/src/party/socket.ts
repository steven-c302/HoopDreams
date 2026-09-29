import { io, type Socket } from "socket.io-client";

export type Ack<T extends object = object> = ({ ok: true } & T) | { ok: false; error: string; retryable?: boolean };

let socket: Socket | null = null;

/** One Socket.IO connection per tab, on the page's own origin (Vite proxies it in dev). */
export function getSocket(): Socket {
  socket ??= io({ reconnectionDelayMax: 2000 });
  return socket;
}

/** Emit and wait for the server's {ok, ...} ack. No answer comes back as a retryable failure. */
export async function call<T extends object = object>(event: string, payload: unknown, timeoutMs = 5000): Promise<Ack<T>> {
  try {
    return (await getSocket().timeout(timeoutMs).emitWithAck(event, payload)) as Ack<T>;
  } catch {
    return { ok: false, error: "No answer from the party server", retryable: true };
  }
}
