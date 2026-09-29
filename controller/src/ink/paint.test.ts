import { describe, expect, it } from 'vitest'
import { paintAll, paintStroke, paintUpTo } from './paint'
import { CRAYONS, type Stroke } from './types'

/** A canvas context that only writes down what it is asked to draw. */
function recorder() {
  const calls: string[] = []
  const ctx = {
    strokeStyle: '', fillStyle: '', lineWidth: 0, lineCap: '', lineJoin: '',
    beginPath: () => calls.push('begin'), moveTo: () => calls.push('move'), lineTo: () => calls.push('line'),
    quadraticCurveTo: () => calls.push('curve'), stroke: () => calls.push('stroke'), arc: () => calls.push('arc'),
    fill: () => calls.push('fill'), clearRect: () => calls.push('clear'),
  }
  return { ctx: ctx as unknown as CanvasRenderingContext2D, calls, raw: ctx }
}
const stroke = (n: number, open = false, c = 3): Stroke => ({ s: 1, c, w: 1, open, pts: Array.from({ length: n * 3 }, (_, i) => (i % 3 === 2 ? 50 : 100 + i * 7)) })
const count = (calls: string[], what: string) => calls.filter((c) => c === what).length

describe('paintStroke', () => {
  it('draws a tap as a dot', () => {
    const { ctx, calls } = recorder()
    paintStroke(ctx, stroke(1, true), 1)
    expect(calls).toEqual(['begin', 'arc', 'fill'])
  })

  it('draws one curve per new point and closes a finished stroke with a straight run', () => {
    const closed = recorder()
    paintStroke(closed.ctx, stroke(3), 1)
    expect(count(closed.calls, 'curve')).toBe(2)
    expect(count(closed.calls, 'line')).toBe(1)
    const open = recorder()
    paintStroke(open.ctx, stroke(3, true), 1)
    expect(count(open.calls, 'curve')).toBe(2)
    expect(count(open.calls, 'line')).toBe(0)
  })

  it('paints only the new points when asked to continue', () => {
    const { ctx, calls } = recorder()
    paintStroke(ctx, stroke(5, true), 1, 3, 5, false)
    expect(count(calls, 'curve')).toBe(2)
  })

  it('uses the stroke colour', () => {
    const { ctx, raw } = recorder()
    paintStroke(ctx, stroke(2, true, 5), 1)
    expect(raw.strokeStyle).toBe(CRAYONS[5])
  })
})

describe('paintAll and paintUpTo', () => {
  it('clears first, then paints every stroke', () => {
    const { ctx, calls } = recorder()
    paintAll(ctx, [stroke(2), stroke(2)], 1, 100, 75)
    expect(calls[0]).toBe('clear')
    expect(count(calls, 'curve')).toBe(2)
  })

  it('a time-lapse paints a share of the points', () => {
    const half = recorder()
    paintUpTo(half.ctx, [stroke(10), stroke(10)], 1, 0.5, 100, 75)
    const all = recorder()
    paintUpTo(all.ctx, [stroke(10), stroke(10)], 1, 1, 100, 75)
    expect(count(half.calls, 'curve')).toBeLessThan(count(all.calls, 'curve'))
    expect(count(half.calls, 'curve')).toBeGreaterThan(0)
    const none = recorder()
    paintUpTo(none.ctx, [stroke(10)], 1, 0, 100, 75)
    expect(count(none.calls, 'curve')).toBe(0)
  })
})
