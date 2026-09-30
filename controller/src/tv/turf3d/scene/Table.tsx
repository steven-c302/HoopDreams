// controller/src/tv/turf3d/scene/Table.tsx
import { useEffect, useMemo } from 'react'
import * as THREE from 'three'

function woodTexture(): THREE.CanvasTexture {
  const cv = document.createElement('canvas'); cv.width = 1024; cv.height = 1024
  const g = cv.getContext('2d')!
  g.fillStyle = '#7a4b2e'; g.fillRect(0, 0, 1024, 1024)
  for (let i = 0; i < 260; i++) {
    const y = Math.random() * 1024, a = Math.random() * 0.18
    g.strokeStyle = Math.random() < 0.5 ? `rgba(40,20,8,${a})` : `rgba(200,140,90,${a})`
    g.lineWidth = 1 + Math.random() * 3
    g.beginPath(); g.moveTo(0, y)
    for (let x = 0; x <= 1024; x += 64) g.lineTo(x, y + Math.sin(x * 0.01 + i) * 4)
    g.stroke()
  }
  for (let p = 0; p < 4; p++) { g.fillStyle = 'rgba(0,0,0,.35)'; g.fillRect(0, p * 256, 1024, 3) }
  const t = new THREE.CanvasTexture(cv)
  t.colorSpace = THREE.SRGBColorSpace; t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(3, 3); t.anisotropy = 8
  return t
}

export function Table() {
  const tex = useMemo(woodTexture, [])
  useEffect(() => () => tex.dispose(), [tex])
  return (
    <mesh rotation-x={-Math.PI / 2} position={[0, -0.42, 0]} receiveShadow>
      <planeGeometry args={[80, 80]} />
      <meshStandardMaterial map={tex} roughness={0.55} metalness={0.05} />
    </mesh>
  )
}
