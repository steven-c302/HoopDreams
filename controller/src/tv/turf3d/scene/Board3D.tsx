// controller/src/tv/turf3d/scene/Board3D.tsx
import { RoundedBox } from '@react-three/drei'
import { useEffect, useMemo } from 'react'
import type { TurfTv } from '../../types'
import { CLASSIC, drawBoardTexture } from '../boardTexture'
import { HALF } from '../layout'

/** The slab and its printed top. The texture is repainted only when the board's names, colours or prices change. */
export function Board3D({ tv }: { tv: TurfTv }) {
  const key = tv.board.map((s) => `${s.label}|${s.color ?? ''}|${s.price}|${s.tax}|${s.kind}`).join(';')
  const tex = useMemo(() => drawBoardTexture(tv.board, CLASSIC, 2048), [key])
  useEffect(() => () => tex.dispose(), [tex])
  return (
    <>
      <RoundedBox args={[HALF * 2 + 0.7, 0.42, HALF * 2 + 0.7]} radius={0.07} smoothness={4} position={[0, -0.21, 0]} castShadow receiveShadow>
        <meshPhysicalMaterial color={CLASSIC.paper} roughness={0.6} clearcoat={0.3} />
      </RoundedBox>
      <mesh rotation-x={-Math.PI / 2} position={[0, 0.003, 0]} receiveShadow>
        <planeGeometry args={[HALF * 2, HALF * 2]} />
        <meshPhysicalMaterial map={tex} roughness={0.6} clearcoat={0.15} />
      </mesh>
    </>
  )
}
