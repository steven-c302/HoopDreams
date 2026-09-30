// controller/src/tv/turf3d/pieces.ts
import type { DrinkKind } from './drinkSpecs'

/** The engine's piece ids today, plus the drink ids Plan 3 renames them to. Anything else is the red cup. */
const MAP: Record<string, DrinkKind> = {
  cup: 'cup', pizza: 'soju', sneaker: 'vodka', boombox: 'beer', cone: 'can', duck: 'shot',
  soju: 'soju', vodka: 'vodka', beer: 'beer', can: 'can', shot: 'shot',
}

export const drinkFor = (piece?: string): DrinkKind => (piece && MAP[piece]) || 'cup'
