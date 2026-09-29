import { useEffect, useState } from 'react'
import type { ActionPayload, Screen } from '../protocol'
import './jeopardy-phone.css'

type BuzzerScreenT = Extract<Screen, { t: 'buzzer' }>

const buzz = (ms: number | number[]) => { try { navigator.vibrate?.(ms) } catch { /* not supported */ } }
const LABELS: Record<BuzzerScreenT['state'], string> = { reading: 'WAIT', open: 'BUZZ!', locked: 'TOO EARLY', beaten: 'TOO SLOW', tried: 'MISSED', out: 'NEXT CLUE' }

/**
 * One giant button. It can be pressed while the clue is still being read (that is ringing in early, and the server
 * locks you out) and while it is open. A locked-out phone opens itself when the lockout ends, if the buzz window is
 * still open: the server has no way to push a view at that moment.
 */
export function BuzzerScreen({ screen, disabled, onAction }: { screen: BuzzerScreenT; disabled: boolean; onAction(p: ActionPayload): void }) {
  const [unlocked, setUnlocked] = useState(false)
  useEffect(() => {
    setUnlocked(false)
    if (screen.state !== 'locked' || !screen.live) return
    const id = setTimeout(() => setUnlocked(true), screen.lockedMs)
    return () => clearTimeout(id)
  }, [screen.state, screen.lockedMs, screen.live])
  const state = screen.state === 'locked' && unlocked ? 'open' : screen.state
  const pressable = state === 'reading' || state === 'open' || state === 'locked'
  return (
    <div className="stack jz">
      <p className="jz-clue"><b>{screen.category}</b> · ${screen.value}</p>
      <button
        type="button"
        className={`jz-buzz ${state}`}
        disabled={disabled || !pressable}
        onClick={() => { buzz(state === 'open' ? [30, 30, 30] : 20); onAction({ kind: 'buzz' }) }}
      >
        {LABELS[state]}
      </button>
      {screen.detail && state !== 'open' && <p className="muted jz-detail">{screen.detail}</p>}
    </div>
  )
}
