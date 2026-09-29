import { useCallback, useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react'
import type { Screen } from '../protocol'
import { InkBatcher, chunkOps, pressureOf, strokeOps, toGrid } from '../ink/codec'
import { paintAll, paintStroke, scaleFor } from '../ink/paint'
import { BRUSHES, BRUSH_NAMES, CRAYONS, CRAYON_NAMES, INK_H, INK_W, type InkOp, type Stroke } from '../ink/types'
import './draw.css'

type DrawScreen = Extract<Screen, { t: 'draw' }>

const LEVELS = ['', 'Easy', 'Medium', 'Hard']
const buzz = (ms: number) => { try { navigator.vibrate?.(ms) } catch { /* not supported */ } }

/**
 * The drawer's canvas. Strokes are kept in refs and painted straight onto the canvas, so the view updates that arrive
 * with every guess never touch the drawing. Points go out in ~50 ms batches over the ink channel; if the socket drops
 * and comes back the whole drawing is resent, and a fresh pad (a page reload) tells the TV to start clean.
 */
export function DrawPad({ screen, online, sendInk }: { screen: DrawScreen; online: boolean; sendInk(ops: InkOp[]): void }) {
  const canvas = useRef<HTMLCanvasElement>(null)
  const strokes = useRef<Stroke[]>([])
  const batch = useRef(new InkBatcher())
  const nextId = useRef(1)
  const active = useRef<{ pointer: number } | null>(null)
  const timer = useRef<ReturnType<typeof setInterval> | null>(null)
  const scale = useRef(1)
  const send = useRef(sendInk)
  send.current = sendInk
  const [colour, setColour] = useState(0)
  const [brush, setBrush] = useState(1)
  const [count, setCount] = useState(0)

  const ctx = () => canvas.current?.getContext('2d') ?? null
  const flush = useCallback(() => {
    for (const c of chunkOps(batch.current.take())) send.current(c)
  }, [])

  const repaint = useCallback(() => {
    const el = canvas.current
    const c = el?.getContext('2d')
    if (el && c) paintAll(c, strokes.current, scale.current, el.width, el.height)
  }, [])

  const fit = useCallback(() => {
    const el = canvas.current
    if (!el || !el.clientWidth) return
    const dpr = Math.min(window.devicePixelRatio || 1, 3)
    const w = Math.round(el.clientWidth * dpr)
    if (el.width !== w) {
      el.width = w
      el.height = Math.round((w * INK_H) / INK_W)
    }
    scale.current = scaleFor(el.width)
    repaint()
  }, [repaint])

  const endStroke = useCallback(() => {
    if (!active.current) return
    active.current = null
    const st = strokes.current[strokes.current.length - 1]
    if (st) {
      st.open = false
      const c = canvas.current?.getContext('2d')
      if (c) paintStroke(c, st, scale.current, st.pts.length / 3, st.pts.length / 3, true)
    }
    batch.current.end()
    if (timer.current) clearInterval(timer.current)
    timer.current = null
    flush()
  }, [flush])

  useEffect(() => {
    fit()
    const el = canvas.current!
    const ro = new ResizeObserver(fit)
    ro.observe(el)
    send.current([{ t: 'clear' }]) // a fresh pad: the TV's canvas must match it
    const stop = () => endStroke()
    window.addEventListener('blur', stop)
    document.addEventListener('visibilitychange', stop)
    return () => {
      ro.disconnect()
      window.removeEventListener('blur', stop)
      document.removeEventListener('visibilitychange', stop)
      if (timer.current) clearInterval(timer.current)
    }
  }, [fit, endStroke])

  const wasOnline = useRef(online)
  useEffect(() => {
    if (online && !wasOnline.current) {
      const ops: InkOp[] = [{ t: 'clear' }, ...strokes.current.flatMap(strokeOps)]
      for (const c of chunkOps(ops)) send.current(c)
    }
    wasOnline.current = online
  }, [online])

  const down = (e: ReactPointerEvent<HTMLCanvasElement>) => {
    if (active.current) return
    e.currentTarget.setPointerCapture(e.pointerId)
    const [x, y] = toGrid(e.clientX, e.clientY, e.currentTarget.getBoundingClientRect())
    const p = pressureOf(e)
    const st: Stroke = { s: nextId.current++, c: colour, w: brush, pts: [x, y, p], open: true }
    strokes.current.push(st)
    active.current = { pointer: e.pointerId }
    batch.current.start(st.s, colour, brush, x, y, p)
    const c = ctx()
    if (c) paintStroke(c, st, scale.current, 0, 1, false)
    timer.current = setInterval(flush, 50)
    setCount(strokes.current.length)
  }

  const move = (e: ReactPointerEvent<HTMLCanvasElement>) => {
    if (!active.current || active.current.pointer !== e.pointerId) return
    const st = strokes.current[strokes.current.length - 1]
    const c = ctx()
    if (!st || !c) return
    const rect = e.currentTarget.getBoundingClientRect()
    const coalesced = e.nativeEvent.getCoalescedEvents?.() ?? []
    for (const ev of coalesced.length ? coalesced : [e.nativeEvent]) {
      const [x, y] = toGrid(ev.clientX, ev.clientY, rect)
      const n = st.pts.length
      if (Math.abs(x - st.pts[n - 3]) + Math.abs(y - st.pts[n - 2]) < 2) continue
      const p = pressureOf(ev)
      const from = n / 3
      st.pts.push(x, y, p)
      batch.current.point(x, y, p)
      paintStroke(c, st, scale.current, from, from + 1, false)
    }
  }

  const undo = () => {
    endStroke()
    if (!strokes.current.length) return
    strokes.current.pop()
    batch.current.op({ t: 'undo' })
    flush()
    repaint()
    setCount(strokes.current.length)
    buzz(15)
  }

  const clear = () => {
    endStroke()
    if (!strokes.current.length) return
    strokes.current.length = 0
    batch.current.op({ t: 'clear' })
    flush()
    repaint()
    setCount(0)
    buzz(25)
  }

  return (
    <div className="draw-pad">
      <div className="draw-word">
        <small>YOU'RE DRAWING · {LEVELS[screen.difficulty]}</small>
        <b>{screen.word}</b>
      </div>
      <div className="draw-paper">
        <canvas
          ref={canvas}
          className="draw-canvas"
          aria-label="Drawing pad"
          onPointerDown={down}
          onPointerMove={move}
          onPointerUp={endStroke}
          onPointerCancel={endStroke}
          onContextMenu={(e) => e.preventDefault()}
        />
      </div>
      <div className="draw-tools">
        <div className="draw-crayons" role="radiogroup" aria-label="Crayon colour">
          {CRAYONS.map((hex, i) => (
            <button key={hex} type="button" role="radio" aria-checked={colour === i} aria-label={CRAYON_NAMES[i]} className={`crayon ${colour === i ? 'on' : ''}`} style={{ background: hex }} onClick={() => setColour(i)} />
          ))}
        </div>
        <div className="draw-brushes" role="radiogroup" aria-label="Brush size">
          {BRUSHES.map((w, i) => (
            <button key={w} type="button" role="radio" aria-checked={brush === i} aria-label={BRUSH_NAMES[i]} className={`brush ${brush === i ? 'on' : ''}`} onClick={() => setBrush(i)}>
              <span style={{ width: 6 + i * 9, height: 6 + i * 9 }} />
            </button>
          ))}
          <button type="button" className="tool" disabled={!count} onClick={undo}>Undo</button>
          <button type="button" className="tool" disabled={!count} onClick={clear}>Clear</button>
        </div>
      </div>
      <p className="draw-status" role="status">{screen.guessed} of {screen.expected} have it</p>
    </div>
  )
}
