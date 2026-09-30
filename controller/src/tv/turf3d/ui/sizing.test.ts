// controller/src/tv/turf3d/ui/sizing.test.ts
import { describe, expect, it } from 'vitest'
import { CARD, FOOT_H, HEAD_H, REF_H, TEXT_MIN, bodyOf, fitFont, stepToward, textPx, worldPerPx } from './sizing'

describe('text minimums', () => {
  it('never lets text go below its kind\'s minimum, and never shrinks bigger text', () => {
    expect(TEXT_MIN).toEqual({ hero: 72, title: 56, body: 28, label: 22 })
    expect(textPx('body', 12)).toBe(28)
    expect(textPx('label', 10)).toBe(22)
    expect(textPx('hero', 96)).toBe(96)
    expect(textPx('body', 40)).toBe(40)
  })
})

describe('worldPerPx', () => {
  it('is the world height a reference pixel covers at a distance', () => {
    expect(worldPerPx(38, 9, REF_H)).toBeCloseTo(0.0057388, 6)
    expect(worldPerPx(38, 18, REF_H)).toBeCloseTo(2 * worldPerPx(38, 9, REF_H), 9)
  })
})

describe('fitFont', () => {
  it('shrinks with length but stays inside [min, max]', () => {
    expect(fitFont(5, 600, 96, 72)).toBe(96)
    expect(fitFont(20, 600, 96, 72)).toBe(72)
    expect(fitFont(12, 600, 96, 40)).toBe(96)
    expect(fitFont(30, 600, 96, 40)).toBe(40)
    expect(fitFont(0, 600, 96, 72)).toBe(96)
  })
})

describe('the card', () => {
  it('has a standard size that fits inside the printed middle of the board, and a bigger setup size', () => {
    expect(CARD.std).toEqual({ w: 610, h: 395 })
    expect(CARD.setup.w).toBeGreaterThan(CARD.std.w)
    expect(CARD.setup.h).toBeGreaterThan(CARD.std.h)
  })
  it('splits into a header, a body and a footer', () => {
    const b = bodyOf('std')
    expect(b.h).toBe(CARD.std.h - HEAD_H - FOOT_H)
    expect(b.cy).toBe((FOOT_H - HEAD_H) / 2)
    expect(b.w).toBe(CARD.std.w - 40)
    expect(bodyOf('setup').h).toBeGreaterThan(b.h)
  })
})

describe('stepToward', () => {
  it('moves toward the target without overshooting, and snaps when close', () => {
    let v = 0
    for (let i = 0; i < 300; i++) { const n = stepToward(v, 1, 1 / 60); expect(n).toBeGreaterThanOrEqual(v); expect(n).toBeLessThanOrEqual(1); v = n }
    expect(v).toBe(1)
    expect(stepToward(1, 0, 1 / 60)).toBeLessThan(1)
    expect(stepToward(0.5, 0.5, 1 / 60)).toBe(0.5)
    expect(stepToward(0, 1, 10)).toBeGreaterThan(0.99) // a long frame cannot overshoot either
    expect(stepToward(0, 1, 0)).toBe(0)
  })
})
