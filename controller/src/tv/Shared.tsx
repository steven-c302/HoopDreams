import { useEffect, useState, type ReactNode } from 'react'
import type { PlayerSummary, ScoreRow, StageInfo, TutorialCard } from '../protocol'
import { sfx } from './audio'
import { AvatarFace, C, Chip, CountUp, Crown, Deal, Keycap, Panel, Pop, Slam, Timer, fireConfetti } from './toon'

export function GameHeader({ title, stage, total, clock, chips, status }: {
  title: string; stage: StageInfo; total: number; clock: { deadline: number | null; frozen: number | null }
  chips: [string, string][]; status?: string | null
}) {
  const timed = clock.deadline != null || clock.frozen != null
  return (
    <div className="game-header">
      <div className="titles">
        <h1>{title.toUpperCase()}</h1>
        <div className="row">{chips.map(([t, c]) => <Chip key={t} fill={c} ink={C.ink}>{t}</Chip>)}</div>
      </div>
      {status && <span className="status-text">{status}</span>}
      {timed && !stage.paused && <Timer deadline={clock.deadline} frozen={clock.frozen} total={total} />}
      {timed && stage.paused && <Timer deadline={null} frozen={clock.frozen ?? 0} total={total} />}
    </div>
  )
}

const TUTORIAL_FILLS = [C.sun, C.sky, C.bubblegum]

export function Tutorial({ cards, acked, players }: { cards: TutorialCard[]; acked: string[]; players: PlayerSummary[] }) {
  return (
    <>
      <div className="tutorial-cards">
        {cards.map((c, i) => (
          <Deal key={c.title} i={i}>
            <Panel fill={C.paper} tilt={[-1.5, 1, -0.5][i % 3]}>
              <span className="num" style={{ background: TUTORIAL_FILLS[i % 3] }}>{i + 1}</span>
              <h2>{c.title}</h2>
              <p>{c.body}</p>
            </Panel>
          </Deal>
        ))}
      </div>
      <div style={{ flex: 1 }} />
      <div className="ready-row">
        <span className="label">TAP READY ON YOUR PHONE</span>
        {players.filter((p) => p.connected && p.role === 'PLAYER').map((p) => acked.includes(p.id)
          ? <Pop key={p.id + 'y'}><AvatarFace avatar={p.avatar} size={80} /></Pop>
          : <AvatarFace key={p.id} avatar={p.avatar} size={80} dim />)}
      </div>
    </>
  )
}

/**
 * Score rows: dealt in, gains pop, totals count up, the leader wears the crown. Up to six players get one column of big
 * rows; a bigger party gets two columns of compact ones, so all sixteen fit on the TV and nobody is "+ 5 more".
 */
export function ScoreBoard({ scores, deltas, unit = '' }: { scores: ScoreRow[]; deltas: Record<string, number>; unit?: string }) {
  const compact = scores.length > 6
  const rows = scores.slice(0, 16)
  return (
    <div className={`score-board ${compact ? 'compact' : ''}`} style={compact ? { gridTemplateRows: `repeat(${Math.ceil(rows.length / 2)}, auto)` } : undefined}>
      {rows.map((r, i) => {
        const d = deltas[r.id] ?? 0
        return (
          <Deal key={r.id} i={i}>
            <Panel className="score-row" fill={i === 0 ? C.sun : C.paper} tilt={i % 2 ? 0.4 : -0.4}>
              <span className="rank-dot">{i + 1}</span>
              <AvatarFace avatar={r.avatar} size={compact ? 48 : 64} />
              <span className="who"><span>{r.name}</span>{i === 0 && <Crown size={compact ? 40 : 56} />}</span>
              {d !== 0 && <Pop delay={0.3 + i * 0.09}><span className={`delta-pill ${d < 0 ? 'neg' : ''}`}>{d > 0 ? '+' : ''}{d.toLocaleString()}</span></Pop>}
              <span className="total"><CountUp from={r.score - d} to={r.score} delay={500 + i * 120} onStep={i < 3 ? (n) => sfx.countTick(n % 20) : undefined} />{unit}</span>
            </Panel>
          </Deal>
        )
      })}
    </div>
  )
}

/** Third, second, then first slam onto their blocks; then the confetti. */
export function Podium({ scores, unit = '' }: { scores: ScoreRow[]; unit?: string }) {
  const top = scores.slice(0, 3)
  const order = [1, 0, 2].filter((r) => r < top.length)
  const lands = [2.6, 1.4, 0.4]
  useEffect(() => {
    const ids = [
      setTimeout(() => sfx.crash(), 400), setTimeout(() => sfx.crash(), 1400),
      setTimeout(() => { sfx.fanfare(); sfx.applause(3.5); fireConfetti(true) }, 2700),
    ]
    return () => ids.forEach(clearTimeout)
  }, [])
  return (
    <div className="podium">
      {order.map((rank) => {
        const r = top[rank]
        return (
          <div key={r.id} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16 }}>
            <Slam delay={lands[rank]} from={2.2} tilt={rank === 1 ? 8 : -8} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
              {rank === 0 && <Crown size={110} />}
              <AvatarFace avatar={r.avatar} size={rank === 0 ? 156 : 124} />
              <span style={{ fontSize: 50, fontWeight: 900 }}>{r.name}</span>
              <span className="display" style={{ fontSize: 44 }}>{r.score.toLocaleString()}{unit}</span>
            </Slam>
            <Deal i={0}>
              <div className="podium-block panel" data-rank={rank + 1} style={{ height: [440, 320, 220][rank], background: [C.sun, C.sky, C.bubblegum][rank], boxShadow: 'var(--shadow-tv) 0 0 var(--ink)' }}>
                <span>{rank + 1}</span>
              </div>
            </Deal>
          </div>
        )
      })}
    </div>
  )
}

export function Paused({ reason }: { reason?: string }) {
  return (
    <div className="paused">
      <Slam from={1.5} tilt={-4}>
        <Panel fill={C.sun} tilt={-2}>
          <h2>PAUSED</h2>
          <p>
            {reason === 'WAITING_FOR_PLAYERS' ? <>Waiting for players to reconnect. <Keycap label="Esc" /> for host controls.</>
              : reason === 'RESTORED' ? <>Picked up where you left off. <Keycap label="P" /> to resume.</> : <><Keycap label="P" /> resumes · <Keycap label="Esc" /> host controls</>}
          </p>
        </Panel>
      </Slam>
    </div>
  )
}

/** Runs [effect] once per [key] after [delayMs]. */
export function useLater(key: unknown, delayMs: number, effect: () => void) {
  useEffect(() => { const id = setTimeout(effect, delayMs); return () => clearTimeout(id) }, [key]) // eslint-disable-line react-hooks/exhaustive-deps
}

export function Fill({ children }: { children: ReactNode }) { return <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>{children}</div> }

export function useStep(total: number, key: unknown, firstMs: number, stepMs: number, paused: boolean) {
  const [shown, setShown] = useState(0)
  useEffect(() => { setShown(0) }, [key])
  useEffect(() => {
    if (paused || shown >= total) return
    const id = setTimeout(() => setShown((n) => n + 1), shown === 0 ? firstMs : stepMs)
    return () => clearTimeout(id)
  }, [shown, total, paused, firstMs, stepMs, key])
  return shown
}
