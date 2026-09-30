// controller/src/tv/turf3d/trajectory.test.ts
import { describe, expect, it } from 'vitest'
import { sampleTrajectory } from './trajectory'

// Two frames: at the origin with no turn, then at (2, 4, 6) turned a quarter about y.
const s = Math.SQRT1_2
const frames = new Float32Array([0, 0, 0, 0, 0, 0, 1, 2, 4, 6, 0, s, 0, s])

describe('sampleTrajectory', () => {
  it('starts on the first frame and holds the last frame after the end', () => {
    expect(sampleTrajectory(frames, 2, 0, 1 / 60).pos).toEqual([0, 0, 0])
    const end = sampleTrajectory(frames, 2, 5, 1 / 60)
    expect(end.pos[0]).toBeCloseTo(2, 5); expect(end.pos[1]).toBeCloseTo(4, 5); expect(end.pos[2]).toBeCloseTo(6, 5)
    expect(end.quat[1]).toBeCloseTo(s, 5)
  })
  it('blends between frames', () => {
    const mid = sampleTrajectory(frames, 2, 1 / 120, 1 / 60)
    expect(mid.pos).toEqual([1, 2, 3])
    expect(Math.hypot(...mid.quat)).toBeCloseTo(1, 6)
  })
  it('takes the short way round when neighbouring rotations have opposite signs', () => {
    const flipped = new Float32Array([0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, -1])
    const q = sampleTrajectory(flipped, 2, 1 / 120, 1 / 60).quat
    expect(Math.abs(q[3])).toBeCloseTo(1, 6) // it stays a no-op turn, it does not spin through zero
  })
})
