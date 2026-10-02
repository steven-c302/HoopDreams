// controller/src/tv/turf3d/scene/Pieces.tsx
import { useFrame } from '@react-three/fiber'
import { useEffect, useMemo, useRef } from 'react'
import * as THREE from 'three'
import type { TurfTv } from '../../types'
import { Drink } from '../Drinks'
import { crowdIndex, crowdSlot, standPos, tileOf } from '../layout'
import { fallPose } from '../moments'
import { drinkFor } from '../pieces'
import type { Craft } from '../useChoreography'
import { JailBars } from './JailBars'

interface Motion { n: number; from: THREE.Vector3; t0: number }

const FALL_MS = 1500
/** Pieces are drawn a fifth smaller than a tile allows, so a coaster and a bottle never crowd the tile's edges or the camera behind it. */
const PIECE_SCALE = 0.8
/** Phases in which the turn's piece is the one everyone is waiting on. */
const WAITING_ON_TURN = new Set(['roll', 'jail', 'buy', 'manage'])

/**
 * The six drinks. They hop when the craft says so, otherwise ease to their slot on the shown space. The piece whose
 * turn it is bobs and glows, a jailed piece sits in a cage, and a bankrupt piece tips over and drops off the table.
 */
export function Pieces({ tv, craft }: { tv: TurfTv; craft: Craft }) {
  const groups = useRef<(THREE.Group | null)[]>([])
  const rings = useRef<(THREE.Mesh | null)[]>([])
  const motion = useRef<(Motion | null)[]>([])
  const falls = useRef<Record<number, number>>({})
  const handled = useRef(0)
  const landedAt = useRef(0)
  const placed = useRef<boolean[]>([])
  const alive = tv.tokens.map((t) => !t.bankrupt)
  const crowd = useMemo(() => crowdIndex(craft.shown, alive), [craft.shown, tv.tokens])

  /** Where token [k] stands (crowds spread along the tile) and how big it is there (crowds shrink). */
  const slotOf = (k: number) => {
    const space = craft.shown[k] ?? tv.tokens[k].pos
    const p = standPos(space), slot = crowdSlot(crowd[k]?.rank ?? 0, crowd[k]?.count ?? 1, tileOf(space))
    return { pos: new THREE.Vector3(p.x + slot.dx, 0, p.z + slot.dz), scale: slot.scale * PIECE_SCALE }
  }

  useEffect(() => { if (craft.landed) landedAt.current = performance.now() }, [craft.landed?.n])
  useEffect(() => {
    for (const m of craft.moments) {
      if (m.n <= handled.current) continue
      handled.current = m.n
      if (m.type === 'fall') falls.current[m.token] = performance.now()
    }
  }, [craft.moments])

  useFrame(() => {
    const now = performance.now()
    tv.tokens.forEach((tok, k) => {
      const g = groups.current[k]
      if (!g) return
      const { pos: target, scale: base } = slotOf(k)
      const fallAt = falls.current[k]
      const falling = fallAt !== undefined && now - fallAt < FALL_MS
      g.visible = !tok.bankrupt || falling
      if (falling) {
        const f = fallPose((now - fallAt) / FALL_MS)
        g.position.set(target.x + f.x, f.y, target.z)
        g.rotation.z = -f.rotZ
        g.scale.setScalar(base)
        return
      }
      const active = k === tv.turn && !tok.bankrupt && WAITING_ON_TURN.has(tv.phase)
      const ring = rings.current[k]
      if (ring) {
        ring.visible = active
        if (active) (ring.material as THREE.MeshBasicMaterial).opacity = 0.5 + 0.3 * Math.sin(now / 200)
      }
      const hop = craft.hop[k]
      if (hop && motion.current[k]?.n !== hop.n) motion.current[k] = { n: hop.n, from: g.position.clone().setY(0), t0: now }
      const m = motion.current[k]
      if (hop && m && m.n === hop.n && now - m.t0 < hop.ms) {
        const t = (now - m.t0) / hop.ms, e = hop.last ? t * t * (3 - 2 * t) : t
        g.position.lerpVectors(m.from, target, e)
        g.position.y = Math.sin(Math.PI * t) * hop.height
        const stretch = 1 + Math.sin(Math.PI * t) * 0.14
        g.scale.set(base / Math.sqrt(stretch), base * stretch, base / Math.sqrt(stretch))
        g.rotation.z = Math.sin(Math.PI * t) * 0.2
      } else {
        g.position.x += (target.x - g.position.x) * 0.25
        g.position.z += (target.z - g.position.z) * 0.25
        g.position.y += ((active ? 0.12 + Math.sin(now / 260) * 0.04 : 0) - g.position.y) * 0.3
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
          <mesh ref={(m) => { rings.current[k] = m }} rotation-x={-Math.PI / 2} position={[0, 0.075, 0]} visible={false}>
            <ringGeometry args={[0.46, 0.56, 48]} />
            <meshBasicMaterial color="#ffd23f" transparent toneMapped={false} />
          </mesh>
          {tok.jailed && craft.shown[k] === 10 && <JailBars />}
        </group>
      ))}
    </>
  )
}
