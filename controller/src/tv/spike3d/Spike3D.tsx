// THROWAWAY SPIKE (branch turf-3d-spike): four looks for a 3D Home Turf, chosen with ?look=toon|vinyl|classic|luxe.
// Everything is procedural (no downloaded assets, no network).
import { Canvas, useFrame, useThree } from '@react-three/fiber'
import { Environment, Lightformer, MeshReflectorMaterial, Outlines, RoundedBox } from '@react-three/drei'
import { Bloom, EffectComposer, N8AO, TiltShift2, ToneMapping, Vignette } from '@react-three/postprocessing'
import { ToneMappingMode } from 'postprocessing'
import { CuboidCollider, Physics, RigidBody, type RapierRigidBody } from '@react-three/rapier'
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import * as THREE from 'three'
import '@fontsource/anton'
import '@fontsource/rammetto-one'
import { Drink } from '../turf3d/Drinks'
import { DRINK_KINDS } from '../turf3d/drinkSpecs'

export type LookId = 'toon' | 'vinyl' | 'classic' | 'luxe'

const HALF = 6
const CORNER = 1.8
const EDGE_W = (HALF * 2 - CORNER * 2) / 9
const C = HALF - CORNER / 2

/** World centre of space i (0 = Payday, bottom-right, running clockwise seen from the couch). */
function spacePos(i: number): THREE.Vector3 {
  const s = i % 40
  const edge = (k: number) => HALF - CORNER - (k - 0.5) * EDGE_W
  if (s === 0) return new THREE.Vector3(C, 0, C)
  if (s < 10) return new THREE.Vector3(edge(s), 0, C)
  if (s === 10) return new THREE.Vector3(-C, 0, C)
  if (s < 20) return new THREE.Vector3(-C, 0, edge(s - 10))
  if (s === 20) return new THREE.Vector3(-C, 0, -C)
  if (s < 30) return new THREE.Vector3(-HALF + CORNER + (s - 20 - 0.5) * EDGE_W, 0, -C)
  if (s === 30) return new THREE.Vector3(C, 0, -C)
  return new THREE.Vector3(C, 0, -HALF + CORNER + (s - 30 - 0.5) * EDGE_W)
}

const NAMES = ['Payday', 'Taco Truck', 'Group Chat', 'Anna\'s Porch', 'Laundromat', 'Aux Cord', 'Ethan\'s Dorm', 'Plot Twist', 'Night Bus', 'Corner Store',
  'Timeout', 'Alex\'s Backyard', 'Late-Night Diner', 'Wi-Fi', 'Steven\'s Garage', 'Rideshare', 'Karaoke Bar', 'Group Chat', 'Sunhye\'s Studio', 'Kaishun\'s Basement',
  'The Couch', 'Junha\'s Kitchen', 'Plot Twist', 'Amanda\'s Balcony', 'Dive Bar', 'Driver', 'Daniel\'s Place', 'Other Daniel\'s', 'Aux Cord', 'Izzy\'s Rooftop',
  'Go to Timeout', 'Living Room', 'Hot Tub', 'Group Chat', 'The Club', 'Long Walk', 'Plot Twist', 'Penthouse', 'Bar Tab', 'Beach House']
const SET_OF: Record<number, string> = { 1: 'sky', 3: 'sky', 6: 'sky', 8: 'sky', 9: 'sky', 11: 'orange', 13: 'orange', 14: 'orange',
  16: 'pink', 18: 'pink', 19: 'pink', 21: 'red', 23: 'red', 24: 'red', 26: 'yellow', 27: 'yellow', 29: 'yellow',
  31: 'green', 32: 'green', 34: 'green', 37: 'blue', 39: 'blue' }

