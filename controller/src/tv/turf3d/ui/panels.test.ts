// controller/src/tv/turf3d/ui/panels.test.ts
import { describe, expect, it } from 'vitest'
import type { TurfPhase } from '../../types'
import { PIECE_ORDER, bankLine, chooseCall, panelFor, panelSize, rollCall } from './panels'

describe('panelFor', () => {
  it('maps the ported phases to their own panel', () => {
    for (const p of ['roll', 'move', 'manage', 'jail', 'choose', 'pieces', 'deal'] as TurfPhase[]) expect(panelFor(p)).toBe(p)
  })
  it('leaves every other phase to the DOM well', () => {
    for (const p of ['teamup', 'buy', 'auction', 'card', 'debt', 'trade', 'tally', 'podium'] as TurfPhase[]) expect(panelFor(p), p).toBeNull()
  })
})

describe('panelSize', () => {
  it('uses the big card only where nobody is walking (setup) and the standard card everywhere else', () => {
    expect(panelSize('pieces')).toBe('setup'); expect(panelSize('deal')).toBe('setup')
    for (const n of ['roll', 'move', 'manage', 'jail', 'choose'] as const) expect(panelSize(n)).toBe('std')
  })
})

describe('the copy', () => {
  it('says who rolls, or that it is doubles', () => {
    expect(rollCall(0, 'Amanda')).toBe('AMANDA ROLLS')
    expect(rollCall(1, 'Amanda')).toBe('DOUBLES! ROLL AGAIN')
    expect(rollCall(0, '')).toBe(' ROLLS')
  })
  it('names the choice', () => {
    expect(chooseCall('bus')).toBe('BUS! PICK A MOVE'); expect(chooseCall('triples')).toBe('TRIPLES! GO ANYWHERE'); expect(chooseCall(undefined)).toBe('TRIPLES! GO ANYWHERE')
  })
  it('counts the bank', () => { expect(bankLine({ housesLeft: 18, hotelsLeft: 11 })).toBe('Bank: 18 houses · 11 hotels') })
  it('lists the six pieces the engine offers', () => { expect(PIECE_ORDER).toEqual(['cup', 'pizza', 'sneaker', 'boombox', 'cone', 'duck']) })
})
