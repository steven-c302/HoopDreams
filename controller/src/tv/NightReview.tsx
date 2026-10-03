import './night.css'
import type { NightRecap } from '../protocol'
import { AvatarFace, C, Keycap, Panel, Pop, Slam } from './toon'

const BOARD_ROWS = 8
const AWARD_FILL: Record<string, string> = {
  'NIGHT CHAMP': C.sun, 'MOST WINS': C.lime, 'HOT STREAK': C.tangerine, 'COMEBACK KID': C.sky, 'WOODEN SPOON': C.bubblegum,
}
const MOMENTS_SHOWN = 3

/** The lobby's recap card: the night's leaderboard, the awards, and the best moments. Opens with A, closes with A or Esc. */
export function NightReview({ night, onClose }: { night: NightRecap; onClose(): void }) {
  const shown = night.board.slice(0, BOARD_ROWS)
  const hidden = night.board.length - shown.length
  return (
    <div className="night-overlay" onClick={onClose}>
      <Slam from={1.15} tilt={-1.5}>
        <Panel className="night-panel" fill={C.paper} onClick={(e) => e.stopPropagation()}>
          <div className="night-head">
            <h2>NIGHT IN REVIEW</h2>
            <span className="night-count">{night.games} {night.games === 1 ? 'game' : 'games'} played</span>
            <span className="night-close"><Keycap label="A" /> close</span>
          </div>
          <div className="night-body">
            <ol className="night-board" aria-label="Night leaderboard">
              {shown.map((r, i) => (
                <Pop key={r.id} delay={0.04 * i}>
                  <li className={`night-row ${i === 0 ? 'first' : ''}`}>
                    <span className="night-rank">{i + 1}</span>
                    <AvatarFace avatar={r.avatar} size={46} />
                    <span className="night-name">{r.name}</span>
                    <span className="night-pts">{r.points}<small>{r.wins} {r.wins === 1 ? 'win' : 'wins'}</small></span>
                  </li>
                </Pop>
              ))}
              {hidden > 0 && <li className="night-more">+ {hidden} more</li>}
            </ol>
            <div className="night-awards">
              {night.awards.length === 0 && <p className="night-empty">Play another game and the awards start landing.</p>}
              {night.awards.map((a, i) => (
                <Pop key={a.title} delay={0.12 + 0.08 * i}>
                  <div className="night-award" style={{ background: AWARD_FILL[a.title] ?? C.white }}>
                    <h3>{a.title}</h3>
                    <div className="night-people">
                      {a.players.map((p) => (
                        <div key={p.id} className="night-who"><AvatarFace avatar={p.avatar} size={46} /><span>{p.name}</span></div>
                      ))}
                    </div>
                    <p>{a.note}</p>
                  </div>
                </Pop>
              ))}
            </div>
          </div>
          {night.moments.length > 0 && (
            <ul className="night-moments" aria-label="Moments">
              {night.moments.slice(0, MOMENTS_SHOWN).map((m, i) => <li key={i}><b>{m.game.toUpperCase()}</b>{m.text}</li>)}
            </ul>
          )}
        </Panel>
      </Slam>
    </div>
  )
}