interface Look {
  bg: string
  chunky: boolean
  toneMapping: THREE.ToneMapping
  set: Record<string, string>
  paper: string; cornerPaper: string; ink: string; text: string; lineW: number
  sticker: string; stickerText: string
  pawns: string[]
  dice: { face: string; pip: string; one: string }
}
const LOOKS: Record<LookId, Look> = {
  toon: {
    bg: '#ffd84a', chunky: true, toneMapping: THREE.NoToneMapping,
    set: { sky: '#5cc8ff', orange: '#ff8a1f', pink: '#ff4fa3', red: '#ff3b30', yellow: '#ffd200', green: '#22c55e', blue: '#2f5bff' },
    paper: '#fff8e6', cornerPaper: '#fff1b8', ink: '#111', text: '#111', lineW: 10,
    sticker: '#ff3b30', stickerText: '#fff',
    pawns: ['#ff3b30', '#2f5bff', '#22c55e', '#ffd200'],
    dice: { face: '#fffdf5', pip: '#111', one: '#ff3b30' },
  },
  vinyl: {
    bg: '#cfeee0', chunky: true, toneMapping: THREE.ACESFilmicToneMapping,
    set: { sky: '#9fdcf5', orange: '#ffb26b', pink: '#ff9ac7', red: '#ff7a70', yellow: '#ffe07a', green: '#8ee0a6', blue: '#8aa4ff' },
    paper: '#fffaf0', cornerPaper: '#ffeec2', ink: '#5b4b6b', text: '#4a3a5c', lineW: 4,
    sticker: '#ff7a70', stickerText: '#fff',
    pawns: ['#ff6b6b', '#5b8cff', '#3ecf7a', '#ffc94a'],
    dice: { face: '#fffaf0', pip: '#5b4b6b', one: '#ff6b6b' },
  },
  classic: {
    bg: '#241710', chunky: false, toneMapping: THREE.ACESFilmicToneMapping,
    set: { sky: '#8fd4f5', orange: '#ff8a2b', pink: '#e64fa0', red: '#e2483d', yellow: '#ffd23f', green: '#3fbf6a', blue: '#2f6bff' },
    paper: '#f4ead2', cornerPaper: '#efe2bd', ink: '#1a1a1a', text: '#1a1a1a', lineW: 6,
    sticker: '#e2483d', stickerText: '#fff',
    pawns: ['#d9d9de', '#c0392b', '#d4a437', '#2c6fbb'],
    dice: { face: '#fbf7ee', pip: '#1a1a1a', one: '#e2483d' },
  },
  luxe: {
    bg: '#050506', chunky: false, toneMapping: THREE.ACESFilmicToneMapping,
    set: { sky: '#5fb7d6', orange: '#d9822b', pink: '#c2417f', red: '#b3261e', yellow: '#e0b93a', green: '#2f9e5b', blue: '#2f56c9' },
    paper: '#101012', cornerPaper: '#17171a', ink: '#c9a24a', text: '#e8cf8a', lineW: 5,
    sticker: '#7a0f16', stickerText: '#e8cf8a',
    pawns: ['#e8cf8a', '#e8cf8a', '#e8cf8a', '#e8cf8a'],
    dice: { face: '#141416', pip: '#e8cf8a', one: '#e8cf8a' },
  },
}
const HEAD_COLORS: Record<LookId, string[] | null> = { toon: null, vinyl: null, classic: null, luxe: ['#e2483d', '#2f6bff', '#2fbf55', '#b57bff'] }

function drawBoard(L: Look): THREE.CanvasTexture {
  const S = 2048, k = S / (HALF * 2)
  const cv = document.createElement('canvas'); cv.width = cv.height = S
  const g = cv.getContext('2d')!
  g.fillStyle = L.paper; g.fillRect(0, 0, S, S)
  g.textAlign = 'center'; g.textBaseline = 'middle'
  for (let i = 0; i < 40; i++) {
    const p = spacePos(i), corner = i % 10 === 0
    const w = corner ? CORNER : EDGE_W, d = CORNER
    const side = i < 10 ? 0 : i < 20 ? 1 : i < 30 ? 2 : 3
    g.save()
    g.translate((p.x + HALF) * k, (p.z + HALF) * k)
    g.rotate(-side * Math.PI / 2)
    g.fillStyle = corner ? L.cornerPaper : L.paper
    g.fillRect(-w * k / 2, -d * k / 2, w * k, d * k)
    g.strokeStyle = L.ink; g.lineWidth = L.lineW
    g.strokeRect(-w * k / 2, -d * k / 2, w * k, d * k)
    if (!corner && SET_OF[i]) { g.fillStyle = L.set[SET_OF[i]]; g.fillRect(-w * k / 2, -d * k / 2, w * k, 0.34 * k); g.strokeRect(-w * k / 2, -d * k / 2, w * k, 0.34 * k) }
    g.fillStyle = L.text
    const words = NAMES[i].split(' ')
    let size = corner ? 64 : 56
    do { g.font = `${size}px Anton, sans-serif`; size -= 2 } while (size > 24 && Math.max(...words.map((wd) => g.measureText(wd.toUpperCase()).width)) > w * k - 10)
    size += 2
    words.forEach((wd, j) => g.fillText(wd.toUpperCase(), 0, (-(words.length - 1) / 2 + j) * (size + 4) + 0.12 * k))
    g.restore()
  }
  g.save(); g.translate(S / 2, S / 2); g.rotate(-Math.PI / 14)
  const SW = 1040, SH = 330
  g.fillStyle = L.sticker; g.fillRect(-SW / 2, -SH / 2, SW, SH)
  g.strokeStyle = L.ink; g.lineWidth = L.lineW * 1.5; g.strokeRect(-SW / 2, -SH / 2, SW, SH)
  g.strokeStyle = L.stickerText; g.lineWidth = 5; g.strokeRect(-SW / 2 + 24, -SH / 2 + 24, SW - 48, SH - 48)
  let fs = 200; do { g.font = `${fs}px "Rammetto One", serif`; fs -= 4 } while (fs > 60 && g.measureText('HOME TURF').width > SW - 190)
  g.fillStyle = L.stickerText; g.fillText('HOME TURF', 0, -22)
  g.font = '44px Anton, sans-serif'; g.fillText('GOOD NEIGHBORS. BAD LANDLORDS.', 0, 92)
  g.restore()
  const t = new THREE.CanvasTexture(cv); t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 16
  return t
}

