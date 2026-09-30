// controller/src/tv/turf3d/scene/Dice.tsx
import { useFrame } from '@react-three/fiber'
import { useEffect, useMemo, useRef, useState } from 'react'
import * as THREE from 'three'
import { DT, loadRapier, safeThrow, type Rapier, type Throw } from '../diceSim'
import { sampleTrajectory } from '../trajectory'
import type { Craft } from '../useChoreography'

const PIPS: number[][][] = [[], [[.5, .5]], [[.25, .25], [.75, .75]], [[.25, .25], [.5, .5], [.75, .75]],
  [[.25, .25], [.75, .25], [.25, .75], [.75, .75]], [[.25, .25], [.75, .25], [.5, .5], [.25, .75], [.75, .75]],
  [[.25, .25], [.75, .25], [.25, .5], [.75, .5], [.25, .75], [.75, .75]]]

/** One material per number (index 1 to 6): ivory with ink pips, and a red single pip on the one. */
function pipMaterials(): THREE.MeshPhysicalMaterial[] {
  const mats: THREE.MeshPhysicalMaterial[] = [new THREE.MeshPhysicalMaterial()]
  for (let n = 1; n <= 6; n++) {
    const cv = document.createElement('canvas'); cv.width = cv.height = 128
    const g = cv.getContext('2d')!
    g.fillStyle = '#fbf7ee'; g.fillRect(0, 0, 128, 128)
    g.fillStyle = n === 1 ? '#e2483d' : '#1a1a1a'
    PIPS[n].forEach(([x, y]) => { g.beginPath(); g.arc(x * 128, y * 128, n === 1 ? 17 : 12, 0, Math.PI * 2); g.fill() })
    const map = new THREE.CanvasTexture(cv); map.colorSpace = THREE.SRGBColorSpace
    mats.push(new THREE.MeshPhysicalMaterial({ map, roughness: 0.3, clearcoat: 1, clearcoatRoughness: 0.08 }))
  }
  return mats
}

/**
 * Two dice that replay a recorded throw. The recording was made by a headless simulation seeded by the roll and
 * relabelled so the engine's numbers end up on top, so what plays is what the game decided. If Rapier is not ready
 * or fails, no dice appear and the show (and the total banner) carries on.
 */
export function Dice({ dice }: { dice: Craft['dice'] }) {
  const groups = useRef<(THREE.Group | null)[]>([null, null])
  const rapier = useRef<Rapier | null>(null)
  const play = useRef<{ throw: Throw; t0: number } | null>(null)
  const pips = useMemo(pipMaterials, [])
  const [labels, setLabels] = useState<number[][] | null>(null)

  useEffect(() => {
    let alive = true
    loadRapier().then((r) => { if (alive) rapier.current = r }, () => undefined)
    return () => { alive = false }
  }, [])
  useEffect(() => () => pips.forEach((m) => { m.map?.dispose(); m.dispose() }), [pips])

  useEffect(() => {
    if (!dice) return
    const t = safeThrow(rapier.current, dice.seed, dice.values)
    if (!t) return
    play.current = { throw: t, t0: performance.now() }
    setLabels(t.labels)
  }, [dice?.n])

  useFrame(() => {
    const p = play.current
    if (!p || !labels) return
    const elapsed = (performance.now() - p.t0) / 1000
    for (let k = 0; k < 2; k++) {
      const g = groups.current[k]
      if (!g) continue
      const s = sampleTrajectory(p.throw.frames[k], p.throw.steps, elapsed, DT)
      g.position.set(s.pos[0], s.pos[1], s.pos[2]); g.quaternion.set(s.quat[0], s.quat[1], s.quat[2], s.quat[3]); g.visible = true
    }
  })

  return (
    <>
      {[0, 1].map((k) => (
        <group key={k} ref={(g) => { groups.current[k] = g }} visible={false}>
          <mesh castShadow material={labels ? labels[k].map((v) => pips[v]) : pips.slice(1, 7)}>
            <boxGeometry args={[0.7, 0.7, 0.7]} />
          </mesh>
        </group>
      ))}
    </>
  )
}
