// controller/src/tv/turf3d/scene/Moments.tsx
import { useFrame } from '@react-three/fiber'
import { useEffect, useMemo, useRef } from 'react'
import * as THREE from 'three'
import type { V3 } from '../diceFaces'
import { spacePos } from '../layout'
import { coinPos, coinsFor, flipPose, type Coin } from '../moments'
import type { Craft } from '../useChoreography'

const MAX_COINS = 72

/** Gold coins for rent (a stream from payer to owner), tax (a burst) and Payday (a rain), all in one instanced mesh. */
export function CoinFx({ craft }: { craft: Craft }) {
  const mesh = useRef<THREE.InstancedMesh>(null)
  const coins = useRef<Coin[]>([])
  const handled = useRef(0)
  const dummy = useMemo(() => new THREE.Object3D(), [])

  useEffect(() => {
    const now = performance.now()
    for (const m of craft.moments) {
      if (m.n <= handled.current) continue
      handled.current = m.n
      coins.current.push(...coinsFor(m, craft.shown, now))
    }
    coins.current = coins.current.slice(-MAX_COINS)
  }, [craft.moments])

  useFrame(() => {
    const inst = mesh.current
    if (!inst) return
    const now = performance.now()
    coins.current = coins.current.filter((c) => now < c.t0 + c.life)
    for (let i = 0; i < MAX_COINS; i++) {
      const c = coins.current[i]
      const p = c ? coinPos(c, now) : null
      if (p) { dummy.position.set(p[0], p[1], p[2]); dummy.rotation.set(now / 120 + i, now / 90, 0); dummy.scale.setScalar(1) }
      else { dummy.position.set(0, -10, 0); dummy.scale.setScalar(0) }
      dummy.updateMatrix()
      inst.setMatrixAt(i, dummy.matrix)
    }
    inst.instanceMatrix.needsUpdate = true
  })

  return (
    <instancedMesh ref={mesh} args={[undefined, undefined, MAX_COINS]} frustumCulled={false}>
      <cylinderGeometry args={[0.1, 0.1, 0.03, 20]} />
      <meshStandardMaterial color="#ffc94a" metalness={0.9} roughness={0.25} emissive="#7a4d00" emissiveIntensity={0.4} />
    </instancedMesh>
  )
}

function cardTexture(): THREE.CanvasTexture {
  const cv = document.createElement('canvas'); cv.width = 256; cv.height = 360
  const g = cv.getContext('2d')!
  g.fillStyle = '#fff3c9'; g.fillRect(0, 0, 256, 360)
  g.strokeStyle = '#1a1a1a'; g.lineWidth = 12; g.strokeRect(6, 6, 244, 348)
  g.fillStyle = '#1a1a1a'; g.textAlign = 'center'; g.textBaseline = 'middle'; g.font = '220px Anton, sans-serif'; g.fillText('?', 128, 190)
  const t = new THREE.CanvasTexture(cv); t.colorSpace = THREE.SRGBColorSpace
  return t
}

/** A card that turns over above the piece that drew it (the words are on the DOM well; this is the flourish). */
export function CardFlips({ craft }: { craft: Craft }) {
  const group = useRef<THREE.Group>(null)
  const pivot = useRef<THREE.Group>(null)
  const mats = useRef<(THREE.MeshBasicMaterial | null)[]>([null, null])
  const flip = useRef<{ t0: number; at: V3 } | null>(null)
  const handled = useRef(0)
  const face = useMemo(cardTexture, [])
  useEffect(() => () => face.dispose(), [face])

  useEffect(() => {
    for (const m of craft.moments) {
      if (m.n <= handled.current) continue
      handled.current = m.n
      if (m.type !== 'card') continue
      const s = craft.shown[m.token]
      if (s === undefined || !Number.isFinite(s)) continue
      const p = spacePos(s)
      flip.current = { t0: performance.now(), at: [p.x, 0, p.z] }
    }
  }, [craft.moments])

  useFrame(() => {
    const g = group.current, pv = pivot.current, f = flip.current
    if (!g || !pv) return
    const u = f ? (performance.now() - f.t0) / 1800 : 2
    g.visible = !!f && u >= 0 && u <= 1
    if (!g.visible || !f) return
    const pose = flipPose(u)
    g.position.set(f.at[0], pose.y, f.at[2])
    pv.rotation.x = Math.PI - pose.rotX // starts face-down, ends face-up
    mats.current.forEach((m) => { if (m) m.opacity = pose.opacity })
  })

  return (
    <group ref={group} visible={false} rotation={[-0.35, 0, 0]}>
      <group ref={pivot}>
        <mesh><planeGeometry args={[0.7, 0.98]} /><meshBasicMaterial ref={(m) => { mats.current[0] = m }} map={face} transparent toneMapped={false} /></mesh>
        <mesh rotation={[0, Math.PI, 0]}><planeGeometry args={[0.7, 0.98]} /><meshBasicMaterial ref={(m) => { mats.current[1] = m }} color="#e2483d" transparent toneMapped={false} /></mesh>
      </group>
    </group>
  )
}
