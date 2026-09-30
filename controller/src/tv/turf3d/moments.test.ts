// controller/src/tv/turf3d/moments.test.ts
import { describe, expect, it } from 'vitest'
import { spacePos } from './layout'
import { barsDrop, coinArc, coinBurst, coinPos, coinRain, coinsFor, fallPose, flipPose, popScale, type Moment } from './moments'

describe('coin paths', () => {
  it('an arc runs from the payer to the owner and peaks half way', () => {
    expect(coinArc(0, [0, 1, 0], [4, 1, 2], 1.6)).toEqual([0, 1, 0])
    expect(coinArc(1, [0, 1, 0], [4, 1, 2], 1.6)[0]).toBeCloseTo(4, 9)
    const mid = coinArc(0.5, [0, 1, 0], [4, 1, 2], 1.6)
    expect(mid[0]).toBeCloseTo(2, 9); expect(mid[1]).toBeCloseTo(2.6, 9); expect(mid[2]).toBeCloseTo(1, 9)
  })
  it('a burst throws coins up and out and brings them back to the start height', () => {
    expect(coinBurst(0, 12, 0, [1, 0.5, 1])[1]).toBeCloseTo(0.5, 9)
    expect(coinBurst(0, 12, 1, [1, 0.5, 1])[1]).toBeCloseTo(0.5, 9)
    expect(coinBurst(3, 12, 0.5, [1, 0.5, 1])[1]).toBeGreaterThan(1.5)
    const a = coinBurst(0, 12, 1, [0, 0, 0]), b = coinBurst(6, 12, 1, [0, 0, 0])
    expect(Math.hypot(a[0] - b[0], a[2] - b[2])).toBeGreaterThan(1) // opposite sides of the fan
  })
  it('rain falls from three units up to the ground, scattered inside a small patch', () => {
    expect(coinRain(5, 24, 0, [2, 0.2, 2])[1]).toBeCloseTo(3.2, 9)
    expect(coinRain(5, 24, 1, [2, 0.2, 2])[1]).toBeCloseTo(0.2, 9)
    for (let i = 0; i < 24; i++) {
      const p = coinRain(i, 24, 0.5, [2, 0.2, 2])
      expect(Math.abs(p[0] - 2)).toBeLessThanOrEqual(0.75); expect(Math.abs(p[2] - 2)).toBeLessThanOrEqual(0.75)
    }
  })
})

describe('shapes over time', () => {
  it('a card flips from face-down to face-up, rises, and fades at the end', () => {
    expect(flipPose(0)).toEqual({ rotX: 0, y: 0.5, opacity: 1 })
    expect(flipPose(0.6).rotX).toBeCloseTo(Math.PI, 5)
    expect(flipPose(0.5).y).toBeGreaterThan(flipPose(0).y)
    expect(flipPose(1).opacity).toBeCloseTo(0, 9)
    expect(flipPose(0.7).opacity).toBe(1)
  })
  it('a fallen piece tips over, slides off the edge and drops below the table', () => {
    expect(fallPose(0)).toEqual({ rotZ: 0, x: 0, y: 0 })
    const end = fallPose(1)
    expect(end.rotZ).toBeCloseTo(Math.PI / 2, 9); expect(end.x).toBeGreaterThan(0.5); expect(end.y).toBeLessThan(-2)
    expect(fallPose(0.3).y).toBe(0)
  })
  it('the cage drops from above and lands at zero', () => {
    expect(barsDrop(0)).toBeCloseTo(2.2, 9); expect(barsDrop(1)).toBe(0); expect(barsDrop(2)).toBe(0)
    expect(barsDrop(0.5)).toBeLessThan(barsDrop(0.25))
  })
  it('a new house pops in from nothing, overshoots, and settles at full size', () => {
    expect(popScale(0)).toBe(0); expect(popScale(1)).toBeCloseTo(1, 9); expect(popScale(0.6)).toBeGreaterThan(1.1)
  })
})

describe('coinsFor', () => {
  const shown = [0, 5, 12, 14, 9, 36]
  const rent: Moment = { type: 'rent', from: 4, to: 1, amount: 600 }

  it('sends a rent stream from the payer\'s space to the owner\'s, staggered', () => {
    const coins = coinsFor(rent, shown, 1000)
    expect(coins).toHaveLength(14)
    expect(coins.every((c) => c.kind === 'arc')).toBe(true)
    expect(coins[0].t0).toBe(1000); expect(coins[13].t0).toBe(1000 + 13 * 45)
    const p = spacePos(9), q = spacePos(5)
    expect(coins[0].from).toEqual([p.x, 0.9, p.z]); expect(coins[0].to).toEqual([q.x, 0.9, q.z])
  })
  it('bursts at the payer for tax and rains over Payday for a pass', () => {
    const tax = coinsFor({ type: 'tax', token: 3, amount: 200 }, shown, 0)
    expect(tax).toHaveLength(12); expect(tax.every((c) => c.kind === 'burst')).toBe(true)
    const rain = coinsFor({ type: 'payday', token: 3 }, shown, 0)
    expect(rain).toHaveLength(24); expect(rain.every((c) => c.kind === 'rain')).toBe(true)
    const p = spacePos(0); expect(rain[0].from[0]).toBeCloseTo(p.x, 9)
  })
  it('makes no coins for a token whose space is unknown, and none for moments that are not coins', () => {
    expect(coinsFor({ type: 'rent', from: 9, to: 1, amount: 5 }, shown, 0)).toEqual([])
    expect(coinsFor({ type: 'tax', token: -1, amount: 5 }, shown, 0)).toEqual([])
    expect(coinsFor({ type: 'card', token: 1 }, shown, 0)).toEqual([])
    expect(coinsFor({ type: 'fall', token: 1 }, shown, 0)).toEqual([])
    expect(coinsFor(rent, [0, Number.NaN, 2, 3, 4, 5], 0)).toEqual([]) // the owner (token 1) has a NaN space: no coins, no NaN positions
  })
  it('reports no position before a coin starts or after it ends', () => {
    const [c] = coinsFor(rent, shown, 1000)
    expect(coinPos(c, 999)).toBeNull(); expect(coinPos(c, 1000 + 901)).toBeNull()
    expect(coinPos(c, 1450)).not.toBeNull()
  })
})
