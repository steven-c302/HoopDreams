import { describe, expect, it } from 'vitest'
import { InkBatcher, chunkOps, pressureOf, strokeOps, toGrid } from './codec'
import type { Stroke } from './types'

describe('InkBatcher', () => {
  it('turns a stroke into start, pts and end', () => {
    const b = new InkBatcher()
    b.start(1, 2, 1, 10, 20, 50)
    b.point(11, 21, 50); b.point(12, 22, 60)
    b.end()
    expect(b.take()).toEqual([
      { t: 'start', s: 1, c: 2, w: 1, x: 10, y: 20, p: 50 },
      { t: 'pts', s: 1, pts: [11, 21, 50, 12, 22, 60] },
      { t: 'end', s: 1 },
    ])
    expect(b.take()).toEqual([])
  })

  it('keeps ops in order around points', () => {
    const b = new InkBatcher()
    b.start(1, 0, 0, 1, 1, 50); b.point(2, 2, 50); b.op({ t: 'undo' }); b.start(2, 0, 0, 3, 3, 50); b.point(4, 4, 50)
    expect(b.take().map((o) => o.t)).toEqual(['start', 'pts', 'undo', 'start', 'pts'])
  })

  it('flushes long runs so no pts op passes 300 numbers', () => {
    const b = new InkBatcher()
    b.start(1, 0, 0, 0, 0, 50)
    for (let i = 0; i < 250; i++) b.point(i % 1000, 5, 50)
    const pts = b.take().filter((o) => o.t === 'pts')
    expect(pts.length).toBeGreaterThan(1)
    for (const o of pts) expect((o as { pts: number[] }).pts.length).toBeLessThanOrEqual(300)
  })
})

describe('strokeOps and chunkOps', () => {
  it('rebuilds a stroke as ops, splitting long point lists and leaving open strokes open', () => {
    const st: Stroke = { s: 5, c: 1, w: 2, pts: Array.from({ length: 3 * 250 }, (_, i) => (i % 3 === 2 ? 50 : i % 900)), open: true }
    const ops = strokeOps(st)
    expect(ops[0]).toEqual({ t: 'start', s: 5, c: 1, w: 2, x: 0, y: 1, p: 50 })
    expect(ops.some((o) => o.t === 'end')).toBe(false)
    const numbers = ops.filter((o) => o.t === 'pts').flatMap((o) => (o as { pts: number[] }).pts)
    expect(numbers).toEqual(st.pts.slice(3))
    expect(strokeOps({ ...st, open: false }).at(-1)).toEqual({ t: 'end', s: 5 })
  })

  it('chunks ops for the 64-op server limit', () => {
    const ops = Array.from({ length: 130 }, () => ({ t: 'undo' as const }))
    expect(chunkOps(ops).map((c) => c.length)).toEqual([60, 60, 10])
    expect(chunkOps([])).toEqual([])
  })
})

describe('pointer helpers', () => {
  const rect = { left: 100, top: 50, width: 400, height: 300 } as DOMRect
  it('maps client pixels onto the 1000 by 750 grid and clamps', () => {
    expect(toGrid(300, 200, rect)).toEqual([500, 375])
    expect(toGrid(0, 0, rect)).toEqual([0, 0])
    expect(toGrid(9999, 9999, rect)).toEqual([1000, 750])
  })
  it('uses pen pressure only when there is a pen', () => {
    expect(pressureOf({ pointerType: 'pen', pressure: 0.8 })).toBe(80)
    expect(pressureOf({ pointerType: 'pen', pressure: 0 })).toBe(50)
    expect(pressureOf({ pointerType: 'touch', pressure: 1 })).toBe(50)
    expect(pressureOf({ pointerType: 'mouse', pressure: 0.5 })).toBe(50)
  })
})
