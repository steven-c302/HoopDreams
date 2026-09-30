// controller/src/tv/turf3d/ui/TimerRing.tsx
import { Text } from '@react-three/drei'
import { useFrame } from '@react-three/fiber'
import { useRef } from 'react'
import * as THREE from 'three'
import { Plate } from './Card'
import { timerFraction, timerLeft, type TimerSpec } from './hud'
import { FONT, GOLD, INK, PAPER, RED } from './theme'

interface TroikaText { text: string; sync: () => void }

/** A countdown pie in a ring: the wedge drains from the top as the phase timer runs out, with the seconds in the middle. */
export function TimerRing({ timer, r = 34, x = 0, y = 0 }: { timer: TimerSpec; r?: number; x?: number; y?: number }) {
  const wedge = useRef<THREE.Mesh>(null)
  const mat = useRef<THREE.MeshBasicMaterial>(null)
  const secs = useRef<TroikaText | null>(null)
  const last = useRef({ step: -1, s: -1 })
  const spec = useRef(timer)
  spec.current = timer
  useFrame(() => {
    const now = Date.now(), t = spec.current
    const frac = timerFraction(t, now), s = Math.ceil(timerLeft(t, now) / 1000)
    const step = Math.round(frac * 90)
    if (step !== last.current.step && wedge.current) {
      last.current.step = step
      wedge.current.geometry.dispose()
      wedge.current.geometry = new THREE.CircleGeometry(r - 7, 48, Math.PI / 2, Math.PI * 2 * Math.max(frac, 0.0001))
      mat.current?.color.set(frac < 0.25 ? RED : GOLD)
    }
    if (s !== last.current.s && secs.current) { last.current.s = s; secs.current.text = String(s); secs.current.sync() }
  })
  return (
    <group position={[x, y, 0]}>
      <Plate w={r * 2 + 6} h={r * 2 + 6} r={r + 3} color={INK} z={1} />
      <Plate w={r * 2} h={r * 2} r={r} color={PAPER} z={2} />
      <mesh ref={wedge} position={[0, 0, 3]}>
        <circleGeometry args={[r - 7, 48, Math.PI / 2, Math.PI * 2]} />
        <meshBasicMaterial ref={mat} color={GOLD} toneMapped={false} />
      </mesh>
      <Text ref={secs as never} font={FONT.display} fontSize={28} color={INK} anchorX="center" anchorY="middle" position={[0, -1, 4]} outlineWidth={2} outlineColor={PAPER}>{String(Math.ceil(timerLeft(timer, Date.now()) / 1000))}</Text>
    </group>
  )
}
