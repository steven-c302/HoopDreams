import { useEffect, useRef, useState } from 'react'
import { Connection, browserSocket, browserSocketUrl, type Status } from '../net/connection'
import { rejectMessage, type ActionPayload, type PhoneState } from '../protocol'
import type { Session } from '../net/token'
import { ScreenView } from '../screens/ScreenView'
import { useCountdown } from './useCountdown'

const BYE: Record<string, string> = {
  KICKED: 'The host removed you from the party.',
  BAD_TOKEN: 'That party has ended or the TV restarted. Join again!',
}

async function takeSeat(token: string, report: (m: string) => void) {
  const r = await fetch('/api/role', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token, role: 'PLAYER' }) })
    .catch(() => null)
  if (r?.ok) return
  const code = (await r?.json().catch(() => ({})))?.error
  report(code === 'FULL' ? 'All 16 player seats are taken.' : code === 'GAME_RUNNING' ? 'You can join when this game ends.' : "Couldn't switch right now.")
}

export function Play({ session, onLeave }: { session: Session; onLeave(why: string): void }) {
  const [view, setView] = useState<PhoneState | null>(null)
  const [seq, setSeq] = useState(0)
  const [status, setStatus] = useState<Status>('connecting')
  const [toast, setToast] = useState<string | null>(null)
  const conn = useRef<Connection | null>(null)

  useEffect(() => {
    const c = new Connection(browserSocketUrl(`token=${encodeURIComponent(session.token)}`), browserSocket, {
      onStatus: setStatus,
      onMessage: (m) => {
        if (m.t === 'view') { setView(m.view); setSeq(m.seq) }
        if (m.t === 'reject') { const text = rejectMessage(m.code); if (text) setToast(text) }
        if (m.t === 'bye') onLeave(BYE[m.reason] ?? 'You left the party.')
      },
    })
    conn.current = c
    c.start()
    return () => c.stop()
  }, [session.token, onLeave])

  useEffect(() => {
    if (!toast) return
    const t = setTimeout(() => setToast(null), 3_000)
    return () => clearTimeout(t)
  }, [toast])

  const seconds = useCountdown(view?.remainingMs, seq, view?.paused ?? false)
  const send = (payload: ActionPayload) => { if (view) conn.current?.act(view.round, payload) }

  if (!view) return <main className="page center"><div className="spinner" /><p>Connecting to the TV…</p></main>

  return (
    <main className="page play">
      <header className="topbar">
        <span className="me"><span className="dot-avatar" style={{ background: view.me.avatar.color }}>{view.me.avatar.emoji}</span>{view.me.name}</span>
        <span className="room-chip">{view.gameTitle ?? `Room ${view.roomCode}`}</span>
        {seconds != null && view.gameId && <span className={`timer ${seconds <= 5 ? 'hot' : ''}`}>{seconds}</span>}
      </header>
      {status !== 'online' && <div className="banner warn">Reconnecting…</div>}
      {view.paused && <div className="banner">{view.pauseReason === 'WAITING_FOR_PLAYERS' ? 'Paused: waiting for players' : 'Paused'}</div>}
      <section className="screen" key={`${view.round}-${view.screen.t}`}>
        <ScreenView screen={view.screen} disabled={view.paused} onAction={send} meId={view.me.id} />
        {view.me.role === 'SPECTATOR' && !view.gameId && (
          <button className="primary big" onClick={() => void takeSeat(session.token, setToast)}>Join as a player</button>
        )}
      </section>
      {toast && <div className="toast" role="status">{toast}</div>}
    </main>
  )
}
