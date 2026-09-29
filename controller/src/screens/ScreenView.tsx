import { useEffect, useState, type CSSProperties } from 'react'
import type { ActionPayload, Choice, Screen, ScoreRow, TeamTag } from '../protocol'
import { Face } from '../theme/Face'
import { Card } from '../tv/Card'
import { DrinkBet, Led } from '../tv/Casino'
import { Brainy, Shape } from '../tv/toon'
import type { InkOp } from '../ink/types'
import { DrawPad } from './DrawPad'
import { GuessPad } from './GuessPad'
import { BoardScreen } from './BoardScreen'
import { BuzzerScreen } from './BuzzerScreen'
import { HuntScreen } from './HuntScreen'
import { SecretCard } from './SecretCard'
import { SprawlScreen } from './SprawlScreen'
import { TurfScreen } from './TurfScreen'

const buzz = (ms: number | number[]) => { try { navigator.vibrate?.(ms) } catch { /* not supported */ } }

interface Props { screen: Screen; disabled: boolean; meId: string; people: Map<string, ScoreRow>; seconds?: number | null; rejected?: { code: string; n: number } | null; onAction(p: ActionPayload): void; ink?: { online: boolean; send(ops: InkOp[]): void } }

export function ScreenView({ screen, disabled, meId, people, seconds, rejected, onAction, ink }: Props) {
  switch (screen.t) {
    case 'waiting': return <Waiting screen={screen} />
    case 'tutorial': return <Tutorial screen={screen} disabled={disabled} onAction={onAction} />
    case 'text': return <TextEntry screen={screen} disabled={disabled} onAction={onAction} />
    case 'choice': return <ChoiceList screen={screen} disabled={disabled} meId={meId} people={people} onAction={onAction} />
    case 'number': return <NumberEntry screen={screen} disabled={disabled} people={people} onAction={onAction} />
    case 'multi': return <MultiSelect screen={screen} disabled={disabled} meId={meId} people={people} onAction={onAction} />
    case 'scores': return <Scores title={screen.title} rows={screen.rows} meId={meId} />
    case 'cards': return <CardTable screen={screen} disabled={disabled} onAction={onAction} />
    case 'secret': return <SecretCard screen={screen} disabled={disabled} onAction={onAction} />
    case 'draw': return <DrawPad screen={screen} online={ink?.online ?? true} sendInk={ink?.send ?? (() => undefined)} />
    case 'guess': return <GuessPad screen={screen} disabled={disabled} onAction={onAction} />
    case 'hunt': return <HuntScreen screen={screen} disabled={disabled} seconds={seconds ?? null} rejected={rejected ?? null} onAction={onAction} />
    case 'board': return <BoardScreen screen={screen} disabled={disabled} onAction={onAction} />
    case 'buzzer': return <BuzzerScreen screen={screen} disabled={disabled} onAction={onAction} />
    case 'turf': return <TurfScreen screen={screen} disabled={disabled} onAction={onAction} />
    case 'sprawl': return <SprawlScreen screen={screen} disabled={disabled} onAction={onAction} />
  }
}

/** The team a screen belongs to, e.g. for the colour band at the top. */
export const teamOf = (s: Screen): TeamTag | undefined => ('team' in s ? s.team : undefined)

function Voters({ ids, meId, people }: { ids?: string[]; meId: string; people: Map<string, ScoreRow> }) {
  const others = (ids ?? []).filter((id) => id !== meId).map((id) => people.get(id)).filter((p): p is ScoreRow => !!p)
  if (!others.length) return null
  return <span className="voters">{others.slice(0, 4).map((p) => <Face key={p.id} face={p.avatar.face} color={p.avatar.color} size={30} />)}</span>
}

function Waiting({ screen }: { screen: Extract<Screen, { t: 'waiting' }> }) {
  const tone = screen.tone ?? 'neutral'
  useEffect(() => { if (screen.tone === 'win') buzz([40, 60, 40]); else if (screen.tone === 'lose') buzz(220) }, [screen.tone, screen.title])
  return (
    <div className={`waiting tone-${tone}`}>
      <Brainy mood={tone === 'win' ? 'happy' : tone === 'lose' ? 'shocked' : 'smug'} size={150} />
      <h1>{screen.title}</h1>
      {screen.detail && <p>{screen.detail}</p>}
    </div>
  )
}

function Tutorial({ screen, disabled, onAction }: { screen: Extract<Screen, { t: 'tutorial' }>; disabled: boolean; onAction(p: ActionPayload): void }) {
  return (
    <div className="tutorial stack">
      <h1>How to play</h1>
      <ol className="cards">
        {screen.cards.map((c, i) => <li key={c.title}><span className="num">{i + 1}</span><div><b>{c.title}</b><span>{c.body}</span></div></li>)}
      </ol>
      {screen.acknowledged
        ? <p className="muted">Waiting for everyone else…</p>
        : <button className="primary big" disabled={disabled} onClick={() => { buzz(30); onAction({ kind: 'ack' }) }}>Ready!</button>}
    </div>
  )
}

