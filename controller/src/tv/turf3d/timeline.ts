// controller/src/tv/turf3d/timeline.ts
import type { TurfBeat } from '../types'
import type { Shot } from './camera'

/** Kept in step with HomeTurf.kt (DICE_MS, LAND_PAD_MS, hopMs, moveDwellMs); both tests assert the same table. */
export const DICE_MS = 2600
export const LAND_PAD_MS = 900
export const QUICK_HOP_MS = 260
export const QUICK_PAD_MS = 1400
const EARLY_HOP_MS = 230
const TAIL_HOP_MS = [820, 460, 320] // last, second to last, third to last

export const hopMs = (index: number, hops: number, quick: boolean): number => {
  if (quick) return QUICK_HOP_MS
  const left = hops - 1 - index
  return left < 3 ? TAIL_HOP_MS[left] : EARLY_HOP_MS
}

export const hopHeight = (index: number, hops: number, quick: boolean): number => {
  if (quick) return 0.55
  const left = hops - 1 - index
  return left === 0 ? 1.5 : left < 3 ? 0.95 : 0.7
}

export const walkMs = (hops: number, quick: boolean): number =>
  Array.from({ length: hops }, (_, i) => hopMs(i, hops, quick)).reduce((a, b) => a + b, 0)

/** How long the engine holds the MOVE phase, and so how long the TV has to finish showing it. */
export const moveDwellMs = (hops: number, diced: boolean, quick: boolean): number =>
  quick ? hops * QUICK_HOP_MS + QUICK_PAD_MS : (diced ? DICE_MS : 0) + walkMs(hops, false) + LAND_PAD_MS

export type Cue =
  | { at: number; kind: 'shot'; shot: Shot; focus: number | null }
  | { at: number; kind: 'hop'; token: number; space: number; ms: number; height: number; last: boolean }
  | { at: number; kind: 'banner'; text: string | null }
  | { at: number; kind: 'target'; space: number | null }
  | { at: number; kind: 'land'; token: number; space: number }
  | { at: number; kind: 'snap'; token: number; space: number }
  | { at: number; kind: 'sfx'; name: 'hop' | 'drumroll'; arg: number }

/**
 * Turns the beats that arrived since the last update into timed cues (ms from now, sorted, ties keep insertion order).
 * A roll only counts as "diced" for the move that follows it; a turn change or Timeout clears it.
 */
export function planBeats(fresh: TurfBeat[], { quick }: { quick: boolean }): { cues: Cue[]; totalMs: number } {
  const cues: Cue[] = []
  let t = 0
  let dice: number[] | null = null
  for (const b of fresh) {
    if (b.kind === 'roll') { dice = b.dice; continue }
    if (b.kind === 'turn') { dice = null; continue }
    if (b.kind === 'jail') { dice = null; cues.push({ at: t, kind: 'snap', token: b.token, space: b.space }); continue }
    if (b.kind !== 'move' || b.path.length === 0) continue

    const rolled = dice
    dice = null
    const path = b.path, n = path.length, dest = path[n - 1]
    if (rolled && !quick) {
      const [a = 0, c = 0] = rolled
      cues.push({ at: t, kind: 'shot', shot: 'dice', focus: null })
      cues.push({ at: t + 1500, kind: 'banner', text: `${a} + ${c} = ${a + c}` })
      cues.push({ at: t + DICE_MS, kind: 'banner', text: null })
      t += DICE_MS
    }
    const tail = Math.max(0, n - 3)
    for (let i = 0; i < n; i++) {
      const ms = hopMs(i, n, quick)
      if (!quick) {
        if (i < tail) cues.push({ at: t, kind: 'shot', shot: 'follow', focus: path[i] })
        if (i === tail) {
          cues.push({ at: t, kind: 'shot', shot: 'close', focus: dest })
          cues.push({ at: t, kind: 'target', space: dest })
          cues.push({ at: t, kind: 'sfx', name: 'drumroll', arg: 1.6 })
        }
      }
      cues.push({ at: t, kind: 'hop', token: b.token, space: path[i], ms, height: hopHeight(i, n, quick), last: i === n - 1 })
      cues.push({ at: t, kind: 'sfx', name: 'hop', arg: i })
      t += ms
    }
    cues.push({ at: t, kind: 'land', token: b.token, space: dest })
    if (!quick) {
      cues.push({ at: t, kind: 'target', space: null })
      cues.push({ at: t + 600, kind: 'shot', shot: 'wide', focus: null })
    }
    t += quick ? QUICK_PAD_MS : LAND_PAD_MS
  }
  return { cues: cues.sort((a, b) => a.at - b.at), totalMs: t }
}
