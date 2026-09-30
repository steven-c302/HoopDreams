// controller/src/tv/turf3d/ui/rails.test.ts
import { describe, expect, it } from 'vitest'
import { RAIL, badgesFor, changeNote, countAt, flowPills, pillWidth, pipPos, railSlots, railY } from './rails'

describe('countAt', () => {
  it('eases from the old value to the new one and ends exactly on it', () => {
    expect(countAt(0, 100, 0)).toBe(0); expect(countAt(0, 100, 1)).toBe(100); expect(countAt(0, 100, 0.5)).toBe(88)
    expect(countAt(500, 300, 1)).toBe(300); expect(countAt(500, 300, 0.5)).toBe(325)
  })
  it('clamps time outside 0 to 1', () => { expect(countAt(10, 20, -3)).toBe(10); expect(countAt(10, 20, 9)).toBe(20) })
})

describe('pills', () => {
  it('sizes a pill from its text, with a floor', () => { expect(pillWidth('A')).toBe(64); expect(pillWidth('IN TIMEOUT')).toBe(153) })
  it('flows pills left to right and wraps to a new row when one would overflow', () => {
    expect(flowPills(['IN TIMEOUT', '2 SETS', 'GET OUT ×1'], 370)).toEqual([{ x: 0, row: 0, w: 153 }, { x: 161, row: 0, w: 103 }, { x: 0, row: 1, w: 153 }])
    expect(flowPills([], 370)).toEqual([])
    expect(flowPills(['ONLY ONE'], 40)).toEqual([{ x: 0, row: 0, w: 128 }]) // a lone pill never wraps forever
  })
})

describe('place squares', () => {
  it('lays squares out in rows of twenty', () => {
    expect(pipPos(0)).toEqual({ x: 0, row: 0 }); expect(pipPos(19)).toEqual({ x: 342, row: 0 }); expect(pipPos(20)).toEqual({ x: 0, row: 1 }); expect(pipPos(27)).toEqual({ x: 126, row: 1 })
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
    expect(railY(0, 3)).toBe(228); expect(railY(1, 3)).toBe(-44); expect(railY(2, 3)).toBe(-316); expect(railY(0, 1)).toBe(-44); expect(railY(0, 2)).toBe(92); expect(railY(1, 2)).toBe(-180)
    expect(RAIL.w).toBe(400); expect(RAIL.h).toBe(250)
  })
})

describe('badgesFor', () => {
  it('lists timeout, sets and Get Out cards, and hides timeout on a bankrupt token', () => {
    expect(badgesFor({ jailed: true, bankrupt: false, sets: 2, jailCards: 1 })).toEqual(['IN TIMEOUT', '2 SETS', 'GET OUT ×1'])
    expect(badgesFor({ jailed: false, bankrupt: false, sets: 1, jailCards: 0 })).toEqual(['1 SET'])
    expect(badgesFor({ jailed: true, bankrupt: true, sets: 0, jailCards: 0 })).toEqual([])
  })
})
