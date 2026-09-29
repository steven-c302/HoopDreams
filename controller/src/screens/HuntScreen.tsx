import { useEffect, useRef, useState, type CSSProperties, type PointerEvent as ReactPointerEvent } from 'react'
import { rejectMessage, type ActionPayload, type Screen } from '../protocol'
import { advance, cellAt, lettersOf, pointsFor, spell, tileCenter, tileRects, type Rect } from './swipe'
import './hunt-phone.css'

const buzz = (ms: number | number[]) => { try { navigator.vibrate?.(ms) } catch { /* not supported */ } }
const clock = (s: number) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`

type HuntData = Extract<Screen, { t: 'hunt' }>
type Flash = { kind: 'ok'; word: string; points: number } | { kind: 'bad'; text: string }

interface Props {
  screen: HuntData
  disabled: boolean
  seconds: number | null
  rejected: { code: string; n: number } | null
  onAction(p: ActionPayload): void
}

export function HuntScreen({ screen, disabled, seconds, rejected, onAction }: Props) {
  const n = screen.size
  const playing = screen.phase === 'hunt' && !disabled && screen.tiles.length === n * n
  const boardRef = useRef<HTMLDivElement>(null)
  const rects = useRef<Rect[]>([])
  const pointer = useRef<number | null>(null)
  const pathRef = useRef<number[]>([])
  const total = useRef(0)
  const [path, setPath] = useState<number[]>([])
  const [ghost, setGhost] = useState<number[]>([])
  const [pending, setPending] = useState<string | null>(null)
  const [flash, setFlash] = useState<Flash | null>(null)

  const put = (next: number[]) => { pathRef.current = next; setPath(next) }

  // A new round starts clean.
  useEffect(() => { total.current = 0; pointer.current = null; put([]); setGhost([]); setPending(null); setFlash(null) }, [screen.round]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (seconds != null) total.current = Math.max(total.current, seconds) }, [seconds])

  // The word we sent shows up in the found list when the engine accepts it.
  useEffect(() => {
    if (!pending) return
    const f = screen.found.find((x) => x.word === pending)
    if (f) { setFlash({ kind: 'ok', word: f.word, points: f.points }); setPending(null); buzz([20, 40, 20]) }
  }, [screen.found, pending])
  // No answer after 1.5 s: drop it quietly.
  useEffect(() => { if (!pending) return; const t = setTimeout(() => setPending(null), 1500); return () => clearTimeout(t) }, [pending])
  useEffect(() => {
    if (!rejected || !pending) return
    setPending(null)
    setFlash({ kind: 'bad', text: (rejectMessage(rejected.code) || 'Try another way').toUpperCase() })
  }, [rejected?.n]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (!flash) return; const t = setTimeout(() => setFlash(null), 1400); return () => clearTimeout(t) }, [flash])
  useEffect(() => { if (!ghost.length) return; const t = setTimeout(() => setGhost([]), 700); return () => clearTimeout(t) }, [ghost])

  const measure = () => {
    const b = boardRef.current?.getBoundingClientRect()
    if (b) rects.current = tileRects({ x: b.left, y: b.top, w: b.width, h: b.height }, n)
  }
  const down = (e: ReactPointerEvent<HTMLDivElement>) => {
    if (!playing || pointer.current !== null) return // a second finger is ignored
    pointer.current = e.pointerId
    try { e.currentTarget.setPointerCapture(e.pointerId) } catch { /* no capture: the pointer events still reach the board */ }
    measure()
    setFlash(null)
    const c = cellAt(rects.current, e.clientX, e.clientY)
    put(c == null ? [] : [c])
    if (c != null) buzz(8)
  }
  const move = (e: ReactPointerEvent<HTMLDivElement>) => {
    if (e.pointerId !== pointer.current) return
    const c = cellAt(rects.current, e.clientX, e.clientY)
    if (c == null) return
    const before = pathRef.current
    const next = advance(before, c, n)
    if (next === before) return
    if (next.length > before.length) buzz(8)
    put(next)
  }
  const finish = (e: ReactPointerEvent<HTMLDivElement>, submit: boolean) => {
    if (e.pointerId !== pointer.current) return
    pointer.current = null
    const p = pathRef.current
    put([])
    if (!submit || p.length === 0) return
    const w = spell(screen.tiles, p)
    if (w.length < 3) { setFlash({ kind: 'bad', text: '3 LETTERS MINIMUM' }); return }
    setGhost(p)
    setPending(w.toLowerCase())
    onAction({ kind: 'word', path: p })
    buzz(20)
  }

  const frac = total.current > 0 && seconds != null ? 1 - seconds / total.current : 0
  const hot = screen.phase === 'hunt' && seconds != null && seconds > 0 && seconds <= 10
  const header = (
    <>
      <div className="hunt-top">
        <span className="hunt-round">Round {screen.round} of {screen.totalRounds}</span>
        {seconds != null && screen.phase === 'hunt' && <span className="hunt-time">{clock(seconds)}</span>}
      </div>
      <div className="hunt-bar" role="timer" aria-label={seconds != null ? `${seconds} seconds left` : undefined}><i style={{ width: `${Math.min(1, Math.max(0, frac)) * 100}%` }} /></div>
    </>
  )

  if (screen.phase === 'reveal' || screen.phase === 'scores') {
    return (
      <div className="hunt">
        {header}
        <div className="hunt-total"><span>{screen.phase === 'scores' ? 'Round score' : 'Your round'}</span><b>{screen.score.toLocaleString()}</b></div>
        {screen.found.length === 0
          ? <p className="hunt-empty">No words this round.</p>
          : <ul className="hunt-list">{screen.found.map((f) => (
              <li key={f.word}><span className="w">{f.word.toUpperCase()}</span><span className="p">+{(f.points + f.bonus).toLocaleString()}</span>
                {f.bonus > 0 ? <em>ONLY YOU</em> : <small>{f.finders} FOUND IT</small>}</li>
            ))}</ul>}
      </div>
    )
  }

  const trail = path.length ? path : ghost
  const trailColor = flash?.kind === 'ok' ? 'var(--proof-green)' : flash?.kind === 'bad' ? 'var(--stamp-red)' : 'var(--type-blue)'
  const word = path.length ? spell(screen.tiles, path) : pending ? pending.toUpperCase() : flash?.kind === 'ok' ? flash.word.toUpperCase() : ''
  const letters = lettersOf(screen.tiles, path)
  const hint = flash?.kind === 'bad' ? flash.text
    : flash?.kind === 'ok' ? 'STAMPED'
    : pending ? 'CHECKING'
    : path.length ? (letters >= 3 ? `${letters} LETTERS = ${pointsFor(letters)}` : `${letters} LETTER${letters === 1 ? '' : 'S'} · NEED 3`)
    : screen.phase === 'hunt' ? 'SWIPE TOUCHING LETTERS · LIFT TO STAMP' : (screen.note ?? '').toUpperCase()
  const size = 1000
  const points = trail.map((i) => { const [x, y] = tileCenter(i, n); return `${x * size},${y * size}` }).join(' ')
  const head = trail.length ? tileCenter(trail[trail.length - 1], n) : null

  return (
    <div className={`hunt ${hot ? 'hot' : ''}`}>
      {header}
      <div className={`hunt-preview ${flash?.kind ?? ''}`} aria-live="polite">
        <span>{word || ' '}</span>
        {flash?.kind === 'ok' && <span className="hunt-pop">+{flash.points}</span>}
      </div>
      <div className={`hunt-hint ${flash?.kind ?? ''}`}>{hint}</div>
      <div className="hunt-bed">
        <div
          ref={boardRef} className={`hunt-board n${n}`} style={{ '--n': n } as CSSProperties}
          role="application" aria-label="Letter board. Drag across touching letters and lift to stamp the word."
          onPointerDown={down} onPointerMove={move} onPointerUp={(e) => finish(e, true)} onPointerCancel={(e) => finish(e, false)}
        >
          {Array.from({ length: n * n }, (_, i) => (
            <div key={i} data-i={i} className={`hunt-tile ${path.includes(i) ? 'on' : ''} ${screen.tiles.length ? '' : 'dim'}`}>{screen.tiles[i] ?? ''}</div>
          ))}
          <svg className="hunt-trail" viewBox={`0 0 ${size} ${size}`} preserveAspectRatio="none" aria-hidden="true">
            {trail.length > 1 && <polyline points={points} fill="none" stroke={trailColor} strokeWidth={n === 5 ? 34 : 44} strokeLinecap="round" strokeLinejoin="round" opacity="0.8" />}
            {head && path.length > 0 && <circle cx={head[0] * size} cy={head[1] * size} r={n === 5 ? 20 : 26} fill={trailColor} stroke="var(--paper-2)" strokeWidth="6" />}
          </svg>
          {!playing && screen.phase !== 'hunt' && screen.note && <div className="hunt-cover">{screen.note}</div>}
        </div>
      </div>
      <div className="hunt-words">
        {screen.found.slice(0, 4).map((f, i) => <span key={f.word} className={i === 0 && flash?.kind === 'ok' ? 'new' : ''}>{f.word}</span>)}
      </div>
    </div>
  )
}
