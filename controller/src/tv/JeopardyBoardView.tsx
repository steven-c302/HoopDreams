import { useEffect, useState, type CSSProperties } from 'react'
import type { HostCommand, PlayerSummary } from '../protocol'
import { Burst, C } from './toon'
import type { JeopardyTv } from './types'

const ROWS = 5

/** The wall of squares. During the intro the categories drop in one by one; while picking, the TV keyboard still works. */
export function JeopardyBoardView({ g, players, cmd }: { g: JeopardyTv; players: PlayerSummary[]; cmd(c: HostCommand): void }) {
  const [at, setAt] = useState({ col: 0, row: 0 })
  const cols = g.categories.length
  const picking = g.phase === 'pick'
  const cell = (col: number, row: number) => g.cells.find((c) => c.col === col && c.row === row)
  const holder = players.find((p) => p.id === g.controller)?.name ?? 'Someone'
  useEffect(() => {
    if (!picking) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'ArrowUp') setAt((a) => ({ ...a, row: Math.max(0, a.row - 1) }))
      else if (e.key === 'ArrowDown') setAt((a) => ({ ...a, row: Math.min(ROWS - 1, a.row + 1) }))
      else if (e.key === 'ArrowLeft') setAt((a) => ({ ...a, col: Math.max(0, a.col - 1) }))
      else if (e.key === 'ArrowRight') setAt((a) => ({ ...a, col: Math.min(cols - 1, a.col + 1) }))
      else if (e.key === 'Enter') {
        const c = cell(at.col, at.row)
        if (c && !c.used) cmd({ t: 'gameAction', action: `pick:${c.id}` })
      } else return
      e.preventDefault()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })
  return (
    <div className="jeo-boardwrap">
      <div className="jeo-board" data-phase={g.phase} style={{ '--cols': cols } as CSSProperties}>
        {g.categories.map((name, col) => (
          <div key={name} className="jeo-head" style={{ '--i': col } as CSSProperties}><span>{name}</span></div>
        ))}
        {Array.from({ length: ROWS }, (_, row) => g.categories.map((_, col) => {
          const c = cell(col, row)
          if (!c) return <div key={`${col}-${row}`} />
          const active = picking && at.col === col && at.row === row
          return (
            <div key={c.id} className={`jeo-cell ${c.used ? 'used' : ''} ${active ? 'cursor' : ''}`} style={{ '--i': col + row } as CSSProperties}>
              <span>{c.used ? '' : `$${c.value}`}</span>
            </div>
          )
        }))}
      </div>
      {picking && (
        <p className="jeo-note"><b>{holder}</b> has the board. Pick on your phone. <small>Arrow keys and Enter work here too.</small></p>
      )}
      {g.phase === 'intro' && <p className="jeo-note">{g.round === 2 ? 'Double Jeopardy! Every clue is worth double.' : 'Here are your categories.'}</p>}
      {g.phase === 'break' && (
        <div className="jeo-break">
          <Burst text="DOUBLE JEOPARDY!" sub="Every clue is worth double" fill={C.sun} ink={C.ink} width={1000} height={420} size={96} tilt={-3} />
        </div>
      )}
    </div>
  )
}
