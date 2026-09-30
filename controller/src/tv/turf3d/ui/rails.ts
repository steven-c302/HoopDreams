// controller/src/tv/turf3d/ui/rails.ts
/** A rail card is 400 by 250 reference pixels; the rails sit 728 px either side of the centre, a little below the middle. */
export const RAIL = { w: 400, h: 250, gap: 22, dx: 728, dy: -30 }

/** The displayed value [u] (0 to 1) of the way from [from] to [to], easing out; ends exactly on [to]. */
export function countAt(from: number, to: number, u: number): number {
  const k = Math.min(1, Math.max(0, u))
  return Math.round(from + (to - from) * (1 - (1 - k) ** 3))
}

export const pillWidth = (text: string): number => Math.max(64, Math.round(text.length * 12.5) + 28)

/** Left edges and rows for pills laid out in a line that wraps at [maxW]. */
export function flowPills(texts: string[], maxW: number, gap = 8): { x: number; row: number; w: number }[] {
  const out: { x: number; row: number; w: number }[] = []
  let x = 0, row = 0
  for (const t of texts) {
    const w = pillWidth(t)
    if (x > 0 && x + w > maxW) { x = 0; row++ }
    out.push({ x, row, w })
    x += w + gap
  }
  return out
}

/** Where the i-th owned-place square goes: [step] px apart, [perRow] to a row. */
export function pipPos(i: number, perRow = 20, step = 18): { x: number; row: number } {
  return { x: (i % perRow) * step, row: Math.floor(i / perRow) }
}

/** The "+$200" or "-$50" note for a cash change, or null when nothing changed. */
export function changeNote(before: number, after: number): { text: string; tone: 'up' | 'down' } | null {
  if (before === after) return null
  const d = after - before
  return { text: `${d > 0 ? '+' : '-'}$${Math.abs(d).toLocaleString()}`, tone: d > 0 ? 'up' : 'down' }
}

/** Which tokens sit on which rail: even to the left, odd to the right (as the flat rails do). */
export function railSlots(count: number): { left: number[]; right: number[] } {
  const idx = Array.from({ length: count }, (_, i) => i)
  return { left: idx.filter((i) => i % 2 === 0), right: idx.filter((i) => i % 2 === 1) }
}

/** The y (reference pixels, up) of the k-th of [n] cards on a rail. */
export const railY = (k: number, n: number): number => ((n - 1) / 2 - k) * (RAIL.h + RAIL.gap) + RAIL.dy

export function badgesFor(t: { jailed: boolean; bankrupt: boolean; sets: number; jailCards: number }): string[] {
  const b: string[] = []
  if (t.jailed && !t.bankrupt) b.push('IN TIMEOUT')
  if (t.sets > 0) b.push(`${t.sets} SET${t.sets > 1 ? 'S' : ''}`)
  if (t.jailCards > 0) b.push(`GET OUT ×${t.jailCards}`)
  return b
}
