// controller/src/tv/turf3d/useChoreography.ts
import { useEffect, useRef, useState } from 'react'
import { sfx } from '../audio'
import type { TurfTv } from '../types'
import type { Shot } from './camera'
import type { Moment } from './moments'
import { ShowQueue, shouldSnap } from './showQueue'
import { planBeats, type Cue } from './timeline'

export interface HopInfo { n: number; ms: number; height: number; last: boolean }

/** Everything the 3D scene needs to know about the show right now. */
export interface Craft {
  /** The space each token is shown on (it lags the engine while a move plays). */
  shown: number[]
  /** The hop a token is making, with a counter so the scene can tell a new hop from the same one. */
  hop: (HopInfo | null)[]
  shot: Shot
  focus: number | null
  banner: string | null
  target: number | null
  landed: { token: number; space: number; n: number } | null
  /** The latest dice throw to play (numbered, so the scene plays each once). */
  dice: { n: number; values: [number, number]; seed: number } | null
  /** Recent moments, oldest first, each with an id that only ever goes up (even across a skip). */
  moments: (Moment & { n: number })[]
}

const latest = (g: TurfTv) => g.beats.reduce((m, b) => Math.max(m, b.seq), 0)

/** The highest beat seq already on the board: everything at or below it is history, not something to animate. */
export const initialSeen = (g: TurfTv): number => latest(g)

export const restCraft = (g: TurfTv): Craft => ({
  shown: g.tokens.map((t) => t.pos), hop: g.tokens.map(() => null), shot: 'wide', focus: null, banner: null, target: null,
  landed: null, dice: null, moments: [],
})

/** Puts every piece back where the engine says it is (a restore, a Timeout, a new token); same object when already right. */
export function snapFor(c: Craft, g: TurfTv): Craft {
  const right = c.shown.length === g.tokens.length && c.shown.every((s, i) => s === g.tokens[i].pos)
  return right ? c : { ...c, shown: g.tokens.map((t) => t.pos), hop: g.tokens.map(() => null) }
}

/** Running ids for hops, moments and dice throws; owned by the hook so they keep climbing across a reset. */
export interface Counters { hop: number; moment: number; dice: number }

/** What one cue does to the craft. Sound cues change nothing here (the hook plays them). */
export function applyCue(cr: Craft, c: Cue, n: Counters): Craft {
  switch (c.kind) {
    case 'hop': {
      const shown = cr.shown.slice(); shown[c.token] = c.space
      const hop = cr.hop.slice(); hop[c.token] = { n: ++n.hop, ms: c.ms, height: c.height, last: c.last }
      return { ...cr, shown, hop }
    }
    case 'snap': {
      const shown = cr.shown.slice(); shown[c.token] = c.space
      const hop = cr.hop.slice(); hop[c.token] = null
      return { ...cr, shown, hop }
    }
    case 'shot': return { ...cr, shot: c.shot, focus: c.focus }
    case 'banner': return { ...cr, banner: c.text }
    case 'target': return { ...cr, target: c.space }
    case 'land': return { ...cr, landed: { token: c.token, space: c.space, n: (cr.landed?.n ?? 0) + 1 } }
    case 'dice': return { ...cr, dice: { n: ++n.dice, values: c.values, seed: c.seed } }
    case 'moment': return { ...cr, moments: [...cr.moments, { ...c.moment, n: ++n.moment }].slice(-8) }
    case 'sfx': return cr
  }
}

/**
 * Plays new engine beats as a timed show. Beats that arrive while a show is running queue behind it. Beats that skip
 * ahead of what this TV has seen (a reconnect), or amount to a huge backlog, snap straight to the engine's positions
 * instead. Any [skip] change jumps to the final state. Only used by the 3D stage; the 2D board keeps its own hops.
 */
export function useChoreography(g: TurfTv, quick: boolean, skip: number): Craft {
  const [craft, setCraft] = useState<Craft>(() => restCraft(g))
  const seen = useRef(initialSeen(g))
  const counters = useRef<Counters>({ hop: 0, moment: 0, dice: 0 })
  const gRef = useRef(g)
  gRef.current = g
  const applyRef = useRef<(c: Cue) => void>(() => undefined)
  const queue = useRef<ShowQueue | null>(null)
  if (!queue.current) queue.current = new ShowQueue((c) => applyRef.current(c), () => setCraft((cr) => snapFor(cr, gRef.current)))

  useEffect(() => {
    const fresh = g.beats.filter((b) => b.seq > seen.current)
    const before = seen.current
    seen.current = Math.max(seen.current, latest(g))
    const q = queue.current!
    if (fresh.length > 0) {
      const plan = planBeats(fresh, { quick })
      if (plan.cues.length > 0) {
        if (shouldSnap(before, fresh, plan.totalMs)) { q.skip(); setCraft(restCraft(g)); return }
        q.enqueue(plan)
        return
      }
    }
    if (!q.isBusy()) setCraft((c) => snapFor(c, g))
  }, [g, quick])

  useEffect(() => {
    if (skip === 0) return
    queue.current!.skip()
    setCraft(restCraft(gRef.current))
  }, [skip])

  useEffect(() => () => queue.current?.skip(), [])

  applyRef.current = (c: Cue) => {
    if (c.kind === 'sfx') { if (c.name === 'hop') sfx.hop(c.arg); else sfx.drumroll(c.arg); return }
    setCraft((cr) => applyCue(cr, c, counters.current))
  }

  return craft
}
