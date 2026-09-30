// controller/src/tv/turf3d/diceSim.ts
import type RAPIER from '@dimforge/rapier3d-compat'
import { labelingFor, slotUp, type Quat } from './diceFaces'

/** The Rapier physics module (loaded on demand, so nothing but the 3D stage ever pays for it). */
export type Rapier = typeof RAPIER

let loading: Promise<Rapier> | null = null
export function loadRapier(): Promise<Rapier> {
  loading ??= import('@dimforge/rapier3d-compat')
    .then(async (m) => { await m.default.init(); return m.default })
    .catch((e) => { loading = null; throw e })
  return loading
}

export const DT = 1 / 60
/** A recording is 104 frames (1.73 s); the dice are damped to rest well before it ends. */
export const STEPS = 104
const HALF = 0.35 // half the edge of a die (0.7 across)

export interface Throw {
  steps: number
  frames: [Float32Array, Float32Array]
  /** For each die, the number printed on each of its six slots, arranged so the engine's number ends up on top. */
  labels: [number[], number[]]
  seed: number
}

export function mulberry32(seed: number): () => number {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/** A uniformly random rotation. */
function randomQuat(rnd: () => number): Quat {
  const [u1, u2, u3] = [rnd(), rnd(), rnd()]
  const a = Math.sqrt(1 - u1), b = Math.sqrt(u1)
  return [a * Math.sin(2 * Math.PI * u2), a * Math.cos(2 * Math.PI * u2), b * Math.sin(2 * Math.PI * u3), b * Math.cos(2 * Math.PI * u3)]
}

/** Runs one throw of two dice into the tray in the middle of the board and records every frame. */
function simulate(R: Rapier, seed: number): { frames: [Float32Array, Float32Array]; rest: [Quat, Quat] } {
  const rnd = mulberry32(seed)
  const world = new R.World({ x: 0, y: -22, z: 0 })
  world.timestep = DT
  world.createCollider(R.ColliderDesc.cuboid(6, 0.2, 6).setTranslation(0, -0.2, 0).setFriction(0.8).setRestitution(0.25))
  ;([[3.3, 0], [-3.3, 0], [0, 3.3], [0, -3.3]] as const).forEach(([x, z], i) =>
    world.createCollider(R.ColliderDesc.cuboid(i < 2 ? 0.2 : 3.5, 2, i < 2 ? 3.5 : 0.2).setTranslation(x, 2, z).setFriction(0.3).setRestitution(0.3)))
  const bodies = [0, 1].map((k) => {
    const q = randomQuat(rnd)
    const body = world.createRigidBody(R.RigidBodyDesc.dynamic()
      .setTranslation(2.6, 1.6 + k * 0.8, 0.6 + k * 0.9)
      .setRotation({ x: q[0], y: q[1], z: q[2], w: q[3] })
      .setLinvel(-8 + (rnd() - 0.5) * 2, 1, (rnd() - 0.5) * 3)
      .setAngvel({ x: (rnd() - 0.5) * 40, y: (rnd() - 0.5) * 40, z: (rnd() - 0.5) * 40 })
      .setLinearDamping(0.15).setAngularDamping(0.2))
    world.createCollider(R.ColliderDesc.cuboid(HALF, HALF, HALF).setRestitution(0.35).setFriction(0.7), body)
    return body
  })
  const frames: [Float32Array, Float32Array] = [new Float32Array(STEPS * 7), new Float32Array(STEPS * 7)]
  for (let s = 0; s < STEPS; s++) {
    if (s === 60) bodies.forEach((b) => { b.setAngularDamping(4); b.setLinearDamping(1.5) }) // settle before the recording ends
    world.step()
    bodies.forEach((b, k) => { const t = b.translation(), r = b.rotation(); frames[k].set([t.x, t.y, t.z, r.x, r.y, r.z, r.w], s * 7) })
  }
  const rest = bodies.map((b) => { const r = b.rotation(); return [r.x, r.y, r.z, r.w] as Quat }) as [Quat, Quat]
  world.free()
  return { frames, rest }
}

/** 1 for a clean throw (both dice flat on the board, still, and apart), lower or 0 otherwise. */
function flatScore(frames: [Float32Array, Float32Array], rest: [Quat, Quat]): number {
  const at = (k: number, step: number) => Array.from(frames[k].slice(step * 7, step * 7 + 3))
  let score = 1
  for (const k of [0, 1]) {
    const p = at(k, STEPS - 1), prev = at(k, STEPS - 2)
    if (Math.abs(p[1] - HALF) > 0.03) return 0 // stacked or propped up
    if (Math.hypot(p[0] - prev[0], p[1] - prev[1], p[2] - prev[2]) > 0.004) return 0 // still moving
    score = Math.min(score, slotUp(rest[k]).alignment)
  }
  const [a, b] = [at(0, STEPS - 1), at(1, STEPS - 1)]
  if (Math.hypot(a[0] - b[0], a[2] - b[2]) < 0.72) return 0 // overlapping
  return score
}

/**
 * A throw whose dice end flat, seeded so the same roll always plays the same. It tries the seed, then nearby seeds,
 * until a throw lands clean, then relabels each die so the engine's number is the one on top.
 */
export function throwFor(R: Rapier, seed: number, values: [number, number]): Throw {
  let best: { frames: [Float32Array, Float32Array]; rest: [Quat, Quat]; score: number; seed: number } | null = null
  for (let tries = 0; tries < 16; tries++) {
    const s = seed + tries * 7919
    const sim = simulate(R, s)
    const score = flatScore(sim.frames, sim.rest)
    if (!best || score > best.score) best = { ...sim, score, seed: s }
    if (score >= 0.985) break
  }
  const b = best!
  const labels = b.rest.map((q, k) => labelingFor(slotUp(q).slot, values[k])) as [number[], number[]]
  return { steps: STEPS, frames: b.frames, labels, seed: b.seed }
}

/** [throwFor], but null (so the show goes on without dice) if Rapier is missing, the numbers are bad, or anything throws. */
export function safeThrow(R: Rapier | null, seed: number, values: [number, number]): Throw | null {
  if (!R) return null
  try { return throwFor(R, seed, values) } catch { return null }
}
