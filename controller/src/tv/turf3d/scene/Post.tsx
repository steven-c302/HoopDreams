// controller/src/tv/turf3d/scene/Post.tsx
import { EffectComposer, N8AO, TiltShift2, ToneMapping, Vignette } from '@react-three/postprocessing'
import { ToneMappingMode } from 'postprocessing'
import { useQuality, usesAO, usesComposer } from '../quality'

/** Ambient occlusion (High only), a very light tilt-shift, tone mapping and a vignette. Low renders straight to screen. */
export function Post() {
  const q = useQuality()
  if (!usesComposer(q)) return null
  if (usesAO(q)) {
    return (
      <EffectComposer multisampling={4}>
        <N8AO aoRadius={0.7} intensity={2.6} distanceFalloff={1} />
        <TiltShift2 blur={0.03} />
        <ToneMapping mode={ToneMappingMode.ACES_FILMIC} />
        <Vignette offset={0.3} darkness={0.6} />
      </EffectComposer>
    )
  }
  return (
    <EffectComposer multisampling={4}>
      <TiltShift2 blur={0.03} />
      <ToneMapping mode={ToneMappingMode.ACES_FILMIC} />
      <Vignette offset={0.3} darkness={0.6} />
    </EffectComposer>
  )
}
