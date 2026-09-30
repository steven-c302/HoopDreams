// controller/src/tv/turf3d/ui/panels/CardPanel.tsx
import { useFrame } from '@react-three/fiber'
import { useEffect, useRef, type ReactNode } from 'react'
import * as THREE from 'three'
import type { TurfTv } from '../../../types'
import { Plate } from '../Card'
import { resolve, sipLine } from '../copy'
import { Label } from '../Label'
import { usePalette } from '../palette'
import { Pill } from '../Pill'
import { bodyOf } from '../sizing'
import { INK, PAPER } from '../theme'

/** Turns over from its back (rotation π) to face-up in about half a second whenever a new card is drawn. */
function Flip({ k, children }: { k: string; children: ReactNode }) {
  const g = useRef<THREE.Group>(null)
  const t0 = useRef(performance.now())
  useEffect(() => { t0.current = performance.now() }, [k])
  useFrame(() => {
    const u = Math.min(1, (performance.now() - t0.current) / 520), e = 1 - (1 - u) ** 3
    if (g.current) g.current.rotation.y = Math.PI * (1 - e)
  })
  return <group ref={g}>{children}</group>
}

/** The drawn card: deck name, the words, and what to drink. */
export function CardPanel({ tv }: { tv: TurfTv }) {
  const c = tv.card
  const p = usePalette()
  if (!c) return null
  const body = bodyOf('std')
  const tint = resolve(p, c.deck === 'chance' ? 'var(--sky)' : 'var(--bubblegum)', '#7fc8ff')
  const sips = sipLine(tv.drinks, c.sips)
  return (
    <Flip k={c.text}>
      <Plate w={body.w - 20} h={body.h - 16} r={18} color={INK} z={1} />
      <Plate w={body.w - 28} h={body.h - 24} r={14} color={tint} z={2} />
      <Label px={40} kind="title" y={78} z={3} color={INK}>{c.deckName.toUpperCase()}</Label>
      <Label px={34} kind="body" y={sips ? 4 : -8} z={3} maxWidth={body.w - 90} font="bodyBold">{c.text}</Label>
      {sips && <Pill w={250} h={44} y={-80} text={sips} fill={PAPER} />}
    </Flip>
  )
}
