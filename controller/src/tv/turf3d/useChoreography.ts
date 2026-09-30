// controller/src/tv/turf3d/useChoreography.ts
import { useEffect, useRef, useState } from 'react'
import { sfx } from '../audio'
import type { TurfTv } from '../types'
import type { Shot } from './camera'
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
 * Plays new engine beats as a timed show. Beats that arrive while a show is running queue behind it. Any [skip] change
 * jumps to the final state. Only used by the 3D stage; the 2D board keeps its own hops.
 */
export function useChoreography(g: TurfTv, quick: boolean, skip: number): Craft {
  const [craft, setCraft] = useState<Craft>(() => restCraft(g))
  const seen = useRef(initialSeen(g))
  const busyUntil = useRef(0)
  const timers = useRef<ReturnType<typeof setTimeout>[]>([])
  const hopN = useRef(0)
  const gRef = useRef(g)
  gRef.current = g

  useEffect(() => {
    const fresh = g.beats.filter((b) => b.seq > seen.current)
    seen.current = Math.max(seen.current, latest(g))
    const now = performance.now()
    if (fresh.length > 0) {
      const plan = planBeats(fresh, { quick })
      if (plan.cues.length > 0) {
        const wait = Math.max(0, busyUntil.current - now)
        busyUntil.current = now + wait + plan.totalMs
        for (const cue of plan.cues) timers.current.push(setTimeout(() => apply(cue), wait + cue.at))
        timers.current.push(setTimeout(() => setCraft((c) => snapFor(c, gRef.current)), wait + plan.totalMs + 50))
        return
      }
    }
    if (now >= busyUntil.current) setCraft((c) => snapFor(c, g))
  }, [g, quick])

  useEffect(() => {
    if (skip === 0) return
    timers.current.forEach(clearTimeout); timers.current = []
    busyUntil.current = 0
    setCraft(restCraft(gRef.current))
  }, [skip])

  useEffect(() => () => timers.current.forEach(clearTimeout), [])

  function apply(c: Cue) {
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
