// controller/src/tv/turf3d/timeline.test.ts
import { describe, expect, it } from 'vitest'
import type { TurfBeat } from '../types'
import { DICE_BANNER_MS, DICE_MS, LAND_PAD_MS, hopMs, moveDwellMs, planBeats, walkMs, type Cue } from './timeline'

let seq = 0
const beat = (kind: string, b: Partial<TurfBeat> = {}): TurfBeat =>
  ({ seq: ++seq, kind, token: -1, other: -1, space: -1, amount: 0, dice: [], path: [], tokens: [], sips: 0, ...b })
const walk = (from: number, n: number) => Array.from({ length: n }, (_, i) => (from + 1 + i) % 40)
type Hop = Extract<Cue, { kind: 'hop' }>
const hops = (p: { cues: Cue[] }) => p.cues.filter((c): c is Hop => c.kind === 'hop')

describe('pacing formula (the Kotlin HomeTurfTest carries the same table)', () => {
  it('matches the agreed values', () => {
    expect(moveDwellMs(3, true, true)).toBe(2180)
    expect(moveDwellMs(7, false, true)).toBe(3220)
    expect(moveDwellMs(3, true, false)).toBe(5100)
    expect(moveDwellMs(7, true, false)).toBe(6020)
    expect(moveDwellMs(7, false, false)).toBe(3420)
    expect(moveDwellMs(1, false, false)).toBe(1720)
    expect(DICE_MS).toBe(2600); expect(LAND_PAD_MS).toBe(900)
  })
  it('walks 230 ms per early hop, then slows for the last three; Quick is a flat 260', () => {
    expect(Array.from({ length: 7 }, (_, i) => hopMs(i, 7, false))).toEqual([230, 230, 230, 230, 320, 460, 820])
    expect(Array.from({ length: 3 }, (_, i) => hopMs(i, 3, true))).toEqual([260, 260, 260])
    expect(walkMs(7, false)).toBe(2520)
  })
})

describe('planBeats', () => {
  it('plays a Quick move as flat hops with no camera work, ending in the old dwell', () => {
    const p = planBeats([beat('move', { token: 1, path: walk(0, 3) })], { quick: true })
    expect(hops(p).map((h) => h.at)).toEqual([0, 260, 520])
    expect(p.cues.some((c) => c.kind === 'shot')).toBe(false)
    expect(p.totalMs).toBe(3 * 260 + 1400)
  })

  it('plays a Theatre roll: dice shot and banner first, the walk after DICE_MS, a close-up for the last three hops', () => {
    const p = planBeats([beat('roll', { token: 3, dice: [4, 5, 1] }), beat('move', { token: 3, path: walk(14, 9) })], { quick: false })
    expect(p.cues[0]).toEqual({ at: 0, kind: 'shot', shot: 'dice', focus: null })
    expect(p.cues).toContainEqual({ at: DICE_BANNER_MS, kind: 'banner', text: '4 + 5 = 9' })
    expect(p.cues).toContainEqual({ at: DICE_MS, kind: 'banner', text: null })
    const h = hops(p)
    expect(h).toHaveLength(9)
    expect(h[0].at).toBe(DICE_MS)
    expect(h[8]).toMatchObject({ ms: 820, height: 1.5, last: true, space: 23 })
    const tailAt = h[6].at
    expect(p.cues).toContainEqual({ at: tailAt, kind: 'shot', shot: 'close', focus: 23 })
    expect(p.cues).toContainEqual({ at: tailAt, kind: 'target', space: 23 })
    expect(p.cues.filter((c) => c.kind === 'sfx' && c.name === 'drumroll')).toHaveLength(1)
    expect(p.cues.filter((c) => c.kind === 'sfx' && c.name === 'hop')).toHaveLength(9)
    const land = p.cues.find((c) => c.kind === 'land')!
    expect(land.at).toBe(DICE_MS + walkMs(9, false))
    expect(p.cues.at(-1)).toMatchObject({ kind: 'shot', shot: 'wide' })
    expect(p.totalMs).toBe(moveDwellMs(9, true, false))
  })

  it('skips the dice theatre for a move that did not follow a roll (a card, the bus)', () => {
    const p = planBeats([beat('move', { token: 0, path: walk(4, 5) })], { quick: false })
    expect(p.cues.some((c) => c.kind === 'shot' && c.shot === 'dice')).toBe(false)
    expect(hops(p)[0].at).toBe(0)
    expect(p.totalMs).toBe(moveDwellMs(5, false, false))
  })

  it('always totals exactly the engine dwell', () => {
    for (const quick of [true, false]) for (const diced of [true, false]) for (let n = 1; n <= 12; n++) {
      const beats = [...(diced ? [beat('roll', { token: 0, dice: [3, 4, 1] })] : []), beat('move', { token: 0, path: walk(0, n) })]
      expect(planBeats(beats, { quick }).totalMs).toBe(moveDwellMs(n, diced, quick))
    }
  })

  it('queues two moves in one update so they never overlap', () => {
    const p = planBeats([beat('move', { token: 0, path: walk(0, 3) }), beat('move', { token: 0, path: walk(3, 2) })], { quick: false })
    const h = hops(p)
    const firstEnd = walkMs(3, false) + LAND_PAD_MS
    expect(h[3].at).toBe(firstEnd)
    expect(p.totalMs).toBe(moveDwellMs(3, false, false) + moveDwellMs(2, false, false))
  })

  it('snaps a token into Timeout after its walk finishes', () => {
    const p = planBeats([beat('move', { token: 2, path: walk(28, 2) }), beat('jail', { token: 2, space: 10 })], { quick: true })
    const snap = p.cues.find((c) => c.kind === 'snap')!
    expect(snap).toMatchObject({ token: 2, space: 10 })
    expect(snap.at).toBe(p.totalMs)
  })

  it('does nothing for an empty path or a roll with no move after it', () => {
    expect(planBeats([beat('move', { token: 0, path: [] })], { quick: false })).toEqual({ cues: [], totalMs: 0 })
    expect(planBeats([beat('roll', { token: 0, dice: [2, 3, 1] })], { quick: false })).toEqual({ cues: [], totalMs: 0 })
    expect(planBeats([], { quick: true })).toEqual({ cues: [], totalMs: 0 })
  })

  it('does not treat a later, unrelated move as diced after a turn change', () => {
    const p = planBeats([beat('roll', { token: 0, dice: [2, 3, 1] }), beat('turn', { token: 1 }), beat('move', { token: 1, path: walk(5, 2) })], { quick: false })
    expect(p.cues.some((c) => c.kind === 'shot' && c.shot === 'dice')).toBe(false)
  })
})

