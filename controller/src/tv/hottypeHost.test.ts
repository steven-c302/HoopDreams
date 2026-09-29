import { describe, expect, it } from 'vitest'
import { hostLine } from './hottypeHost'
import type { HotTypeTv } from './types'

const base: HotTypeTv = {
  t: 'hottype', phase: 'hunt', round: 1, totalRounds: 3, finalRound: false, size: 4,
  tiles: [], rail: [], wordsFound: 0, longest: 0, page: [], deltas: [], drinks: [],
}
const row = (name: string, lengths: number[]) => ({ id: name, name, count: lengths.length, score: 0, lengths })

describe('hostLine during play', () => {
  it('has a line for ready and press', () => {
    expect(hostLine({ ...base, phase: 'ready' })).toBe('Same board for everyone. Fingers ready.')
    expect(hostLine({ ...base, phase: 'press' })).toBe('Pencils down. Nothing more gets stamped.')
  })

  it('says so when nobody has a word', () => {
    const g = { ...base, rail: [row('Ava', []), row('Ben', [])] }
    expect(hostLine(g)).toBe('Nobody has a word yet. The board is right there.')
  })

  it('names the only player with the longest word once it is 7 letters or more', () => {
    const g = { ...base, rail: [row('Ava', [3, 7]), row('Ben', [4])], wordsFound: 3, longest: 7 }
    expect(hostLine(g)).toBe('Ava has the longest word so far: 7 letters.')
  })

  it('does not single anyone out when two players tie for the longest', () => {
    const g = { ...base, rail: [row('Ava', [7]), row('Ben', [7])], wordsFound: 2, longest: 7 }
    expect(hostLine(g)).toBe('2 words found so far. Plenty left.')
  })

  it('names a player who is pulling ahead by two words or more', () => {
    const g = { ...base, rail: [row('Ava', [3, 3, 4, 3]), row('Ben', [3])], wordsFound: 5, longest: 4 }
    expect(hostLine(g)).toBe('Ava is pulling ahead with 4 words.')
  })

  it('falls back to the running count', () => {
    const g = { ...base, rail: [row('Ava', [3, 4]), row('Ben', [3, 3])], wordsFound: 4, longest: 4 }
    expect(hostLine(g)).toBe('4 words found so far. Plenty left.')
  })
})
