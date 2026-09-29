import { INK_H, INK_W, type InkOp, type Stroke } from './types'

const MAX_PTS_NUMBERS = 300
const MAX_OPS_PER_MESSAGE = 60

/** Collects a stroke's points as the pen moves and hands them out as ops, in order, whenever asked. */
export class InkBatcher {
  private ops: InkOp[] = []
  private buf: number[] = []
  private sid = 0

  start(id: number, c: number, w: number, x: number, y: number, p: number) {
    this.flushPts()
    this.sid = id
    this.ops.push({ t: 'start', s: id, c, w, x, y, p })
  }

  point(x: number, y: number, p: number) {
    this.buf.push(x, y, p)
    if (this.buf.length >= MAX_PTS_NUMBERS) this.flushPts()
  }

  end() {
    this.flushPts()
    this.ops.push({ t: 'end', s: this.sid })
  }

  /** undo, clear and anything else that isn't a point. */
  op(o: InkOp) {
    this.flushPts()
    this.ops.push(o)
  }

  take(): InkOp[] {
    this.flushPts()
    const out = this.ops
    this.ops = []
    return out
  }

  private flushPts() {
    if (this.buf.length) {
      this.ops.push({ t: 'pts', s: this.sid, pts: this.buf })
      this.buf = []
    }
  }
}

/** A stroke as ops (start, points in chunks, end if it is closed): used to resend a drawing after a reconnect. */
export function strokeOps(st: Stroke): InkOp[] {
  const ops: InkOp[] = [{ t: 'start', s: st.s, c: st.c, w: st.w, x: st.pts[0], y: st.pts[1], p: st.pts[2] }]
  for (let i = 3; i < st.pts.length; i += MAX_PTS_NUMBERS) ops.push({ t: 'pts', s: st.s, pts: st.pts.slice(i, i + MAX_PTS_NUMBERS) })
  if (!st.open) ops.push({ t: 'end', s: st.s })
  return ops
}

/** The server takes at most 64 ops per message. */
export function chunkOps(ops: InkOp[], size = MAX_OPS_PER_MESSAGE): InkOp[][] {
  const out: InkOp[][] = []
  for (let i = 0; i < ops.length; i += size) out.push(ops.slice(i, i + size))
  return out
}

export function toGrid(clientX: number, clientY: number, rect: DOMRect): [number, number] {
  const x = Math.round(((clientX - rect.left) / rect.width) * INK_W)
  const y = Math.round(((clientY - rect.top) / rect.height) * INK_H)
  return [Math.min(INK_W, Math.max(0, x)), Math.min(INK_H, Math.max(0, y))]
}

/** Only a pen reports a pressure worth using; a finger or mouse gets a steady middle. */
export function pressureOf(e: { pointerType: string; pressure: number }): number {
  return e.pointerType === 'pen' && e.pressure > 0 ? Math.round(e.pressure * 100) : 50
}
