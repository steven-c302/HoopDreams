import { useState } from 'react'
import type { ActionPayload, Screen, ScoreRow } from '../protocol'
import { Card } from '../tv/Card'
import { DrinkBet, Led } from '../tv/Casino'

const buzz = (ms: number | number[]) => { try { navigator.vibrate?.(ms) } catch { /* not supported */ } }

interface Props { screen: Screen; disabled: boolean; meId: string; onAction(p: ActionPayload): void }

export function ScreenView({ screen, disabled, meId, onAction }: Props) {
  switch (screen.t) {
    case 'waiting': return <Waiting title={screen.title} detail={screen.detail} />
    case 'tutorial': return <Tutorial screen={screen} disabled={disabled} onAction={onAction} />
    case 'text': return <TextEntry screen={screen} disabled={disabled} onAction={onAction} />
    case 'choice': return <ChoiceList screen={screen} disabled={disabled} onAction={onAction} />
    case 'scores': return <Scores title={screen.title} rows={screen.rows} meId={meId} />
    case 'cards': return <CardTable screen={screen} disabled={disabled} onAction={onAction} />
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
        : <button className="primary big" disabled={disabled} onClick={() => { buzz(30); onAction({ kind: 'ack' }) }}>Got it!</button>}
    </div>
  )
}

function TextEntry({ screen, disabled, onAction }: { screen: Extract<Screen, { t: 'text' }>; disabled: boolean; onAction(p: ActionPayload): void }) {
  const [draft, setDraft] = useState(screen.value ?? '')
  const locked = screen.value != null && screen.value === draft.trim()
  return (
    <form className="stack" onSubmit={(e) => { e.preventDefault(); if (draft.trim()) { buzz(40); onAction({ kind: screen.kind, text: draft.trim() }) } }}>
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
            onClick={() => { buzz(25); onAction({ kind: screen.kind, option: o.id }) }}>{o.text}</button>
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


/** Drunk Blackjack on the phone: your hand, the dealer's up-card, and big keypad buttons. */
function CardTable({ screen, disabled, onAction }: { screen: Extract<Screen, { t: 'cards' }>; disabled: boolean; onAction(p: ActionPayload): void }) {
  const act = (id: string) => { buzz(id === 'double' ? [30, 40, 30] : 35); onAction({ kind: screen.kind, option: id }) }
  const betting = screen.kind === 'bet'
  return (
    <div className="stack cardtable">
      <div className="row between">
        <h1>{screen.title}</h1>
        {screen.stack != null && <Led value={screen.stack} tone={screen.stack < 0 ? 'red' : 'green'} size={17} digits={4} />}
      </div>
      {screen.dealer.length > 0 && (
        <div className="dealer-strip">
          <span className="label">HOUSE</span>
          <div className="mini-hand">{screen.dealer.map((c, i) => <Card key={i} card={c} width={52} animate={false} tilt={i % 2 ? 2 : -2} />)}</div>
        </div>
      )}
      {screen.hand.length > 0 && (
        <div className="my-hand">
          <div className="hand-row">{screen.hand.map((c, i) => <Card key={i} card={c} width={96} tilt={(i - (screen.hand.length - 1) / 2) * 6} from={{ x: 60 - i * 40, y: -260 }} delay={i < 2 ? i * 0.18 : 0} />)}</div>
          {screen.total != null && <Led value={screen.total} tone={screen.total > 21 ? 'red' : screen.total === 21 ? 'gold' : 'green'} size={34} />}
        </div>
      )}
      {screen.note && <p className={`note tone-${screen.tone ?? 'neutral'}`}>{screen.note}</p>}
      {screen.actions.length > 0 && (
        <div className={betting ? 'bet-chips' : 'keypad'}>
          {screen.actions.map((a) => betting ? (
            <button key={a.id} className="casino-chip" disabled={disabled} onClick={() => act(a.id)}>
              <DrinkBet sips={Number(a.id.slice(1)) || 1} size={44} />
              <span className="bet-label">{a.text}</span>
            </button>
          ) : (
            <button key={a.id} className={`key key-${a.id}`} disabled={disabled} onClick={() => act(a.id)}>{a.text}</button>
          ))}
        </div>
      )}
    </div>
  )
}