function TextEntry({ screen, disabled, onAction }: { screen: Extract<Screen, { t: 'text' }>; disabled: boolean; onAction(p: ActionPayload): void }) {
  const [draft, setDraft] = useState(screen.value ?? '')
  const locked = screen.value != null && screen.value === draft.trim()
  return (
    <form className="stack" onSubmit={(e) => { e.preventDefault(); if (draft.trim()) { buzz(40); onAction({ kind: screen.kind, text: draft.trim() }) } }}>
      <h1 className="prompt">{screen.prompt}</h1>
      <textarea value={draft} maxLength={screen.maxLen} rows={2} onChange={(e) => setDraft(e.target.value)} placeholder="Type here" disabled={disabled} />
      <div className="row between muted"><span>{screen.hint}</span><span>{draft.length}/{screen.maxLen}</span></div>
      <button className="primary big" disabled={disabled || !draft.trim() || locked}>{locked ? 'Locked in' : screen.value != null ? 'Update answer' : 'Lock it in'}</button>
    </form>
  )
}

const ANS: Record<string, string> = { a: 'var(--ans-a)', b: 'var(--ans-b)', c: 'var(--ans-c)', d: 'var(--ans-d)' }

function ChoiceList({ screen, disabled, meId, people, onAction }: { screen: Extract<Screen, { t: 'choice' }>; disabled: boolean; meId: string; people: Map<string, ScoreRow>; onAction(p: ActionPayload): void }) {
  const pick = (o: Choice) => { buzz(25); onAction({ kind: screen.kind, option: o.id }) }
  if (screen.style === 'shapes') {
    return (
      <div className="stack">
        <h1 className="prompt small">{screen.prompt}</h1>
        <div className="choices shapes">
          {screen.options.map((o) => (
            <button key={o.id} className={`choice shape ${screen.selected === o.id ? 'on' : screen.selected ? 'off' : ''}`} disabled={disabled}
              style={{ '--ans': ANS[o.id] ?? 'var(--paper)' } as CSSProperties} onClick={() => pick(o)}>
              <Shape id={o.id} size={34} />
              <span className="txt">{o.text}</span>
              <Voters ids={screen.votes?.[o.id]} meId={meId} people={people} />
            </button>
          ))}
        </div>
        <p className="muted">{screen.selected ? 'Locked in. Switch any time before the buzzer.' : "Your team's top pick counts."}</p>
      </div>
    )
  }
  if (screen.style === 'sides') {
    return (
      <div className="stack">
        <h1 className="prompt">{screen.prompt}</h1>
        <div className="choices sides">
          {screen.options.map((o, i) => (
            <button key={o.id} className={`choice side ${screen.selected === o.id ? 'on' : screen.selected ? 'off' : ''}`} disabled={disabled}
              style={{ '--ans': i === 0 ? 'var(--bubblegum)' : 'var(--blueberry)' } as CSSProperties} onClick={() => pick(o)}>
              <span className="txt">{o.text}</span>
              <Voters ids={screen.votes?.[o.id]} meId={meId} people={people} />
            </button>
          ))}
        </div>
      </div>
    )
  }
  if (screen.style === 'teams') {
    return (
      <div className="stack">
        <h1 className="prompt">{screen.prompt}</h1>
        <div className="choices teams">
          {screen.options.map((o) => (
            <button key={o.id} className={`choice team ${screen.selected === o.id ? 'on' : ''}`} disabled={disabled}
              style={{ '--ans': o.color ?? 'var(--paper)', color: inkOnHex(o.color) } as CSSProperties} onClick={() => pick(o)}>
              <span className="txt">{o.text}</span>
              {o.detail && <small>{o.detail}</small>}
              <Voters ids={screen.votes?.[o.id]} meId={meId} people={people} />
            </button>
          ))}
        </div>
      </div>
    )
  }
  if (screen.style === 'faces') {
    return (
      <div className="stack">
        <h1 className="prompt">{screen.prompt}</h1>
        <div className="choices faces">
          {screen.options.map((o) => {
            const p = people.get(o.id)
            return (
              <button key={o.id} className={`choice face-pick ${screen.selected === o.id ? 'on' : screen.selected ? 'off' : ''}`} disabled={disabled} onClick={() => pick(o)}>
                {p && <Face face={p.avatar.face} color={p.avatar.color} size={64} />}
                <span className="txt">{o.text}</span>
              </button>
            )
          })}
        </div>
        {screen.selected && <p className="muted">You can change your vote until time runs out.</p>}
      </div>
    )
  }
  return (
    <div className="stack">
      <h1 className="prompt">{screen.prompt}</h1>
      <div className="choices">
        {screen.options.map((o) => (
          <button key={o.id} className={`choice ${screen.selected === o.id ? 'on' : ''}`} disabled={disabled} onClick={() => pick(o)}>
            {o.text}
            {o.detail && <small className="choice-detail">{o.detail}</small>}
          </button>
        ))}
      </div>
      {screen.selected && <p className="muted">You can change your pick until time runs out.</p>}
    </div>
  )
}

