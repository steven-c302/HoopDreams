import { useEffect, useRef, useState, type FormEvent } from 'react'
import type { ActionPayload, Screen } from '../protocol'
import { blankCells } from '../ink/blanks'
import './draw.css'

type GuessScreen = Extract<Screen, { t: 'guess' }>

const buzz = (ms: number | number[]) => { try { navigator.vibrate?.(ms) } catch { /* not supported */ } }

/** A guesser: the blanks, a field that clears and stays focused after every guess, and quiet feedback on the last miss. */
export function GuessPad({ screen, disabled, onAction }: { screen: GuessScreen; disabled: boolean; onAction(p: ActionPayload): void }) {
  const [text, setText] = useState('')
  const field = useRef<HTMLInputElement>(null)
  useEffect(() => { if (screen.solved) buzz([40, 60, 40]) }, [screen.solved])

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const t = text.trim()
    if (!t || disabled) return
    buzz(25)
    onAction({ kind: screen.kind, text: t })
    setText('')
    field.current?.focus()
  }

  const cells = blankCells(screen.blanks)
  return (
    <div className="guess-pad stack">
      <h1 className="prompt small">{screen.drawer} is drawing</h1>
      <div className="blank-cells" aria-label={`The word: ${screen.blanks.replace(/_/g, 'blank')}`}>
        {cells.map((word, wi) => (
          <span key={wi} className="blank-word">
            {word.map((c, ci) => <span key={ci} className={`blank-cell ${c === '_' ? '' : 'shown'}`}>{c === '_' ? '' : c}</span>)}
          </span>
        ))}
      </div>
      {screen.solved ? (
        <div className="guess-solved" role="status">
          <b>You got it!</b>
          {screen.points != null && <span>+{screen.points.toLocaleString()}</span>}
          <small>Now watch the rest of the room sweat.</small>
        </div>
      ) : (
        <form className="stack" onSubmit={submit}>
          <input
            ref={field}
            value={text}
            maxLength={40}
            autoFocus
            autoComplete="off"
            autoCorrect="off"
            autoCapitalize="none"
            spellCheck={false}
            enterKeyHint="send"
            aria-label="Your guess"
            placeholder="Type a guess"
            disabled={disabled}
            onChange={(e) => setText(e.target.value)}
          />
          <button className="primary big" disabled={disabled || !text.trim()}>Guess</button>
          <p className={`guess-note ${screen.close ? 'close' : ''}`} role="status">
            {screen.close ? 'So close!' : screen.last ? `Not “${screen.last}”` : ' '}
          </p>
        </form>
      )}
      <p className="muted draw-status">{screen.guessed} of {screen.expected} have it</p>
    </div>
  )
}
