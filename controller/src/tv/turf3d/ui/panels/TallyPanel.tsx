// controller/src/tv/turf3d/ui/panels/TallyPanel.tsx
import { useFrame } from '@react-three/fiber'
import { useRef } from 'react'
import * as THREE from 'three'
import type { TurfTv } from '../../../types'
import { Plate } from '../Card'
import { barFraction, money } from '../copy'
import { DrinkIcon } from '../DrinkIcon'
import { Label } from '../Label'
import { GOLD, INK, MUTED, PAPER } from '../theme'

const BAR_W = 330

/** A bar that grows from its left end to [frac] of its full width, starting after [delay] seconds. */
function GrowBar({ frac, color, delay, y }: { frac: number; color: string; delay: number; y: number }) {
  const g = useRef<THREE.Group>(null)
  const t0 = useRef(performance.now())
  useFrame(() => {
    const u = Math.min(1, Math.max(0, (performance.now() - t0.current) / 1000 - delay) / 1.2)
    g.current?.scale.set(Math.max(0.001, 1 - (1 - u) ** 3), 1, 1)
  })
  const w = Math.max(14, frac * BAR_W)
  return (
    <group ref={g} position={[-110, y, 0]}>
      <Plate w={w + 6} h={36} r={9} color={INK} x={w / 2} z={1} />
      <Plate w={w} h={30} r={7} color={color} x={w / 2} z={2} />
    </group>
  )
}

/** The final tally: best first, a bar per token in proportion to worth. */
export function TallyPanel({ tv }: { tv: TurfTv }) {
  const rows = tv.tally.slice().sort((a, b) => a.rank - b.rank).slice(0, 6)
  const max = Math.max(1, ...rows.map((r) => r.worth))
  return (
    <group>
      <Label px={60} kind="title" y={196}>FINAL TALLY</Label>
      {rows.map((r, k) => {
        const t = tv.tokens[r.token]
        if (!t) return null
        const y = 130 - k * 54
        return (
          <group key={r.token}>
            <Plate w={40} h={40} r={20} color={r.rank === 1 ? GOLD : PAPER} x={-366} y={y} z={1} />
            <Label px={28} kind="body" font="display" x={-366} y={y} z={3}>{String(r.rank)}</Label>
            <DrinkIcon piece={t.piece} color={t.color} size={44} x={-318} y={y - 6} />
            <Label px={28} kind="body" font="bodyBold" anchorX="left" align="left" x={-284} y={y} maxWidth={172}>{t.name}</Label>
            <GrowBar frac={barFraction(r.worth, max)} color={t.color} delay={0.4 + k * 0.2} y={y} />
            <Label px={28} kind="body" font="bodyBold" anchorX="right" align="right" x={372} y={y} maxWidth={120}>{money(r.worth)}</Label>
          </group>
        )
      })}
      <Label px={28} kind="body" y={-206} color={MUTED} font="bodyBold" maxWidth={740}>Cash + places (half if mortgaged) + buildings at cost</Label>
    </group>
  )
}
