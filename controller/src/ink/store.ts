import { applyOp } from './board'
import type { InkOp, InkSyncMsg, Stroke } from './types'

/**
 * The TV's copy of every drawing this game, built from the server's `inkSync` and `ink` messages. Batches carry the
 * server's running number, so a batch already contained in a sync is ignored and a reconnecting TV never doubles a stroke.
 */
export class InkStore {
  private byTurn = new Map<number, Stroke[]>()
  private upTo = 0
  private subs = new Set<() => void>()
  /** Bumps on every change; canvases repaint when it moves. */
  version = 0

  strokes(turn: number): Stroke[] {
    return this.byTurn.get(turn) ?? []
  }

  turns(): number[] {
    return [...this.byTurn.keys()].sort((a, b) => a - b)
  }

  applyEvent(m: { turn: number; n: number; ops: InkOp[] }) {
    if (m.n <= this.upTo) return
    this.upTo = m.n
    let list = this.byTurn.get(m.turn)
    if (!list) this.byTurn.set(m.turn, (list = []))
    for (const op of m.ops) applyOp(list, op)
    this.bump()
  }

  applySync(m: InkSyncMsg) {
    this.byTurn.clear()
    for (const t of m.turns) this.byTurn.set(t.turn, t.strokes.map((s) => ({ s: s.s, c: s.c, w: s.w, pts: [...s.pts], open: s.open ?? false })))
    this.upTo = m.upTo
    this.bump()
  }

  /** The game ended: forget the drawings. What it has seen so far is kept, so late batches stay ignored. */
  reset() {
    this.byTurn.clear()
    this.bump()
  }

  subscribe(fn: () => void): () => void {
    this.subs.add(fn)
    return () => { this.subs.delete(fn) }
  }

  private bump() {
    this.version += 1
    for (const fn of this.subs) fn()
  }
}
