import { describe, expect, it, vi } from 'vitest'
import { InkStore } from './store'

const stroke = { t: 'start' as const, s: 1, c: 0, w: 0, x: 1, y: 2, p: 50 }

describe('InkStore', () => {
  it('builds a turn from live batches', () => {
    const s = new InkStore()
    s.applyEvent({ turn: 1, n: 1, ops: [stroke] })
    s.applyEvent({ turn: 1, n: 2, ops: [{ t: 'pts', s: 1, pts: [3, 4, 50] }, { t: 'end', s: 1 }] })
    expect(s.strokes(1)).toEqual([{ s: 1, c: 0, w: 0, pts: [1, 2, 50, 3, 4, 50], open: false }])
    expect(s.turns()).toEqual([1])
  })

  it('a sync replaces everything and live batches it already contains are skipped', () => {
    const s = new InkStore()
    s.applyEvent({ turn: 1, n: 1, ops: [stroke] })
    s.applySync({ turns: [{ turn: 1, strokes: [{ s: 1, c: 0, w: 0, pts: [1, 2, 50, 3, 4, 50], open: true }] }], upTo: 2 })
    s.applyEvent({ turn: 1, n: 2, ops: [{ t: 'pts', s: 1, pts: [9, 9, 50] }] }) // already in the sync
    expect(s.strokes(1)[0].pts).toEqual([1, 2, 50, 3, 4, 50])
    s.applyEvent({ turn: 1, n: 3, ops: [{ t: 'pts', s: 1, pts: [5, 6, 50] }] })
    expect(s.strokes(1)[0].pts).toEqual([1, 2, 50, 3, 4, 50, 5, 6, 50])
    s.applyEvent({ turn: 1, n: 4, ops: [{ t: 'end', s: 1 }] })
    expect(s.strokes(1)[0].open).toBe(false)
  })

  it('a synced stroke with no open flag is closed', () => {
    const s = new InkStore()
    s.applySync({ turns: [{ turn: 1, strokes: [{ s: 1, c: 0, w: 0, pts: [1, 2, 50] }] }], upTo: 1 })
    expect(s.strokes(1)[0].open).toBe(false)
  })

  it('a stroke the server said was open stays open', () => {
    const s = new InkStore()
    s.applySync({ turns: [{ turn: 2, strokes: [{ s: 1, c: 0, w: 0, pts: [1, 2, 50], open: true }] }], upTo: 1 })
    expect(s.strokes(2)[0].open).toBe(true)
  })

  it('reset forgets the drawings but not how far it got', () => {
    const s = new InkStore()
    s.applyEvent({ turn: 1, n: 5, ops: [stroke] })
    s.reset()
    expect(s.turns()).toEqual([])
    s.applyEvent({ turn: 1, n: 4, ops: [stroke] }) // older than what it saw: ignored
    expect(s.turns()).toEqual([])
  })

  it('tells subscribers when anything changes and lets them stop listening', () => {
    const s = new InkStore()
    const fn = vi.fn()
    const off = s.subscribe(fn)
    s.applyEvent({ turn: 1, n: 1, ops: [stroke] })
    s.applySync({ turns: [], upTo: 1 })
    s.reset()
    expect(fn).toHaveBeenCalledTimes(3)
    off()
    s.applyEvent({ turn: 1, n: 2, ops: [stroke] })
    expect(fn).toHaveBeenCalledTimes(3)
  })
})
