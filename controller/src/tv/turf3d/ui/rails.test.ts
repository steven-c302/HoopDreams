// controller/src/tv/turf3d/ui/rails.test.ts
import { describe, expect, it } from 'vitest'
import { RAIL, badgesFor, changeNote, countAt, flowPills, pillWidth, pipPos, placeRow, railSlots, railY } from './rails'

describe('countAt', () => {
  it('eases from the old value to the new one and ends exactly on it', () => {
    expect(countAt(0, 100, 0)).toBe(0); expect(countAt(0, 100, 1)).toBe(100); expect(countAt(0, 100, 0.5)).toBe(88)
    expect(countAt(500, 300, 1)).toBe(300); expect(countAt(500, 300, 0.5)).toBe(325)
  })
  it('clamps time outside 0 to 1', () => { expect(countAt(10, 20, -3)).toBe(10); expect(countAt(10, 20, 9)).toBe(20) })
})

describe('pills', () => {
  it('sizes a pill from its text, with a floor', () => { expect(pillWidth('A')).toBe(64); expect(pillWidth('TIMEOUT')).toBe(116) })
  it('flows pills left to right and wraps to a new row when one would overflow', () => {
    expect(flowPills(['TIMEOUT', 'GET OUT ×1', 'OK'], 300)).toEqual([{ x: 0, row: 0, w: 116 }, { x: 124, row: 0, w: 153 }, { x: 0, row: 1, w: 64 }])
    expect(flowPills([], 370)).toEqual([])
    expect(flowPills(['ONLY ONE'], 40)).toEqual([{ x: 0, row: 0, w: 128 }]) // a lone pill never wraps forever
  })
})

describe('place squares', () => {
  it('lays squares out in rows of fourteen, 14 px apart', () => {
    expect(pipPos(0)).toEqual({ x: 0, row: 0 }); expect(pipPos(13)).toEqual({ x: 182, row: 0 }); expect(pipPos(14)).toEqual({ x: 0, row: 1 })
  })
  it('shows up to twelve places, and past that eleven and a count of the rest', () => {
    expect(placeRow([1, 2, 3])).toEqual({ shown: [1, 2, 3], more: 0 })
    expect(placeRow(Array.from({ length: 12 }, (_, i) => i))).toEqual({ shown: Array.from({ length: 12 }, (_, i) => i), more: 0 })
    expect(placeRow(Array.from({ length: 15 }, (_, i) => i))).toEqual({ shown: Array.from({ length: 11 }, (_, i) => i), more: 4 })
    expect(placeRow([])).toEqual({ shown: [], more: 0 })
  })
})

describe('changeNote', () => {
  it('describes a rise or a fall in cash, and nothing when it did not change', () => {
    expect(changeNote(100, 300)).toEqual({ text: '+$200', tone: 'up' }); expect(changeNote(1500, 450)).toEqual({ text: '-$1,050', tone: 'down' }); expect(changeNote(5, 5)).toBeNull()
  })
})

describe('rail geometry', () => {
  it('sends even tokens to the left rail and odd ones to the right, as the flat rails do', () => {
    expect(railSlots(5)).toEqual({ left: [0, 2, 4], right: [1, 3] }); expect(railSlots(0)).toEqual({ left: [], right: [] }); expect(railSlots(1)).toEqual({ left: [0], right: [] })
  })
  it('stacks a rail from the top, centred a little below the middle', () => {
    expect(railY(0, 3)).toBe(104); expect(railY(1, 3)).toBe(-44); expect(railY(2, 3)).toBe(-192); expect(railY(0, 1)).toBe(-44); expect(railY(0, 2)).toBe(30); expect(railY(1, 2)).toBe(-118)
    expect(RAIL.w).toBe(320); expect(RAIL.h).toBe(132)
  })
})

describe('badgesFor', () => {
  it('lists timeout and Get Out cards only (sets are on the tally), and nothing for a bankrupt token', () => {
    expect(badgesFor({ jailed: true, bankrupt: false, sets: 2, jailCards: 1 })).toEqual(['TIMEOUT', 'GET OUT'])
    expect(badgesFor({ jailed: false, bankrupt: false, sets: 1, jailCards: 0 })).toEqual([])
    expect(badgesFor({ jailed: false, bankrupt: false, sets: 0, jailCards: 2 })).toEqual(['GET OUT ×2'])
    expect(badgesFor({ jailed: true, bankrupt: true, sets: 0, jailCards: 0 })).toEqual([])
  })
})
