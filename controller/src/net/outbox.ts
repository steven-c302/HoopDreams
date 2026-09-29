import type { ClientMsg } from '../protocol'

type Sendable = Extract<ClientMsg, { t: 'action' } | { t: 'host' }>

/**
 * Actions sent but not yet acknowledged. They are resent after a reconnect (the server dedupes by id)
 * and dropped on ack or reject, so a stale action is never retried in a loop.
 */
export class Outbox {
  private items: Sendable[] = []

  add(m: Sendable) {
    // A newer answer of the same kind in the same round supersedes the old one. Actions aimed at a target (Home Turf's
    // build and mortgage taps) or a resource (Sprawl's Windfall picks and bank trades) are separate moves, so they all stay.
    if (m.t === 'action' && m.payload.target === undefined && m.payload.res === undefined && m.payload.give === undefined) {
      this.items = this.items.filter((o) => !(o.t === 'action' && o.round === m.round && o.payload.kind === m.payload.kind))
    }
    this.items.push(m)
  }

  settle(id: string) {
    this.items = this.items.filter((m) => m.id !== id)
  }

  pending(): Sendable[] {
    return [...this.items]
  }
}