function drawTable(id: LookId): THREE.CanvasTexture | null {
  if (id === 'luxe') return null
  const cv = document.createElement('canvas'); cv.width = 1024; cv.height = 1024
  const g = cv.getContext('2d')!
  if (id === 'toon') {
    g.fillStyle = '#ffd84a'; g.fillRect(0, 0, 1024, 1024); g.fillStyle = '#ffc21a'
    for (let y = 0; y < 1024; y += 32) for (let x = (y / 32) % 2 ? 16 : 0; x < 1024; x += 32) { g.beginPath(); g.arc(x, y, 6, 0, Math.PI * 2); g.fill() }
  } else if (id === 'vinyl') {
    g.fillStyle = '#cfeee0'; g.fillRect(0, 0, 1024, 1024)
  } else {
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
  }
  const t = new THREE.CanvasTexture(cv); t.colorSpace = THREE.SRGBColorSpace; t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(3, 3); t.anisotropy = 8
  return t
}

const toonGradient = (() => {
  const t = new THREE.DataTexture(new Uint8Array([70, 150, 255]), 3, 1, THREE.RedFormat)
  t.minFilter = t.magFilter = THREE.NearestFilter; t.needsUpdate = true; return t
})()

const PIP: number[][][] = [[], [[.5, .5]], [[.25, .25], [.75, .75]], [[.25, .25], [.5, .5], [.75, .75]],
  [[.25, .25], [.75, .25], [.25, .75], [.75, .75]], [[.25, .25], [.75, .25], [.5, .5], [.25, .75], [.75, .75]],
  [[.25, .25], [.75, .25], [.25, .5], [.75, .5], [.25, .75], [.75, .75]]]
function dieMaterials(id: LookId): THREE.Material[] {
  const L = LOOKS[id]
  return [3, 4, 1, 6, 2, 5].map((n) => {
    const cv = document.createElement('canvas'); cv.width = cv.height = 128
    const g = cv.getContext('2d')!
    g.fillStyle = L.dice.face; g.fillRect(0, 0, 128, 128)
    g.fillStyle = n === 1 ? L.dice.one : L.dice.pip
    PIP[n].forEach(([x, y]) => { g.beginPath(); g.arc(x * 128, y * 128, n === 1 ? 17 : 12, 0, Math.PI * 2); g.fill() })
    const map = new THREE.CanvasTexture(cv); map.colorSpace = THREE.SRGBColorSpace
    if (id === 'toon') return new THREE.MeshToonMaterial({ map, gradientMap: toonGradient })
    return new THREE.MeshPhysicalMaterial({ map, roughness: id === 'vinyl' ? 0.4 : 0.22, clearcoat: id === 'vinyl' ? 0.6 : 1, clearcoatRoughness: 0.08 })
  })
}
const FACE_NORMALS = [new THREE.Vector3(1, 0, 0), new THREE.Vector3(-1, 0, 0), new THREE.Vector3(0, 1, 0), new THREE.Vector3(0, -1, 0), new THREE.Vector3(0, 0, 1), new THREE.Vector3(0, 0, -1)]
const FACE_VALUES = [3, 4, 1, 6, 2, 5]

const tween = (ms: number, fn: (t: number) => void) => new Promise<void>((res) => {
  const t0 = performance.now()
  const step = () => { const t = Math.min(1, (performance.now() - t0) / ms); fn(t); t < 1 ? requestAnimationFrame(step) : res() }
  requestAnimationFrame(step)
})
const wait = (ms: number) => new Promise<void>((r) => setTimeout(r, ms))

