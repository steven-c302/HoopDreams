// controller/src/tv/turf3d/drinkSpecs.test.ts
import { describe, expect, it } from 'vitest'
import { BOTTLES, CAN_LABEL, DRINK_KINDS, DRINK_NAMES, labelStrings } from './drinkSpecs'

const DENY = ['smirnoff', 'absolut', 'jinro', 'chamisul', 'budweiser', 'heineken', 'solo']

describe('drink specs', () => {
  it('has six drinks, each with a name', () => {
    expect([...DRINK_KINDS]).toEqual(['soju', 'vodka', 'beer', 'can', 'shot', 'cup'])
    for (const k of DRINK_KINDS) expect(DRINK_NAMES[k].length).toBeGreaterThan(2)
  })

  it('prints only generic words: nothing on the deny-list appears on any label or name', () => {
    const strings = labelStrings().map((s) => s.toLowerCase())
    expect(strings.length).toBeGreaterThanOrEqual(10)
    for (const s of strings) for (const bad of DENY) expect(s).not.toContain(bad)
  })

  it('keeps every bottle label on the straight part of its body', () => {
    for (const b of Object.values(BOTTLES)) {
      expect(b.label.y0).toBeGreaterThan(0)
      expect(b.label.y1).toBeLessThan(b.body)
      expect(b.label.y0).toBeLessThan(b.label.y1)
      expect(b.neck).toBeLessThan(b.r)
    }
    expect(CAN_LABEL.top).toBe('LAGER')
  })
})
