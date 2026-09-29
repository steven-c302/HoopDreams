import { useState, type CSSProperties } from 'react'
import type { ActionPayload, Screen } from '../protocol'
import './jeopardy-phone.css'

type BoardScreenT = Extract<Screen, { t: 'board' }>

const buzz = (ms: number) => { try { navigator.vibrate?.(ms) } catch { /* not supported */ } }

/** The clue board on a phone. Tap a square, then confirm, so nobody picks by accident. */
export function BoardScreen({ screen, disabled, onAction }: { screen: BoardScreenT; disabled: boolean; onAction(p: ActionPayload): void }) {
  const [selected, setSelected] = useState<string | null>(null)
  const rows = [...new Set(screen.cells.map((c) => c.row))].sort((a, b) => a - b)
  const cellAt = (col: number, row: number) => screen.cells.find((c) => c.col === col && c.row === row)
  const cell = screen.cells.find((c) => c.id === selected)
  return (
    <div className="stack jb">
      <h1 className="prompt small">{screen.prompt}</h1>
      <div className="jb-board" style={{ '--cols': screen.categories.length } as CSSProperties}>
        {screen.categories.map((name, col) => <div key={`h${col}`} className="jb-head"><span>{name}</span></div>)}
        {rows.flatMap((row) => screen.categories.map((_, col) => {
          const c = cellAt(col, row)
          if (!c) return <div key={`${col}-${row}`} />
          const on = selected === c.id
          return (
            <button
              key={c.id}
              type="button"
              className={`jb-cell ${c.used ? 'used' : ''} ${on ? 'on' : ''}`}
              disabled={disabled || !screen.canPick || c.used}
              aria-pressed={on}
              aria-label={c.used ? 'Played' : `${screen.categories[col]} for ${c.value}`}
              onClick={() => { buzz(12); setSelected(on ? null : c.id) }}
            >
              {c.used ? '' : `$${c.value}`}
            </button>
          )
        }))}
      </div>
      {screen.canPick && cell
        ? (
          <button className="primary big" disabled={disabled} onClick={() => { buzz(40); onAction({ kind: 'pick', cell: cell.id }) }}>
            Pick {screen.categories[cell.col]} for ${cell.value}{screen.pickFor ? ` (for ${screen.pickFor})` : ''}
          </button>
        )
        : <p className="muted jb-note">{screen.canPick ? 'Tap a square to choose it' : screen.note ?? ''}</p>}
    </div>
  )
}