const SLIM = [[0, 0], [0.34, 0], [0.36, 0.05], [0.3, 0.12], [0.16, 0.3], [0.12, 0.5], [0.2, 0.56], [0.13, 0.6], [0, 0.6]]
const CHUNK = [[0, 0], [0.4, 0], [0.43, 0.06], [0.38, 0.16], [0.3, 0.3], [0.22, 0.48], [0.3, 0.56], [0.2, 0.62], [0, 0.64]]
const profile = (a: number[][]) => a.map(([x, y]) => new THREE.Vector2(x, y))

type Cam = 'wide' | 'dice' | 'follow' | 'close' | 'lineup'
interface Rig { mode: Cam; focus: THREE.Vector3; shake: number }

function CameraRig({ rig }: { rig: React.MutableRefObject<Rig> }) {
  const { camera } = useThree()
  const look = useRef(new THREE.Vector3(0, 0, 0.6))
  useFrame((_, dt) => {
    const r = rig.current, f = r.focus
    ;(window as unknown as { __rigMode: string }).__rigMode = r.mode
    const want = r.mode === 'wide' ? new THREE.Vector3(0, 12.2, 13.4)
      : r.mode === 'dice' ? new THREE.Vector3(0, 8.4, 8.6)
      : r.mode === 'lineup' ? new THREE.Vector3(0, 2.3, 5.6)
      : r.mode === 'follow' ? new THREE.Vector3(f.x * 0.7, 6.2, f.z * 0.7 + 6.6)
      : new THREE.Vector3(f.x * 0.85, 3.4, f.z * 0.85 + 3.6)
    const wantLook = r.mode === 'wide' ? new THREE.Vector3(0, 0, 0.6) : r.mode === 'lineup' ? new THREE.Vector3(0, 0.45, 1.2) : r.mode === 'dice' ? new THREE.Vector3(0, 0, 0) : f.clone()
    const k = 1 - Math.pow(0.001, dt)
    camera.position.lerp(want, k * (r.mode === 'close' ? 0.9 : 0.6))
    look.current.lerp(wantLook, k * 0.9)
    const sh = r.shake; r.shake *= Math.pow(0.02, dt)
    camera.position.x += (Math.random() - 0.5) * sh; camera.position.y += (Math.random() - 0.5) * sh
    camera.lookAt(look.current)
  })
  return null
}

/** One surface, shaded per look. [kind] picks the finish; metal reads best with the luxe/classic reflections. */
function Surface({ id, color, kind }: { id: LookId; color: string; kind: 'piece' | 'house' | 'slab' }) {
  if (id === 'toon') return <><meshToonMaterial color={color} gradientMap={toonGradient} /><Outlines thickness={0.035} color="#111" /></>
  if (id === 'vinyl') return <meshPhysicalMaterial color={color} roughness={0.42} clearcoat={1} clearcoatRoughness={0.18} sheen={0.6} sheenColor="#fff" />
  if (id === 'classic') return kind === 'piece'
    ? <meshPhysicalMaterial color={color} metalness={0.95} roughness={0.22} clearcoat={0.4} envMapIntensity={1.6} />
    : <meshPhysicalMaterial color={color} roughness={kind === 'slab' ? 0.6 : 0.45} clearcoat={kind === 'slab' ? 0.3 : 0.5} />
  return kind === 'piece'
    ? <meshPhysicalMaterial color={color} metalness={1} roughness={0.12} envMapIntensity={2.2} />
    : <meshPhysicalMaterial color={kind === 'slab' ? '#0b0b0d' : color} roughness={0.18} clearcoat={1} clearcoatRoughness={0.05} metalness={kind === 'slab' ? 0.2 : 0} />
}

function Pawn({ id, color, head, group }: { id: LookId; color: string; head: string | null; group: React.RefObject<THREE.Group | null> }) {
  const L = LOOKS[id], prof = useMemo(() => profile(L.chunky ? CHUNK : SLIM), [L.chunky])
  const hr = L.chunky ? 0.27 : 0.2, hy = L.chunky ? 0.78 : 0.72
  return (
    <group ref={group}>
      <mesh castShadow><latheGeometry args={[prof, 48]} /><Surface id={id} color={color} kind="piece" /></mesh>
      <mesh castShadow position={[0, hy, 0]}><sphereGeometry args={[hr, 40, 28]} /><Surface id={id} color={head ?? color} kind="piece" /></mesh>
    </group>
  )
}