describe('planBeats: dice cues and moments', () => {
  const moments = (p: { cues: Cue[] }) => p.cues.filter((c): c is Extract<Cue, { kind: 'moment' }> => c.kind === 'moment')

  it('adds a dice cue seeded by the roll beat, and a doubles banner', () => {
    const roll = beat('roll', { token: 3, dice: [4, 4, 1] })
    const p = planBeats([roll, beat('move', { token: 3, path: walk(0, 8) })], { quick: false })
    expect(p.cues).toContainEqual({ at: 0, kind: 'dice', values: [4, 4], seed: roll.seq })
    expect(p.cues).toContainEqual({ at: DICE_BANNER_MS, kind: 'banner', text: '4 + 4 = 8 DOUBLES!' })
    expect(DICE_BANNER_MS).toBeLessThan(DICE_MS)
  })

  it('skips the dice cue for a value no die can show, but still plans the banner', () => {
    const p = planBeats([beat('roll', { token: 3, dice: [0, 9, 1] }), beat('move', { token: 3, path: walk(0, 4) })], { quick: false })
    expect(p.cues.some((c) => c.kind === 'dice')).toBe(false)
    expect(p.cues.some((c) => c.kind === 'banner' && c.text === '0 + 9 = 9')).toBe(true)
  })

  it('plans no dice cue at Quick pace', () => {
    const p = planBeats([beat('roll', { token: 3, dice: [2, 3, 1] }), beat('move', { token: 3, path: walk(0, 5) })], { quick: true })
    expect(p.cues.some((c) => c.kind === 'dice')).toBe(false)
  })

  it('turns rent, tax, card and bankrupt beats into moments at the start of the update', () => {
    const p = planBeats([
      beat('rent', { token: 4, other: 1, space: 9, amount: 600 }),
      beat('tax', { token: 3, space: 4, amount: 200 }),
      beat('card', { token: 2, text: 'Last call' }),
      beat('bankrupt', { token: 4, other: 1 }),
    ], { quick: false })
    expect(moments(p).map((c) => [c.at, c.moment])).toEqual([
      [0, { type: 'rent', from: 4, to: 1, amount: 600 }],
      [0, { type: 'tax', token: 3, amount: 200 }],
      [0, { type: 'card', token: 2 }],
      [0, { type: 'fall', token: 4 }],
    ])
    expect(p.totalMs).toBe(0) // moments never hold the queue
  })

  it('fires the Payday rain as the piece reaches Payday, not at the end of the walk', () => {
    const p = planBeats([beat('move', { token: 3, path: [38, 39, 0, 1, 2, 3] }), beat('payday', { token: 3, amount: 200 })], { quick: false })
    expect(moments(p)).toEqual([{ at: 690, kind: 'moment', moment: { type: 'payday', token: 3 } }]) // three early hops of 230 ms
    const q = planBeats([beat('move', { token: 3, path: [38, 39, 0, 1, 2, 3] }), beat('payday', { token: 3, amount: 200 })], { quick: true })
    expect(moments(q)[0].at).toBe(780) // three hops of 260 ms
  })

  it('still fires a Payday moment when no walk came in the same update', () => {
    const p = planBeats([beat('payday', { token: 1, amount: 200 })], { quick: false })
    expect(moments(p)).toEqual([{ at: 0, kind: 'moment', moment: { type: 'payday', token: 1 } }])
  })

  it('queues a moment behind a walk in the same update', () => {
    const p = planBeats([beat('move', { token: 0, path: walk(0, 3) }), beat('rent', { token: 0, other: 1, space: 3, amount: 50 })], { quick: false })
    expect(moments(p)[0].at).toBe(moveDwellMs(3, false, false))
  })
})
