// controller/src/tv/turf3d/layout.test.ts
import { describe, expect, it } from 'vitest'
import { CORNER, EDGE_W, HALF, crowdIndex, crowdSlot, sideOf, spacePos, tileOf } from './layout'

const all = Array.from({ length: 40 }, (_, i) => tileOf(i))

describe('board layout', () => {
  it('gives every space a distinct centre', () => {
    const keys = new Set(all.map((t) => `${t.cx.toFixed(3)},${t.cz.toFixed(3)}`))
    expect(keys.size).toBe(40)
  })

  it('tiles the ring exactly: four corners plus 36 edge tiles', () => {
    const area = all.reduce((sum, t) => sum + t.ex * t.ez, 0)
    const expected = (HALF * 2) ** 2 - (HALF * 2 - CORNER * 2) ** 2
    expect(area).toBeCloseTo(expected, 6)
    expect(EDGE_W * 9 + CORNER * 2).toBeCloseTo(HALF * 2, 9)
  })

  it('makes neighbouring spaces share an edge', () => {
    for (let i = 0; i < 40; i++) {
      const a = all[i], b = all[(i + 1) % 40]
      const gapX = Math.abs(a.cx - b.cx) - (a.ex + b.ex) / 2
      const gapZ = Math.abs(a.cz - b.cz) - (a.ez + b.ez) / 2
      expect(gapX).toBeLessThan(1e-9)
      expect(gapZ).toBeLessThan(1e-9)
      // On one axis they touch exactly (gap 0); on the other they overlap (gap below zero).
      expect(Math.max(gapX, gapZ)).toBeGreaterThan(-1e-9)
    }
  })

  it('puts the corners where the board says: Payday bottom right, then clockwise as seen from the couch', () => {
    expect(tileOf(0).cx).toBeGreaterThan(0); expect(tileOf(0).cz).toBeGreaterThan(0)
    expect(tileOf(10).cx).toBeLessThan(0); expect(tileOf(10).cz).toBeGreaterThan(0)
    expect(tileOf(20).cx).toBeLessThan(0); expect(tileOf(20).cz).toBeLessThan(0)
    expect(tileOf(30).cx).toBeGreaterThan(0); expect(tileOf(30).cz).toBeLessThan(0)
    expect([0, 9, 10, 19, 20, 29, 30, 39].map(sideOf)).toEqual(['bottom', 'bottom', 'left', 'left', 'top', 'top', 'right', 'right'])
  })

  it('points every edge tile\'s inward vector at the middle of the board', () => {
    for (const t of all.filter((x) => !x.corner)) {
      expect(-t.cx * t.inward.x + -t.cz * t.inward.z).toBeGreaterThan(0)
    }
  })

  it('wraps and gives spacePos the tile centre', () => {
    expect(spacePos(40)).toEqual(spacePos(0))
    expect(spacePos(5)).toEqual({ x: tileOf(5).cx, z: tileOf(5).cz })
  })
})

describe('crowds on one space', () => {
  it('keeps up to six pieces in distinct slots that stay inside the smallest tile', () => {
    for (let n = 1; n <= 6; n++) {
      const slots = Array.from({ length: n }, (_, k) => crowdSlot(k, n))
      for (let a = 0; a < n; a++) {
        expect(Math.abs(slots[a].dx)).toBeLessThanOrEqual(EDGE_W / 2)
        for (let b = a + 1; b < n; b++) expect(Math.hypot(slots[a].dx - slots[b].dx, slots[a].dz - slots[b].dz)).toBeGreaterThanOrEqual(0.3)
      }
    }
    expect(crowdSlot(0, 1)).toEqual({ dx: 0, dz: 0 })
  })

  it('ranks pieces on the same space and ignores the bankrupt', () => {
    const idx = crowdIndex([0, 0, 0, 5, 0, 5], [true, true, true, true, false, true])
    // Token 4 is bankrupt and hidden, so its own entry is irrelevant; the living counts must not include it.
    expect(idx.filter((_, k) => k !== 4).map((x) => x.count)).toEqual([3, 3, 3, 2, 2])
    expect(idx.slice(0, 3).map((x) => x.rank)).toEqual([0, 1, 2])
    expect([idx[3].rank, idx[5].rank]).toEqual([0, 1])
  })
})
