import { useEffect, useState, type ReactNode } from 'react'
import type { PlayerSummary, ScoreRow, StageInfo, TutorialCard } from '../protocol'
import { sfx } from './audio'
import { AvatarDot, Badge, C, Clock, CountUp, Deal, fireConfetti, Marquee, OnAir, Pop, Prop, Slam } from './Studio'

export function GameHeader({ title, titleColor, stage, total, clock, chips, status }: {
  title: string; titleColor: string; stage: StageInfo; total: number; clock: { deadline: number | null; frozen: number | null }
  chips: [string, string][]; status?: string | null
}) {
  const timed = clock.deadline != null || clock.frozen != null
  return (
    <div className="game-header">
      <div className="titles">
        <h1 style={{ color: titleColor }}>{title}</h1>
        <div className="row">{chips.map(([t, c]) => <Badge key={t} text={t} fill={c} />)}</div>
      </div>
      {status && <span className="status-text">{status}</span>}
      <OnAir lit={timed && !stage.paused} />
      {timed && <Clock deadline={clock.deadline} frozen={clock.frozen} total={total} />}
    </div>
  )
}

export function Tutorial({ cards, acked, players, accent, card }: { cards: TutorialCard[]; acked: string[]; players: PlayerSummary[]; accent: string; card: string }) {
  return (
    <>
      <div className="row" style={{ gap: 44, marginTop: 52 }}>
        {cards.map((c, i) => (
          <Deal key={c.title} i={i} style={{ flex: 1 }}>
            <Prop shadow={accent} tilt={[-1.5, 1, -0.5][i % 3]} style={{ minHeight: 400, padding: 40 }}>
              <span className="avatar-dot" style={{ width: 88, height: 88, background: card, color: accent, fontFamily: 'Bungee', fontSize: 52 }}>{i + 1}</span>
              <h2 style={{ fontSize: 52, fontWeight: 900, marginTop: 20 }}>{c.title}</h2>
              <p style={{ fontSize: 36, lineHeight: 1.4, marginTop: 12, color: '#0b0716c0' }}>{c.body}</p>
            </Prop>
          </Deal>
        ))}
      </div>
      <div style={{ flex: 1 }} />
      <div className="row" style={{ gap: 16 }}>
        <span className="display" style={{ color: C.gold, fontSize: 32 }}>READY</span>
        {players.filter((p) => p.connected && p.role === 'PLAYER').map((p) => acked.includes(p.id)
          ? <Pop key={p.id + 'y'}><AvatarDot emoji={p.avatar.emoji} color={p.avatar.color} size={80} /></Pop>
          : <AvatarDot key={p.id} emoji={p.avatar.emoji} color={p.avatar.color} size={80} dim />)}
      </div>
    </>
  )
}

/** Score rows: dealt in, gains pop, totals count up, the leader wears the crown. */
export function ScoreBoard({ scores, deltas, unit = '' }: { scores: ScoreRow[]; deltas: Record<string, number>; unit?: string }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16, marginTop: 28 }}>
      {scores.slice(0, 8).map((r, i) => {
        const d = deltas[r.id] ?? 0
        return (
          <Deal key={r.id} i={i}>
            <div className="score-row prop" style={{ background: i === 0 ? C.gold : C.cream, boxShadow: `8px 8px 0 ${i === 0 ? C.pink : C.brass}` }}>
              <span className="rank-dot">{i + 1}</span>
              <AvatarDot emoji={r.avatar.emoji} color={r.avatar.color} size={64} />
              <span style={{ flex: 1 }}>{r.name}{i === 0 ? '  👑' : ''}</span>
              {d !== 0 && <Pop delay={0.3 + i * 0.09}><span className={`delta-pill ${d < 0 ? 'neg' : ''}`}>{d > 0 ? '+' : ''}{d.toLocaleString()}</span></Pop>}
              <span className="total"><CountUp from={r.score - d} to={r.score} delay={500 + i * 120} onStep={i < 3 ? (n) => sfx.countTick(n % 20) : undefined} />{unit}</span>
            </div>
          </Deal>
        )
      })}
      {scores.length > 8 && <p style={{ fontSize: 32, color: C.muted }}>+ {scores.length - 8} more</p>}
    </div>
  )
}

/** Third, second, then first slam onto lit blocks; then the cannons. */
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
            <Slam delay={lands[rank]} from={2.2} tilt={rank === 1 ? 8 : -8} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
              {rank === 0 && <span style={{ fontSize: 68 }}>👑</span>}
              <AvatarDot emoji={r.avatar.emoji} color={r.avatar.color} size={rank === 0 ? 152 : 120} />
              <span style={{ fontSize: 52, fontWeight: 900 }}>{r.name}</span>
              <span className="display" style={{ fontSize: 48, color: C.gold }}>{r.score.toLocaleString()}{unit}</span>
            </Slam>
            <Deal i={0}>
              <div className="podium-block prop" style={{ height: [460, 330, 230][rank], background: [C.gold, C.cream, C.brass][rank], boxShadow: `16px 16px 0 ${C.pink}`, transitionDelay: `${lands[rank]}s` }}>
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
      <div className="curtain" style={{ left: 0 }} />
      <div className="curtain" style={{ right: 0 }} />
      <Slam from={1.5} tilt={-4}>
        <Marquee style={{ maxWidth: 1240 }}>
          <span className="marquee-font" style={{ fontSize: 120, color: C.gold }}>PAUSED</span>
          <p style={{ fontSize: 40, fontWeight: 700, textAlign: 'center' }}>
            {reason === 'WAITING_FOR_PLAYERS' ? 'Waiting for players to reconnect · press Esc for host controls'
              : reason === 'RESTORED' ? 'Picked up where you left off · press Esc and choose Resume' : 'Press Esc for host controls'}
          </p>
        </Marquee>
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
