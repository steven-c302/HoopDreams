import { useRef, useState, type FormEvent, type PointerEvent } from 'react'
import type { Session } from '../net/token'
import { Face, PRESET_FACES } from '../theme/Face'

/** Player colours, as hex because the server validates #RRGGBB. They match the theme's crayons. */
const COLORS = ['#FF4B3E', '#FF8A2B', '#FFD23F', '#2FBF55', '#7FD3FF', '#2F6BFF', '#8B4DFF', '#FF6FB5']
const PRESETS = Array.from({ length: PRESET_FACES }, (_, i) => `p:${String(i).padStart(2, '0')}`)
/** Keep in step with MAX_FACE in PartyEngine.kt (the "d:" prefix included). */
const MAX_FACE = 1600

const ERRORS: Record<string, string> = {
  WRONG_ROOM: "That room code doesn't match the TV. Rescan the QR code.",
  NAME_TAKEN: 'Someone already has that name. Try another!',
  BAD_NAME: 'Names need 1 to 16 characters.',
  FULL: 'The party is full. You can join to watch.',
  LAN_ONLY: 'Your phone must be on the same Wi-Fi as the TV.',
}

export function Join({ room, notice, onJoined }: { room: string | null; notice: string | null; onJoined(s: Session): void }) {
  const [code, setCode] = useState(room ?? '')
  const [name, setName] = useState('')
  const [color, setColor] = useState(() => COLORS[Math.floor(Math.random() * COLORS.length)])
  const [strokes, setStrokes] = useState<string[]>([])
  const [preset, setPreset] = useState(() => PRESETS[Math.floor(Math.random() * PRESETS.length)])
  const [mode, setMode] = useState<'draw' | 'pick'>('draw')
  const [error, setError] = useState<string | null>(notice)
  const [busy, setBusy] = useState(false)

  const drawn = strokes.join('')
  const face = mode === 'draw' && drawn ? `d:${drawn}` : preset

  async function join(spectator: boolean, e?: FormEvent) {
    e?.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const r = await fetch('/api/join', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ room: code.trim().toUpperCase(), name: name.trim(), avatar: { face, color }, spectator }),
      })
      const body = await r.json().catch(() => ({}))
      if (r.ok) onJoined({ room: code.trim().toUpperCase(), token: body.token, playerId: body.playerId })
      else setError(ERRORS[body.error] ?? `Couldn't join (${body.error ?? r.status}).`)
    } catch {
      setError("Can't reach the TV. Check that you're on the same Wi-Fi.")
    } finally {
      setBusy(false)
    }
  }

  const ready = code.trim().length === 4 && name.trim().length > 0 && !busy

  return (
    <main className="page join">
      <header className="brand">
        <span className="logo">PARTY<b>OS</b></span>
        {room && <span className="room-chip">Room {room}</span>}
      </header>
      <form onSubmit={(e) => join(false, e)} className="stack">
        {!room && (
          <label className="field">
            <span>Room code</span>
            <input value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} maxLength={4}
              autoCapitalize="characters" autoComplete="off" placeholder="ABCD" className="code-input" />
          </label>
        )}
        <label className="field">
          <span>Your name</span>
          <input value={name} onChange={(e) => setName(e.target.value)} maxLength={16} autoComplete="nickname" placeholder="Sam" />
        </label>

        <div className="face-maker">
          <div className="tabs" role="tablist">
            <button type="button" role="tab" aria-selected={mode === 'draw'} className={mode === 'draw' ? 'on' : ''} onClick={() => setMode('draw')}>Draw your face</button>
            <button type="button" role="tab" aria-selected={mode === 'pick'} className={mode === 'pick' ? 'on' : ''} onClick={() => setMode('pick')}>Pick one</button>
          </div>
          {mode === 'draw' ? (
            <>
              <DrawPad color={color} strokes={strokes} setStrokes={setStrokes} />
              <div className="row between">
                <span className="muted">{drawn ? 'Looking good.' : 'Draw with your finger. Eyes, mouth, hair, whatever.'}</span>
                <span className="row">
                  <button type="button" className="small ghost" disabled={!strokes.length} onClick={() => setStrokes(strokes.slice(0, -1))}>Undo</button>
                  <button type="button" className="small ghost" disabled={!strokes.length} onClick={() => setStrokes([])}>Clear</button>
                </span>
              </div>
            </>
          ) : (
            <div className="grid preset-grid" role="radiogroup" aria-label="Face">
              {PRESETS.map((p) => (
                <button type="button" key={p} role="radio" aria-checked={p === preset} className={p === preset ? 'on' : ''} onClick={() => setPreset(p)}>
                  <Face face={p} color={color} size={52} />
                </button>
              ))}
            </div>
          )}
          <div className="grid color-grid" role="radiogroup" aria-label="Colour">
            {COLORS.map((c) => (
              <button type="button" key={c} className={c === color ? 'on' : ''} aria-label={`Colour ${c}`} aria-checked={c === color} role="radio"
                style={{ background: c }} onClick={() => setColor(c)} />
            ))}
          </div>
        </div>

        {error && <p className="error" role="alert">{error}</p>}
        <button className="primary big" disabled={!ready}>Join the party</button>
        <button type="button" className="ghost" disabled={!ready} onClick={() => join(true)}>Just watch</button>
      </form>
    </main>
  )
}

/** Finger-painting on a face: strokes are quantised to a 0..99 grid and kept small enough for the server. */
function DrawPad({ color, strokes, setStrokes }: { color: string; strokes: string[]; setStrokes(s: string[]): void }) {
  const pad = useRef<HTMLDivElement>(null)
  const live = useRef<{ d: string; x: number; y: number } | null>(null)
  const [, force] = useState(0)
  const used = strokes.join('').length + 2
  const full = used >= MAX_FACE - 20

  const point = (e: PointerEvent) => {
    const r = pad.current!.getBoundingClientRect()
    // The face's viewBox is -4..104; map the touch into it and clamp to the drawable 0..99 grid.
    const clamp = (v: number) => Math.max(0, Math.min(99, Math.round(v)))
    return { x: clamp(((e.clientX - r.left) / r.width) * 108 - 4), y: clamp(((e.clientY - r.top) / r.height) * 108 - 4) }
  }
  const down = (e: PointerEvent) => {
    if (full) return
    pad.current!.setPointerCapture(e.pointerId)
    const p = point(e)
    live.current = { d: `M${p.x},${p.y}L${p.x},${p.y}`, x: p.x, y: p.y }
    force((n) => n + 1)
  }
  const move = (e: PointerEvent) => {
    const s = live.current
    if (!s) return
    const p = point(e)
    if (Math.hypot(p.x - s.x, p.y - s.y) < 2.5) return
    const seg = `L${p.x},${p.y}`
    if (used + s.d.length + seg.length > MAX_FACE) return
    live.current = { d: s.d + seg, x: p.x, y: p.y }
    force((n) => n + 1)
  }
  const up = () => {
    if (live.current) setStrokes([...strokes, live.current.d])
    live.current = null
  }
  const preview = strokes.join('') + (live.current?.d ?? '')
  return (
    <div className="draw-pad" ref={pad} onPointerDown={down} onPointerMove={move} onPointerUp={up} onPointerCancel={up} style={{ touchAction: 'none' }}>
      <Face face={preview ? `d:${preview}` : 'd:'} color={color} size={260} />
      {full && <span className="pad-full">Canvas full</span>}
    </div>
  )
}
