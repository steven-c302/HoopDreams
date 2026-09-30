// controller/src/tv/turf3d/TurfStage3D.tsx
import { Canvas } from '@react-three/fiber'
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import * as THREE from 'three'
import type { TurfTv } from '../types'
import { ensureFonts } from './fonts'
import { QualityContext, parseQuality } from './quality'
import { TurfScene } from './TurfScene'
import { FALLBACK_PALETTE, type Palette } from './ui/copy'
import { buildHud, type HudIn } from './ui/hud'
import { PaletteContext, readPalette } from './ui/palette'
import { panelFor } from './ui/panels'
import { useChoreography } from './useChoreography'

/**
 * The 3D board fills the whole TV stage behind the rails. [children] (the well panel) floats over the middle of the
 * board. Space or Enter skips the animation in progress. A lost WebGL context reports through [onLost] so the caller
 * can fall back to the flat board.
 */
export function TurfStage3D({ g, hud, children, onLost }: { g: TurfTv; hud: HudIn; children: ReactNode; onLost: () => void }) {
  const [skip, setSkip] = useState(0)
  const [fontsReady, setFontsReady] = useState(false)
  const quality = useMemo(() => parseQuality(location.search), [])
  const craft = useChoreography(g, !!g.quick, skip)
  const [panelReady, setPanelReady] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const [palette, setPalette] = useState<Palette>(FALLBACK_PALETTE)
  useEffect(() => { if (root.current) setPalette(readPalette(root.current)) }, [])
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => { const id = setInterval(() => setNow(Date.now()), 500); return () => clearInterval(id) }, [])
  const hudNow = useMemo(() => buildHud(g, hud, now), [g, hud, now])

  useEffect(() => { void ensureFonts().then(() => setFontsReady(true)) }, [])
  useEffect(() => {
    const on = (e: KeyboardEvent) => { if (e.key === ' ' || e.key === 'Enter') setSkip((s) => s + 1) }
    window.addEventListener('keydown', on)
    return () => window.removeEventListener('keydown', on)
  }, [])

  return (
    <div className="turf-3d" ref={root}>
      {fontsReady && (
        <Canvas
          shadows
          dpr={[1, 2]}
          camera={{ fov: 38, position: [0, 15.4, 16.9] }}
          gl={{ antialias: true, toneMapping: THREE.ACESFilmicToneMapping }}
          onCreated={({ gl }) => gl.domElement.addEventListener('webglcontextlost', (e) => { e.preventDefault(); onLost() })}
        >
          <QualityContext.Provider value={quality}>
            <PaletteContext.Provider value={palette}>
              <TurfScene tv={g} craft={craft} hud={hudNow} onPanelReady={() => setPanelReady(true)} />
            </PaletteContext.Provider>
          </QualityContext.Provider>
        </Canvas>
      )}
      <div className="turf-banner3d" aria-live="polite">{craft.banner}</div>
      <div className={`turf-well3d ${craft.shot === 'wide' ? '' : 'dim'} ${panelReady && fontsReady && panelFor(g.phase) ? 'hidden3d' : ''}`}>{children}</div>
    </div>
  )
}
