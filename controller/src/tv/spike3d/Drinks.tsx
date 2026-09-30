// THROWAWAY SPIKE (branch turf-3d-spike): Home Turf's six pieces as drinks, built from scratch with lathe geometry.
// Labels are generic on purpose (no brand names or logos), the same IP rule as the rest of Home Turf.
import { useMemo } from 'react'
import * as THREE from 'three'

export const DRINK_KINDS = ['soju', 'vodka', 'beer', 'can', 'shot', 'cup'] as const
export type DrinkKind = (typeof DRINK_KINDS)[number]

const FAST = new URLSearchParams(location.search).get('glass') === 'fast'
const v = (pts: number[][]) => pts.map(([x, y]) => new THREE.Vector2(x, y))
/** Smooth (smoothstep) run from radius r0 at y0 to r1 at y1, for bottle shoulders. */
const ease = (r0: number, r1: number, y0: number, y1: number, n = 10) =>
  Array.from({ length: n }, (_, i) => { const t = (i + 1) / n, e = t * t * (3 - 2 * t); return [r0 + (r1 - r0) * e, y0 + (y1 - y0) * t] })

interface BottleCfg { r: number; body: number; neck: number; shoulder: number; top: number; glass: string; att: string; label: { y0: number; y1: number; bg: string; accent: string; text: string; sub: string; ink: string }; cap: { color: string; metal: boolean } }
const BOTTLES: Record<'soju' | 'vodka' | 'beer', BottleCfg> = {
  soju: { r: 0.27, body: 0.5, neck: 0.085, shoulder: 0.74, top: 0.94, glass: '#7fe0a4', att: '#3aa866', label: { y0: 0.1, y1: 0.4, bg: '#f5f5ec', accent: '#2d9a55', text: 'SOJU', sub: 'ORIGINAL', ink: '#1b6b3a' }, cap: { color: '#2d9a55', metal: false } },
  vodka: { r: 0.25, body: 0.56, neck: 0.09, shoulder: 0.84, top: 1.02, glass: '#eef7ff', att: '#d4e8f5', label: { y0: 0.14, y1: 0.52, bg: '#c62828', accent: '#ffffff', text: 'VODKA', sub: 'PREMIUM', ink: '#ffffff' }, cap: { color: '#d7d7de', metal: true } },
  beer: { r: 0.2, body: 0.4, neck: 0.07, shoulder: 0.72, top: 0.96, glass: '#e08a2a', att: '#c46a10', label: { y0: 0.1, y1: 0.34, bg: '#f3e2b8', accent: '#b8341f', text: 'BEER', sub: 'COLD LAGER', ink: '#7a1f12' }, cap: { color: '#c9a227', metal: true } },
}

const fit = (g: CanvasRenderingContext2D, text: string, max: number, start: number) => { let fs = start; do { g.font = `${fs}px Anton, sans-serif`; fs -= 4 } while (fs > 18 && g.measureText(text).width > max); }
function labelTexture(l: BottleCfg['label']): THREE.CanvasTexture {
  const cv = document.createElement('canvas'); cv.width = 512; cv.height = 256
  const g = cv.getContext('2d')!
  g.fillStyle = l.bg; g.fillRect(0, 0, 512, 256)
  g.fillStyle = l.accent; g.fillRect(0, 0, 512, 24); g.fillRect(0, 232, 512, 24)
  g.textAlign = 'center'; g.textBaseline = 'middle'
  // The visible arc from the couch is about a third of the wrap, so keep the name inside ~150px and print it front and back.
  for (const cx of [128, 384]) {
    g.strokeStyle = l.ink; g.lineWidth = 4; g.beginPath(); g.ellipse(cx, 128, 84, 76, 0, 0, Math.PI * 2); g.stroke()
    g.fillStyle = l.ink; fit(g, l.text, 124, 96); g.fillText(l.text, cx, 112)
    g.fillRect(cx - 52, 150, 104, 4)
    fit(g, l.sub, 120, 30); g.fillText(l.sub, cx, 176)
  }
  const t = new THREE.CanvasTexture(cv); t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 8
  return t
}
function canTexture(): THREE.CanvasTexture {
  const cv = document.createElement('canvas'); cv.width = 512; cv.height = 256
  const g = cv.getContext('2d')!
  g.fillStyle = '#dcdde2'; g.fillRect(0, 0, 512, 256)
  g.fillStyle = '#c62828'; g.fillRect(0, 56, 512, 144)
  g.fillStyle = '#f5c542'; g.fillRect(0, 48, 512, 8); g.fillRect(0, 200, 512, 8)
  g.textAlign = 'center'; g.textBaseline = 'middle'
  for (const cx of [128, 384]) {
    g.fillStyle = '#fff'; fit(g, 'LAGER', 124, 100); g.fillText('LAGER', cx, 118)
    fit(g, 'COLD & CRISP', 130, 26); g.fillText('COLD & CRISP', cx, 168)
  }
  const t = new THREE.CanvasTexture(cv); t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 8
  return t
}

const Glass = ({ color, att, dist = 0.7, thick = 0.5 }: { color: string; att: string; dist?: number; thick?: number }) => FAST ? (
  <meshPhysicalMaterial color={att} roughness={0.06} clearcoat={1} clearcoatRoughness={0.02} metalness={0.1} envMapIntensity={2} />
) : (
  <meshPhysicalMaterial color={color} transmission={1} thickness={thick} ior={1.45} roughness={0.05} attenuationColor={att} attenuationDistance={dist} clearcoat={1} clearcoatRoughness={0.03} envMapIntensity={1.5} />
)

