import { describe, expect, it } from 'vitest'
import { conduct, START, stopInMs, type ClipView } from './clipConductor'

const view = (o: Partial<ClipView>): ClipView => ({ phase: 'load', clipSeq: 1, videoId: 'vidAAAAAAAA', startSec: 30, ...o })

describe('conduct', () => {
  it('loads the video for a new clip and remembers it', () => {
    const { next, actions } = conduct(START, view({}))
    expect(actions).toEqual([{ t: 'load', seq: 1, videoId: 'vidAAAAAAAA', startSec: 30 }])
    expect(next).toMatchObject({ loaded: 'vidAAAAAAAA', seq: 1 })
  })

  it('does nothing when the same snapshot arrives again', () => {
    const first = conduct(START, view({}))
    expect(conduct(first.next, view({})).actions).toEqual([])
  })

  it('seeks, not reloads, when the next stage is the same video', () => {
    const first = conduct(START, view({}))
    const { actions } = conduct(first.next, view({ clipSeq: 2 }))
    expect(actions).toEqual([{ t: 'seek', seq: 2, startSec: 30 }])
  })

  it('loads when the next song is a different video', () => {
    const first = conduct(START, view({}))
    const { actions } = conduct(first.next, view({ clipSeq: 2, videoId: 'vidBBBBBBBB' }))
    expect(actions).toEqual([{ t: 'load', seq: 2, videoId: 'vidBBBBBBBB', startSec: 30 }])
  })

  it('asks the engine to replay a stage it never started (a TV that reloaded mid-clip), once', () => {
    const first = conduct(START, view({ phase: 'stage', clipSeq: 5 }))
    expect(first.actions).toEqual([{ t: 'replay', seq: 5 }])
    expect(conduct(first.next, view({ phase: 'stage', clipSeq: 5 })).actions).toEqual([])
    // The engine answers with a new load for the same song; the player has nothing loaded, so it loads it.
    expect(conduct(first.next, view({ clipSeq: 6 })).actions).toEqual([{ t: 'load', seq: 6, videoId: 'vidAAAAAAAA', startSec: 30 }])
  })

  it('leaves a stage it has already started alone', () => {
    const loaded = conduct(START, view({ clipSeq: 3 }))
    expect(conduct(loaded.next, view({ phase: 'stage', clipSeq: 3 })).actions).toEqual([])
  })

  it('plays the hook once at the reveal', () => {
    const loaded = conduct(START, view({ clipSeq: 3 }))
    const revealed = conduct(loaded.next, view({ phase: 'reveal', clipSeq: 3 }))
    expect(revealed.actions).toEqual([{ t: 'hook', videoId: 'vidAAAAAAAA', startSec: 30 }])
    expect(conduct(revealed.next, view({ phase: 'reveal', clipSeq: 3 })).actions).toEqual([])
  })

  it('stops once when the game leaves the songs, and stays quiet', () => {
    const loaded = conduct(START, view({}))
    const done = conduct(loaded.next, view({ phase: 'podium', videoId: '' }))
    expect(done.actions).toEqual([{ t: 'stop' }])
    expect(conduct(done.next, view({ phase: 'podium', videoId: '' })).actions).toEqual([])
    expect(conduct(START, null).actions).toEqual([])
  })
})

describe('stopInMs', () => {
  it('follows the engine clock, frozen while paused, and falls back to the clip length', () => {
    expect(stopInMs({ deadline: 10_000, frozen: null }, 2_000, 8_500)).toBe(1_500)
    expect(stopInMs({ deadline: 10_000, frozen: null }, 2_000, 12_000)).toBe(0)
    expect(stopInMs({ deadline: null, frozen: 700 }, 2_000, 99)).toBe(700)
    expect(stopInMs({ deadline: null, frozen: null }, 2_000, 99)).toBe(2_000)
  })
})
