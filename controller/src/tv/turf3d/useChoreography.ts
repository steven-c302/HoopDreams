// controller/src/tv/turf3d/useChoreography.ts
import { useEffect, useRef, useState } from 'react'
import { sfx } from '../audio'
import type { TurfTv } from '../types'
import type { Shot } from './camera'
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
}

const latest = (g: TurfTv) => g.beats.reduce((m, b) => Math.max(m, b.seq), 0)

/** The highest beat seq already on the board: everything at or below it is history, not something to animate. */
export const initialSeen = (g: TurfTv): number => latest(g)

export const restCraft = (g: TurfTv): Craft => ({
  shown: g.tokens.map((t) => t.pos), hop: g.tokens.map(() => null), shot: 'wide', focus: null, banner: null, target: null, landed: null,
})

/** Puts every piece back where the engine says it is (a restore, a Timeout, a new token); same object when already right. */
export function snapFor(c: Craft, g: TurfTv): Craft {
  const right = c.shown.length === g.tokens.length && c.shown.every((s, i) => s === g.tokens[i].pos)
  return right ? c : { ...c, shown: g.tokens.map((t) => t.pos), hop: g.tokens.map(() => null) }
}

/**
 * Plays new engine beats as a timed show. Beats that arrive while a show is running queue behind it. Beats that skip
 * ahead of what this TV has seen (a reconnect), or amount to a huge backlog, snap straight to the engine's positions
 * instead. Any [skip] change jumps to the final state. Only used by the 3D stage; the 2D board keeps its own hops.
 */
export function useChoreography(g: TurfTv, quick: boolean, skip: number): Craft {
  const [craft, setCraft] = useState<Craft>(() => restCraft(g))
  const seen = useRef(initialSeen(g))
  const hopN = useRef(0)
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
    switch (c.kind) {
      case 'hop':
        setCraft((cr) => {
          const shown = cr.shown.slice(); shown[c.token] = c.space
          const hop = cr.hop.slice(); hop[c.token] = { n: ++hopN.current, ms: c.ms, height: c.height, last: c.last }
          return { ...cr, shown, hop }
        })
        break
      case 'snap':
        setCraft((cr) => {
          const shown = cr.shown.slice(); shown[c.token] = c.space
          const hop = cr.hop.slice(); hop[c.token] = null
          return { ...cr, shown, hop }
        })
        break
      case 'shot': setCraft((cr) => ({ ...cr, shot: c.shot, focus: c.focus })); break
      case 'banner': setCraft((cr) => ({ ...cr, banner: c.text })); break
      case 'target': setCraft((cr) => ({ ...cr, target: c.space })); break
      case 'land': setCraft((cr) => ({ ...cr, landed: { token: c.token, space: c.space, n: (cr.landed?.n ?? 0) + 1 } })); break
      case 'sfx': if (c.name === 'hop') sfx.hop(c.arg); else sfx.drumroll(c.arg); break
    }
  }

  return craft
}
