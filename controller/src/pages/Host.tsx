import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Connection, browserSocket, browserSocketUrl, type Status } from '../net/connection'
import { rejectMessage, type GameListing, type HostCommand, type TvState } from '../protocol'

const KEY = 'partyos.host'

export function Host() {
  const [token, setToken] = useState<string | null>(() => { try { return sessionStorage.getItem(KEY) } catch { return null } })
  if (!token) return <PinForm onToken={(t) => { try { sessionStorage.setItem(KEY, t) } catch { /* ignore */ } setToken(t) }} />
  return <HostPanel token={token} onLogout={() => { try { sessionStorage.removeItem(KEY) } catch { /* ignore */ } setToken(null) }} />
}

function PinForm({ onToken }: { onToken(t: string): void }) {
  const [pin, setPin] = useState('')
  const [error, setError] = useState<string | null>(null)
  async function submit(e: FormEvent) {
    e.preventDefault()
    const r = await fetch('/api/host/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ pin }) })
    const body = await r.json().catch(() => ({}))
    if (r.ok) onToken(body.hostToken)
    else setError(body.error === 'LOCKED' ? `Too many tries. Wait ${body.retryAfterSec}s.` : 'Wrong PIN. It is shown in the TV settings.')
  }
  return (
    <main className="page join">
      <header className="brand"><span className="logo">PARTY<b>OS</b></span><span className="room-chip">Host</span></header>
      <form className="stack" onSubmit={submit}>
        <label className="field"><span>Host PIN</span>
          <input value={pin} onChange={(e) => setPin(e.target.value)} inputMode="numeric" maxLength={8} autoFocus className="code-input" />
        </label>
        {error && <p className="error" role="alert">{error}</p>}
        <button className="primary big" disabled={!pin}>Unlock host controls</button>
      </form>
    </main>
  )
}

function HostPanel({ token, onLogout }: { token: string; onLogout(): void }) {
  const [tv, setTv] = useState<TvState | null>(null)
  const [games, setGames] = useState<GameListing[]>([])
  const [rounds, setRounds] = useState(5)
  const [status, setStatus] = useState<Status>('connecting')
  const [toast, setToast] = useState<string | null>(null)
  const conn = useRef<Connection | null>(null)

  useEffect(() => { fetch('/api/games').then((r) => r.json()).then(setGames).catch(() => setGames([])) }, [])
  useEffect(() => {
    const c = new Connection(browserSocketUrl(`host=${encodeURIComponent(token)}`), browserSocket, {
      onStatus: setStatus,
      onMessage: (m) => {
        if (m.t === 'tv') setTv(m.tv)
        if (m.t === 'reject') setToast(rejectMessage(m.code) || m.code)
        if (m.t === 'bye') onLogout()
      },
    })
    conn.current = c
    c.start()
    return () => c.stop()
  }, [token, onLogout])

  const cmd = (c: HostCommand) => conn.current?.host(c)
  if (!tv) return <main className="page center"><div className="spinner" /><p>Connecting…</p></main>
  const stage = tv.stage
  const game = stage?.game as { phase?: string; round?: number; totalRounds?: number } | undefined

  return (
    <main className="page host">
      <header className="topbar"><span className="logo small">PARTY<b>OS</b> host</span><span className="room-chip">Room {tv.roomCode}</span></header>
      {status !== 'online' && <div className="banner warn">Reconnecting…</div>}
      {stage ? (
        <section className="panel stack">
          <h2>{stage.title}{game?.round ? ` · round ${game.round}/${game.totalRounds}` : ''}</h2>
          <p className="muted">{stage.tutorial ? 'Tutorial' : game?.phase ?? ''}{stage.paused ? ' · paused' : ''}</p>
          <div className="row wrap">
            {stage.paused ? <button className="primary" onClick={() => cmd({ t: 'resume' })}>Resume</button>
              : <button onClick={() => cmd({ t: 'pause' })}>Pause</button>}
            <button onClick={() => cmd({ t: 'skip' })}>Skip phase</button>
            <button className="danger" onClick={() => { if (confirm('End this game now?')) cmd({ t: 'end' }) }}>End game</button>
          </div>
        </section>
      ) : (
        <section className="panel stack">
          <h2>Start a game</h2>
          <label className="row between"><span>Rounds</span>
            <select value={rounds} onChange={(e) => setRounds(Number(e.target.value))}>{[3, 4, 5, 6, 7, 8].map((n) => <option key={n}>{n}</option>)}</select>
          </label>
          {games.map((g) => (
            <button key={g.id} className="primary game-btn" onClick={() => cmd({ t: 'start', gameId: g.id, rounds })}>
              <b>{g.title}</b><span>{g.tagline} · {g.minPlayers}+ players</span>
            </button>
          ))}
        </section>
      )}
      <section className="panel">
        <h2>Players ({tv.players.filter((p) => p.role === 'PLAYER').length})</h2>
        <ul className="players">
          {tv.players.map((p) => (
            <li key={p.id}>
              <span className={`status ${p.connected ? 'on' : ''}`} />
              <span className="dot-avatar" style={{ background: p.avatar.color }}>{p.avatar.emoji}</span>
              <span className="name">{p.name}{p.role === 'SPECTATOR' ? ' (watching)' : ''}</span>
              <button className="ghost small" onClick={() => { if (confirm(`Remove ${p.name}?`)) cmd({ t: 'kick', playerId: p.id }) }}>Remove</button>
            </li>
          ))}
        </ul>
      </section>
      {toast && <div className="toast" role="status" onAnimationEnd={() => setToast(null)}>{toast}</div>}
    </main>
  )
}
