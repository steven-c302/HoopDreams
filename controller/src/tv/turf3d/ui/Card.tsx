// controller/src/tv/turf3d/ui/Card.tsx
import { useEffect, useMemo, type ReactNode } from 'react'
import * as THREE from 'three'
import { INK, PAPER, SHADOW } from './theme'

/** A rounded rectangle centred on the origin, facing +z. */
export function roundedRect(w: number, h: number, r: number): THREE.ShapeGeometry {
  const rr = Math.min(r, w / 2, h / 2), x = -w / 2, y = -h / 2
  const s = new THREE.Shape()
  s.moveTo(x + rr, y); s.lineTo(x + w - rr, y); s.quadraticCurveTo(x + w, y, x + w, y + rr)
  s.lineTo(x + w, y + h - rr); s.quadraticCurveTo(x + w, y + h, x + w - rr, y + h)
  s.lineTo(x + rr, y + h); s.quadraticCurveTo(x, y + h, x, y + h - rr)
  s.lineTo(x, y + rr); s.quadraticCurveTo(x, y, x + rr, y)
  return new THREE.ShapeGeometry(s, 8)
}

/** A flat, unlit rounded plate. Unlit so paper stays paper whatever the scene lights are doing. */
export function Plate({ w, h, r = 14, color, x = 0, y = 0, z = 0, opacity = 1 }: { w: number; h: number; r?: number; color: string; x?: number; y?: number; z?: number; opacity?: number }) {
  const geo = useMemo(() => roundedRect(w, h, r), [w, h, r])
  useEffect(() => () => geo.dispose(), [geo])
  return (
    <mesh geometry={geo} position={[x, y, z]}>
      <meshBasicMaterial color={color} transparent={opacity < 1} opacity={opacity} toneMapped={false} />
    </mesh>
  )
}

/** The paper card: a hard offset shadow, an ink outline and a paper face. Children draw at z >= 1. */
export function Card({ w, h, children }: { w: number; h: number; children?: ReactNode }) {
  return (
    <group>
      <Plate w={w + 16} h={h + 16} r={26} color={SHADOW} x={10} y={-10} z={-3} />
      <Plate w={w + 16} h={h + 16} r={26} color={INK} z={-2} />
      <Plate w={w} h={h} r={20} color={PAPER} z={-1} />
      {children}
    </group>
  )
}
