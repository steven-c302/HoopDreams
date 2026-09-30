// controller/src/tv/turf3d/camera.test.ts
import { describe, expect, it } from 'vitest'
import { shotPose, wideScale } from './camera'

describe('camera poses', () => {
  it('keeps the wide shot at 16:9 and backs off for narrower frames', () => {
    expect(wideScale(16 / 9)).toBe(1)
    expect(wideScale(1)).toBeCloseTo(1.7, 6)
    expect(wideScale(0)).toBeGreaterThan(1) // a zero-size canvas never divides by zero
  })
  it('descends from wide to follow to close and stays near the piece it follows', () => {
    const focus = { x: 3, z: 5 }
    const wide = shotPose('wide', null, 16 / 9), follow = shotPose('follow', focus, 16 / 9), close = shotPose('close', focus, 16 / 9)
    expect(wide.pos[1]).toBeGreaterThan(follow.pos[1]); expect(follow.pos[1]).toBeGreaterThan(close.pos[1])
    expect(close.look).toEqual([3, 0.2, 5])
    expect(Math.hypot(close.pos[0] - 3, close.pos[1], close.pos[2] - 5)).toBeLessThan(6)
  })
  it('looks at the middle of the board for the dice', () => {
    expect(shotPose('dice', null, 16 / 9).look).toEqual([0, 0, 0])
  })
})
