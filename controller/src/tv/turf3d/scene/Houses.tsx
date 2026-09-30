// controller/src/tv/turf3d/scene/Houses.tsx
import type { TurfTv } from '../../types'
import { CLASSIC } from '../boardTexture'
import { tileOf } from '../layout'

function HouseMesh({ hotel }: { hotel?: boolean }) {
  return (
    <group scale={0.85}>
      <mesh castShadow position={[0, 0.16, 0]}><boxGeometry args={[hotel ? 0.6 : 0.3, 0.32, 0.3]} /><meshPhysicalMaterial color={hotel ? '#e2483d' : '#2fbf55'} roughness={0.45} clearcoat={0.5} /></mesh>
      <mesh castShadow position={[0, 0.4, 0]} rotation={[0, Math.PI / 4, 0]}><coneGeometry args={[hotel ? 0.42 : 0.24, 0.24, 4]} /><meshPhysicalMaterial color={hotel ? '#8a2a22' : '#1f8a3f'} roughness={0.45} clearcoat={0.5} /></mesh>
    </group>
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
