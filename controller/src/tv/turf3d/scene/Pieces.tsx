// controller/src/tv/turf3d/scene/Pieces.tsx
import { useFrame } from '@react-three/fiber'
import { useEffect, useMemo, useRef } from 'react'
import * as THREE from 'three'
import type { TurfTv } from '../../types'
import { Drink } from '../Drinks'
import { crowdIndex, crowdSlot, spacePos, tileOf } from '../layout'
import { drinkFor } from '../pieces'
import type { Craft } from '../useChoreography'

interface Motion { n: number; from: THREE.Vector3; t0: number }

/** The six drinks. They hop when the craft says so, otherwise ease to their slot on the shown space. */
export function Pieces({ tv, craft }: { tv: TurfTv; craft: Craft }) {
  const groups = useRef<(THREE.Group | null)[]>([])
  const motion = useRef<(Motion | null)[]>([])
  const landedAt = useRef(0)
  const placed = useRef<boolean[]>([])
  const alive = tv.tokens.map((t) => !t.bankrupt)
  const crowd = useMemo(() => crowdIndex(craft.shown, alive), [craft.shown, tv.tokens])

  /** Where token [k] stands (crowds spread along the tile) and how big it is there (crowds shrink). */
  const slotOf = (k: number) => {
    const space = craft.shown[k] ?? tv.tokens[k].pos
    const p = spacePos(space), slot = crowdSlot(crowd[k]?.rank ?? 0, crowd[k]?.count ?? 1, tileOf(space))
    return { pos: new THREE.Vector3(p.x + slot.dx, 0, p.z + slot.dz), scale: slot.scale }
  }

  useEffect(() => { if (craft.landed) landedAt.current = performance.now() }, [craft.landed?.n])

  useFrame(() => {
    const now = performance.now()
    tv.tokens.forEach((_, k) => {
      const g = groups.current[k]
      if (!g) return
      const { pos: target, scale: base } = slotOf(k), hop = craft.hop[k]
      if (hop && motion.current[k]?.n !== hop.n) motion.current[k] = { n: hop.n, from: g.position.clone().setY(0), t0: now }
      const m = motion.current[k]
      if (hop && m && m.n === hop.n && now - m.t0 < hop.ms) {
        const t = (now - m.t0) / hop.ms, e = hop.last ? t * t * (3 - 2 * t) : t
        g.position.lerpVectors(m.from, target, e)
        g.position.y = Math.sin(Math.PI * t) * hop.height
        const stretch = 1 + Math.sin(Math.PI * t) * 0.14
        g.scale.set((base * 1) / Math.sqrt(stretch), base * stretch, (base * 1) / Math.sqrt(stretch))
        g.rotation.z = Math.sin(Math.PI * t) * 0.2
      } else {
        g.position.x += (target.x - g.position.x) * 0.25
        g.position.z += (target.z - g.position.z) * 0.25
        g.position.y += (0 - g.position.y) * 0.4
        g.scale.lerp(new THREE.Vector3(base, base, base), 0.3)
        const since = (now - landedAt.current) / 1000
        g.rotation.z = craft.landed?.token === k && since < 0.9 ? Math.exp(-5 * since) * Math.sin(since * 26) * 0.2 : 0
      }
    })
  })

  return (
    <>
      {tv.tokens.map((tok, k) => (
        <group
          key={k}
          visible={!tok.bankrupt}
          ref={(gr) => {
            groups.current[k] = gr
            if (gr && !placed.current[k]) { const s = slotOf(k); gr.position.copy(s.pos); gr.scale.setScalar(s.scale); placed.current[k] = true }
          }}
        >
          <Drink kind={drinkFor(tok.piece)} color={tok.color} />
        </group>
      ))}
    </>
  )
}
