// controller/src/tv/turf3d/ui/FaceIcon.tsx
import { createElement, useEffect, useState } from 'react'
import * as THREE from 'three'
import { inlineVars, photoCircle, svgDataUrl } from './faceTexture'
import { usePalette } from './palette'

const load = (src: string) => new Promise<HTMLImageElement>((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = rej; i.src = src })

/**
 * A player's avatar: the existing SVG Face, rendered to markup (dynamic import, so nothing else pays for it), its CSS
 * variables replaced from the palette, drawn to a canvas; a photo face gets its picture painted on top, clipped to the
 * face circle. Anything that fails leaves the preset (or nothing): the panel never depends on it.
 */
export function FaceIcon({ face, color, size = 48, x = 0, y = 0 }: { face: string; color: string; size?: number; x?: number; y?: number }) {
  const p = usePalette()
  const [tex, setTex] = useState<THREE.CanvasTexture | null>(null)
  useEffect(() => {
    let alive = true
    let made: THREE.CanvasTexture | null = null
    const S = 192
    void (async () => {
      const [{ renderToStaticMarkup }, mod] = await Promise.all([import('react-dom/server'), import('../../../theme/Face')])
      const markup = renderToStaticMarkup(createElement(mod.Face, { face, color, size: S })).replace('<svg ', '<svg xmlns="http://www.w3.org/2000/svg" ')
      const img = await load(svgDataUrl(inlineVars(markup, p)))
      const cv = document.createElement('canvas')
      cv.width = cv.height = S
      const g = cv.getContext('2d')
      if (!g) return
      g.drawImage(img, 0, 0, S, S)
      const pid = mod.photoId(face)
      if (pid) {
        try {
          const photo = await load(mod.photoUrl(pid))
          const c = photoCircle(S)
          g.save(); g.beginPath(); g.arc(c.cx, c.cy, c.r, 0, Math.PI * 2); g.clip(); g.drawImage(photo, c.x, c.y, c.w, c.w); g.restore()
        } catch { /* the preset shows through */ }
      }
      if (!alive) return
      made = new THREE.CanvasTexture(cv)
      made.colorSpace = THREE.SRGBColorSpace
      setTex(made)
    })().catch(() => undefined)
    return () => { alive = false; made?.dispose() }
  }, [face, color, p])
  if (!tex) return null
  return (
    <mesh position={[x, y, 2]}>
      <planeGeometry args={[size, size]} />
      <meshBasicMaterial map={tex} transparent toneMapped={false} />
    </mesh>
  )
}
