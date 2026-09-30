// controller/src/tv/turf3d/scene/Houses.tsx
import { useFrame } from '@react-three/fiber'
import { useRef, type ReactNode } from 'react'
import type * as THREE from 'three'
import type { TurfTv } from '../../types'
import { CLASSIC } from '../boardTexture'
import { tileOf } from '../layout'
import { popScale } from '../moments'

const LOADED = performance.now()

/** Grows its children from nothing with a little overshoot, unless they were already there when the stage opened. */
function Pop({ children }: { children: ReactNode }) {
  const ref = useRef<THREE.Group>(null)
  const t0 = useRef(performance.now())
  const animate = useRef(performance.now() - LOADED > 2500)
  useFrame(() => {
    const g = ref.current
    if (g) g.scale.setScalar(animate.current ? popScale((performance.now() - t0.current) / 500) : 1)
  })
  return <group ref={ref}>{children}</group>
}

function HouseMesh({ hotel }: { hotel?: boolean }) {
  return (
    <Pop><group scale={0.85}>
      <mesh castShadow position={[0, 0.16, 0]}><boxGeometry args={[hotel ? 0.6 : 0.3, 0.32, 0.3]} /><meshPhysicalMaterial color={hotel ? '#e2483d' : '#2fbf55'} roughness={0.45} clearcoat={0.5} /></mesh>
      <mesh castShadow position={[0, 0.4, 0]} rotation={[0, Math.PI / 4, 0]}><coneGeometry args={[hotel ? 0.42 : 0.24, 0.24, 4]} /><meshPhysicalMaterial color={hotel ? '#8a2a22' : '#1f8a3f'} roughness={0.45} clearcoat={0.5} /></mesh>
    </group></Pop>
  )
}

/** Levels 1 to 3 are houses, level 4 is a hotel, standing on the colour band. */
function TileBuildings({ space, level }: { space: number; level: number }) {
  const t = tileOf(space)
  const depth = t.side === 'bottom' || t.side === 'top' ? t.ez : t.ex
  const along = { x: Math.abs(t.inward.z), z: Math.abs(t.inward.x) }
  const cx = t.cx + t.inward.x * (depth / 2 - CLASSIC.bandDepth / 2)
  const cz = t.cz + t.inward.z * (depth / 2 - CLASSIC.bandDepth / 2)
  if (level >= 4) return <group position={[cx, 0, cz]}><HouseMesh hotel /></group>
  return (
    <>
      {Array.from({ length: level }, (_, k) => {
        const o = (k - (level - 1) / 2) * 0.24
        return <group key={k} position={[cx + along.x * o, 0, cz + along.z * o]}><HouseMesh /></group>
      })}
    </>
  )
}

export function Houses({ tv }: { tv: TurfTv }) {
  return <>{tv.level.map((lvl, i) => (lvl > 0 ? <TileBuildings key={i} space={i} level={lvl} /> : null))}</>
}
