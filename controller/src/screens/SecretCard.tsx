import { useEffect, useState, type FormEvent } from 'react'
import type { ActionPayload, Screen } from '../protocol'
import './secret.css'

type SecretScreen = Extract<Screen, { t: 'secret' }>

const buzz = (ms: number) => { try { navigator.vibrate?.(ms) } catch { /* not supported */ } }

/**
 * A face-down card. The word is not in the page at all until the card is held, and it drops out again on release,
 * when the tab is hidden or when the window loses focus. Face-down, the crew and imposter cards are the same markup,
 * so a neighbour cannot tell them apart. With an [input] it is the clue phase: a small peek chip above the field.
 */
export function SecretCard({ screen, disabled, onAction }: { screen: SecretScreen; disabled: boolean; onAction(p: ActionPayload): void }) {
  const [peek, setPeek] = useState(false)
  const input = screen.input
  const [draft, setDraft] = useState(input?.value ?? '')

  useEffect(() => {
    const hide = () => setPeek(false)
    const onVisibility = () => { if (document.hidden) hide() }
    window.addEventListener('blur', hide)
    document.addEventListener('visibilitychange', onVisibility)
    return () => {
      window.removeEventListener('blur', hide)
      document.removeEventListener('visibilitychange', onVisibility)
    }
  }, [])

  const hide = () => setPeek(false)
  const locked = input?.value != null && input.value === draft.trim()
  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (input && draft.trim()) { buzz(40); onAction({ kind: input.kind, text: draft.trim() }) }
  }

  return (
    <div className="stack secret">
      <h1 className="prompt small">{screen.title}</h1>
      <button
        type="button"
        className={`secret-card ${peek ? `up ${screen.role}` : 'down'} ${input ? 'chip' : ''}`}
        aria-label={peek ? `Your card: ${screen.face}` : 'Your secret card. Hold to peek.'}
        onPointerDown={(e) => { e.preventDefault(); setPeek(true) }}
        onPointerUp={hide}
        onPointerCancel={hide}
        onPointerLeave={hide}
        onKeyDown={(e) => { if (e.key === ' ' || e.key === 'Enter') { e.preventDefault(); setPeek(true) } }}
        onKeyUp={hide}
        onBlur={hide}
        onContextMenu={(e) => e.preventDefault()}
      >
        {peek ? <><small>{screen.category}</small><b>{screen.face}</b></> : <span>HOLD TO PEEK</span>}
      </button>
      {screen.note && <p className="muted">{screen.note}</p>}
      {screen.kind && (screen.acknowledged
        ? <p className="muted">Waiting for everyone else…</p>
        : <button type="button" className="primary big" disabled={disabled} onClick={() => { buzz(30); onAction({ kind: screen.kind! }) }}>Got it</button>)}
      {input && (
        <form className="stack" onSubmit={submit}>
          <p className="prompt small">{input.prompt}</p>
          <input
            value={draft}
            maxLength={input.maxLen}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="One word"
            aria-label={input.prompt}
            autoComplete="off"
            autoCapitalize="none"
            disabled={disabled}
          />
          <div className="row between muted"><span>{input.hint}</span><span>{draft.length}/{input.maxLen}</span></div>
          <button className="primary big" disabled={disabled || !draft.trim() || locked}>
            {locked ? 'Locked in' : input.value != null ? 'Update clue' : 'Lock it in'}
          </button>
        </form>
      )}
    </div>
  )
}
