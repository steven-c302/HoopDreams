import { describe, expect, it } from 'vitest'
import { applyOp, applyOps } from './board'
import type { InkOp, Stroke } from './types'

describe('ink board reducer', () => {
  it('grows and closes a stroke', () => {
    const strokes: Stroke[] = []
    applyOps(strokes, [
      { t: 'start', s: 1, c: 2, w: 1, x: 100, y: 100, p: 50 },
      { t: 'pts', s: 1, pts: [110, 105, 50, 120, 110, 60] },
      { t: 'end', s: 1 },
    ])
    expect(strokes).toEqual([{ s: 1, c: 2, w: 1, pts: [100, 100, 50, 110, 105, 50, 120, 110, 60], open: false }])
  })

  it('undo and clear on an empty board do nothing', () => {
    const strokes: Stroke[] = []
    applyOp(strokes, { t: 'undo' })
    applyOp(strokes, { t: 'clear' })
    expect(strokes).toEqual([])
  })

  // The same sequence and result are pinned in InkBoardTest.kt (theGoldenSequenceEndsWhereTheControllerDoes).
  it('ends the golden sequence where the server does', () => {
    const ops: InkOp[] = [
      { t: 'start', s: 1, c: 2, w: 1, x: 100, y: 100, p: 50 }, { t: 'pts', s: 1, pts: [110, 105, 50, 120, 110, 60] }, { t: 'end', s: 1 },
      { t: 'start', s: 2, c: 0, w: 0, x: 500, y: 500, p: 50 }, { t: 'pts', s: 2, pts: [510, 500, 50] }, { t: 'undo' },
      { t: 'start', s: 3, c: 7, w: 2, x: 10, y: 20, p: 30 }, { t: 'end', s: 3 }, { t: 'end', s: 3 },
      { t: 'undo' }, { t: 'undo' }, { t: 'undo' },
      { t: 'start', s: 1, c: 1, w: 1, x: 1, y: 2, p: 3 }, { t: 'clear' }, { t: 'start', s: 4, c: 3, w: 0, x: 900, y: 700, p: 100 },
    ]
    const strokes: Stroke[] = []
    for (const op of ops) applyOp(strokes, op)
    expect(strokes).toEqual([{ s: 4, c: 3, w: 0, pts: [900, 700, 100], open: true }])
  })
})
