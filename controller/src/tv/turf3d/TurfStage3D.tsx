// controller/src/tv/turf3d/TurfStage3D.tsx
import { Canvas } from '@react-three/fiber'
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import * as THREE from 'three'
import type { TurfTv } from '../types'
import { ensureFonts } from './fonts'
import { QualityContext, parseQuality } from './quality'
import { TurfScene } from './TurfScene'
import { useChoreography } from './useChoreography'

/**
 * The 3D board fills the whole TV stage behind the rails. [children] (the well panel) floats over the middle of the
 * board. Space or Enter skips the animation in progress. A lost WebGL context reports through [onLost] so the caller
 * can fall back to the flat board.
 */
export function TurfStage3D({ g, children, onLost }: { g: TurfTv; children: ReactNode; onLost: () => void }) {
  const [skip, setSkip] = useState(0)
  const [fontsReady, setFontsReady] = useState(false)
  const quality = useMemo(() => parseQuality(location.search), [])
  const craft = useChoreography(g, !!g.quick, skip)

  useEffect(() => { void ensureFonts().then(() => setFontsReady(true)) }, [])
  useEffect(() => {
    const on = (e: KeyboardEvent) => { if (e.key === ' ' || e.key === 'Enter') setSkip((s) => s + 1) }
    window.addEventListener('keydown', on)
    return () => window.removeEventListener('keydown', on)
  }, [])

  return (
    <div className="turf-3d">
      {fontsReady && (
        <Canvas
          shadows
          dpr={[1, 2]}
          camera={{ fov: 38, position: [0, 15.4, 16.9] }}
          gl={{ antialias: true, toneMapping: THREE.ACESFilmicToneMapping }}
          onCreated={({ gl }) => gl.domElement.addEventListener('webglcontextlost', (e) => { e.preventDefault(); onLost() })}
        >
          <QualityContext.Provider value={quality}>
            <TurfScene tv={g} craft={craft} />
          </QualityContext.Provider>
        </Canvas>
      )}
      <div className="turf-banner3d" aria-live="polite">{craft.banner}</div>
      <div className={`turf-well3d ${craft.shot === 'wide' ? '' : 'dim'}`}>{children}</div>
    </div>
  )
}
