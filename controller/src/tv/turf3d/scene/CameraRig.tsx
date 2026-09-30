// controller/src/tv/turf3d/scene/CameraRig.tsx
import { useFrame, useThree } from '@react-three/fiber'
import { useEffect, useRef } from 'react'
import * as THREE from 'three'
import { shotPose, type Shot } from '../camera'
import { spacePos } from '../layout'

/** Eases the camera toward the current shot, with a short shake on each landing. */
export function CameraRig({ shot, focus, landedN }: { shot: Shot; focus: number | null; landedN: number }) {
  const { camera, size } = useThree()
  const look = useRef(new THREE.Vector3(0, 0, 0.5))
  const shake = useRef(0)
  useEffect(() => { if (landedN > 0) shake.current = 0.3 }, [landedN])

  useFrame((_, dt) => {
    const f = focus == null ? null : spacePos(focus)
    const pose = shotPose(shot, f, size.width / Math.max(size.height, 1))
    const k = 1 - Math.pow(0.001, dt)
    camera.position.lerp(new THREE.Vector3(...pose.pos), k * (shot === 'close' ? 0.9 : 0.6))
    look.current.lerp(new THREE.Vector3(...pose.look), k * 0.9)
    const s = shake.current
    shake.current *= Math.pow(0.02, dt)
    camera.position.x += (Math.random() - 0.5) * s
    camera.position.y += (Math.random() - 0.5) * s
    camera.lookAt(look.current)
  })
  return null
}
