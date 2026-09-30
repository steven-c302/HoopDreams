// controller/src/tv/turf3d/showQueue.test.ts
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { TurfBeat } from '../types'
import type { Cue } from './timeline'
import { MAX_SHOW_MS, ShowQueue, shouldSnap } from './showQueue'

const hop = (at: number, space = 1): Cue => ({ at, kind: 'hop', token: 0, space, ms: 200, height: 1, last: false })
const beat = (seq: number): TurfBeat => ({ seq, kind: 'move', token: 0, other: -1, space: -1, amount: 0, dice: [], path: [1], tokens: [], sips: 0 })
const spaces = (cues: Cue[]) => cues.map((c) => (c.kind === 'hop' ? c.space : -1))

beforeEach(() => { vi.useFakeTimers() })
afterEach(() => { vi.useRealTimers() })

function make() {
  const applied: Cue[] = []
  const snap = vi.fn()
  const q = new ShowQueue((c) => applied.push(c), snap, () => Date.now())
  return { q, applied, snap }
}

describe('ShowQueue', () => {
  it('plays cues at their offsets and snaps once, just after the show ends', () => {
    const { q, applied, snap } = make()
    q.enqueue({ cues: [hop(0), hop(500, 2)], totalMs: 1000 })
    vi.advanceTimersByTime(499); expect(applied).toHaveLength(1)
    vi.advanceTimersByTime(1); expect(applied).toHaveLength(2)
    vi.advanceTimersByTime(549); expect(snap).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1); expect(snap).toHaveBeenCalledTimes(1)
  })

  it('queues a second show behind the first, and the first show\'s end-snap never cuts into it', () => {
    const { q, applied, snap } = make()
    q.enqueue({ cues: [hop(0, 1)], totalMs: 1000 })
    vi.advanceTimersByTime(900)
    q.enqueue({ cues: [hop(0, 7)], totalMs: 1000 }) // starts at t=1000, ends at t=2000
    vi.advanceTimersByTime(150) // t=1050: where the first show's snap used to fire
    expect(snap).not.toHaveBeenCalled()
    expect(spaces(applied)).toEqual([1, 7])
    vi.advanceTimersByTime(1000) // t=2050
    expect(snap).toHaveBeenCalledTimes(1)
  })

  it('reports busy while a show runs', () => {
    const { q } = make()
    expect(q.isBusy()).toBe(false)
    q.enqueue({ cues: [hop(0)], totalMs: 1000 })
    expect(q.isBusy()).toBe(true)
    vi.advanceTimersByTime(1000)
    expect(q.isBusy()).toBe(false)
  })

  it('skip drops every pending cue and the end-snap, and frees the queue', () => {
    const { q, applied, snap } = make()
    q.enqueue({ cues: [hop(0), hop(400, 2), hop(800, 3)], totalMs: 1200 })
    vi.advanceTimersByTime(100)
    q.skip()
    vi.advanceTimersByTime(5000)
    expect(spaces(applied)).toEqual([1])
    expect(snap).not.toHaveBeenCalled()
    expect(q.isBusy()).toBe(false)
  })
})

describe('shouldSnap', () => {
  it('lets a normal, contiguous update play', () => {
    expect(shouldSnap(10, [beat(11), beat(12)], 6000)).toBe(false)
  })
  it('snaps when beats were missed (a gap in the sequence), so a reconnect does not replay old moves', () => {
    expect(shouldSnap(10, [beat(15), beat(16)], 6000)).toBe(true)
  })
  it('snaps when one update holds more show than a person could sit through', () => {
    expect(shouldSnap(10, [beat(11)], MAX_SHOW_MS + 1)).toBe(true)
    expect(shouldSnap(10, [beat(11)], MAX_SHOW_MS)).toBe(false)
  })
  it('has nothing to snap for when there are no fresh beats', () => {
    expect(shouldSnap(10, [], 99_999)).toBe(false)
  })
})
