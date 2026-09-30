// controller/src/tv/turf3d/scene/Flags.tsx
import { useMemo } from 'react'
import * as THREE from 'three'
import type { TurfTv } from '../../types'
import { tileOf } from '../layout'

/** Six flag shapes, one per token, so an owner is never told by colour alone. */
function flagGeometry(k: number): THREE.ShapeGeometry {
  const s = new THREE.Shape()
  switch (k % 6) {
    case 0: s.moveTo(0, 0); s.lineTo(0.26, 0); s.lineTo(0.26, 0.2); s.lineTo(0, 0.2); break // square
    case 1: s.moveTo(0, 0); s.lineTo(0.3, 0.1); s.lineTo(0, 0.2); break // pennant
    case 2: s.moveTo(0, 0); s.lineTo(0.28, 0); s.lineTo(0.2, 0.1); s.lineTo(0.28, 0.2); s.lineTo(0, 0.2); break // swallowtail
    case 3: s.absarc(0.11, 0.1, 0.11, 0, Math.PI * 2, false); break // circle
    case 4: s.moveTo(0.12, 0); s.lineTo(0.24, 0.1); s.lineTo(0.12, 0.2); s.lineTo(0, 0.1); break // diamond
    default: s.moveTo(0.06, 0); s.lineTo(0.22, 0); s.lineTo(0.28, 0.1); s.lineTo(0.22, 0.2); s.lineTo(0.06, 0.2); s.lineTo(0, 0.1); break // hexagon
  }
  s.closePath()
  return new THREE.ShapeGeometry(s)
}

function OwnerMark({ space, owner, color, mortgaged }: { space: number; owner: number; color: string; mortgaged: boolean }) {
  const t = tileOf(space)
  const geo = useMemo(() => flagGeometry(owner), [owner])
  const tint = mortgaged ? '#8a8a8a' : color
  const horizontal = t.side === 'bottom' || t.side === 'top'
  const depth = horizontal ? t.ez : t.ex
  const out = { x: -t.inward.x, z: -t.inward.z }
  // The tint strip hugs the outer edge; the flag stands just inside it, near the tile's centre line.
  const stripAt = { x: t.cx + out.x * (depth / 2 - 0.05), z: t.cz + out.z * (depth / 2 - 0.05) }
  const poleAt = { x: t.cx + out.x * (depth / 2 - 0.22), z: t.cz + out.z * (depth / 2 - 0.22) }
  return (
    <>
      <mesh rotation-x={-Math.PI / 2} position={[stripAt.x, 0.006, stripAt.z]} receiveShadow>
        <planeGeometry args={horizontal ? [t.ex - 0.08, 0.09] : [0.09, t.ez - 0.08]} />
        <meshStandardMaterial color={tint} roughness={0.6} />
      </mesh>
      <group position={[poleAt.x, 0, poleAt.z]}>
        <mesh position={[0, 0.25, 0]} castShadow><cylinderGeometry args={[0.012, 0.012, 0.5, 8]} /><meshStandardMaterial color="#d9d9de" metalness={0.9} roughness={0.3} /></mesh>
        <mesh geometry={geo} position={[0.01, 0.3, 0]} castShadow><meshStandardMaterial color={tint} roughness={0.55} side={THREE.DoubleSide} /></mesh>
      </group>
    </>
  )
}

export function Flags({ tv }: { tv: TurfTv }) {
  return (
    <>
      {tv.owner.map((o, i) => {
        const tok = o >= 0 ? tv.tokens[o] : undefined
        if (!tok || tileOf(i).corner) return null
        return <OwnerMark key={i} space={i} owner={o} color={tok.color} mortgaged={tv.mortgaged.includes(i)} />
      })}
    </>
  )
}
