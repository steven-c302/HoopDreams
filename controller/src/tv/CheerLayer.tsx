import './cheer.css'
import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useRef, useState } from 'react'
import type { Cheer, CheerKind } from '../protocol'
import { sfx } from './audio'
import { AvatarFace, C } from './toon'

/** Each cheer's word and colour, matching the buttons on the phone. */
const LOOK: Record<CheerKind, { word: string; fill: string; ink: string }> = {
  yes: { word: 'YES!', fill: C.lime, ink: C.ink },
  boo: { word: 'BOO!', fill: C.tomato, ink: C.white },
  ooh: { word: 'OOOH', fill: C.grape, ink: C.white },
  wow: { word: 'WOW!', fill: C.sun, ink: C.ink },
}
const SOUND: Record<CheerKind, () => void> = {
  yes: () => sfx.pop(),
  boo: () => sfx.laugh(),
  ooh: () => sfx.ooh(1),
  wow: () => sfx.ooh(3),
}
const SHOWN_MS = 2_800
const MAX_ON_SCREEN = 5

/** Where a stamp lands, from its number alone so it never jumps: spread along the bottom edge, a little askew. */
const spot = (seq: number) => ({ left: 4 + ((seq * 37) % 68), tilt: ((seq * 53) % 17) - 8, lift: (seq * 29) % 90 })

/**
 * The crowd's cheers, stamped over whatever is on the TV. The server keeps the latest few with a running number; this
 * shows each number it hasn't seen yet for a moment. What was already there when the TV connected is not replayed.
 */
export function CheerLayer({ cheers, sound }: { cheers: Cheer[]; sound: boolean }) {
  const seen = useRef<number | null>(null)
  const [live, setLive] = useState<Cheer[]>([])
  useEffect(() => {
    const top = cheers.reduce((m, c) => Math.max(m, c.seq), 0)
    // First look, or the server restarted and counts from 1 again: take what's there as already seen.
    if (seen.current === null || top < seen.current) { seen.current = top; return }
    const fresh = cheers.filter((c) => c.seq > seen.current!)
    if (fresh.length === 0) return
    seen.current = top
    setLive((l) => [...l, ...fresh].slice(-MAX_ON_SCREEN))
    if (sound) fresh.slice(-2).forEach((c) => SOUND[c.kind]?.())
    // Not cancelled by a re-render: each stamp has to come down by itself.
    fresh.forEach((c) => setTimeout(() => setLive((l) => l.filter((x) => x.seq !== c.seq)), SHOWN_MS))
  }, [cheers, sound])
  return (
    <div className="cheer-layer" aria-hidden="true">
      <AnimatePresence>
        {live.map((c) => {
          const look = LOOK[c.kind] ?? LOOK.yes
          const at = spot(c.seq)
          return (
            <motion.div key={c.seq} className="cheer-stamp" style={{ left: `${at.left}%`, bottom: 40 + at.lift, background: look.fill, color: look.ink }}
              initial={{ scale: 2.2, opacity: 0, rotate: at.tilt * 3 }} animate={{ scale: 1, opacity: 1, rotate: at.tilt }} exit={{ scale: 0.8, opacity: 0, y: -50 }}
              transition={{ type: 'spring', stiffness: 520, damping: 22 }}>
              <b>{look.word}</b>
              <span><AvatarFace avatar={c.avatar} size={44} />{c.name}</span>
            </motion.div>
          )
        })}
      </AnimatePresence>
    </div>
  )
}
