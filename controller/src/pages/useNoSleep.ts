import NoSleep from 'nosleep.js'
import { useEffect } from 'react'

let noSleep: NoSleep | null = null

/**
 * Keeps the phone's screen on while [active] (a game is running): an asleep phone drops its connection, and the TV
 * pauses for it. Phones load the controller over plain http on the LAN, where the Wake Lock API isn't offered, so
 * NoSleep falls back to a tiny muted looping video. Either way it has to start from a tap, so it switches on at the
 * next tap, and again after the phone comes back from the lock screen.
 */
export function useNoSleep(active: boolean) {
  useEffect(() => {
    if (!active) return
    noSleep ??= new NoSleep()
    const ns = noSleep
    let stale = true
    const wake = () => {
      if (!stale && ns.isEnabled) return
      stale = false
      ns.enable().catch(() => { stale = true })
    }
    const onVisible = () => { if (document.visibilityState === 'visible') stale = true }
    document.addEventListener('pointerdown', wake, true)
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      document.removeEventListener('pointerdown', wake, true)
      document.removeEventListener('visibilitychange', onVisible)
      ns.disable()
    }
  }, [active])
}
