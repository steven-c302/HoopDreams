// controller/src/tv/turf3d/ui/copy.test.ts
import { describe, expect, it } from 'vitest'
import type { TurfSpace } from '../../types'
import { FALLBACK_PALETTE, PALETTE_NAMES, auctionHint, barFraction, buyCall, debtLine, debtTo, deedFacts, deedTags, inkOn, money, resolve, sipLine, sipText, tradeTitle, varName } from './copy'

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

describe('debt and trade copy', () => {
  const toks = [{ name: 'Amanda' }, { name: 'Izzy' }]
  it('says who the debt is to', () => {
    expect(debtTo({ to: 1 }, toks)).toBe('Izzy'); expect(debtTo({ to: -1 }, toks)).toBe('the bank'); expect(debtTo({ to: -2 }, toks)).toBe('everyone'); expect(debtTo({ to: 9 }, toks)).toBe('the bank')
  })
  it('states the debt', () => { expect(debtLine('Amanda', 1200)).toBe('AMANDA OWES $1,200') })
  it('titles a trade, counting counter-offers', () => { expect(tradeTitle(0)).toBe('TRADE OFFER!'); expect(tradeTitle(2)).toBe('COUNTER-OFFER #2') })
  it('lists the places in a trade, marks mortgaged ones, and caps the list', () => {
    const board = [{ name: 'A', color: '#111111' }, { name: 'B' }, { name: 'C', color: '#333333' }, { name: 'D' }]
    expect(deedTags([0, 1], board, [1])).toEqual({ tags: [{ name: 'A', band: '#111111' }, { name: 'B (M)', band: null }], more: 0 })
    expect(deedTags([0, 1, 2, 3], board, [], 2)).toEqual({ tags: [{ name: 'A', band: '#111111' }, { name: 'B', band: null }], more: 2 })
    expect(deedTags([], board, [])).toEqual({ tags: [], more: 0 })
    expect(deedTags([7], board, [])).toEqual({ tags: [], more: 0 })
  })
})

describe('tally and team helpers', () => {
  it('scales a bar to the best worth without leaving 0 to 1', () => {
    expect(barFraction(500, 1000)).toBe(0.5); expect(barFraction(2000, 1000)).toBe(1); expect(barFraction(-5, 1000)).toBe(0); expect(barFraction(0, 0)).toBe(0)
  })
  it('picks ink or white text for a hex colour', () => {
    expect(inkOn('#ffd23f')).toBe('#1a1a1a'); expect(inkOn('#1a1a1a')).toBe('#ffffff'); expect(inkOn('#e2483d')).toBe('#ffffff'); expect(inkOn('nonsense')).toBe('#1a1a1a')
  })
})
