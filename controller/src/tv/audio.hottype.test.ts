import { describe, expect, it } from 'vitest'
import cues from '../../scripts/audio/cues.json'
import { sfx } from './audio'

const IDS = ['htFlip', 'htStamp', 'htPlaten', 'htPaper'] as const

describe('Hot Type sound cues', () => {
  it('has a generation prompt for every effect the TV plays', () => {
    const ids = new Set((cues.sfx as { id: string }[]).map((c) => c.id))
    for (const id of IDS) expect(ids.has(id), id).toBe(true)
  })

  it('exposes a function for each cue', () => {
    for (const id of IDS) expect(typeof (sfx as Record<string, unknown>)[id], id).toBe('function')
  })
})
