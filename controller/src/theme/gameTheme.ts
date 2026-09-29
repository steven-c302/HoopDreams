import { createContext, useContext } from 'react'

/** Games with their own environment (theme/games.css). Every other game keeps the Saturday Morning shell. */
export const THEMED_GAMES = ['turf', 'sprawl', 'blackjack', 'bluff', 'writeitdown'] as const
export type GameTheme = (typeof THEMED_GAMES)[number]

export const gameThemeOf = (gameId?: string | null): GameTheme | undefined =>
  (THEMED_GAMES as readonly string[]).includes(gameId ?? '') ? (gameId as GameTheme) : undefined

/** Set by GameScene on the TV, so shared pieces (the clock, the host) can dress for the game they're in. */
export const GameThemeContext = createContext<GameTheme | undefined>(undefined)
export const useGameTheme = () => useContext(GameThemeContext)
