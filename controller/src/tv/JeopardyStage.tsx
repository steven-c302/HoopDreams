import type { HostCommand, PlayerSummary, ScoreRow, StageInfo } from '../protocol'
import { Fill, GameHeader, Podium, Tutorial } from './Shared'
import { useTimerScale } from './timerScale'
import { AvatarFace, C } from './toon'
import { JeopardyBoardView } from './JeopardyBoardView'
import { JeopardyClueView } from './JeopardyClueView'
import { JeopardyFinalView } from './JeopardyFinalView'
import type { JeopardyTv } from './types'
import './jeopardy.css'

type Clock = { deadline: number | null; frozen: number | null }

/** How long each phase lasts, for the clock face. Decision phases stretch with the lobby's timer setting. */
const PHASE_MS: Record<string, number> = {
  intro: 8_000, pick: 20_000, wager: 20_000, clue: 5_000, buzz: 10_000, answer: 15_000, reveal: 5_000, break: 8_000,
  final_category: 6_000, final_wager: 30_000, final_answer: 30_000, final_reveal: 5_000, podium: 15_000,
}
const DECISIONS = new Set(['pick', 'wager', 'buzz', 'answer', 'final_wager', 'final_answer'])

export function JeopardyStage({ stage, players, scores, clock, cmd }: {
  stage: StageInfo; players: PlayerSummary[]; scores: ScoreRow[]; clock: Clock; cmd: (c: HostCommand) => void
}) {
  const scale = useTimerScale()
  if (stage.tutorial) {
    return (
      <div className="stage-pad">
        <GameHeader title="Answer & Question" stage={stage} total={30_000} clock={clock} chips={[['HOW TO PLAY', C.paper]]} />
        <Tutorial cards={stage.tutorial.cards} acked={stage.tutorial.acked} players={players} />
      </div>
    )
  }
  const g = stage.game as unknown as JeopardyTv
  const chip = g.round === 3 ? 'FINAL JEOPARDY' : g.boards === 2 ? (g.round === 1 ? 'JEOPARDY!' : 'DOUBLE JEOPARDY!') : 'THE BOARD'
  const base = g.dailyDouble && g.phase === 'answer' ? 30_000 : PHASE_MS[g.phase] ?? 5_000
  const total = base * (DECISIONS.has(g.phase) ? scale : 1)
  const status = g.phase === 'final_wager' && g.final ? `${g.final.wagers}/${g.final.expected} BETS IN`
    : g.phase === 'final_answer' && g.final ? `${g.final.wagers}/${g.final.expected} ANSWERS IN` : null
  const view =
    g.phase === 'podium' ? <Podium scores={scores} />
      : g.phase.startsWith('final') ? <JeopardyFinalView g={g} players={players} />
        : ['wager', 'clue', 'buzz', 'answer', 'reveal'].includes(g.phase) ? <JeopardyClueView g={g} players={players} />
          : <JeopardyBoardView g={g} players={players} cmd={cmd} />
  return (
    <div className="stage-pad jeo">
      <GameHeader title="Answer & Question" stage={stage} total={total} clock={clock} chips={[[chip, C.paper]]} status={status} />
      <Fill>{view}</Fill>
      {g.phase !== 'podium' && <Scoreboard g={g} players={players} scores={scores} />}
    </div>
  )
}

/** Everyone's score along the bottom: the board holder is marked, the player with the floor lights up. */
function Scoreboard({ g, players, scores }: { g: JeopardyTv; players: PlayerSummary[]; scores: ScoreRow[] }) {
  const score = new Map(scores.map((s) => [s.id, s.score]))
  const crew = players.filter((p) => p.role === 'PLAYER')
  return (
    <div className={`jeo-strip ${crew.length > 8 ? 'many' : ''}`}>
      {crew.map((p) => {
        const n = score.get(p.id) ?? 0
        const cls = [g.controller === p.id && 'holds', g.floor === p.id && 'floor', g.locked.includes(p.id) && 'locked', g.tried.includes(p.id) && 'tried'].filter(Boolean).join(' ')
        return (
          <div key={p.id} className={`jeo-chip ${cls}`}>
            <AvatarFace avatar={p.avatar} size={crew.length > 8 ? 34 : 46} />
            <b>{p.name}</b>
            <span className={`jeo-score ${n < 0 ? 'neg' : ''}`}>{n < 0 ? `-$${-n}` : `$${n}`}</span>
          </div>
        )
      })}
    </div>
  )
}
