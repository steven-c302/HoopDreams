// controller/src/tv/turf3d/ui/copy.ts
import type { TurfSpace } from '../../types'

export const money = (n: number): string => `$${n.toLocaleString()}`
export const sipText = (n: number): string => (n >= 99 ? 'FINISH YOUR DRINK' : n === 5 ? 'A SHOT' : n === 1 ? '1 SIP' : `${n} SIPS`)
export const sipLine = (drinks: boolean, sips: number): string | null => (drinks && sips > 0 ? `DRINK ${sipText(sips)}` : null)

export interface DeedFacts { kind: string; name: string; band: string | null; pills: string[]; foot: string }

/** The short version of a deed for the 3D card: name on its band, three or two facts, and the foot line. */
export function deedFacts(s: TurfSpace, ownerName?: string): DeedFacts {
  const band = s.color ?? null
  if (s.kind === 'street') {
    const r0 = s.rent[0] ?? 0, r5 = s.rent[5] ?? 0
    return { kind: 'PLACE', name: s.name, band, pills: [`RENT ${money(r0)}`, `SET ${money(r0 * 2)}`, `HOTEL ${money(r5)}`], foot: `House ${money(s.houseCost)}` }
  }
  const foot = ownerName ? `Owner: ${ownerName}` : 'For sale'
  if (s.kind === 'railroad') return { kind: 'RIDE HOME', name: s.name, band, pills: ['1 RIDE $25', 'ALL 4 $200'], foot }
  return { kind: 'UTILITY', name: s.name, band, pills: ['ONE 4 × DICE', 'BOTH 10 × DICE'], foot }
}

export const buyCall = (s?: TurfSpace): string => `BUY IT FOR ${money(s?.price ?? 0)}?`
export const auctionHint = (bids: number): string => `${bids} bid${bids === 1 ? '' : 's'} · each bid resets the clock`

/** A callout as the DOM stage queues it: fill and ink are CSS colours (usually var(--name)). */
export interface FlashSpec { id: number; text: string; sub?: string; fill: string; ink?: string; ms: number; small?: boolean }

/** The CSS variable name inside `var(--x)`, or null for anything else (plain colours, fallbacks). */
export function varName(expr: string): string | null {
  const m = /^var\((--[\w-]+)\)$/.exec(expr.trim())
  return m ? m[1] : null
}

export type Palette = Record<string, string>
export const PALETTE_NAMES = ['ink', 'paper', 'white', 'sun', 'tomato', 'blueberry', 'lime', 'grape', 'bubblegum', 'tangerine', 'sky']
export const FALLBACK_PALETTE: Palette = {
  ink: '#1a1a1a', paper: '#fbf3dc', white: '#ffffff', sun: '#ffd23f', tomato: '#e2483d', blueberry: '#3b5bdb', lime: '#7bd94a',
  grape: '#8a4fd8', bubblegum: '#ff8fc0', tangerine: '#ff9a1f', sky: '#7fc8ff',
}

/** A CSS colour as hex: variables go through the palette (by name, without the dashes); anything else is used as is. */
export function resolve(p: Palette, expr: string, fallback = '#1a1a1a'): string {
  const n = varName(expr)
  if (n == null) return expr
  const v = p[n.slice(2)]
  return v ? v : fallback
}
