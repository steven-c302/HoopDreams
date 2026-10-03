import { useEffect, useRef, useState } from 'react'
import type { CheerKind } from '../protocol'

const buzz = (ms: number) => { try { navigator.vibrate?.(ms) } catch { /* not supported */ } }

/** The four cheers on offer, as the words the TV stamps up. */
export const CHEERS: { kind: CheerKind; word: string; fill: string }[] = [
  { kind: 'yes', word: 'YES!', fill: 'var(--lime)' },
  { kind: 'boo', word: 'BOO!', fill: 'var(--tomato)' },
  { kind: 'ooh', word: 'OOOH', fill: 'var(--grape)' },
  { kind: 'wow', word: 'WOW!', fill: 'var(--sun)' },
]

/** Matches the server's limit of one cheer per person at a time; the pad rests for that long after a tap. */
const REST_MS = 1_200

/** For people watching: tap to stamp a cheer on the TV with your name and face. */
export function CheerPad({ onCheer }: { onCheer(kind: CheerKind): void }) {
  const [resting, setResting] = useState(false)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  useEffect(() => () => { if (timer.current) clearTimeout(timer.current) }, [])
  const tap = (kind: CheerKind) => {
    if (resting) return
    buzz(20)
    onCheer(kind)
    setResting(true)
    timer.current = setTimeout(() => setResting(false), REST_MS)
  }
  return (
    <div className="cheer-pad" role="group" aria-label="Cheer for the room">
      <p>Cheer the room on. It lands on the TV with your name.</p>
      <div className="cheer-grid">
        {CHEERS.map((c) => (
          <button key={c.kind} className="cheer-btn" style={{ background: c.fill }} disabled={resting} onClick={() => tap(c.kind)}>{c.word}</button>
        ))}
      </div>
    </div>
  )
}
