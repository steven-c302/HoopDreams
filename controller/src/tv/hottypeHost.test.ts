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
const named = (id: string) => ({ p0: 'Ava', p1: 'Ben', p2: 'Cleo' }[id] ?? '?')
const word = (w: string, finders: string[], bonus: number, longest: boolean) => ({ word: w, points: 100, bonus, finders, longest })

describe('hostLine at the reveal', () => {
  it('names who found the longest word and counts the one-player words', () => {
    const g = { ...base, phase: 'reveal' as const, page: [word('ton', ['p0', 'p1'], 0, false), word('stone', ['p0'], 800, true)] }
    expect(hostLine(g, named)).toBe('Ava found STONE. 5 letters. One stamped word was found by one player only.')
  })

  it('joins several finders of the longest word', () => {
    const g = { ...base, phase: 'reveal' as const, page: [word('stone', ['p0', 'p1'], 0, true)] }
    expect(hostLine(g, named)).toBe('Ava and Ben found STONE. 5 letters. Everyone found the same words.')
  })

  it('says so when nobody found a word, and names the one that got away', () => {
    const g = { ...base, phase: 'reveal' as const, page: [], missed: { word: 'tones', points: 800 } }
    expect(hostLine(g, named)).toBe('Nobody found a thing. TONES was right there.')
  })
})

describe('hostLine at the scores', () => {
  it('names the round winner', () => {
    const g = { ...base, phase: 'scores' as const, deltas: [{ id: 'p0', name: 'Ava', base: 1, unique: 0, longest: 0, total: 2300 }] }
    expect(hostLine(g, named)).toBe('Ava takes the round with 2,300.')
  })

  it('is kind when nobody scored', () => {
    const g = { ...base, phase: 'scores' as const, deltas: [{ id: 'p0', name: 'Ava', base: 0, unique: 0, longest: 0, total: 0 }] }
    expect(hostLine(g, named)).toBe('Nobody scored. Tough crowd.')
  })
})
