// controller/src/tv/turf3d/scene/Deed.tsx
import { useFrame } from '@react-three/fiber'
import { useEffect, useMemo, useRef } from 'react'
import * as THREE from 'three'
import type { TurfSpace, TurfTv } from '../../types'
import { CLASSIC, fitSize } from '../boardTexture'
import { tileOf } from '../layout'

function deedTexture(space: TurfSpace): THREE.CanvasTexture {
  const W = 256, H = 360
  const cv = document.createElement('canvas'); cv.width = W; cv.height = H
  const g = cv.getContext('2d')!
  g.fillStyle = CLASSIC.paper; g.fillRect(0, 0, W, H)
  g.fillStyle = space.color ?? '#888888'; g.fillRect(0, 0, W, 86)
  g.strokeStyle = CLASSIC.ink; g.lineWidth = 10; g.strokeRect(5, 5, W - 10, H - 10); g.lineWidth = 6; g.strokeRect(5, 86, W - 10, 1)
  g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillStyle = CLASSIC.text
  const words = space.label.toUpperCase().split(' ')
  const measure = (t: string, s: number) => { g.font = `${s}px Anton, sans-serif`; return g.measureText(t).width }
  const size = fitSize(measure, words, W - 40, 58, 24)
  g.font = `${size}px Anton, sans-serif`
  words.forEach((w, i) => g.fillText(w, W / 2, 150 + i * (size + 6)))
  g.font = '64px Anton, sans-serif'; g.fillText(`$${space.price}`, W / 2, H - 56)
  const t = new THREE.CanvasTexture(cv); t.colorSpace = THREE.SRGBColorSpace
  return t
}

/** While a place is on offer (a buy decision or an auction), its deed rises off the tile and bobs above it. */
export function Deed({ tv }: { tv: TurfTv }) {
  const space = tv.phase === 'buy' ? tv.buy : tv.phase === 'auction' ? tv.auction?.space ?? -1 : -1
  const spec = space >= 0 ? tv.board[space] : undefined
  const tex = useMemo(() => (spec ? deedTexture(spec) : null), [space, spec?.label, spec?.price, spec?.color])
  useEffect(() => () => tex?.dispose(), [tex])
  const group = useRef<THREE.Group>(null)
  const t0 = useRef(performance.now())
  useEffect(() => { t0.current = performance.now() }, [space])

  useFrame(() => {
    const g = group.current
    if (!g) return
    g.visible = space >= 0
    if (space < 0) return
    const now = performance.now(), u = Math.min(1, (now - t0.current) / 600)
    const t = tileOf(space)
    g.position.set(t.cx, 0.3 + 0.9 * (1 - (1 - u) ** 3) + Math.sin(now / 400) * 0.04, t.cz)
  })

  return (
    <group ref={group} visible={false} rotation={[-0.6, 0, 0]}>
      {tex && (
        <mesh>
          <planeGeometry args={[0.9, 1.26]} />
          <meshBasicMaterial map={tex} side={THREE.DoubleSide} toneMapped={false} />
        </mesh>
      )}
    </group>
  )
}
