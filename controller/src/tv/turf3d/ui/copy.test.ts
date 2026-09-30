// controller/src/tv/turf3d/ui/copy.test.ts
import { describe, expect, it } from 'vitest'
import type { TurfSpace } from '../../types'
import { FALLBACK_PALETTE, PALETTE_NAMES, auctionHint, buyCall, deedFacts, money, resolve, sipLine, sipText, varName } from './copy'

const street: TurfSpace = { name: "Amanda's Balcony", label: "Amanda's Balcony", kind: 'street', group: 1, color: '#e2483d', price: 220, rent: [18, 90, 250, 700, 875, 1050], houseCost: 150, tax: 0 }
const ride: TurfSpace = { name: 'Rideshare', label: 'Rideshare', kind: 'railroad', group: 9, price: 200, rent: [], houseCost: 0, tax: 0 }
const util: TurfSpace = { name: 'Aux Cord', label: 'Aux Cord', kind: 'utility', group: 10, price: 150, rent: [], houseCost: 0, tax: 0 }

describe('money and sips', () => {
  it('formats dollars with separators', () => { expect(money(220)).toBe('$220'); expect(money(1050)).toBe('$1,050') })
  it('names sips the way the DOM well does', () => {
    expect(sipText(1)).toBe('1 SIP'); expect(sipText(2)).toBe('2 SIPS'); expect(sipText(5)).toBe('A SHOT'); expect(sipText(99)).toBe('FINISH YOUR DRINK')
  })
  it('shows a drink line only when drinking is on and there is something to drink', () => {
    expect(sipLine(true, 2)).toBe('DRINK 2 SIPS'); expect(sipLine(true, 0)).toBeNull(); expect(sipLine(false, 3)).toBeNull()
  })
})

describe('deedFacts', () => {
  it('summarises a street: rent, whole set, hotel, and the house price', () => {
    expect(deedFacts(street)).toEqual({ kind: 'PLACE', name: "Amanda's Balcony", band: '#e2483d', pills: ['RENT $18', 'SET $36', 'HOTEL $1,050'], foot: 'House $150' })
  })
  it('summarises a ride and a utility, and says who owns it', () => {
    expect(deedFacts(ride).pills).toEqual(['1 RIDE $25', 'ALL 4 $200']); expect(deedFacts(ride).kind).toBe('RIDE HOME'); expect(deedFacts(ride).band).toBeNull()
    expect(deedFacts(util).pills).toEqual(['ONE 4 × DICE', 'BOTH 10 × DICE']); expect(deedFacts(util).kind).toBe('UTILITY')
    expect(deedFacts(ride, 'Izzy').foot).toBe('Owner: Izzy'); expect(deedFacts(ride).foot).toBe('For sale')
  })
  it('does not crash on a street with a short rent table', () => {
    expect(deedFacts({ ...street, rent: [] }).pills).toEqual(['RENT $0', 'SET $0', 'HOTEL $0'])
  })
})

describe('the calls', () => {
  it('asks to buy at the price, with a safe default', () => { expect(buyCall(street)).toBe('BUY IT FOR $220?'); expect(buyCall(undefined)).toBe('BUY IT FOR $0?') })
  it('counts bids, with 1 singular and 0 sensible', () => {
    expect(auctionHint(0)).toBe('0 bids · each bid resets the clock'); expect(auctionHint(1)).toBe('1 bid · each bid resets the clock'); expect(auctionHint(3)).toBe('3 bids · each bid resets the clock')
  })
})

describe('the palette', () => {
  it('pulls the variable name out of var(--x)', () => {
    expect(varName('var(--tomato)')).toBe('--tomato'); expect(varName(' var(--sun) ')).toBe('--sun'); expect(varName('#ff0000')).toBeNull(); expect(varName('var(--a, red)')).toBeNull()
  })
  it('resolves a variable through the palette, passes a plain colour through, and falls back when missing', () => {
    const p = { tomato: '#c8463b' }
    expect(resolve(p, 'var(--tomato)')).toBe('#c8463b')
    expect(resolve(p, '#123456')).toBe('#123456')
    expect(resolve(p, 'var(--nope)')).toBe('#1a1a1a')
    expect(resolve(p, 'var(--nope)', '#fff')).toBe('#fff')
    expect(resolve({ tomato: '' }, 'var(--tomato)')).toBe('#1a1a1a')
  })
  it('has a hex fallback for every name it reads', () => {
    for (const n of PALETTE_NAMES) expect(FALLBACK_PALETTE[n], n).toMatch(/^#[0-9a-f]{6}$/i)
  })
})
