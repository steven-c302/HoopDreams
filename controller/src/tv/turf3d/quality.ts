// controller/src/tv/turf3d/quality.ts
import { createContext, useContext } from 'react'

/** high: everything. balanced: no ambient occlusion. low: also no real glass and no post-processing. */
export type Quality = 'high' | 'balanced' | 'low'
export const DEFAULT_QUALITY: Quality = 'balanced'

export function parseQuality(search: string): Quality {
  const v = new URLSearchParams(search).get('quality')
  return v === 'high' || v === 'balanced' || v === 'low' ? v : DEFAULT_QUALITY
}

export const usesAO = (q: Quality) => q === 'high'
export const usesRealGlass = (q: Quality) => q !== 'low'
export const usesComposer = (q: Quality) => q !== 'low'

export const QualityContext = createContext<Quality>(DEFAULT_QUALITY)
export const useQuality = () => useContext(QualityContext)
