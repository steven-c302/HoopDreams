// controller/src/tv/turf3d/ui/panels.ts
import type { TurfPhase, TurfTv } from '../../types'
import type { CardSize } from './sizing'

export type PanelName = 'roll' | 'move' | 'manage' | 'jail' | 'choose' | 'pieces' | 'deal' | 'buy' | 'auction' | 'card' | 'debt' | 'trade' | 'tally' | 'teamup'

const PORTED: Partial<Record<TurfPhase, PanelName>> = { roll: 'roll', move: 'move', manage: 'manage', jail: 'jail', choose: 'choose', pieces: 'pieces', deal: 'deal', buy: 'buy', auction: 'auction', card: 'card', debt: 'debt', trade: 'trade', tally: 'tally', teamup: 'teamup' }

/** The 3D panel for a phase, or null while that phase still uses the DOM well. */
export const panelFor = (phase: TurfPhase): PanelName | null => PORTED[phase] ?? null

/** The setup panels are dense and nobody is walking then, so they get the bigger card. */
export const panelSize = (name: PanelName): CardSize => (name === 'pieces' || name === 'deal' || name === 'trade' || name === 'tally' || name === 'teamup' ? 'setup' : 'std')

export const rollCall = (doubles: number, seatName: string): string => (doubles > 0 ? 'DOUBLES! ROLL AGAIN' : `${seatName.toUpperCase()} ROLLS`)
export const chooseCall = (choose?: 'bus' | 'triples'): string => (choose === 'bus' ? 'BUS! PICK A MOVE' : 'TRIPLES! GO ANYWHERE')
export const bankLine = (g: Pick<TurfTv, 'housesLeft' | 'hotelsLeft'>): string => `Bank: ${g.housesLeft} houses · ${g.hotelsLeft} hotels`

/** The piece ids the engine hands out today, in the order the picker shows them. */
export const PIECE_ORDER = ['cup', 'pizza', 'sneaker', 'boombox', 'cone', 'duck']
