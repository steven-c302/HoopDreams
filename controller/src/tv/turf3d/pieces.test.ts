// controller/src/tv/turf3d/pieces.test.ts
import { describe, expect, it } from 'vitest'
import { drinkFor } from './pieces'

describe('drinkFor', () => {
  it('maps the six engine piece ids to drinks', () => {
    expect(['cup', 'pizza', 'sneaker', 'boombox', 'cone', 'duck'].map(drinkFor)).toEqual(['cup', 'soju', 'vodka', 'beer', 'can', 'shot'])
  })
  it('already understands the drink ids Plan 3 will rename to', () => {
    expect(['soju', 'vodka', 'beer', 'can', 'shot', 'cup'].map(drinkFor)).toEqual(['soju', 'vodka', 'beer', 'can', 'shot', 'cup'])
  })
  it('falls back to the cup for a missing or unknown piece', () => {
    expect(drinkFor(undefined)).toBe('cup')
    expect(drinkFor('')).toBe('cup')
    expect(drinkFor('unicorn')).toBe('cup')
  })
})
