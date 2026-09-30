// controller/src/tv/turf3d/showQueue.ts
import type { TurfBeat } from '../types'
import type { Cue } from './timeline'

/** More show than a person should sit through in one update: snap to the truth instead of playing it. */
export const MAX_SHOW_MS = 14_000

/**
 * Whether new beats should be skipped straight to the engine's positions. A gap in the sequence means beats were missed
 * (a reconnect), and a very long show means a backlog; replaying either would run old moves while a live prompt waits.
 */
export function shouldSnap(seen: number, fresh: TurfBeat[], totalMs: number): boolean {
  return fresh.length > 0 && (fresh[0].seq > seen + 1 || totalMs > MAX_SHOW_MS)
}

type Timer = ReturnType<typeof setTimeout>

/**
 * Runs planned shows one after another. A show queued while another is running starts when it ends, and only the
 * newest show's end-snap survives, so an earlier show's snap can never cut into the one behind it.
 */
export class ShowQueue {
  private busyUntil = 0
  private timers: Timer[] = []
  private snapTimer: Timer | null = null

  constructor(
    private readonly apply: (cue: Cue) => void,
    private readonly snap: () => void,
    private readonly now: () => number = () => performance.now(),
    private readonly schedule: (fn: () => void, ms: number) => Timer = (fn, ms) => setTimeout(fn, ms),
    private readonly cancel: (t: Timer) => void = (t) => clearTimeout(t),
  ) {}

  isBusy(): boolean { return this.now() < this.busyUntil }

  enqueue(plan: { cues: Cue[]; totalMs: number }): void {
    const now = this.now()
    const wait = Math.max(0, this.busyUntil - now)
    this.busyUntil = now + wait + plan.totalMs
    for (const cue of plan.cues) this.timers.push(this.schedule(() => this.apply(cue), wait + cue.at))
    if (this.snapTimer) this.cancel(this.snapTimer)
    this.snapTimer = this.schedule(() => { this.snapTimer = null; this.snap() }, wait + plan.totalMs + 50)
  }

  /** Drops every pending cue and the end-snap, and frees the queue. */
  skip(): void {
    this.timers.forEach((t) => this.cancel(t))
    this.timers = []
    if (this.snapTimer) this.cancel(this.snapTimer)
    this.snapTimer = null
    this.busyUntil = 0
  }
}
