import { useEffect, useRef } from 'react'
import { paintAll, paintStroke, paintUpTo, scaleFor } from '../ink/paint'
import type { InkStore } from '../ink/store'
import { INK_H, INK_W } from '../ink/types'

const BACKING = 1440

/**
 * One turn's drawing. `live` follows the store as strokes arrive (painting only what's new, with a glowing pen tip at
 * the end of the open stroke), `still` shows the finished picture, `replay` draws it again as a time-lapse.
 * It fills its parent; the parent sets the aspect ratio (4:3) and `position: relative`.
 */
export function InkCanvas({ store, turn, mode, replayMs = 3200 }: { store: InkStore; turn: number; mode: 'live' | 'still' | 'replay'; replayMs?: number }) {
  const canvas = useRef<HTMLCanvasElement>(null)
  const tip = useRef<HTMLSpanElement>(null)

  useEffect(() => {
    const el = canvas.current!
    const ctx = el.getContext('2d')!
    const k = scaleFor(el.width)
    let raf = 0
    let painted: { s: number; n: number; closed: boolean }[] = []

    const full = () => {
      const strokes = store.strokes(turn)
      paintAll(ctx, strokes, k, el.width, el.height)
      painted = strokes.map((st) => ({ s: st.s, n: st.pts.length / 3, closed: !st.open }))
    }

    const moveTip = () => {
      const t = tip.current
      if (!t) return
      const strokes = store.strokes(turn)
      const st = strokes[strokes.length - 1]
      if (st?.open) {
        t.style.opacity = '1'
        t.style.left = `${(st.pts[st.pts.length - 3] / INK_W) * 100}%`
        t.style.top = `${(st.pts[st.pts.length - 2] / INK_H) * 100}%`
      } else {
        t.style.opacity = '0'
      }
    }

    const update = () => {
      const strokes = store.strokes(turn)
      const intact = strokes.length >= painted.length && painted.every((p, i) => strokes[i].s === p.s && strokes[i].pts.length / 3 >= p.n)
      if (!intact) full()
      else {
        strokes.forEach((st, i) => {
          const p = (painted[i] ??= { s: st.s, n: 0, closed: false })
          const n = st.pts.length / 3
          if (n > p.n) { paintStroke(ctx, st, k, p.n, n, false); p.n = n }
          if (!st.open && !p.closed) { paintStroke(ctx, st, k, n, n, true); p.closed = true }
        })
      }
      moveTip()
    }

    if (mode === 'replay') {
      const strokes = store.strokes(turn)
      const started = performance.now()
      const step = (now: number) => {
        const t = Math.min(1, (now - started) / replayMs)
        const eased = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2
        paintUpTo(ctx, strokes, k, eased, el.width, el.height)
        if (t < 1) raf = requestAnimationFrame(step)
      }
      raf = requestAnimationFrame(step)
      return () => cancelAnimationFrame(raf)
    }

    full()
    moveTip()
    const off = store.subscribe(() => {
      if (raf) return
      raf = requestAnimationFrame(() => { raf = 0; mode === 'live' ? update() : full() })
    })
    return () => { off(); cancelAnimationFrame(raf) }
  }, [store, turn, mode, replayMs])

  return (
    <>
      <canvas ref={canvas} width={BACKING} height={(BACKING * INK_H) / INK_W} aria-label="The drawing" />
      {mode === 'live' && <span ref={tip} className="pen-tip" />}
    </>
  )
}
