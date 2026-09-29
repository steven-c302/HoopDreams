/** Hot Type's swipe rules as pure functions: which tile a finger is on, and how a path grows, backs up or stops. */

export interface Rect { x: number; y: number; w: number; h: number }

/** The gap between tiles as a fraction of the board width. The board's CSS (hunt-phone.css) uses the same number. */
export const GAP = 0.025
/** A tile is touched when the finger is inside a circle this fraction of the tile wide, centred on it. */
export const HIT = 0.6

/** Base points by word length. Keep in step with HotTypeRules.points in the engine. */
export function pointsFor(letters: number): number {
  if (letters < 3) return 0
  return [100, 400, 800, 1400, 1800][letters - 3] ?? 2200
}

/** The screen rectangle of every tile, from the board's own rectangle (square, gap included). */
export function tileRects(board: Rect, n: number): Rect[] {
  const gap = GAP * board.w
  const cell = (board.w - gap * (n - 1)) / n
  return Array.from({ length: n * n }, (_, i) => ({
    x: board.x + (i % n) * (cell + gap),
    y: board.y + Math.floor(i / n) * (cell + gap),
    w: cell,
    h: cell,
  }))
}

/** A tile's centre as fractions (0 to 1) of the board, for drawing the trail. */
export function tileCenter(i: number, n: number): [number, number] {
  const cell = (1 - GAP * (n - 1)) / n
  return [(i % n) * (cell + GAP) + cell / 2, Math.floor(i / n) * (cell + GAP) + cell / 2]
}

/** The tile under a point, or null in a gap, a tile corner, or off the board. */
export function cellAt(rects: Rect[], x: number, y: number, hit = HIT): number | null {
  for (let i = 0; i < rects.length; i++) {
    const r = rects[i]
    const radius = (hit * Math.min(r.w, r.h)) / 2
    if ((x - (r.x + r.w / 2)) ** 2 + (y - (r.y + r.h / 2)) ** 2 <= radius ** 2) return i
  }
  return null
}

export function adjacent(a: number, b: number, n: number): boolean {
  return a !== b && Math.abs(Math.floor(a / n) - Math.floor(b / n)) <= 1 && Math.abs((a % n) - (b % n)) <= 1
}

/** The tiles a straight walk from [from] to [to] crosses, ending at [to] (each step touches the last). */
function between(from: number, to: number, n: number): number[] {
  const r0 = Math.floor(from / n), c0 = from % n
  const dr = Math.floor(to / n) - r0, dc = (to % n) - c0
  const steps = Math.max(Math.abs(dr), Math.abs(dc))
  return Array.from({ length: steps }, (_, k) => {
    const i = k + 1
    return (r0 + Math.round((i * dr) / steps)) * n + (c0 + Math.round((i * dc) / steps))
  })
}

/**
 * The path after the finger reaches [target]: unchanged on the last tile or a used one, one shorter when it returns to
 * the second-to-last tile, else longer. A fast jump walks the tiles in between and stops before a used one. Returns the
 * same array when nothing changes.
 */
export function advance(path: number[], target: number, n: number): number[] {
  if (path.length === 0) return [target]
  const last = path[path.length - 1]
  if (target === last) return path
  if (path.length >= 2 && target === path[path.length - 2]) return path.slice(0, -1)
  if (path.includes(target)) return path
  let out = path
  for (const cell of between(last, target, n)) {
    if (out.includes(cell)) return out
    out = [...out, cell]
  }
  return out
}

export const spell = (tiles: string[], path: number[]): string => path.map((i) => tiles[i] ?? '').join('')
export const lettersOf = (tiles: string[], path: number[]): number => spell(tiles, path).length
