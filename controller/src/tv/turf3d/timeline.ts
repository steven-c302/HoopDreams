// controller/src/tv/turf3d/timeline.ts
import type { TurfBeat } from '../types'
import type { Shot } from './camera'
import type { Moment } from './moments'

/** Kept in step with HomeTurf.kt (DICE_MS, LAND_PAD_MS, hopMs, moveDwellMs); both tests assert the same table. */
export const DICE_MS = 2600
/** The dice settle by about 1.73 s; the total appears just after. */
export const DICE_BANNER_MS = 1800
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
  return left === 0 ? 1.1 : left < 3 ? 0.85 : 0.65
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
  | { at: number; kind: 'dice'; values: [number, number]; seed: number }
  | { at: number; kind: 'moment'; moment: Moment }

const isDie = (v: number) => Number.isInteger(v) && v >= 1 && v <= 6

/**
 * Turns the beats that arrived since the last update into timed cues (ms from now, sorted, ties keep insertion order).
 * A roll only counts as "diced" for the move that follows it; a turn change or Timeout clears it. Money and drama
 * beats become moments; they never lengthen the show, so they cannot hold the queue.
 */
export function planBeats(fresh: TurfBeat[], { quick }: { quick: boolean }): { cues: Cue[]; totalMs: number } {
  const cues: Cue[] = []
  let t = 0
  let roll: { dice: number[]; seq: number } | null = null
  let walk: { path: number[]; ends: number[] } | null = null
  const moment = (at: number, m: Moment) => cues.push({ at, kind: 'moment', moment: m })
  for (const b of fresh) {
    if (b.kind === 'roll') { roll = { dice: b.dice, seq: b.seq }; continue }
    if (b.kind === 'turn') { roll = null; continue }
    if (b.kind === 'jail') { roll = null; cues.push({ at: t, kind: 'snap', token: b.token, space: b.space }); continue }
    if (b.kind === 'rent') { moment(t, { type: 'rent', from: b.token, to: b.other, amount: b.amount }); continue }
    if (b.kind === 'tax') { moment(t, { type: 'tax', token: b.token, amount: b.amount }); continue }
    if (b.kind === 'card') { moment(t, { type: 'card', token: b.token }); continue }
    if (b.kind === 'bankrupt') { moment(t, { type: 'fall', token: b.token }); continue }
    if (b.kind === 'payday') {
      const i = walk ? walk.path.indexOf(0) : -1
      moment(walk && i >= 0 ? walk.ends[i] : t, { type: 'payday', token: b.token })
      continue
    }
    if (b.kind !== 'move' || b.path.length === 0) continue

    const rolled = roll
    roll = null
    const path = b.path, n = path.length, dest = path[n - 1]
    if (rolled && !quick) {
      const [a = 0, c = 0] = rolled.dice
      cues.push({ at: t, kind: 'shot', shot: 'dice', focus: null })
      if (isDie(a) && isDie(c)) cues.push({ at: t, kind: 'dice', values: [a, c], seed: rolled.seq })
      cues.push({ at: t + DICE_BANNER_MS, kind: 'banner', text: `${a} + ${c} = ${a + c}${a === c ? ' DOUBLES!' : ''}` })
      cues.push({ at: t + DICE_MS, kind: 'banner', text: null })
      t += DICE_MS
    }
    const ends: number[] = []
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
      ends.push(t)
    }
    walk = { path, ends }
    cues.push({ at: t, kind: 'land', token: b.token, space: dest })
    if (!quick) {
      cues.push({ at: t, kind: 'target', space: null })
      cues.push({ at: t + 600, kind: 'shot', shot: 'wide', focus: null })
    }
    t += quick ? QUICK_PAD_MS : LAND_PAD_MS
  }
  return { cues: cues.sort((a, b) => a.at - b.at), totalMs: t }
}
