import { useCallback, useEffect, useState } from 'react'

/** Spotify on the Mac running the show, as the party server reports it (see MusicPlayer.kt). */
export interface MusicStatus { available: boolean; running?: boolean; playing?: boolean; track?: string; artist?: string }
export type MusicCommand = 'play' | 'pause' | 'playpause' | 'next' | 'previous'

const POLL_MS = 4_000

/** What's playing, refreshed while [watch] is on; [send] steers Spotify and updates straight away. */
export function useSpotify(watch: boolean) {
  const [status, setStatus] = useState<MusicStatus | null>(null)
  const [failed, setFailed] = useState(false)

  const refresh = useCallback(async () => {
    try {
      const r = await fetch('/api/music')
      if (r.ok) setStatus(await r.json())
    } catch { /* server restarting; the next poll catches up */ }
  }, [])

  useEffect(() => {
    if (!watch) return
    void refresh()
    const id = setInterval(() => void refresh(), POLL_MS)
    return () => clearInterval(id)
  }, [watch, refresh])

  const send = useCallback(async (cmd: MusicCommand) => {
    try {
      const r = await fetch(`/api/music/${cmd}`, { method: 'POST' })
      setFailed(!r.ok)
      if (r.ok) setStatus(await r.json())
    } catch { setFailed(true) }
  }, [])

  return { status, failed, send }
}

/** "Mr. Brightside · The Killers", or null when nothing is loaded. */
export const nowPlaying = (s: MusicStatus | null) => (s?.track ? [s.track, s.artist].filter(Boolean).join(' · ') : null)
