import { useState } from 'react'
import type { ActionPayload, Screen, ScoreRow } from '../protocol'

interface Props { screen: Screen; disabled: boolean; meId: string; onAction(p: ActionPayload): void }

export function ScreenView({ screen, disabled, meId, onAction }: Props) {
  switch (screen.t) {
    case 'waiting': return <Waiting title={screen.title} detail={screen.detail} />
    case 'tutorial': return <Tutorial screen={screen} disabled={disabled} onAction={onAction} />
    case 'text': return <TextEntry screen={screen} disabled={disabled} onAction={onAction} />
    case 'choice': return <ChoiceList screen={screen} disabled={disabled} onAction={onAction} />
    case 'scores': return <Scores title={screen.title} rows={screen.rows} meId={meId} />
  }
}

function Waiting({ title, detail }: { title: string; detail?: string }) {
  return <div className="waiting"><div className="pulse" /><h1>{title}</h1>{detail && <p>{detail}</p>}</div>
}

function Tutorial({ screen, disabled, onAction }: { screen: Extract<Screen, { t: 'tutorial' }>; disabled: boolean; onAction(p: ActionPayload): void }) {
  return (
    <div className="tutorial stack">
      <h1>How to play</h1>
      <ol className="cards">
        {screen.cards.map((c) => <li key={c.title}><b>{c.title}</b><span>{c.body}</span></li>)}
      </ol>
      {screen.acknowledged
        ? <p className="muted">Waiting for everyone else…</p>
        : <button className="primary big" disabled={disabled} onClick={() => onAction({ kind: 'ack' })}>Got it!</button>}
    </div>
  )
}

function TextEntry({ screen, disabled, onAction }: { screen: Extract<Screen, { t: 'text' }>; disabled: boolean; onAction(p: ActionPayload): void }) {
  const [draft, setDraft] = useState(screen.value ?? '')
  const locked = screen.value != null && screen.value === draft.trim()
  return (
    <form className="stack" onSubmit={(e) => { e.preventDefault(); if (draft.trim()) onAction({ kind: screen.kind, text: draft.trim() }) }}>
      <h1 className="prompt">{screen.prompt}</h1>
      <textarea value={draft} maxLength={screen.maxLen} rows={2} onChange={(e) => setDraft(e.target.value)} placeholder="Type your answer" disabled={disabled} />
      <div className="row between muted"><span>{screen.hint}</span><span>{draft.length}/{screen.maxLen}</span></div>
      <button className="primary big" disabled={disabled || !draft.trim() || locked}>{locked ? 'Locked in ✓' : screen.value != null ? 'Update answer' : 'Lock it in'}</button>
    </form>
  )
}

function ChoiceList({ screen, disabled, onAction }: { screen: Extract<Screen, { t: 'choice' }>; disabled: boolean; onAction(p: ActionPayload): void }) {
  return (
    <div className="stack">
      <h1 className="prompt">{screen.prompt}</h1>
      <div className="choices">
        {screen.options.map((o) => (
          <button key={o.id} className={`choice ${screen.selected === o.id ? 'on' : ''}`} disabled={disabled}
            onClick={() => onAction({ kind: screen.kind, option: o.id })}>{o.text}</button>
        ))}
      </div>
      {screen.selected && <p className="muted">You can change your pick until time runs out.</p>}
    </div>
  )
}

function Scores({ title, rows, meId }: { title: string; rows: ScoreRow[]; meId: string }) {
  return (
    <div className="stack">
      <h1>{title}</h1>
      <ol className="scores">
        {rows.map((r, i) => (
          <li key={r.id} className={r.id === meId ? 'me' : ''}>
            <span className="rank">{i + 1}</span>
            <span className="dot-avatar" style={{ background: r.avatar.color }}>{r.avatar.emoji}</span>
            <span className="name">{r.name}</span>
            <span className="pts">{r.score.toLocaleString()}</span>
          </li>
        ))}
      </ol>
    </div>
  )
}
