import { useState } from 'react'
import { browserTokenStore, type Session } from './net/token'
import { Join } from './pages/Join'
import { Play } from './pages/Play'
import { Host } from './pages/Host'

const store = browserTokenStore()

function roomFromPath(path: string): string | null {
  const m = path.match(/^\/j\/([A-Za-z]{4})\/?$/)
  return m ? m[1].toUpperCase() : null
}

export function App() {
  const path = window.location.pathname
  const urlRoom = roomFromPath(path)
  const [session, setSession] = useState<Session | null>(() => {
    const s = store.load()
    return s && (!urlRoom || s.room === urlRoom) ? s : null
  })
  const [notice, setNotice] = useState<string | null>(null)

  if (path.startsWith('/host')) return <Host />

  if (!session) {
    return (
      <Join
        room={urlRoom}
        notice={notice}
        onJoined={(s) => { store.save(s); setNotice(null); setSession(s) }}
      />
    )
  }
  return (
    <Play
      session={session}
      onLeave={(why) => { store.clear(); setNotice(why); setSession(null) }}
    />
  )
}
