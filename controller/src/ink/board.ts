import type { InkOp, Stroke } from './types'

/** Applies one op to a turn's strokes in place. The same rules as InkBoard.kt, minus the validation. */
export function applyOp(strokes: Stroke[], op: InkOp): void {
  switch (op.t) {
    case 'start':
      strokes.push({ s: op.s, c: op.c, w: op.w, pts: [op.x, op.y, op.p], open: true })
      break
    case 'pts': {
      const st = strokes.find((k) => k.s === op.s && k.open)
      if (st) for (const n of op.pts) st.pts.push(n)
      break
    }
    case 'end': {
      const st = strokes.find((k) => k.s === op.s)
      if (st) st.open = false
      break
    }
    case 'undo':
      strokes.pop()
      break
    case 'clear':
      strokes.length = 0
      break
  }
}

export function applyOps(strokes: Stroke[], ops: InkOp[]): Stroke[] {
  for (const op of ops) applyOp(strokes, op)
  return strokes
}
