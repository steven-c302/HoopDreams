import { BRUSHES, CRAYONS, INK_W, type Stroke } from './types'

const widthAt = (base: number, pressure: number) => base * (0.55 + 0.9 * (pressure / 100))

/** A crayon wobbles a little: the width breathes by up to 7% along the line. */
function segmentWidth(st: Stroke, i: number, k: number) {
  const jitter = 1 + 0.07 * Math.sin(i * 1.9 + st.s * 3.1)
  return widthAt(BRUSHES[st.w] ?? BRUSHES[1], st.pts[i * 3 + 2]) * jitter * k
}

/**
 * Draws points [from, to) of a stroke as smoothed curves: each segment runs midpoint to midpoint with the point as its
 * control, so a line drawn a few points at a time joins up seamlessly. [tail] adds the last straight run once the
 * stroke is finished. [k] is canvas pixels per grid unit.
 */
export function paintStroke(ctx: CanvasRenderingContext2D, st: Stroke, k: number, from = 0, to = st.pts.length / 3, tail = !st.open): void {
  const n = Math.min(to, st.pts.length / 3)
  if (n <= 0) return
  const colour = CRAYONS[st.c] ?? CRAYONS[0]
  ctx.strokeStyle = colour
  ctx.fillStyle = colour
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  const px = (i: number) => st.pts[i * 3] * k
  const py = (i: number) => st.pts[i * 3 + 1] * k
  const mid = (i: number, j: number): [number, number] => [(px(i) + px(j)) / 2, (py(i) + py(j)) / 2]
  if (n === 1) {
    if (from === 0) {
      ctx.beginPath()
      ctx.arc(px(0), py(0), segmentWidth(st, 0, k) / 2, 0, Math.PI * 2)
      ctx.fill()
    }
    return
  }
  for (let i = Math.max(from, 1); i < n; i++) {
    const a: [number, number] = i === 1 ? [px(0), py(0)] : mid(i - 2, i - 1)
    const b = mid(i - 1, i)
    ctx.lineWidth = segmentWidth(st, i, k)
    ctx.beginPath()
    ctx.moveTo(a[0], a[1])
    ctx.quadraticCurveTo(px(i - 1), py(i - 1), b[0], b[1])
    ctx.stroke()
  }
  if (tail && to >= st.pts.length / 3) {
    const a = mid(n - 2, n - 1)
    ctx.lineWidth = segmentWidth(st, n - 1, k)
    ctx.beginPath()
    ctx.moveTo(a[0], a[1])
    ctx.lineTo(px(n - 1), py(n - 1))
    ctx.stroke()
  }
}

/** Clears the canvas and redraws every stroke. */
export function paintAll(ctx: CanvasRenderingContext2D, strokes: Stroke[], k: number, w: number, h: number): void {
  ctx.clearRect(0, 0, w, h)
  for (const st of strokes) paintStroke(ctx, st, k)
}

/** A time-lapse frame: the first [fraction] (0-1) of all the points, in the order they were drawn. */
export function paintUpTo(ctx: CanvasRenderingContext2D, strokes: Stroke[], k: number, fraction: number, w: number, h: number): void {
  ctx.clearRect(0, 0, w, h)
  let budget = Math.floor(strokes.reduce((sum, st) => sum + st.pts.length / 3, 0) * Math.min(1, Math.max(0, fraction)))
  for (const st of strokes) {
    const n = st.pts.length / 3
    const take = Math.min(n, budget)
    if (take <= 0) break
    paintStroke(ctx, st, k, 0, take, take === n)
    budget -= take
  }
}

/** Pixels per grid unit for a canvas of the given width. */
export const scaleFor = (canvasWidth: number) => canvasWidth / INK_W
