// controller/src/tv/turf3d/ui/Dais.tsx
import { useFrame, useThree } from '@react-three/fiber'
import { Suspense, useMemo, useRef, useState, type ReactNode } from 'react'
import * as THREE from 'three'
import { REF_H, stepToward, worldPerPx } from './sizing'

/** How far in front of the camera the card hangs. Its size on screen is set by REF_H and the field of view, not by this. */
const DEFAULT_DIST = 9

/**
 * Hangs whatever it is given in front of the camera, always at the same spot and size on screen, whatever the shot.
 * When [panelKey] changes the old card drops away, then the new one lifts in; it also drops away while [visible] is
 * false (any shot but the wide one, so it never covers a walking piece). Children are laid out in reference pixels
 * (1080 tall, centred, y up).
 */
export function Dais({ panelKey, visible, dist = DEFAULT_DIST, offsetX = 0, offsetY = 0, lit = true, children }: { panelKey: string | null; visible: boolean; dist?: number; offsetX?: number; offsetY?: number; lit?: boolean; children: (key: string) => ReactNode }) {
  const { camera } = useThree()
  const group = useRef<THREE.Group>(null)
  const inner = useRef<THREE.Group>(null)
  const grow = useRef(0)
  const [shown, setShown] = useState<string | null>(panelKey)
  const tmp = useMemo(() => new THREE.Vector3(), [])

  useFrame((_, dt) => {
    const g = group.current, i = inner.current
    if (!g || !i) return
    const cam = camera as THREE.PerspectiveCamera
    const want = visible && shown != null && panelKey === shown ? 1 : 0
    grow.current = stepToward(grow.current, want, Math.min(dt, 0.1))
    if (panelKey !== shown && grow.current < 0.02) setShown(panelKey) // the old card has dropped away: swap
    g.visible = grow.current > 0.01
    g.quaternion.copy(cam.quaternion)
    const wpp = worldPerPx(cam.fov, dist, REF_H)
    g.position.copy(cam.position).add(tmp.set(offsetX * wpp, offsetY * wpp, -dist).applyQuaternion(cam.quaternion))
    g.scale.setScalar(wpp * grow.current)
    i.position.y = -(1 - grow.current) * 90
  })

  return (
    <group ref={group} visible={false}>
      {lit && <pointLight position={[0, 140, 420]} intensity={14} distance={6} decay={2} />}
      <group ref={inner}>
        <Suspense fallback={null}>{shown ? children(shown) : null}</Suspense>
      </group>
    </group>
  )
}
