// controller/src/tv/turf3d/ui/sizing.ts
/** The kit lays everything out in reference pixels, as if the TV were 1080 px tall; the Dais scales that to world units. */
export const REF_H = 1080

export type TextKind = 'hero' | 'title' | 'body' | 'label'
/** The smallest each kind of text may be on screen at 1080p: readable from across the room and when drunk. */
export const TEXT_MIN: Record<TextKind, number> = { hero: 72, title: 56, body: 28, label: 22 }
export const textPx = (kind: TextKind, px: number): number => Math.max(TEXT_MIN[kind], px)

/** World units covered by one reference pixel at [dist] from a camera with vertical field of view [fovDeg]. */
export const worldPerPx = (fovDeg: number, dist: number, refH: number = REF_H): number =>
  (2 * dist * Math.tan((fovDeg * Math.PI) / 360)) / refH

/** A font size that lets [chars] characters fit [widthPx], clamped to [minPx, maxPx]. [em] is the average glyph width in ems. */
export function fitFont(chars: number, widthPx: number, maxPx: number, minPx: number, em = 0.5): number {
  const ideal = Math.floor(widthPx / Math.max(chars, 1) / em)
  return Math.min(maxPx, Math.max(minPx, ideal))
}

export type CardSize = 'std' | 'setup' | 'strip'
/** std sits inside the printed middle of the board; setup is used while nobody is walking; strip is just the header, for the move phase, so the board stays clear. */
export const CARD: Record<CardSize, { w: number; h: number }> = { std: { w: 610, h: 369 }, setup: { w: 800, h: 560 }, strip: { w: 610, h: 100 } }
export const HEAD_H = 84
export const FOOT_H = 54

/**
 * The body area of a card: its size and the y of its centre (the card is centred on 0, y up). The standard card keeps a
 * footer for the ticker; the setup card has no ticker, so its body runs down to the bottom edge.
 */
export function bodyOf(size: CardSize): { w: number; h: number; cy: number } {
  const c = CARD[size]
  const foot = size === 'std' ? FOOT_H : 0
  return { w: c.w - 40, h: c.h - HEAD_H - foot, cy: (foot - HEAD_H) / 2 }
}

/** One frame of exponential easing. Frame-rate independent, never overshoots, snaps when within a thousandth. */
export function stepToward(cur: number, target: number, dt: number, rate = 14): number {
  if (Math.abs(target - cur) < 0.001) return target
  return cur + (target - cur) * (1 - Math.exp(-rate * dt))
}
