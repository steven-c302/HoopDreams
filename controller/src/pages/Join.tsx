import { useState, type FormEvent } from 'react'
import type { Session } from '../net/token'

const EMOJI = ['🦊', '🐸', '🐙', '🦄', '🐯', '🐼', '🦖', '🐝', '👽', '🤖', '🎃', '🍕', '🌮', '🚀', '🎸', '💎']
const COLORS = ['#FF4D8D', '#FF7A00', '#FFD23F', '#3DDC97', '#2EC4F1', '#6C63FF', '#B15CFF', '#F5F5F5']

const ERRORS: Record<string, string> = {
  WRONG_ROOM: "That room code doesn't match the TV. Rescan the QR code.",
  NAME_TAKEN: 'Someone already has that name. Try another!',
  BAD_NAME: 'Names need 1 to 16 characters.',
  FULL: 'The party is full. You can join as a spectator.',
  LAN_ONLY: 'Your phone must be on the same Wi-Fi as the TV.',
}

export function Join({ room, notice, onJoined }: { room: string | null; notice: string | null; onJoined(s: Session): void }) {
  const [code, setCode] = useState(room ?? '')
  const [name, setName] = useState('')
  const [emoji, setEmoji] = useState(() => EMOJI[Math.floor(Math.random() * EMOJI.length)])
  const [color, setColor] = useState(() => COLORS[Math.floor(Math.random() * COLORS.length)])
  const [error, setError] = useState<string | null>(notice)
  const [busy, setBusy] = useState(false)

  async function join(spectator: boolean, e?: FormEvent) {
    e?.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const r = await fetch('/api/join', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ room: code.trim().toUpperCase(), name: name.trim(), avatar: { emoji, color }, spectator }),
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
          <input value={name} onChange={(e) => setName(e.target.value)} maxLength={16} autoComplete="nickname"
            placeholder="What should the TV call you?" autoFocus />
        </label>
        <div className="avatar-preview" style={{ background: color }}>{emoji}</div>
        <div className="grid emoji-grid" role="radiogroup" aria-label="Avatar">
          {EMOJI.map((x) => (
            <button type="button" key={x} className={x === emoji ? 'on' : ''} aria-checked={x === emoji} role="radio" onClick={() => setEmoji(x)}>{x}</button>
          ))}
        </div>
        <div className="grid color-grid" role="radiogroup" aria-label="Color">
          {COLORS.map((c) => (
            <button type="button" key={c} className={c === color ? 'on' : ''} aria-label={c} aria-checked={c === color} role="radio"
              style={{ background: c }} onClick={() => setColor(c)} />
          ))}
        </div>
        {error && <p className="error" role="alert">{error}</p>}
        <button className="primary big" disabled={!ready}>Join the party</button>
        <button type="button" className="ghost" disabled={!ready} onClick={() => join(true)}>Just watch</button>
      </form>
    </main>
  )
}