function Bottle({ kind }: { kind: 'soju' | 'vodka' | 'beer' }) {
  const c = BOTTLES[kind]
  const geo = useMemo(() => v([[0, 0], [c.r - 0.05, 0], [c.r - 0.015, 0.02], [c.r, 0.06], [c.r, c.body], ...ease(c.r, c.neck, c.body, c.shoulder), [c.neck, c.top - 0.03], [c.neck + 0.022, c.top - 0.02], [c.neck + 0.022, c.top], [0, c.top]]), [kind])
  const tex = useMemo(() => labelTexture(c.label), [kind])
  const ly = (c.label.y0 + c.label.y1) / 2
  return (
    <group>
      <mesh castShadow><latheGeometry args={[geo, 56]} /><Glass color={c.glass} att={c.att} dist={kind === 'vodka' ? 3 : 1.4} /></mesh>
      <mesh position={[0, ly, 0]} rotation-y={-Math.PI / 2} castShadow><cylinderGeometry args={[c.r + 0.006, c.r + 0.006, c.label.y1 - c.label.y0, 56, 1, true]} /><meshStandardMaterial map={tex} roughness={0.55} side={THREE.DoubleSide} /></mesh>
      <mesh position={[0, c.top + 0.02, 0]} castShadow><cylinderGeometry args={[c.neck + 0.03, c.neck + 0.03, 0.09, 32]} />{c.cap.metal ? <meshStandardMaterial color={c.cap.color} metalness={1} roughness={0.25} /> : <meshStandardMaterial color={c.cap.color} roughness={0.4} />}</mesh>
    </group>
  )
}

function Can() {
  const tex = useMemo(canTexture, [])
  return (
    <group>
      <mesh position={[0, 0.36, 0]} rotation-y={-Math.PI / 2} castShadow><cylinderGeometry args={[0.26, 0.26, 0.66, 56]} /><meshStandardMaterial map={tex} metalness={0.35} roughness={0.4} envMapIntensity={1.2} /></mesh>
      <mesh position={[0, 0.69, 0]} castShadow><cylinderGeometry args={[0.22, 0.26, 0.05, 56]} /><meshStandardMaterial color="#cfd2d8" metalness={1} roughness={0.25} /></mesh>
      <mesh position={[0, 0.725, 0]}><cylinderGeometry args={[0.2, 0.2, 0.03, 56]} /><meshStandardMaterial color="#dfe2e8" metalness={1} roughness={0.2} /></mesh>
      <mesh position={[0.04, 0.745, 0]} rotation={[Math.PI / 2, 0, 0]}><torusGeometry args={[0.055, 0.014, 12, 24]} /><meshStandardMaterial color="#aeb2ba" metalness={1} roughness={0.25} /></mesh>
    </group>
  )
}

function Shot() {
  const shell = useMemo(() => v([[0, 0], [0.15, 0], [0.17, 0.02], [0.2, 0.1], [0.27, 0.5], [0.245, 0.5], [0.235, 0.47], [0.15, 0.16], [0.0, 0.16]]), [])
  const liquid = useMemo(() => v([[0, 0.16], [0.15, 0.16], [0.22, 0.4], [0, 0.4]]), [])
  return (
    <group>
      <mesh castShadow><latheGeometry args={[shell, 48]} /><Glass color="#f2f9ff" att="#dbeaf5" dist={2} thick={0.12} /></mesh>
      <mesh><latheGeometry args={[liquid, 48]} /><meshPhysicalMaterial color="#d99a2b" roughness={0.1} transmission={0.4} thickness={0.3} emissive="#7a4a08" emissiveIntensity={0.35} /></mesh>
    </group>
  )
}

function Cup() {
  const shell = useMemo(() => v([[0, 0], [0.17, 0], [0.185, 0.02], [0.335, 0.7], [0.345, 0.7], [0.345, 0.72], [0.32, 0.72], [0.31, 0.7], [0.16, 0.06], [0, 0.06]]), [])
  return (
    <group>
      <mesh castShadow><latheGeometry args={[shell, 56]} /><meshPhysicalMaterial color="#d62828" roughness={0.35} clearcoat={0.5} side={THREE.DoubleSide} /></mesh>
      <mesh position={[0, 0.5, 0]}><cylinderGeometry args={[0.245, 0.245, 0.05, 40]} /><meshPhysicalMaterial color="#d99a2b" roughness={0.15} emissive="#7a4a08" emissiveIntensity={0.3} /></mesh>
      <mesh position={[0, 0.535, 0]}><cylinderGeometry args={[0.245, 0.25, 0.03, 40]} /><meshStandardMaterial color="#fff8e8" roughness={0.9} /></mesh>
    </group>
  )
}

/** One drink standing on a coaster in the player's colour (the coaster is how you tell whose piece it is). */
export function Drink({ kind, color }: { kind: DrinkKind; color: string }) {
  return (
    <group>
      <mesh position={[0, 0.03, 0]} castShadow receiveShadow><cylinderGeometry args={[0.44, 0.44, 0.06, 48]} /><meshStandardMaterial color={color} roughness={0.5} /></mesh>
      <mesh position={[0, 0.062, 0]} rotation-x={-Math.PI / 2}><ringGeometry args={[0.34, 0.38, 48]} /><meshBasicMaterial color="#ffffff" transparent opacity={0.55} toneMapped={false} /></mesh>
      <group position={[0, 0.06, 0]}>
        {kind === 'can' ? <Can /> : kind === 'shot' ? <Shot /> : kind === 'cup' ? <Cup /> : <Bottle kind={kind} />}
      </group>
    </group>
  )
}
