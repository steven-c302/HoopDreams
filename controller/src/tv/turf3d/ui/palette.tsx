// controller/src/tv/turf3d/ui/palette.tsx
import { createContext, useContext } from 'react'
import { FALLBACK_PALETTE, PALETTE_NAMES, type Palette } from './copy'

export const PaletteContext = createContext<Palette>(FALLBACK_PALETTE)
export const usePalette = (): Palette => useContext(PaletteContext)

/** The game's colours as the browser resolves them for [el] (they differ per game theme); a missing one keeps its fallback. */
export function readPalette(el: Element): Palette {
  const cs = getComputedStyle(el)
  const out: Palette = { ...FALLBACK_PALETTE }
  for (const n of PALETTE_NAMES) {
    const v = cs.getPropertyValue(`--${n}`).trim()
    if (/^#[0-9a-f]{3,8}$/i.test(v)) out[n] = v.length === 4 ? `#${v[1]}${v[1]}${v[2]}${v[2]}${v[3]}${v[3]}` : v
  }
  return out
}
