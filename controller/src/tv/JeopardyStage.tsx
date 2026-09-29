import { useEffect, useState } from 'react'
import type { HostCommand } from '../protocol'
import type { PlayerSummary, ScoreRow, StageInfo } from '../protocol'
import { Fill, GameHeader, Podium, Tutorial } from './Shared'
import { Bubble, C, Panel, Pop, Slam } from './toon'
import type { JeopardyCellTv, JeopardyTv } from './types'

const ANSWER_MS = 30_000, REVEAL_MS = 6_000, PODIUM_MS = 15_000

export function JeopardyStage({ stage, players, scores, clock, cmd }: {
  stage: StageInfo; players: PlayerSummary[]; scores: ScoreRow[]
  clock: { deadline: number | null; frozen: number | null }; cmd: (c: HostCommand) => void
}) {
  if (stage.tutorial) {
    return (
      <div className="stage-pad">
        <GameHeader title="Answer & Question" stage={stage} total={30_000} clock={clock} chips={[['HOW TO PLAY', C.paper]]} />
        <Tutorial cards={stage.tutorial.cards} acked={stage.tutorial.acked} players={players} />
      </div>
    )
  }
  const g = stage.game as unknown as JeopardyTv
  const total = { select: undefined, answer: ANSWER_MS, reveal: REVEAL_MS, podium: PODIUM_MS }[g.phase]
  const status = g.phase === 'answer' ? `${g.submitted}/${g.expected} LOCKED IN` : null
  const remaining = g.board.filter((c) => !c.used).length
  const chips: [string, string][] = [[g.phase === 'podium' ? 'FINAL RESULTS' : `${remaining} CLUES LEFT`, C.paper]]
  return (
    <div className="stage-pad">
      <GameHeader title="Answer & Question" stage={stage} total={total ?? 0} clock={clock} chips={chips} status={status} />
      <Fill>
        {g.phase === 'select' && <Board board={g.board} cmd={cmd} />}
        {g.phase === 'answer' && <Clue g={g} />}
        {g.phase === 'reveal' && <Reveal g={g} />}
        {g.phase === 'podium' && <Podium scores={scores} />}
      </Fill>
    </div>
  )
}

function Board({ board, cmd }: { board: JeopardyCellTv[]; cmd: (c: HostCommand) => void }) {
  const categories = [...new Set(board.map((c) => c.category))]
  const [row, setRow] = useState(0)
  const [col, setCol] = useState(0)
  const values = [...new Set(board.map((c) => c.value))].sort((a, b) => a - b)
  const cellAt = (r: number, c: number) => board.find((x) => x.category === categories[c] && x.value === values[r])
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'ArrowUp') setRow((r) => Math.max(0, r - 1))
      else if (e.key === 'ArrowDown') setRow((r) => Math.min(values.length - 1, r + 1))
      else if (e.key === 'ArrowLeft') setCol((c) => Math.max(0, c - 1))
      else if (e.key === 'ArrowRight') setCol((c) => Math.min(categories.length - 1, c + 1))
      else if (e.key === 'Enter') {
        const cell = cellAt(row, col)
        if (cell && !cell.used) cmd({ t: 'gameAction', action: `pick:${cell.id}` })
      } else return
      e.preventDefault()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [row, col, categories, values, cmd]) // eslint-disable-line react-hooks/exhaustive-deps
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, flex: 1, justifyContent: 'center' }}>
      <div style={{ display: 'grid', gridTemplateColumns: `repeat(${categories.length}, 1fr)`, gap: 10 }}>
        {categories.map((name) => (
          <Panel key={name} className="jeo-category" fill={C.blueberry} tilt={0}>
            <span style={{ color: C.white, fontWeight: 800, fontSize: 20, textAlign: 'center' }}>{name}</span>
          </Panel>
        ))}
      </div>
      {values.map((v, r) => (
        <div key={v} style={{ display: 'grid', gridTemplateColumns: `repeat(${categories.length}, 1fr)`, gap: 10 }}>
          {categories.map((name, c) => {
            const cell = board.find((x) => x.category === name && x.value === v)
            const active = r === row && c === col
            return (
              <Panel key={name} className="jeo-cell" fill={cell?.used ? C.paper2 : active ? C.sun : C.paper}
                tilt={0} style={{ opacity: cell?.used ? 0.35 : 1, outline: active ? `4px solid ${C.ink}` : 'none' }}>
                <span style={{ fontWeight: 800, fontSize: 28 }}>{cell?.used ? '' : `$${v}`}</span>
              </Panel>
            )
          })}
        </div>
      ))}
      <p style={{ textAlign: 'center', color: C.paper, opacity: 0.85, marginTop: 10, fontSize: 26, fontWeight: 700 }}>Arrow keys to move, Enter to pick a clue.</p>
    </div>
  )
}

function Clue({ g }: { g: JeopardyTv }) {
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 40 }}>
      <Pop><span className="display" style={{ fontSize: 26, color: C.tomato }}>{g.category} · ${g.value}</span></Pop>
      <Slam from={1.4} tilt={-2} style={{ width: '100%' }}>
        <Bubble tail="none" className="prompt-bubble">{g.clue}</Bubble>
      </Slam>
    </div>
  )
}

function Reveal({ g }: { g: JeopardyTv }) {
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 32 }}>
      <Panel className="reveal-card" fill={C.sun} tilt={-1}>
        <h2>{g.answer}</h2>
      </Panel>
      {g.correct.length > 0 ? (
        <div className="fooled-row">
          <span className="display" style={{ fontSize: 26, color: C.paper }}>GOT IT</span>
          {g.correct.map((name, i) => (
            <Pop key={name} delay={0.2 + i * 0.1}><span className="name-pill">{name}</span></Pop>
          ))}
        </div>
      ) : (
        <span className="display" style={{ fontSize: 24, color: C.paper }}>Nobody got it</span>
      )}
      {g.deltas.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {g.deltas.map((d) => (
            <Pop key={d.id}><span className="past-chip">{d.name} <strong>+{d.points}</strong></span></Pop>
          ))}
        </div>
      )}
    </div>
  )
}
