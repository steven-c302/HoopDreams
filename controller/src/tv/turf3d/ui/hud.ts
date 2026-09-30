// controller/src/tv/turf3d/ui/hud.ts
import type { TurfTv } from '../../types'

/** Phases before the game proper: no turn header, no ticker, and the game clock is not counting yet. */
export const SETUP_PHASES = new Set<string>(['teamup', 'pieces', 'deal', 'tally', 'podium'])

/** How long the engine gives each decision (before the timer setting stretches it); the TV's pie drains over this. */
export const DECISION_MS: Record<string, number> = { roll: 20_000, jail: 15_000, buy: 15_000, auction: 10_000, choose: 10_000, manage: 20_000, debt: 60_000, trade: 30_000, pieces: 15_000, teamup: 15_000 }

export type Clock = { deadline: number | null; frozen: number | null }

/** Game clock left in ms, or null when the game has no time limit. The engine sends a value at the start of each phase; the phase clock ticks it down between updates. */
export function gameClockLeft(g: Pick<TurfTv, 'clockLeftMs' | 'phaseMs' | 'phase'>, clock: Clock, now: number): number | null {
  if (g.clockLeftMs == null) return null
  if (SETUP_PHASES.has(g.phase)) return Math.max(0, g.clockLeftMs)
  const left = clock.frozen ?? (clock.deadline ? Math.max(0, clock.deadline - now) : g.phaseMs ?? 0)
  return Math.max(0, g.clockLeftMs - Math.max(0, (g.phaseMs ?? 0) - left))
}

export function clockLabel(left: number | null, lastLap: boolean): { text: string; tone: 'gold' | 'red' | 'plain' } {
  if (lastLap) return { text: 'LAST LAP', tone: 'red' }
  if (left == null) return { text: 'NO TIME LIMIT', tone: 'plain' }
  const m = Math.floor(left / 60_000), s = Math.floor((left % 60_000) / 1000)
  return { text: `${m}:${String(s).padStart(2, '0')}`, tone: left < 5 * 60_000 ? 'red' : 'gold' }
}

export interface TimerSpec { deadline: number | null; frozen: number | null; total: number }

/** The decision timer for the phase, or null when the game is untimed or no clock is running. Mirrors the DOM well. */
export function timerFor(g: Pick<TurfTv, 'timed' | 'phase' | 'auction'>, clock: Clock, paused: boolean, scale: number): TimerSpec | null {
  if (!g.timed || (clock.deadline == null && clock.frozen == null)) return null
  const total = (g.phase === 'auction' && (g.auction?.bids ?? 0) > 0 ? 6_000 : DECISION_MS[g.phase] ?? 20_000) * scale
  return { deadline: paused ? null : clock.deadline, frozen: paused ? clock.frozen ?? 0 : clock.frozen, total }
}

export const timerLeft = (t: TimerSpec, now: number): number => t.frozen ?? (t.deadline ? Math.max(0, t.deadline - now) : 0)
export const timerFraction = (t: TimerSpec, now: number): number => (t.total > 0 ? Math.min(1, Math.max(0, timerLeft(t, now) / t.total)) : 0)

export const tickerTail = (ticker: string[], n = 3): string[] => ticker.slice(-n)

export interface HudIn { clock: Clock; paused: boolean; timerScale: number; seatName: string }
export interface Hud {
  showTurn: boolean
  name: string
  seatName: string
  color: string
  piece?: string
  clock: ReturnType<typeof clockLabel>
  timer: TimerSpec | null
  ticker: string[]
}

/** Everything the card's header and footer show, from the game state and the stage's clock. */
export function buildHud(g: TurfTv, input: HudIn, now: number): Hud {
  const cur = g.tokens[g.turn]
  return {
    showTurn: !!cur && !SETUP_PHASES.has(g.phase),
    name: cur?.name ?? '',
    seatName: input.seatName,
    color: cur?.color ?? '#888888',
    piece: cur?.piece,
    clock: clockLabel(gameClockLeft(g, input.clock, now), g.lastLap),
    timer: timerFor(g, input.clock, input.paused, input.timerScale),
    ticker: SETUP_PHASES.has(g.phase) ? [] : tickerTail(g.ticker),
  }
}
