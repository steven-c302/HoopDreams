// controller/src/tv/turf3d/boardTexture.test.ts
import { describe, expect, it } from 'vitest'
import { CLASSIC, bandRect, fitSize, textRect, tileRect } from './boardTexture'

const S = 2048

describe('board texture geometry', () => {
  it('keeps every tile inside the texture and lines neighbours up edge to edge', () => {
    for (let i = 0; i < 40; i++) {
      const r = tileRect(i, S)
      expect(r.x).toBeGreaterThanOrEqual(-1e-6); expect(r.y).toBeGreaterThanOrEqual(-1e-6)
      expect(r.x + r.w).toBeLessThanOrEqual(S + 1e-6); expect(r.y + r.h).toBeLessThanOrEqual(S + 1e-6)
    }
    const a = tileRect(1, S), b = tileRect(2, S)
    expect(a.x).toBeCloseTo(b.x + b.w, 6) // space 2 sits just left of space 1 along the bottom row
    expect(a.y).toBeCloseTo(b.y, 6)
  })

  it('puts each street\'s colour band on the edge nearest the middle of the board', () => {
    const depth = CLASSIC.bandDepth * (S / 12)
    const bottom = tileRect(3, S), bBand = bandRect(3, S)!
    expect(bBand.y).toBeCloseTo(bottom.y, 6); expect(bBand.h).toBeCloseTo(depth, 6); expect(bBand.w).toBeCloseTo(bottom.w, 6)
    const top = tileRect(24, S), tBand = bandRect(24, S)!
    expect(tBand.y + tBand.h).toBeCloseTo(top.y + top.h, 6)
    const left = tileRect(13, S), lBand = bandRect(13, S)!
    expect(lBand.x + lBand.w).toBeCloseTo(left.x + left.w, 6); expect(lBand.w).toBeCloseTo(depth, 6)
    const right = tileRect(34, S), rBand = bandRect(34, S)!
    expect(rBand.x).toBeCloseTo(right.x, 6)
    expect(bandRect(0, S)).toBeNull() // corners have no band
  })

  it('leaves a text area that plus the band exactly covers the tile', () => {
    for (const i of [1, 9, 12, 19, 22, 28, 33, 38]) {
      const tile = tileRect(i, S), band = bandRect(i, S)!, text = textRect(i, S)
      expect(text.w * text.h + band.w * band.h).toBeCloseTo(tile.w * tile.h, 3)
    }
    expect(textRect(0, S)).toEqual(tileRect(0, S))
  })
})

describe('fitSize', () => {
  const measure = (text: string, size: number) => text.length * size * 0.5
  it('returns the start size when everything fits', () => { expect(fitSize(measure, ['ABC'], 500, 40, 10)).toBe(40) })
  it('steps down until the widest word fits', () => {
    const s = fitSize(measure, ['SHORT', 'KAISHUNS'], 100, 56, 10)
    expect(measure('KAISHUNS', s)).toBeLessThanOrEqual(100)
    expect(measure('KAISHUNS', s + 2)).toBeGreaterThan(100)
  })
  it('never goes below the minimum, even if the word cannot fit', () => { expect(fitSize(measure, ['EXTRAORDINARILYLONG'], 20, 40, 14)).toBe(14) })
})
