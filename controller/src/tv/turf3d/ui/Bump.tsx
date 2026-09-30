// controller/src/tv/turf3d/ui/Bump.tsx
import { useFrame } from '@react-three/fiber'
import { useEffect, useRef, type ReactNode } from 'react'
import * as THREE from 'three'

/** Pops its children (scale 1.45 easing back to 1) every time [k] changes. */
export function Bump({ k, children }: { k: string | number; children: ReactNode }) {
  const g = useRef<THREE.Group>(null)
  const t0 = useRef(performance.now())
  useEffect(() => { t0.current = performance.now() }, [k])
  useFrame(() => {
    const u = Math.min(1, (performance.now() - t0.current) / 320)
    g.current?.scale.setScalar(1 + 0.45 * (1 - u) * (1 - u))
  })
  return <group ref={g}>{children}</group>
}
