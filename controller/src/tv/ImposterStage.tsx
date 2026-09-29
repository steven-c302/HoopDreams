import type { PlayerSummary, ScoreRow, StageInfo } from '../protocol'
import { useTimerScale } from './timerScale'
import { Fill, GameHeader, Podium, ScoreBoard, Tutorial } from './Shared'
import { AvatarFace, Bubble, C, Deal, Panel, Pop, Slam, Stamp } from './toon'
import type { ImposterTv } from './types'
import './imposter.css'

const ROLE_MS = 15_000, CLUE_MS = 45_000, DISCUSS_MS = 60_000, VOTE_MS = 30_000, RESULT_MS = 6_000, GUESS_MS = 20_000, SCORES_MS = 8_000, PODIUM_MS = 15_000

type Clock = { deadline: number | null; frozen: number | null }

export function ImposterStage({ stage, players, scores, clock }: { stage: StageInfo; players: PlayerSummary[]; scores: ScoreRow[]; clock: Clock }) {
  const scale = useTimerScale()
  if (stage.tutorial) {
    return (
      <div className="stage-pad">
        <GameHeader title="Imposter" stage={stage} total={30_000} clock={clock} chips={[['HOW TO PLAY', C.paper]]} />
        <Tutorial cards={stage.tutorial.cards} acked={stage.tutorial.acked} players={players} />
      </div>
    )
  }
  const g = stage.game as unknown as ImposterTv
  const who = new Map(players.map((p) => [p.id, p]))
  const chips: [string, string][] = [[g.phase === 'podium' ? 'FINAL RESULTS' : `ROUND ${g.round} OF ${g.totalRounds}`, C.paper]]
  if (g.finalRound && g.phase !== 'scores' && g.phase !== 'podium') chips.push(['FINAL ROUND: DOUBLE POINTS', C.sun])
  const total = { role: ROLE_MS, clue: CLUE_MS * scale, discuss: DISCUSS_MS, vote: VOTE_MS * scale, result: RESULT_MS, guess: GUESS_MS * scale, scores: SCORES_MS, podium: PODIUM_MS }[g.phase]
  const statuses: Record<string, string> = {
    role: `${g.submitted}/${g.expected} LOOKED`,
    clue: `${g.submitted}/${g.expected} CLUES IN`,
    vote: `${g.submitted}/${g.expected} VOTED`,
  }
  return (
    <div className="stage-pad">
      <GameHeader title="Imposter" stage={stage} total={total} clock={clock} chips={chips} status={statuses[g.phase] ?? null} />
      <Fill>
        {g.phase === 'role' && <Waiting g={g} line={`${g.imposterCount === 1 ? 'One of you is the imposter.' : `${g.imposterCount} of you are imposters.`} Hold your card on your phone.`} />}
        {g.phase === 'clue' && <Waiting g={g} line="Type ONE word on your phone." />}
        {(g.phase === 'discuss' || g.phase === 'vote') && <Wall g={g} who={who} note={g.phase === 'discuss' ? 'Who is faking it? Talk it out.' : 'Vote on your phone.'} />}
        {g.phase === 'result' && <Result g={g} who={who} />}
        {g.phase === 'guess' && <Guess g={g} who={who} />}
        {g.phase === 'scores' && <Recap g={g} scores={scores} />}
        {g.phase === 'podium' && <Podium scores={scores} />}
      </Fill>
    </div>
  )
}

function Dots({ done, total }: { done: number; total: number }) {
  return (
    <div className="chips">
      {Array.from({ length: total }, (_, i) => i < done
        ? <Pop key={`y${i}`}><div className="chip-dot" style={{ background: C.sun }} /></Pop>
        : <div key={`n${i}`} className="chip-dot" style={{ background: C.paper, opacity: 0.6 }} />)}
    </div>
  )
}

/** The category on a big card while people look at their phones or type their clue. */
function Waiting({ g, line }: { g: ImposterTv; line: string }) {
  return (
    <div className="imp-center">
      <Slam from={1.4} tilt={-3}><Bubble tail="none" className="prompt-bubble">{g.category}</Bubble></Slam>
      <p className="imp-note">{line}</p>
      <Dots done={g.submitted} total={g.expected} />
    </div>
  )
}

/** The lineup: one placard per player with their face, name and clue, and the votes and stamps once they are known. */
function Wall({ g, who, note }: { g: ImposterTv; who: Map<string, PlayerSummary>; note?: string }) {
  const imposters = new Set(g.imposters)
  const accused = new Set(g.accused)
  const votes = new Map<string, number>()
  g.votes.forEach((v) => votes.set(v.suspect, (votes.get(v.suspect) ?? 0) + 1))
  return (
    <div className="imp-col">
      <Bubble tail="none" className="prompt-bubble small">{g.category}</Bubble>
      <div className="imp-wall">
        {g.clues.map((c, i) => {
          const p = who.get(c.id)
          const n = votes.get(c.id) ?? 0
          return (
            <Deal key={c.id} i={i}>
              <Panel className={`imp-placard ${accused.has(c.id) ? 'accused' : ''}`} tilt={i % 2 ? 1 : -1}>
                {p && <AvatarFace avatar={p.avatar} size={84} />}
                <b className="imp-name">{c.name}</b>
                <span className="imp-clue">{c.text ?? '...'}</span>
                {n > 0 && <span className="imp-votes">{n} {n === 1 ? 'VOTE' : 'VOTES'}</span>}
                {imposters.has(c.id) && <Stamp text="IMPOSTER" color={C.bubblegum} size={40} />}
              </Panel>
            </Deal>
          )
        })}
      </div>
      {note && <p className="imp-note">{note}</p>}
    </div>
  )
}

function Result({ g, who }: { g: ImposterTv; who: Map<string, PlayerSummary> }) {
  const caught = g.imposters.some((id) => g.accused.includes(id))
  const headline = caught ? 'CAUGHT!' : g.accused.length ? 'WRONG SUSPECT!' : 'NOBODY GOT ACCUSED'
  return (
    <div className="imp-col">
      <Slam from={1.5} tilt={-2}><Bubble tail="none" className="prompt-bubble small">{headline}</Bubble></Slam>
      <Wall g={g} who={who} />
      {g.word && <p className="imp-note">The word was <b>{g.word}</b></p>}
      {g.drinks.length > 0 && <ul className="imp-drinks">{g.drinks.map((d) => <li key={d.id}><b>{d.name}</b> {d.text}</li>)}</ul>}
    </div>
  )
}

function Guess({ g, who }: { g: ImposterTv; who: Map<string, PlayerSummary> }) {
  const names = g.imposters.filter((id) => g.accused.includes(id)).map((id) => who.get(id)?.name ?? '?').join(' & ')
  return (
    <div className="imp-center">
      <Slam from={1.4} tilt={-3}><Bubble tail="none" className="prompt-bubble">{names} {g.submitted ? 'guessed' : 'is guessing the word'}</Bubble></Slam>
      <p className="imp-note">Category: {g.category}. One guess to steal the round.</p>
    </div>
  )
}

function Recap({ g, scores }: { g: ImposterTv; scores: ScoreRow[] }) {
  return (
    <div className="imp-col">
      {g.word && <p className="imp-note">The word was <b>{g.word}</b></p>}
      {g.guesses.map((x) => <p key={x.id} className="imp-note"><b>{x.name}</b> guessed “{x.text}” {x.right ? 'and stole it.' : 'and missed.'}</p>)}
      <ScoreBoard scores={scores} deltas={Object.fromEntries(g.deltas.map((d) => [d.id, d.points]))} />
    </div>
  )
}