function House({ id, p, hotel }: { id: LookId; p: THREE.Vector3; hotel?: boolean }) {
  const fat = LOOKS[id].chunky ? 1.25 : 1
  return (
    <group position={[p.x, 0, p.z]} scale={fat}>
      <mesh castShadow position={[0, 0.16, 0]}><boxGeometry args={[hotel ? 0.6 : 0.3, 0.32, 0.3]} /><Surface id={id} color={hotel ? '#e2483d' : '#2fbf55'} kind="house" /></mesh>
      <mesh castShadow position={[0, 0.4, 0]} rotation={[0, Math.PI / 4, 0]}><coneGeometry args={[hotel ? 0.42 : 0.24, 0.24, 4]} /><Surface id={id} color={hotel ? '#8a2a22' : '#1f8a3f'} kind="house" /></mesh>
    </group>
  )
}

function Lights({ id }: { id: LookId }) {
  const shadow = { 'shadow-mapSize': [2048, 2048] as [number, number], 'shadow-camera-left': -9, 'shadow-camera-right': 9, 'shadow-camera-top': 9, 'shadow-camera-bottom': -9, 'shadow-bias': -0.0004 }
  if (id === 'toon') return <>
    <directionalLight castShadow position={[-6, 12, 6]} intensity={2.6} shadow-radius={1} {...shadow} />
    <ambientLight intensity={1.1} />
  </>
  if (id === 'vinyl') return <>
    <Environment resolution={256} environmentIntensity={0.9}>
      <Lightformer form="rect" intensity={3} position={[0, 9, 2]} scale={[16, 8, 1]} rotation-x={Math.PI / 2} />
      <Lightformer form="rect" intensity={1.5} position={[-9, 3, 4]} scale={[10, 5, 1]} rotation-y={Math.PI / 2} color="#ffe9d6" />
      <Lightformer form="rect" intensity={1.2} position={[9, 4, -4]} scale={[10, 5, 1]} rotation-y={-Math.PI / 2} color="#dcefff" />
    </Environment>
    <directionalLight castShadow position={[-5, 12, 7]} intensity={1.5} shadow-radius={9} {...shadow} />
    <ambientLight intensity={0.5} />
  </>
  if (id === 'classic') return <>
    <Environment resolution={256} environmentIntensity={0.6}>
      <Lightformer form="rect" intensity={4} position={[0, 8, 2]} scale={[14, 6, 1]} rotation-x={Math.PI / 2} />
      <Lightformer form="rect" intensity={2} position={[-9, 3, 4]} scale={[8, 4, 1]} rotation-y={Math.PI / 2} color="#ffe2b8" />
      <Lightformer form="rect" intensity={1.5} position={[9, 4, -4]} scale={[8, 4, 1]} rotation-y={-Math.PI / 2} />
    </Environment>
    <spotLight castShadow position={[-3, 15, 6]} angle={0.62} penumbra={0.85} intensity={420} color="#ffd9a6" shadow-mapSize={[2048, 2048]} shadow-bias={-0.0003} shadow-radius={6} />
    <ambientLight intensity={0.18} />
  </>
  return <>
    <Environment resolution={256} environmentIntensity={1.1}>
      <Lightformer form="rect" intensity={9} position={[0, 9, -2]} scale={[14, 5, 1]} rotation-x={Math.PI / 2} color="#fff1cf" />
      <Lightformer form="ring" intensity={7} position={[-9, 4, 4]} scale={6} rotation-y={Math.PI / 2} color="#ffd9a0" />
      <Lightformer form="rect" intensity={5} position={[10, 3, 6]} scale={[8, 4, 1]} rotation-y={-Math.PI / 2} color="#9ec5ff" />
    </Environment>
    <spotLight castShadow position={[0, 16, 3]} angle={0.5} penumbra={0.7} intensity={1500} color="#ffe6b8" shadow-mapSize={[2048, 2048]} shadow-bias={-0.0003} shadow-radius={4} />
    <ambientLight intensity={0.14} />
  </>
}

