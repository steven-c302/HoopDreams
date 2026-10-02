// controller/src/tv/turf3d/layout.ts
/** Home Turf's board in world units: a 12 x 12 square, 1.8 corners, nine 0.933-wide spaces per side, Payday bottom right. */
export const HALF = 6
export const CORNER = 1.8
export const EDGE_W = (HALF * 2 - CORNER * 2) / 9
const CENTRE = HALF - CORNER / 2

export type Side = 'bottom' | 'left' | 'top' | 'right'

export interface Tile {
  /** Centre in world x and z (y is up). */
  cx: number; cz: number
  /** Full extent along x and along z. */
  ex: number; ez: number
  corner: boolean
  side: Side
  /** Unit vector from this tile toward the middle of the board (zero for corners). */
  inward: { x: number; z: number }
}

const wrap = (i: number) => ((i % 40) + 40) % 40

export function sideOf(i: number): Side {
  const s = wrap(i)
  return s < 10 ? 'bottom' : s < 20 ? 'left' : s < 30 ? 'top' : 'right'
}

export function tileOf(i: number): Tile {
  const s = wrap(i)
  const side = sideOf(s)
  const corner = s % 10 === 0
  const run = (k: number) => HALF - CORNER - (k - 0.5) * EDGE_W
  let cx: number, cz: number
  if (s === 0) { cx = CENTRE; cz = CENTRE }
  else if (s < 10) { cx = run(s); cz = CENTRE }
  else if (s === 10) { cx = -CENTRE; cz = CENTRE }
  else if (s < 20) { cx = -CENTRE; cz = run(s - 10) }
  else if (s === 20) { cx = -CENTRE; cz = -CENTRE }
  else if (s < 30) { cx = -HALF + CORNER + (s - 20 - 0.5) * EDGE_W; cz = -CENTRE }
  else if (s === 30) { cx = CENTRE; cz = -CENTRE }
  else { cx = CENTRE; cz = -HALF + CORNER + (s - 30 - 0.5) * EDGE_W }
  const horizontal = side === 'bottom' || side === 'top'
  const ex = corner ? CORNER : horizontal ? EDGE_W : CORNER
  const ez = corner ? CORNER : horizontal ? CORNER : EDGE_W
  const inward = corner ? { x: 0, z: 0 } : side === 'bottom' ? { x: 0, z: -1 } : side === 'top' ? { x: 0, z: 1 } : side === 'left' ? { x: 1, z: 0 } : { x: -1, z: 0 }
  return { cx, cz, ex, ez, corner, side, inward }
}

export const spacePos = (i: number): { x: number; z: number } => { const t = tileOf(i); return { x: t.cx, z: t.cz } }

/**
 * Where a piece (and the ring that marks it) stands on a space. The camera looks across the board from the bottom, so a
 * piece's body is drawn up the screen from its feet; on the top and bottom rows it stands at the back of the deep tile, so
 * its body falls on the board's middle or the table instead of over its own tile's name and price. The bottom row stands a little less far back, to stay off the houses on its inner band.
 */
export function standPos(i: number): { x: number; z: number } {
  const t = tileOf(i)
  return { x: t.cx, z: t.corner || t.side === 'left' || t.side === 'right' ? t.cz : t.cz - (t.ez / 2 - (t.side === 'top' ? 0.3 : 0.5)) }
}

/** How much a piece shrinks when [n] share a space, so coasters (0.88 across at full size) never overlap. */
const CROWD_SCALE = [1, 1, 0.8, 0.65, 0.55, 0.55, 0.55]

/**
 * Where piece [k] of [n] on one space stands, relative to the space's centre, and how big it is there. The crowd is a
 * small grid laid along the tile's long axis, so a deep bottom tile and a wide side tile both use their length.
 */
export function crowdSlot(k: number, n: number, tile: { ex: number; ez: number }): { dx: number; dz: number; scale: number } {
  const count = Math.max(1, Math.min(n, 6))
  const scale = CROWD_SCALE[count]
  if (count === 1) return { dx: 0, dz: 0, scale }
  const across = count <= 3 ? 1 : 2
  const along = Math.ceil(count / across)
  const gap = 0.9 * scale
  const u = ((k % along) - (along - 1) / 2) * gap
  const v = (Math.floor(k / along) - (across - 1) / 2) * gap
  return tile.ex >= tile.ez ? { dx: u, dz: v, scale } : { dx: v, dz: u, scale }
}

/** For each token, its rank among the living tokens shown on the same space and how many share it. */
export function crowdIndex(shown: number[], alive: boolean[]): { rank: number; count: number }[] {
  const groups = new Map<number, number[]>()
  shown.forEach((space, k) => { if (alive[k]) groups.set(space, [...(groups.get(space) ?? []), k]) })
  return shown.map((space, k) => {
    const group = groups.get(space) ?? [k]
    return { rank: Math.max(0, group.indexOf(k)), count: group.length }
  })
}
