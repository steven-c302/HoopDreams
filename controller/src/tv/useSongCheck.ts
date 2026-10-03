import { useEffect, useRef, useState, type RefObject } from 'react'
import { chooseMaker } from './ytClip'

export type SongCheck = 'idle' | 'checking' | 'ok' | 'fail'
const CHECK_TIMEOUT_MS = 12_000
const done = new Map<string, SongCheck>()

/**
 * Song Drop's lobby check: when [enabled], quietly loads [probe] (muted) in a small visible player and reports whether
 * YouTube really plays on this TV, so a problem shows before anyone starts. Attach [host] to a 200×200 element.
 */
export function useSongCheck(probe: string | undefined, enabled: boolean): { state: SongCheck; host: RefObject<HTMLDivElement | null> } {
  const host = useRef<HTMLDivElement>(null)
  const [state, setState] = useState<SongCheck>('idle')
  useEffect(() => {
    if (!enabled || !probe) { setState('idle'); return }
    const known = done.get(probe)
    if (known === 'ok') { setState('ok'); return }
    const el = host.current
    if (!el) return
    setState('checking')
    let over = false
    let player: { pause(): void; destroy(): void } | null = null
    const finish = (r: SongCheck) => {
      if (over) return
      over = true
      done.set(probe, r)
      setState(r)
      try { player?.pause(); player?.destroy() } catch { /* the player may already be gone */ }
    }
    const timer = setTimeout(() => finish('fail'), CHECK_TIMEOUT_MS)
    chooseMaker()(el, { onPlaying: () => finish('ok'), onError: () => finish('fail'), onBlocked: () => finish('fail') })
      .then((p) => { player = p; if (over) { p.destroy(); return } p.mute(true); p.load(probe, 10) })
      .catch(() => finish('fail'))
    return () => { clearTimeout(timer); over = true; try { player?.destroy() } catch { /* already gone */ } }
  }, [enabled, probe])
  return { state, host }
}
