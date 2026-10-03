import { describe, expect, it } from 'vitest'
import type { TvState } from '../protocol'
import { musicFor } from './director'

const trivia = (phase: string, format: string) => ({ stage: { gameId: 'trivia', game: { t: 'trivia', phase, format } } }) as unknown as TvState

describe('musicFor Brain Drain', () => {
  it('plays the tense last-lap loop through every Final Wager phase, so the climax is never silent', () => {
    for (const phase of ['final_category', 'final_wager', 'final_question', 'final_reveal']) expect(musicFor(trivia(phase, 'final'))).toBe('lastlap')
  })
  it('keeps the other rounds on their own loops', () => {
    expect(musicFor(trivia('question', 'quick'))).toBe('quick')
    expect(musicFor(trivia('question', 'write'))).toBe('gauntlet')
    expect(musicFor(trivia('podium', 'final'))).toBe('podium')
  })
})
