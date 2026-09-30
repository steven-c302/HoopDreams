// controller/src/tv/turf3d/scene/JailBars.tsx
import { useFrame } from '@react-three/fiber'
import { useRef } from 'react'
import * as THREE from 'three'
import { barsDrop } from '../moments'

/** A cage that drops over a piece when it is sent to Timeout and stays while it is there. Mount it to drop it. */
export function JailBars() {
  const ref = useRef<THREE.Group>(null)
  const t0 = useRef(performance.now())
  useFrame(() => { if (ref.current) ref.current.position.y = barsDrop((performance.now() - t0.current) / 500) })
  return (
    <group ref={ref}>
      {Array.from({ length: 8 }, (_, i) => {
        const a = (i / 8) * Math.PI * 2
        return (
          <mesh key={i} position={[Math.cos(a) * 0.5, 0.6, Math.sin(a) * 0.5]} castShadow>
            <cylinderGeometry args={[0.03, 0.03, 1.2, 8]} />
            <meshStandardMaterial color="#2b2b2b" metalness={0.8} roughness={0.35} />
          </mesh>
        )
      })}
      <mesh position={[0, 1.2, 0]} rotation-x={Math.PI / 2}>
        <torusGeometry args={[0.5, 0.035, 8, 32]} />
        <meshStandardMaterial color="#2b2b2b" metalness={0.8} roughness={0.35} />
      </mesh>
    </group>
  )
}
