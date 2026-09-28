import { createContext, useContext } from 'react'

/** Timer length choices ("timers" setting): normal, relaxed, no rush. Keep in step with TIMER_SCALES in Game.kt. */
export const TIMER_SCALES = [1, 1.5, 2]
export const TIMER_NAMES = ['NORMAL', 'RELAXED', 'NO RUSH']

/**
 * How much longer this game's decision timers run. The engine stretches them; stages whose clock faces use fixed
 * totals (Home Turf, Sprawl, Bluff Battle, Blackjack) multiply by this so the pie drains at the right rate. Brain
 * Drain sends its real duration and doesn't need it.
 */
export const TimerScale = createContext(1)
export const useTimerScale = () => useContext(TimerScale)
