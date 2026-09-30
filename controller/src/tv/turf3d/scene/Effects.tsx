// controller/src/tv/turf3d/scene/Effects.tsx
import { useFrame } from '@react-three/fiber'
import { useEffect, useRef } from 'react'
import * as THREE from 'three'
import { spacePos } from '../layout'
import type { Craft } from '../useChoreography'

/** The pulsing target ring while a piece approaches, and the shock ring plus tile lift when it lands. */
export function Effects({ craft }: { craft: Craft }) {
  const ring = useRef<THREE.Mesh>(null)
  const pulse = useRef<THREE.Mesh>(null)
  const lift = useRef<THREE.Mesh>(null)
  const landedAt = useRef<number | null>(null)

  useEffect(() => {
    if (!craft.landed) return
    landedAt.current = performance.now()
    const p = spacePos(craft.landed.space)
    pulse.current?.position.set(p.x, 0.03, p.z)
    lift.current?.position.set(p.x, 0, p.z)
  }, [craft.landed?.n])

  useFrame(() => {
    if (ring.current) {
      if (craft.target != null) {
        const p = spacePos(craft.target)
        ring.current.position.set(p.x, 0.02, p.z)
        const s = 1 + Math.sin(performance.now() / 110) * 0.12
        ring.current.scale.set(s, 1, s)
        ring.current.visible = true
      } else ring.current.visible = false
    }
    const since = landedAt.current == null ? 1 : (performance.now() - landedAt.current) / 700
    const on = since < 1
    if (pulse.current) {
      pulse.current.visible = on
      if (on) { const s = 0.5 + since * 4.5; pulse.current.scale.set(s, s, 1); (pulse.current.material as THREE.MeshBasicMaterial).opacity = 0.9 * (1 - since) }
    }
    if (lift.current) {
      lift.current.visible = on
      if (on) lift.current.position.y = Math.sin(Math.PI * since) * 0.3
    }
  })

  return (
    <>
      <mesh ref={ring} rotation-x={-Math.PI / 2} visible={false}><ringGeometry args={[0.42, 0.56, 48]} /><meshBasicMaterial color="#ffd23f" toneMapped={false} /></mesh>
      <mesh ref={pulse} rotation-x={-Math.PI / 2} visible={false}><ringGeometry args={[0.4, 0.55, 48]} /><meshBasicMaterial color="#ffffff" transparent toneMapped={false} /></mesh>
      <mesh ref={lift} visible={false}><boxGeometry args={[0.9, 0.05, 0.9]} /><meshStandardMaterial color="#ffd23f" emissive="#ffb000" emissiveIntensity={0.6} transparent opacity={0.85} /></mesh>
    </>
  )
}
