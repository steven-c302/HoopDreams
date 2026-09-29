import type { PlayerSummary } from '../protocol'
import { Burst, C, Panel, Pop, Slam } from './toon'
import type { JeopardyTv } from './types'

const signed = (n: number) => `${n > 0 ? '+' : ''}${n}`

/** From the Daily Double wager to the reveal: the clue, who has the floor, and the verdict. */
export function JeopardyClueView({ g, players }: { g: JeopardyTv; players: PlayerSummary[] }) {
  const name = (id?: string) => players.find((p) => p.id === id)?.name ?? 'Someone'
  return (
    <div className="jeo-cluewrap">
      <div className="jeo-cat">
        <Pop><span>{g.category}</span></Pop>
        <b>${g.value}</b>
        {g.dailyDouble && <em>DAILY DOUBLE{g.wager != null ? ` · WAGER $${g.wager}` : ''}</em>}
      </div>
      {g.phase === 'wager' ? (
        <div className="jeo-dd">
          <Burst text="DAILY DOUBLE" fill={C.sun} ink={C.ink} width={900} height={380} size={104} tilt={-4} />
          <p className="jeo-note"><b>{name(g.controller)}</b> is placing a wager</p>
        </div>
      ) : (
        <>
          <Slam from={1.25} tilt={-1} style={{ width: '100%' }}>
            <Panel className="jeo-clue" fill={C.paper} tilt={0}>{g.clue}</Panel>
          </Slam>
          {g.phase === 'clue' && <p className="jeo-note">{g.dailyDouble ? `${name(g.controller)}, this one is all yours` : 'Get ready to ring in...'}</p>}
          {g.phase === 'buzz' && (
            <div className="jeo-buzzrow">
              <span className="jeo-buzz">BUZZ IN!</span>
              {g.tried.length > 0 && <p className="jeo-note">Missed: {g.tried.map(name).join(', ')}</p>}
            </div>
          )}
          {g.phase === 'answer' && <p className="jeo-note"><b>{name(g.floor)}</b> {g.dailyDouble ? 'is answering' : 'rang in first'}</p>}
          {g.phase === 'reveal' && (
            <div className="jeo-revealrow">
              <Panel className="jeo-answer" fill={g.right ? C.lime : C.sun} tilt={-1}>{g.answer}</Panel>
              <p className="jeo-note">{g.right ? 'Got it!' : 'Nobody got it'}</p>
              <div className="jeo-deltas">
                {g.deltas.map((d) => (
                  <Pop key={d.id}><span className={`jeo-delta ${d.points < 0 ? 'neg' : ''}`}>{d.name} {signed(d.points)}</span></Pop>
                ))}
              </div>
              {g.drinks.length > 0 && <ul className="jeo-drinks">{g.drinks.map((d) => <li key={d.id}><b>{d.name}</b> {d.text}</li>)}</ul>}
            </div>
          )}
        </>
      )}
    </div>
  )
}
