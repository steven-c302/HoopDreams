// controller/src/tv/turf3d/ui/Callouts.tsx
import { useEffect, useMemo } from 'react'
import * as THREE from 'three'
import { resolve, type FlashSpec } from './copy'
import { Dais } from './Dais'
import { Label } from './Label'
import { usePalette } from './palette'
import { INK, SHADOW } from './theme'

/** A spiky star centred on the origin: [spikes] points, inner valleys at [valley] of the outer radius. */
export function starGeometry(w: number, h: number, spikes = 16, valley = 0.78): THREE.ShapeGeometry {
  const s = new THREE.Shape()
  for (let i = 0; i < spikes * 2; i++) {
    const a = (i / (spikes * 2)) * Math.PI * 2, k = i % 2 === 0 ? 1 : valley
    const x = Math.cos(a) * (w / 2) * k, y = Math.sin(a) * (h / 2) * k
    if (i === 0) s.moveTo(x, y); else s.lineTo(x, y)
  }
  s.closePath()
  return new THREE.ShapeGeometry(s)
}

function Star({ w, h, color, x = 0, y = 0, z = 0 }: { w: number; h: number; color: string; x?: number; y?: number; z?: number }) {
  const geo = useMemo(() => starGeometry(w, h), [w, h])
  useEffect(() => () => geo.dispose(), [geo])
  return <mesh geometry={geo} position={[x, y, z]}><meshBasicMaterial color={color} toneMapped={false} /></mesh>
}

/** One callout as a star-burst sticker: the words in the middle, a hard shadow behind. */
function Burst({ f }: { f: FlashSpec }) {
  const p = usePalette()
  const w = f.small ? 440 : 880, h = f.small ? 240 : 420
  const fill = resolve(p, f.fill, '#ffd23f'), ink = resolve(p, f.ink ?? 'var(--ink)', INK)
  return (
    <group rotation-z={0.07}>
      <Star w={w + 24} h={h + 24} color={SHADOW} x={14} y={-14} z={-3} />
      <Star w={w + 24} h={h + 24} color={INK} z={-2} />
      <Star w={w} h={h} color={fill} z={-1} />
      <Label px={f.small ? 72 : 112} kind="hero" color={ink} y={f.sub ? 30 : 0} maxWidth={w * 0.66}>{f.text}</Label>
      {f.sub && <Label px={30} kind="body" font="bodyBold" color={ink} y={f.small ? -50 : -64} maxWidth={w * 0.6}>{f.sub}</Label>}
    </group>
  )
}

/** The flash callout and the total banner, each on its own Dais so they can sit in front of and above the card. */
export function Callouts({ flash, banner }: { flash: FlashSpec | null; banner: string | null }) {
  return (
    <>
      <Dais panelKey={flash ? String(flash.id) : null} visible dist={8.4} offsetY={flash?.small ? 250 : 0}>
        {() => (flash ? <Burst f={flash} /> : null)}
      </Dais>
      <Dais panelKey={banner} visible dist={8.7} offsetY={380}>
        {(text) => <Label px={128} kind="hero" color="#ffffff" outline={INK} maxWidth={1500}>{text}</Label>}
      </Dais>
    </>
  )
}
