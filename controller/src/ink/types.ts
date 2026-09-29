/**
 * Ink is drawn on a 1000 by 750 grid whatever the screen, so the phone and the TV always agree.
 * Keep the grid, the counts and the op shapes in step with InkBoard.kt (tv/server).
 */
export const INK_W = 1000
export const INK_H = 750

/** The eight crayons; an op carries the index. */
export const CRAYONS = ['#3b2f2a', '#e5484d', '#f5851f', '#f7c331', '#3fa34d', '#2f8ff0', '#7c4dd6', '#ec5fa5'] as const
export const CRAYON_NAMES = ['Brown', 'Red', 'Orange', 'Yellow', 'Green', 'Blue', 'Purple', 'Pink'] as const
/** Brush widths in grid units; an op carries the index. */
export const BRUSHES = [6, 14, 30] as const
export const BRUSH_NAMES = ['Thin', 'Medium', 'Fat'] as const

export type InkOp =
  | { t: 'start'; s: number; c: number; w: number; x: number; y: number; p: number }
  | { t: 'pts'; s: number; pts: number[] }
  | { t: 'end'; s: number }
  | { t: 'undo' }
  | { t: 'clear' }

/** [pts] is flat: x, y, pressure (0-100), x, y, pressure, ... */
export interface Stroke { s: number; c: number; w: number; pts: number[]; open: boolean }

export interface InkSyncMsg {
  turns: { turn: number; strokes: { s: number; c: number; w: number; pts: number[]; open?: boolean }[] }[]
  upTo: number
}
