// controller/src/tv/turf3d/useChoreography.test.ts
import { describe, expect, it } from 'vitest'
import type { TurfTv } from '../types'
import { applyCue, initialSeen, restCraft, snapFor, type Counters } from './useChoreography'

const tv = (pos: number[], seqs: number[] = []): TurfTv => ({
  tokens: pos.map((p) => ({ pos: p })), beats: seqs.map((seq) => ({ seq, kind: 'x', token: 0, other: -1, space: -1, amount: 0, dice: [], path: [], tokens: [], sips: 0 })),
}) as unknown as TurfTv

describe('choreography helpers', () => {
  it('starts already caught up: a TV that joins mid-game does not replay old beats', () => {
    expect(initialSeen(tv([0], [4, 9, 7]))).toBe(9)
    expect(initialSeen(tv([0], []))).toBe(0)
  })
  it('rests pieces where the engine says they are', () => {
    const c = restCraft(tv([3, 14, 0]))
    expect(c.shown).toEqual([3, 14, 0]); expect(c.hop).toEqual([null, null, null]); expect(c.shot).toBe('wide')
    expect(c.dice).toBeNull(); expect(c.moments).toEqual([])
  })
  it('snaps only when shown positions are wrong, and returns the same object when they are right', () => {
    const g = tv([3, 14])
    const right = restCraft(g)
    expect(snapFor(right, g)).toBe(right)
    const stale = { ...right, shown: [3, 9] }
    expect(snapFor(stale, g).shown).toEqual([3, 14])
    expect(snapFor({ ...right, shown: [3] }, g).shown).toEqual([3, 14]) // a token joined
  })
})

describe('applyCue', () => {
  const counters = (): Counters => ({ hop: 0, moment: 0, dice: 0 })
  const g = tv([0, 5, 9])

  it('moves a token and numbers each hop', () => {
    const n = counters()
    const a = applyCue(restCraft(g), { at: 0, kind: 'hop', token: 1, space: 6, ms: 230, height: 0.7, last: false }, n)
    expect(a.shown).toEqual([0, 6, 9]); expect(a.hop[1]).toEqual({ n: 1, ms: 230, height: 0.7, last: false })
    const b = applyCue(a, { at: 0, kind: 'hop', token: 1, space: 7, ms: 230, height: 0.7, last: true }, n)
    expect(b.hop[1]?.n).toBe(2)
  })

  it('snaps a token into place with no hop', () => {
    const c = applyCue(restCraft(g), { at: 0, kind: 'snap', token: 2, space: 10 }, counters())
    expect(c.shown[2]).toBe(10); expect(c.hop[2]).toBeNull()
  })

  it('appends moments with ever-increasing ids, keeps the last eight, and keeps counting after a reset', () => {
    const n = counters()
    let c = restCraft(g)
    for (let i = 0; i < 10; i++) c = applyCue(c, { at: 0, kind: 'moment', moment: { type: 'tax', token: 0, amount: i } }, n)
    expect(c.moments).toHaveLength(8)
    expect(c.moments.map((m) => m.n)).toEqual([3, 4, 5, 6, 7, 8, 9, 10])
    c = applyCue(restCraft(g), { at: 0, kind: 'moment', moment: { type: 'card', token: 1 } }, n) // a skip resets the craft
    expect(c.moments[0].n).toBe(11) // later moments are never ignored as already handled
  })

  it('numbers dice throws and passes the seed and values through', () => {
    const n = counters()
    const a = applyCue(restCraft(g), { at: 0, kind: 'dice', values: [2, 5], seed: 77 }, n)
    expect(a.dice).toEqual({ n: 1, values: [2, 5], seed: 77 })
    expect(applyCue(a, { at: 0, kind: 'dice', values: [1, 1], seed: 78 }, n).dice?.n).toBe(2)
  })

  it('sets the shot, banner and target, and counts landings', () => {
    const n = counters()
    let c = applyCue(restCraft(g), { at: 0, kind: 'shot', shot: 'close', focus: 6 }, n)
    c = applyCue(c, { at: 0, kind: 'banner', text: '4 + 5 = 9' }, n)
    c = applyCue(c, { at: 0, kind: 'target', space: 6 }, n)
    c = applyCue(c, { at: 0, kind: 'land', token: 1, space: 6 }, n)
    expect([c.shot, c.focus, c.banner, c.target, c.landed]).toEqual(['close', 6, '4 + 5 = 9', 6, { token: 1, space: 6, n: 1 }])
  })

  it('leaves the craft alone for a sound cue', () => {
    const c = restCraft(g)
    expect(applyCue(c, { at: 0, kind: 'sfx', name: 'hop', arg: 0 }, counters())).toBe(c)
  })
})