function Post({ id }: { id: LookId }) {
  if (id === 'toon') return null
  if (id === 'vinyl') return <EffectComposer multisampling={4}><N8AO aoRadius={0.6} intensity={2.2} distanceFalloff={1} /><TiltShift2 blur={0.05} /><ToneMapping mode={ToneMappingMode.ACES_FILMIC} /><Vignette offset={0.35} darkness={0.35} /></EffectComposer>
  if (id === 'classic') return <EffectComposer multisampling={4}><N8AO aoRadius={0.7} intensity={2.6} distanceFalloff={1} /><TiltShift2 blur={0.03} /><ToneMapping mode={ToneMappingMode.ACES_FILMIC} /><Vignette offset={0.3} darkness={0.6} /></EffectComposer>
  return <EffectComposer multisampling={4}><N8AO aoRadius={0.8} intensity={3} distanceFalloff={1} /><Bloom mipmapBlur luminanceThreshold={0.85} intensity={0.55} /><ToneMapping mode={ToneMappingMode.ACES_FILMIC} /><Vignette offset={0.25} darkness={0.85} /></EffectComposer>
}

function Table({ id, tex }: { id: LookId; tex: THREE.Texture | null }): ReactNode {
  return (
    <mesh rotation-x={-Math.PI / 2} position={[0, -0.42, 0]} receiveShadow>
      <planeGeometry args={[80, 80]} />
      {id === 'toon' ? <meshToonMaterial map={tex} gradientMap={toonGradient} />
        : id === 'vinyl' ? <meshPhysicalMaterial map={tex} roughness={0.7} />
        : id === 'classic' ? <meshStandardMaterial map={tex} roughness={0.55} metalness={0.05} />
        : <MeshReflectorMaterial color="#14141a" blur={[260, 80]} resolution={1024} mixBlur={1} mixStrength={38} mirror={0.75} roughness={0.9} depthScale={1.1} minDepthThreshold={0.4} maxDepthThreshold={1.3} metalness={0.4} />}
    </mesh>
  )
}

