import { uuid } from "../party/ids";
import { call, type Ack } from "../party/socket";

export type Run = (action: string, payload?: Record<string, unknown>) => Promise<boolean>;

export function hostAction(action: string, payload: Record<string, unknown> = {}): Promise<Ack> {
  return call("host:action", { requestId: uuid(), action, payload });
}

const PIN_KEY = "hoop.hostPin";

/** The PIN lives in sessionStorage so a reconnect can re-authenticate without asking again. */
export function savedPin(): string | null {
  try {
    return sessionStorage.getItem(PIN_KEY);
  } catch {
    return null;
  }
}

export function savePin(pin: string | null): void {
  try {
    if (pin) sessionStorage.setItem(PIN_KEY, pin);
    else sessionStorage.removeItem(PIN_KEY);
  } catch {
    // Storage blocked: the host re-enters the PIN after a reconnect.
  }
}
