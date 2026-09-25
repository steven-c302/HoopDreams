import { useEffect, useState } from 'react'

/** Seconds left, counting down locally from the server's remainingMs at the moment [seq] arrived. */
export function useCountdown(remainingMs: number | undefined, seq: number, paused: boolean): number | null {
  const [now, setNow] = useState(() => Date.now())
  const [anchor, setAnchor] = useState(() => ({ at: Date.now(), ms: remainingMs }))
  useEffect(() => { setAnchor({ at: Date.now(), ms: remainingMs }) }, [remainingMs, seq])
  useEffect(() => {
    if (paused || remainingMs == null) return
    const t = setInterval(() => setNow(Date.now()), 250)
    return () => clearInterval(t)
  }, [paused, remainingMs])
  if (anchor.ms == null) return null
  const left = paused ? anchor.ms : anchor.ms - (now - anchor.at)
  return Math.max(0, Math.ceil(left / 1000))
}