function Scene({ id, api }: { id: LookId; api: React.MutableRefObject<{ roll: () => void; busy: boolean } | null> }) {
  const L = LOOKS[id]
  const boardTex = useMemo(() => drawBoard(L), [id])
  const tableTex = useMemo(() => drawTable(id), [id])
  const dieMats = useMemo(() => dieMaterials(id), [id])
  const dice = useRef<(RapierRigidBody | null)[]>([null, null])
  const pawns = [useRef<THREE.Group>(null), useRef<THREE.Group>(null), useRef<THREE.Group>(null), useRef<THREE.Group>(null), useRef<THREE.Group>(null), useRef<THREE.Group>(null)]
  const ring = useRef<THREE.Mesh>(null)
  const pulse = useRef<THREE.Mesh>(null)
  const lift = useRef<THREE.Mesh>(null)
  const rig = useRef<Rig>({ mode: 'wide', focus: new THREE.Vector3(), shake: 0 })
  const at = useRef([0, 0, 0, 0, 0, 0])
  const turn = useRef(0)
  const [banner, setBanner] = useState<string | null>(null)
  const houses = useMemo(() => [[1, 0], [1, 1], [3, 0], [21, 0], [23, 0], [37, 1], [39, 0]] as const, [])

  const slot = (p: number, i: number) => { const s = spacePos(i), n = at.current.filter((a, k) => a === i && k < p).length; return s.add(new THREE.Vector3((n % 3) * 0.3 - 0.3, 0, Math.floor(n / 3) * 0.42 - 0.2)) }
  useEffect(() => {
    pawns.forEach((r, p) => r.current?.position.copy(slot(p, 0)))
    if (new URLSearchParams(location.search).get('view') === 'lineup') {
      pawns.forEach((r, p) => r.current?.position.set(-3.1 + p * 1.24, 0.001, 1.2))
      rig.current.mode = 'lineup'
    }
  }, [])

  useEffect(() => {
    api.current = {
      busy: false,
      roll: async () => {
        const a = api.current!; if (a.busy) return; a.busy = true
        const p = turn.current, pawn = pawns[p].current!
        rig.current.mode = 'dice'; await wait(650)
        dice.current.forEach((d, k) => {
          if (!d) return
          d.setTranslation({ x: 2.6, y: 1.6 + k * 0.7, z: 0.6 + k * 0.9 }, true)
          d.setRotation(new THREE.Quaternion().setFromEuler(new THREE.Euler(Math.random() * 6, Math.random() * 6, Math.random() * 6)), true)
          d.setLinvel({ x: -7 - Math.random() * 2, y: 1, z: -1.5 + Math.random() * 3 }, true)
          d.setAngvel({ x: (Math.random() - 0.5) * 40, y: (Math.random() - 0.5) * 40, z: (Math.random() - 0.5) * 40 }, true)
        })
        let still = 0
        await wait(700)
        while (still < 8) {
          await wait(60)
          const moving = dice.current.some((d) => { if (!d) return false; const v = d.linvel(), w = d.angvel(); return Math.hypot(v.x, v.y, v.z) + Math.hypot(w.x, w.y, w.z) > 0.08 })
          still = moving ? 0 : still + 1
        }
        const vals = dice.current.map((d) => {
          const q = d!.rotation(), qq = new THREE.Quaternion(q.x, q.y, q.z, q.w)
          let best = 0, by = -2
          FACE_NORMALS.forEach((n, i) => { const y = n.clone().applyQuaternion(qq).y; if (y > by) { by = y; best = i } })
          return FACE_VALUES[best]
        })
        const total = vals[0] + vals[1]
        setBanner(`${vals[0]} + ${vals[1]} = ${total}`); await wait(900); setBanner(null)
        rig.current.mode = 'follow'
        const from = at.current[p], dest = (from + total) % 40
        for (let s = 1; s <= total; s++) {
          const left = total - s, a0 = pawn.position.clone(), a1 = slot(p, (from + s) % 40)
          const last = left === 0
          const ms = left >= 3 ? 230 : left === 2 ? 320 : left === 1 ? 460 : 820
          const hop = last ? 1.5 : left < 3 ? 0.95 : 0.7
          if (left <= 2) { rig.current.mode = 'close'; if (ring.current) { ring.current.visible = true; ring.current.position.copy(spacePos(dest)).setY(0.02) } }
          rig.current.focus.copy(a1)
          await tween(ms, (t) => {
            const e = last ? t * t * (3 - 2 * t) : t
            pawn.position.lerpVectors(a0, a1, e); pawn.position.y = Math.sin(Math.PI * t) * hop
            const stretch = 1 + Math.sin(Math.PI * t) * 0.18
            pawn.scale.set(1 / Math.sqrt(stretch), stretch, 1 / Math.sqrt(stretch)); pawn.rotation.z = Math.sin(Math.PI * t) * 0.22
            if (ring.current) { const s2 = 1 + Math.sin(performance.now() / 110) * 0.12; ring.current.scale.set(s2, 1, s2) }
          })
          pawn.scale.set(1.12, 0.82, 1.12); pawn.position.y = 0; await wait(last ? 0 : 20)
          pawn.scale.set(1, 1, 1); pawn.rotation.z = 0
        }
        at.current[p] = dest
        if (ring.current) ring.current.visible = false
        if (pulse.current) { pulse.current.position.copy(spacePos(dest)).setY(0.03); pulse.current.visible = true }
        if (lift.current) { lift.current.position.copy(spacePos(dest)); lift.current.visible = true }
        rig.current.shake = 0.35
        void tween(900, (t) => { pawn.rotation.z = Math.exp(-5 * t) * Math.sin(t * 26) * 0.2; if (t === 1) pawn.rotation.z = 0 })
        await Promise.all([
          tween(700, (t) => { if (pulse.current) { const s = 0.5 + t * 4.5; pulse.current.scale.set(s, s, 1); (pulse.current.material as THREE.MeshBasicMaterial).opacity = 0.9 * (1 - t) } }),
          tween(700, (t) => { if (lift.current) lift.current.position.y = Math.sin(Math.PI * t) * 0.35 }),
        ])
        if (pulse.current) pulse.current.visible = false
        if (lift.current) lift.current.visible = false
        rig.current.mode = 'wide'; turn.current = (p + 1) % 6; await wait(500); a.busy = false
      },
    }
  }, [])

  const heads = HEAD_COLORS[id]
  return (
    <>
      <color attach="background" args={[L.bg]} />
      <CameraRig rig={rig} />
      <Lights id={id} />
      <Table id={id} tex={tableTex} />
      <RoundedBox args={[HALF * 2 + 0.7, 0.42, HALF * 2 + 0.7]} radius={0.07} smoothness={4} position={[0, -0.21, 0]} castShadow receiveShadow>
        <Surface id={id} color={id === 'luxe' ? '#0b0b0d' : L.paper} kind="slab" />
      </RoundedBox>
      <mesh rotation-x={-Math.PI / 2} position={[0, 0.003, 0]} receiveShadow>
        <planeGeometry args={[HALF * 2, HALF * 2]} />
        {id === 'toon' ? <meshBasicMaterial map={boardTex} toneMapped={false} />
          : id === 'luxe' ? <meshPhysicalMaterial map={boardTex} roughness={0.28} clearcoat={1} clearcoatRoughness={0.04} metalness={0.15} />
          : <meshPhysicalMaterial map={boardTex} roughness={0.6} clearcoat={id === 'vinyl' ? 0.5 : 0.15} />}
      </mesh>

      {houses.map(([i, n], k) => <House key={k} id={id} p={spacePos(i).add(new THREE.Vector3(n * 0.38 - 0.19, 0, -0.25))} hotel={i === 39 || i === 23} />)}
      {pawns.map((r, p) => id === 'classic'
        ? <group key={p} ref={r}><Drink kind={DRINK_KINDS[p]} color={['#e2483d', '#2f6bff', '#2fbf55', '#ffd23f', '#b57bff', '#ff8a2b'][p]} /></group>
        : p < 4 ? <Pawn key={p} id={id} color={L.pawns[p]} head={heads ? heads[p] : null} group={r} /> : <group key={p} ref={r} />)}

      <mesh ref={ring} rotation-x={-Math.PI / 2} visible={false}><ringGeometry args={[0.42, 0.56, 48]} /><meshBasicMaterial color={id === 'luxe' ? '#ffe6a0' : '#ffd23f'} toneMapped={false} /></mesh>
      <mesh ref={pulse} rotation-x={-Math.PI / 2} visible={false}><ringGeometry args={[0.4, 0.55, 48]} /><meshBasicMaterial color="#fff" transparent toneMapped={false} /></mesh>
      <mesh ref={lift} visible={false}><boxGeometry args={[0.9, 0.06, 1.4]} /><meshStandardMaterial color="#ffd23f" emissive="#ffb000" emissiveIntensity={0.6} /></mesh>

      <Physics gravity={[0, -22, 0]}>
        <RigidBody type="fixed" colliders={false} friction={0.8} restitution={0.25}>
          <CuboidCollider args={[HALF, 0.2, HALF]} position={[0, -0.2, 0]} />
          {[[3.3, 0], [-3.3, 0], [0, 3.3], [0, -3.3]].map(([x, z], i) => <CuboidCollider key={i} args={i < 2 ? [0.2, 2, 3.5] : [3.5, 2, 0.2]} position={[x, 2, z]} />)}
        </RigidBody>
        {[0, 1].map((k) => (
          <RigidBody key={k} ref={(r) => { dice.current[k] = r }} position={[8, 6 + k, 8]} colliders="cuboid" restitution={0.35} friction={0.7} linearDamping={0.15} angularDamping={0.2}>
            <mesh castShadow material={dieMats}>
              <boxGeometry args={[0.7, 0.7, 0.7]} />
              {id === 'toon' && <Outlines thickness={0.03} color="#111" />}
            </mesh>
          </RigidBody>
        ))}
      </Physics>
      <Post id={id} />
      {banner && <BannerHtml text={banner} />}
    </>
  )
}

