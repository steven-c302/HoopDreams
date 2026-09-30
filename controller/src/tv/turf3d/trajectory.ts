// controller/src/tv/turf3d/trajectory.ts
import type { Quat, V3 } from './diceFaces'

export interface Pose { pos: V3; quat: Quat }

/**
 * The pose at [tSec] of a recording made at [dt] seconds per frame (seven numbers per frame: position, then rotation).
 * Positions blend linearly, rotations by normalised lerp taking the short way; after the end the last frame holds.
 */
export function sampleTrajectory(frames: Float32Array, steps: number, tSec: number, dt: number): Pose {
  const f = Math.max(0, tSec / dt)
  const i = Math.min(Math.floor(f), steps - 1), j = Math.min(i + 1, steps - 1), a = Math.min(1, f - i)
  const A = i * 7, B = j * 7
  const lerp = (x: number, y: number) => x + (y - x) * a
  const pos: V3 = [lerp(frames[A], frames[B]), lerp(frames[A + 1], frames[B + 1]), lerp(frames[A + 2], frames[B + 2])]
  let dot = 0
  for (let k = 3; k < 7; k++) dot += frames[A + k] * frames[B + k]
  const sign = dot < 0 ? -1 : 1
  const q = [0, 1, 2, 3].map((c) => lerp(frames[A + 3 + c], sign * frames[B + 3 + c]))
  const len = Math.hypot(q[0], q[1], q[2], q[3]) || 1
  return { pos, quat: [q[0] / len, q[1] / len, q[2] / len, q[3] / len] }
}
