import { describe, expect, it } from 'vitest'
import { advance, cellAt, lettersOf, pointsFor, spell, tileCenter, tileRects } from './swipe'

// A 400 px square board at (10, 10), 4 x 4: gap 10, tiles 92.5 wide, hit radius 27.75.
const rects = tileRects({ x: 10, y: 10, w: 400, h: 400 }, 4)
const center = (i: number) => ({ x: rects[i].x + rects[i].w / 2, y: rects[i].y + rects[i].h / 2 })

describe('tileRects', () => {
  it('lays out n x n tiles with a gap that is a fraction of the board', () => {
    expect(rects).toHaveLength(16)
    expect(rects[0]).toEqual({ x: 10, y: 10, w: 92.5, h: 92.5 })
    expect(rects[1].x).toBeCloseTo(10 + 92.5 + 10)
    expect(rects[4].y).toBeCloseTo(10 + 92.5 + 10)
  })
})

describe('tileCenter', () => {
  it('gives fractions of the board that match the rects', () => {
    const [cx, cy] = tileCenter(5, 4)
    expect(10 + cx * 400).toBeCloseTo(center(5).x)
    expect(10 + cy * 400).toBeCloseTo(center(5).y)
  })
})

describe('cellAt', () => {
  it('hits a tile near its centre', () => {
    expect(cellAt(rects, center(5).x + 10, center(5).y)).toBe(5)
  })
  it('misses in the gap between tiles', () => {
    expect(cellAt(rects, rects[0].x + rects[0].w + 5, center(0).y)).toBeNull()
  })
  it('misses in the corner of a tile, so a diagonal squeeze does not pick a neighbour by accident', () => {
    expect(cellAt(rects, rects[0].x + 2, rects[0].y + 2)).toBeNull()
  })
  it('misses off the board', () => {
    expect(cellAt(rects, 0, 0)).toBeNull()
  })
})

describe('advance', () => {
  it('starts a path', () => {
    expect(advance([], 5, 4)).toEqual([5])
  })
  it('extends onto a touching tile', () => {
    expect(advance([0], 1, 4)).toEqual([0, 1])
  })
  it('does nothing on the tile it is already on, and returns the same array', () => {
    const path = [0, 1]
    expect(advance(path, 1, 4)).toBe(path)
  })
  it('backs up when the finger returns to the second-to-last tile', () => {
    expect(advance([0, 1, 2], 1, 4)).toEqual([0, 1])
  })
  it('ignores a tile already used earlier in the path', () => {
    const path = [0, 1, 5]
    expect(advance(path, 0, 4)).toBe(path)
  })
  it('walks the tiles in between when a fast diagonal jumps', () => {
    expect(advance([0], 10, 4)).toEqual([0, 5, 10])
  })
  it('walks the tiles in between when a fast straight drag jumps', () => {
    expect(advance([0], 3, 4)).toEqual([0, 1, 2, 3])
  })
  it('stops before a used tile in the middle of a jump', () => {
    const path = [0, 1, 5, 6]
    expect(advance(path, 4, 4)).toEqual([0, 1, 5, 6]) // 6 to 4 crosses 5, which is used
  })
})

describe('spell and points', () => {
  const tiles = ['QU', 'I', 'T', 'S', ...Array(12).fill('X')]
  it('joins the tiles on the path, and QU is two letters', () => {
    expect(spell(tiles, [0, 1, 2])).toBe('QUIT')
    expect(lettersOf(tiles, [0, 1, 2])).toBe(4)
  })
  it('scores by length like the engine does', () => {
    expect([2, 3, 4, 5, 6, 7, 8, 12].map(pointsFor)).toEqual([0, 100, 400, 800, 1400, 1800, 2200, 2200])
  })
})