function BannerHtml({ text }: { text: string }) {
  useEffect(() => { const el = document.getElementById('spike-banner'); if (el) el.textContent = text; return () => { if (el) el.textContent = '' } }, [text])
  return null
}

export function Spike3D() {
  const [ready, setReady] = useState(false)
  useEffect(() => { Promise.all([document.fonts.load('56px Anton'), document.fonts.load('180px "Rammetto One"')]).catch(() => undefined).then(() => setReady(true)) }, [])
  const api = useRef<{ roll: () => void; busy: boolean } | null>(null)
  const raw = new URLSearchParams(location.search).get('look')
  const id: LookId = raw === 'toon' || raw === 'vinyl' || raw === 'luxe' ? raw : 'classic'
  return (
    <div style={{ position: 'fixed', inset: 0, background: LOOKS[id].bg }}>
      {ready && <Canvas key={id} shadows dpr={[1, 2]} camera={{ fov: 38, position: [0, 12.2, 13.4] }} gl={{ antialias: true, toneMapping: LOOKS[id].toneMapping }}>
        <Scene id={id} api={api} />
      </Canvas>}
      <div id="spike-banner" style={{ position: 'absolute', top: '8%', left: 0, right: 0, textAlign: 'center', font: '900 120px system-ui', color: '#fff', textShadow: '0 6px 0 #1a1a1a', pointerEvents: 'none' }} />
      <button onClick={() => api.current?.roll()} style={{ position: 'absolute', top: 20, right: 20, font: '900 30px system-ui', padding: '10px 30px', border: '4px solid #1a1a1a', background: '#ffd23f', borderRadius: 14, cursor: 'pointer' }}>ROLL</button>
    </div>
  )
}