function inkOnHex(hex?: string): string {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex ?? '')
  if (!m) return 'var(--ink)'
  const n = parseInt(m[1], 16)
  return (0.2126 * ((n >> 16) & 255) + 0.7152 * ((n >> 8) & 255) + 0.0722 * (n & 255)) / 255 > 0.5 ? 'var(--ink)' : 'var(--white)'
}

const KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '0', 'del']
/** A wager is a whole number of dollars, so its pad swaps the decimal point for a blank. */
const WAGER_KEYS = KEYS.map((k) => (k === '.' ? '' : k))

function NumberEntry({ screen, disabled, people, onAction }: { screen: Extract<Screen, { t: 'number' }>; disabled: boolean; people: Map<string, ScoreRow>; onAction(p: ActionPayload): void }) {
  const [draft, setDraft] = useState(screen.value != null ? String(screen.value) : '')
  const value = Number(draft)
  const valid = draft !== '' && draft !== '.' && Number.isFinite(value)
  const sent = screen.value != null && valid && value === screen.value
  const press = (k: string) => {
    buzz(12)
    setDraft((d) => (k === 'del' ? d.slice(0, -1) : k === '.' && d.includes('.') ? d : (d + k).replace(/^0(?=\d)/, '').slice(0, 12)))
  }
  const shown = draft ? Number(draft.replace(/\.$/, '')).toLocaleString(undefined, { maximumFractionDigits: 4 }) + (draft.endsWith('.') ? '.' : '') : '0'
  return (
    <div className="stack">
      <h1 className="prompt small">{screen.prompt}</h1>
      <div className="number-display"><b>{shown}</b>{screen.unit && <span>{screen.unit}</span>}</div>
      <div className="numpad">
        {(screen.kind === 'wager' ? WAGER_KEYS : KEYS).map((k) => k === '' ? <span key="gap" /> : (
          <button key={k} type="button" className="numkey" disabled={disabled} aria-label={k === 'del' ? 'Delete' : k} onClick={() => press(k)}>
            {k === 'del' ? <svg width="34" height="26" viewBox="0 0 34 26" aria-hidden="true"><path d="M11 2 H31 V24 H11 L2 13 Z" fill="none" stroke="currentColor" strokeWidth="3.5" strokeLinejoin="round" /><path d="M16 8 L25 18 M25 8 L16 18" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" /></svg> : k}
          </button>
        ))}
      </div>
      <button className="primary big" disabled={disabled || !valid || sent} onClick={() => { buzz(40); onAction({ kind: screen.kind, value }) }}>
        {screen.kind === 'wager'
          ? (sent ? 'Wager locked' : screen.value != null ? 'Change wager' : 'Lock in wager')
          : (sent ? 'Guess sent' : screen.value != null ? 'Update guess' : 'Send guess')}
      </button>
      {(screen.guesses?.length ?? 0) > 0 && (
        <div className="team-guesses">
          {screen.guesses!.map((g) => { const p = people.get(g.id); return p ? <span key={g.id}><Face face={p.avatar.face} color={p.avatar.color} size={26} />{g.value.toLocaleString()}</span> : null })}
        </div>
      )}
    </div>
  )
}

function MultiSelect({ screen, disabled, meId, people, onAction }: { screen: Extract<Screen, { t: 'multi' }>; disabled: boolean; meId: string; people: Map<string, ScoreRow>; onAction(p: ActionPayload): void }) {
  const [picks, setPicks] = useState<string[]>(screen.selected)
  const send = (next: string[], lock: boolean) => onAction({ kind: screen.kind, picks: next, lock })
  const toggle = (id: string) => {
    buzz(18)
    const next = picks.includes(id) ? picks.filter((x) => x !== id) : [...picks, id]
    setPicks(next)
    send(next, screen.locked)
  }
  return (
    <div className="stack">
      <h1 className="prompt small">{screen.prompt}</h1>
      <p className="muted">Tap every answer that fits.</p>
      <div className="choices multi">
        {screen.options.map((o) => {
          const out = screen.eliminated?.includes(o.id)
          const on = picks.includes(o.id)
          return (
            <button key={o.id} className={`choice multi ${on ? 'on' : ''} ${out ? 'out' : ''}`} disabled={disabled || out} onClick={() => toggle(o.id)}>
              <span className="box">{on && <svg width="22" height="22" viewBox="0 0 40 40" aria-hidden="true"><path d="M7 21 L16 30 L33 9" stroke="currentColor" strokeWidth="7" fill="none" strokeLinecap="round" strokeLinejoin="round" /></svg>}</span>
              <span className="txt">{o.text}</span>
              {out ? <small>Not this one</small> : <Voters ids={screen.votes?.[o.id]} meId={meId} people={people} />}
            </button>
          )
        })}
      </div>
      <button className="primary big" disabled={disabled || screen.locked} onClick={() => { buzz([30, 40, 30]); send(picks, true) }}>{screen.locked ? 'Locked in' : 'Lock it in'}</button>
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
            <Face face={r.avatar.face} color={r.avatar.color} size={34} />
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
