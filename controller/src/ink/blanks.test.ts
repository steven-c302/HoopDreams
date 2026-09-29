import { describe, expect, it } from 'vitest'
import { blankCells } from './blanks'

describe('blankCells', () => {
  it('splits the server blanks into words of cells', () => {
    expect(blankCells('_ _ _ _ _')).toEqual([['_', '_', '_', '_', '_']])
    expect(blankCells('I _ _   _ _ _ _ _')).toEqual([['I', '_', '_'], ['_', '_', '_', '_', '_']])
    expect(blankCells('')).toEqual([])
  })
})
