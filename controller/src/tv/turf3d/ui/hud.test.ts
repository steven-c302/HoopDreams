// controller/src/tv/turf3d/ui/hud.test.ts
import { describe, expect, it } from 'vitest'
import type { TurfTv } from '../../types'
import { buildHud, clockLabel, gameClockLeft, tickerTail, timerFor, timerFraction, timerLeft } from './hud'

const tok = (name: string, color: string, piece?: string) => ({ name, color, piece, members: [], cash: 0, pos: 0, jailed: false, jailCards: 0, bankrupt: false, worth: 0, sets: 0 })
const g = (over: Partial<TurfTv> = {}): TurfTv => ({
  t: 'turf', phase: 'roll', teams: false, board: [], chanceName: '', chestName: '', owner: [], level: [], mortgaged: [],
  tokens: [tok('Amanda', '#7a3cff', 'boombox'), tok('Daniel', '#ff9a1f')], turn: 0, dice: [], doubles: 0, housesLeft: 18, hotelsLeft: 11, buy: -1,
  lastLap: false, timed: true, drinks: true, beats: [], ticker: ['a', 'b', 'c', 'd'], pieces: [], tally: [], clockLeftMs: 600_000, phaseMs: 20_000, ...over,
})

describe('gameClockLeft', () => {
  it('is null with no limit, whole during setup, and counts down with the phase clock otherwise', () => {
    expect(gameClockLeft(g({ clockLeftMs: undefined }), { deadline: null, frozen: null }, 0)).toBeNull()
    expect(gameClockLeft(g({ phase: 'pieces' }), { deadline: null, frozen: null }, 0)).toBe(600_000)
    expect(gameClockLeft(g(), { deadline: null, frozen: 5000 }, 0)).toBe(585_000)
    expect(gameClockLeft(g(), { deadline: 100_000, frozen: null }, 95_000)).toBe(585_000)
  })
  it('never goes negative or NaN, even with a deadline in the past', () => {
    expect(gameClockLeft(g({ clockLeftMs: 1000 }), { deadline: 1, frozen: null }, 999_999)).toBe(0)
    expect(gameClockLeft(g({ phaseMs: undefined }), { deadline: null, frozen: null }, 0)).toBe(600_000)
  })
})

describe('clockLabel', () => {
  it('reads as minutes and seconds, red under five minutes, with the special states spelled out', () => {
    expect(clockLabel(585_000, false)).toEqual({ text: '9:45', tone: 'gold' })
    expect(clockLabel(200_000, false)).toEqual({ text: '3:20', tone: 'red' })
    expect(clockLabel(5_000, false)).toEqual({ text: '0:05', tone: 'red' })
    expect(clockLabel(null, false)).toEqual({ text: 'NO TIME LIMIT', tone: 'plain' })
    expect(clockLabel(585_000, true)).toEqual({ text: 'LAST LAP', tone: 'red' })
  })
})

describe('the timer', () => {
  const idle = { deadline: null, frozen: null }
  it('shows only for a timed game with a running or frozen clock', () => {
    expect(timerFor(g({ timed: false }), { deadline: 5, frozen: null }, false, 1)).toBeNull()
    expect(timerFor(g(), idle, false, 1)).toBeNull()
    expect(timerFor(g(), { deadline: 5, frozen: null }, false, 1)).toEqual({ deadline: 5, frozen: null, total: 20_000 })
  })
  it('uses the decision length for the phase, stretched by the timer setting, and 6 s once an auction has bids', () => {
    expect(timerFor(g({ phase: 'jail' }), { deadline: 5, frozen: null }, false, 1.5)?.total).toBe(22_500)
    expect(timerFor(g({ phase: 'auction', auction: { id: 1, space: 3, top: 10, leader: 0, bids: 2 } }), { deadline: 5, frozen: null }, false, 1)?.total).toBe(6_000)
    expect(timerFor(g({ phase: 'card' }), { deadline: 5, frozen: null }, false, 1)?.total).toBe(20_000)
  })
  it('freezes at the frozen value when paused, and at zero if there is none', () => {
    expect(timerFor(g(), { deadline: 5, frozen: 7000 }, true, 1)).toEqual({ deadline: null, frozen: 7000, total: 20_000 })
    expect(timerFor(g(), { deadline: 5, frozen: null }, true, 1)).toEqual({ deadline: null, frozen: 0, total: 20_000 })
  })
  it('reports time left and a 0 to 1 fraction that cannot leave that range', () => {
    const t = { deadline: 30_000, frozen: null, total: 20_000 }
    expect(timerLeft(t, 20_000)).toBe(10_000)
    expect(timerFraction(t, 20_000)).toBe(0.5)
    expect(timerFraction(t, 40_000)).toBe(0)
    expect(timerFraction(t, 0)).toBe(1)
    expect(timerFraction({ deadline: null, frozen: 3000, total: 20_000 }, 0)).toBeCloseTo(0.15, 9)
    expect(timerFraction({ deadline: null, frozen: null, total: 0 }, 0)).toBe(0)
  })
})

describe('tickerTail and buildHud', () => {
  it('keeps the last three lines', () => { expect(tickerTail(['a', 'b', 'c', 'd'])).toEqual(['b', 'c', 'd']); expect(tickerTail([])).toEqual([]) })
  it('describes the turn during play', () => {
    const h = buildHud(g(), { clock: { deadline: null, frozen: 5000 }, paused: false, timerScale: 1, seatName: 'Amanda P' }, 0)
    expect(h).toMatchObject({ showTurn: true, name: 'Amanda', seatName: 'Amanda P', color: '#7a3cff', piece: 'boombox', ticker: ['b', 'c', 'd'] })
    expect(h.clock.text).toBe('9:45'); expect(h.timer?.total).toBe(20_000)
  })
  it('drops the turn header and the ticker in setup phases', () => {
    const h = buildHud(g({ phase: 'deal' }), { clock: { deadline: null, frozen: null }, paused: false, timerScale: 1, seatName: '' }, 0)
    expect(h.showTurn).toBe(false); expect(h.ticker).toEqual([])
  })
  it('copes with no tokens at all', () => {
    const h = buildHud(g({ tokens: [] }), { clock: { deadline: null, frozen: null }, paused: false, timerScale: 1, seatName: '' }, 0)
    expect(h.showTurn).toBe(false); expect(h.name).toBe('')
  })
})
