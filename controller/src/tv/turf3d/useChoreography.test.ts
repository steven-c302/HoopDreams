// controller/src/tv/turf3d/useChoreography.test.ts
import { describe, expect, it } from 'vitest'
import type { TurfTv } from '../types'
import { initialSeen, restCraft, snapFor } from './useChoreography'

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
