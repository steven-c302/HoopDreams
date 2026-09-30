// controller/src/tv/turf3d/moments.ts
import type { V3 } from './diceFaces'
import { spacePos } from './layout'

/** Something worth a flourish on the board. Built from engine beats by the timeline. */
export type Moment =
  | { type: 'rent'; from: number; to: number; amount: number }
  | { type: 'tax'; token: number; amount: number }
  | { type: 'payday'; token: number }
  | { type: 'card'; token: number }
  | { type: 'fall'; token: number }

const clamp01 = (t: number) => Math.min(1, Math.max(0, t))
const easeOutCubic = (u: number) => 1 - (1 - u) ** 3

/** A coin flying [from] to [to] along a parabola that peaks [lift] above the straight line. */
export function coinArc(t: number, from: V3, to: V3, lift: number): V3 {
  const u = clamp01(t)
  return [from[0] + (to[0] - from[0]) * u, from[1] + (to[1] - from[1]) * u + Math.sin(Math.PI * u) * lift, from[2] + (to[2] - from[2]) * u]
}

/** Coin [i] of [n] thrown up and out around [at]: a fan that rises 1.4 and lands back at the start height. */
export function coinBurst(i: number, n: number, t: number, at: V3): V3 {
  const u = clamp01(t), a = (i / n) * Math.PI * 2, r = 0.9 * u
  return [at[0] + Math.cos(a) * r, at[1] + 4 * 1.4 * u * (1 - u), at[2] + Math.sin(a) * r]
}

/** Coin [i] of [n] raining onto a small patch around [at], falling faster as it goes. */
export function coinRain(i: number, n: number, t: number, at: V3): V3 {
  void n
  const u = clamp01(t)
  const col = ((i * 7919) % 97) / 97, row = ((i * 104729) % 89) / 89
  return [at[0] + (col - 0.5) * 1.5, at[1] + 3 * (1 - u * u), at[2] + (row - 0.5) * 1.5]
}

/** A card turning over: face-down (rotX 0) to face-up (π) by 60% of its life, rising, then fading out. */
export function flipPose(t: number): { rotX: number; y: number; opacity: number } {
  const u = clamp01(t), f = easeOutCubic(clamp01(u / 0.6))
  return { rotX: Math.PI * f, y: 0.5 + 1.0 * f, opacity: u < 0.8 ? 1 : 1 - (u - 0.8) / 0.2 }
}

/** A piece tipping over (first half), then sliding off and dropping below the table (second half). */
export function fallPose(t: number): { rotZ: number; x: number; y: number } {
  const u = clamp01(t)
  const tip = clamp01(u / 0.5)
  const drop = clamp01((u - 0.5) / 0.5)
  return { rotZ: (Math.PI / 2) * tip * tip, x: 0.9 * clamp01((u - 0.4) / 0.6), y: 0 - 2.2 * drop * drop }
}

/** Height of a cage dropping over a piece: 2.2 at the start, accelerating down to 0. */
export function barsDrop(t: number): number {
  const u = clamp01(t)
  return 2.2 * (1 - u * u)
}

/** Scale of something popping in: 0, overshooting to 1.18 at 60%, settling to 1. */
export function popScale(t: number): number {
  const u = clamp01(t)
  return u < 0.6 ? 1.18 * (1 - (1 - u / 0.6) ** 2) : 1.18 - 0.18 * ((u - 0.6) / 0.4)
}

export interface Coin { kind: 'arc' | 'burst' | 'rain'; t0: number; life: number; i: number; n: number; from: V3; to: V3 }

/** The coins a moment throws (none for moments that are not about money, or for a token with no known space). */
export function coinsFor(moment: Moment, shown: number[], now: number): Coin[] {
  const spot = (token: number, y: number): V3 | null => {
    const s = shown[token]
    if (s === undefined || !Number.isFinite(s) || s < 0) return null
    const p = spacePos(s)
    return [p.x, y, p.z]
  }
  switch (moment.type) {
    case 'rent': {
      const from = spot(moment.from, 0.9), to = spot(moment.to, 0.9)
      if (!from || !to) return []
      return Array.from({ length: 14 }, (_, i) => ({ kind: 'arc' as const, t0: now + i * 45, life: 900, i, n: 14, from, to }))
    }
    case 'tax': {
      const at = spot(moment.token, 0.5)
      if (!at) return []
      return Array.from({ length: 12 }, (_, i) => ({ kind: 'burst' as const, t0: now, life: 1000, i, n: 12, from: at, to: at }))
    }
    case 'payday': {
      const p = spacePos(0)
      const at: V3 = [p.x, 0.2, p.z]
      return Array.from({ length: 24 }, (_, i) => ({ kind: 'rain' as const, t0: now + i * 30, life: 1400, i, n: 24, from: at, to: at }))
    }
    default: return []
  }
}

/** Where a coin is at [now], or null before it starts and after it ends. */
export function coinPos(c: Coin, now: number): V3 | null {
  const u = (now - c.t0) / c.life
  if (u < 0 || u > 1) return null
  if (c.kind === 'arc') return coinArc(u, c.from, c.to, 1.6)
  return c.kind === 'burst' ? coinBurst(c.i, c.n, u, c.from) : coinRain(c.i, c.n, u, c.from)
}
