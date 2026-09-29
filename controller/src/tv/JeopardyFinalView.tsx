import type { PlayerSummary } from '../protocol'
import { AvatarFace, Burst, C, Deal, Panel } from './toon'
import type { JeopardyTv } from './types'

const signed = (n: number) => `${n > 0 ? '+' : ''}${n}`

/** The category card, the secret-bet wait, the think time, and the lowest-score-first reveal. */
export function JeopardyFinalView({ g, players }: { g: JeopardyTv; players: PlayerSummary[] }) {
  const f = g.final!
  const who = new Map(players.map((p) => [p.id, p]))
  return (
    <div className={`jeo-finalwrap ${g.phase === 'final_reveal' ? 'reveal' : ''}`}>
      {g.phase !== 'final_reveal' && <Burst text="FINAL JEOPARDY" fill={C.sun} ink={C.ink} width={880} height={300} size={84} tilt={-3} />}
      <p className="jeo-final-cat">{f.category}</p>
      {g.phase === 'final_category' && <p className="jeo-note">Get your bets ready</p>}
      {g.phase === 'final_wager' && <p className="jeo-note">Place your bet on your phone. {f.wagers}/{f.expected} in</p>}
      {(g.phase === 'final_answer' || g.phase === 'final_reveal') && f.clue && <Panel className="jeo-clue small" fill={C.paper} tilt={0}>{f.clue}</Panel>}
      {g.phase === 'final_answer' && <p className="jeo-note">Think! {f.wagers}/{f.expected} answers in</p>}
      {g.phase === 'final_reveal' && (
        <>
          <p className="jeo-note">The answer: <b>{f.answer}</b></p>
          <div className="jeo-steps">
            {f.steps.map((s, i) => {
              const p = who.get(s.id)
              return (
                <Deal key={s.id} i={i}>
                  <div className={`jeo-step ${s.right ? 'right' : 'wrong'}`}>
                    {p && <AvatarFace avatar={p.avatar} size={52} />}
                    <b>{s.name}</b>
                    <span className="ans">{s.answer ?? 'no answer'}</span>
                    <span className="bet">bet ${s.wager}</span>
                    <strong>{signed(s.delta)}</strong>
                    <span className="tot">{s.total < 0 ? '-' : ''}${Math.abs(s.total)}</span>
                  </div>
                </Deal>
              )
            })}
          </div>
        </>
      )}
    </div>
  )
}
