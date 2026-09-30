// controller/src/tv/turf3d/ui/faceTexture.ts
import { FALLBACK_PALETTE, type Palette } from './copy'

/** Replaces CSS variables in an SVG string with palette colours, so it can be drawn as a standalone image. */
export function inlineVars(svg: string, p: Palette): string {
  return svg.replace(/var\(--([\w-]+)\)/g, (_, n: string) => p[n] || FALLBACK_PALETTE[n] || '#1a1a1a')
}

export const svgDataUrl = (svg: string): string => `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`

/** The photo circle of a Face (viewBox -4..104: centre 50, radius 41, image square 9..91) in pixels of a [size] canvas. */
export function photoCircle(size: number): { cx: number; cy: number; r: number; x: number; y: number; w: number } {
  const k = size / 108
  return { cx: 54 * k, cy: 54 * k, r: 41 * k, x: 13 * k, y: 13 * k, w: 82 * k }
}
