import { motion, type HTMLMotionProps } from 'motion/react'
import { useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import confetti from 'canvas-confetti'
import { Face } from '../theme/Face'
import type { Avatar, PlayerSummary } from '../protocol'

/**
 * The "Saturday Morning" kit: ink-outlined props, speech bubbles, comic bursts, the host, the clock.
 * Every colour is a token from theme/tokens.css; C only names them for inline styles and SVG fills.
 */
export const C = {
  ink: 'var(--ink)', inkSoft: 'var(--ink-soft)', paper: 'var(--paper)', paper2: 'var(--paper-2)', white: 'var(--white)',
  sun: 'var(--sun)', tomato: 'var(--tomato)', blueberry: 'var(--blueberry)', lime: 'var(--lime)', grape: 'var(--grape)',
  bubblegum: 'var(--bubblegum)', tangerine: 'var(--tangerine)', sky: 'var(--sky)', felt: 'var(--felt)', feltDeep: 'var(--felt-deep)',
  mahogany: 'var(--mahogany)', right: 'var(--right)', wrong: 'var(--wrong)',
}

export const ANSWER_COLOR: Record<string, string> = { a: 'var(--ans-a)', b: 'var(--ans-b)', c: 'var(--ans-c)', d: 'var(--ans-d)' }

/** Ink or white text, whichever reads better on a team's hex colour. */
export function inkOn(hex: string): string {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex)
  if (!m) return C.ink
  const n = parseInt(m[1], 16)
  const lum = (0.2126 * ((n >> 16) & 255) + 0.7152 * ((n >> 8) & 255) + 0.0722 * (n & 255)) / 255
  return lum > 0.5 ? C.ink : C.white
}

// ---------- scene ----------

/** A comic panel filling the stage: flat colour, halftone dots, thick ink border, ink gutter around it. */
export function Scene({ color = C.sun, split, children, className = '' }: { color?: string; split?: [string, string]; children?: ReactNode; className?: string }) {
  return (
    <div className="scene-gutter">
      <div className={`scene ${className}`} style={{ '--scene': color } as CSSProperties}>
        {split && <><div className="scene-half left" style={{ '--scene': split[0] } as CSSProperties} /><div className="scene-half right" style={{ '--scene': split[1] } as CSSProperties} /></>}
        <div className="scene-dots" />
        <div className="scene-content">{children}</div>
      </div>
    </div>
  )
}

// ---------- props ----------

/** Flat fill, ink outline, hard offset shadow, set slightly askew. */
export function Panel({ fill = C.paper, shadow = C.ink, tilt = 0, className = '', style, children, ...rest }: HTMLMotionProps<'div'> & { fill?: string; shadow?: string; tilt?: number }) {
  return (
    <motion.div className={`panel ${className}`} style={{ background: fill, boxShadow: `var(--shadow-tv) var(--shadow-tv) 0 ${shadow}`, rotate: tilt, ...style }} {...rest}>
      {children}
    </motion.div>
  )
}

/** A speech bubble with a tail pointing [tail]. */
export function Bubble({ children, tail = 'left', fill = C.white, className = '', style }: { children: ReactNode; tail?: 'left' | 'down' | 'none'; fill?: string; className?: string; style?: CSSProperties }) {
  return (
    <div className={`bubble tail-${tail} ${className}`} style={{ '--bubble': fill, ...style } as CSSProperties}>
      {children}
    </div>
  )
}

export const Chip = ({ children, fill = C.ink, ink = C.sun, style }: { children: ReactNode; fill?: string; ink?: string; style?: CSSProperties }) =>
  <span className="chip-label" style={{ background: fill, color: ink, ...style }}>{children}</span>

/** Seeded so each word always gets the same hand-cut spikes. */
function burstPoints(seed: string, spikes: number, w: number, h: number): string {
  let x = 7
  for (const ch of seed) x = (x * 31 + ch.charCodeAt(0)) >>> 0
  const rnd = () => { x = (x * 1664525 + 1013904223) >>> 0; return x / 4294967296 }
  const pts: string[] = []
  for (let i = 0; i < spikes * 2; i++) {
    const a = (Math.PI * i) / spikes - Math.PI / 2
    const r = i % 2 === 0 ? 1 - rnd() * 0.08 : 0.72 + rnd() * 0.08
    pts.push(`${(w / 2 + Math.cos(a) * (w / 2 - 14) * r).toFixed(1)},${(h / 2 + Math.sin(a) * (h / 2 - 14) * r).toFixed(1)}`)
  }
  return pts.join(' ')
}

/** Comic-book starburst ("CORRECT!", "TIME!", "STEAL!") that slams in. */
export function Burst({ text, sub, fill = C.sun, ink = C.ink, width = 620, height = 340, size = 84, tilt = -6, delay = 0, spikes = 16, className = '', style }: {
  text: string; sub?: string; fill?: string; ink?: string; width?: number; height?: number; size?: number; tilt?: number; delay?: number; spikes?: number; className?: string; style?: CSSProperties
}) {
  const points = useMemo(() => burstPoints(text, spikes, width, height), [text, spikes, width, height])
  return (
    <motion.div className={`burst ${className}`} style={{ width, height, rotate: tilt, ...style }}
      initial={{ scale: 2.2, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} transition={{ ...SLAM, delay, opacity: { duration: 0.08, delay } }}>
      <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} aria-hidden="true">
        <polygon points={points} fill={C.ink} transform="translate(10 12)" />
        <polygon points={points} fill={fill} stroke={C.ink} strokeWidth="7" strokeLinejoin="round" />
      </svg>
      <div className="burst-text" style={{ color: ink }}>
        <span style={{ fontSize: size }}>{text}</span>
        {sub && <small>{sub}</small>}
      </div>
    </motion.div>
  )
}

/** A rubber stamp: FAKE!, THE TRUTH, BUST. */
export function Stamp({ text, color, tilt = -10, delay = 0.25, size = 56, className = '' }: { text: string; color: string; tilt?: number; delay?: number; size?: number; className?: string }) {
  return (
    <motion.div className={`stamp ${className}`} style={{ color, borderColor: color, fontSize: size, rotate: tilt }}
      initial={{ scale: 2.6, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} transition={{ ...SLAM, delay, opacity: { duration: 0.06, delay } }}>
      {text}
    </motion.div>
  )
}

export function Crown({ size = 64, style }: { size?: number; style?: CSSProperties }) {
  return (
    <svg width={size} height={size * 0.72} viewBox="0 0 100 72" style={style} aria-hidden="true">
      <path d="M8 62 L4 16 L28 36 L50 6 L72 36 L96 16 L92 62 Z" fill={C.sun} stroke={C.ink} strokeWidth="6" strokeLinejoin="round" />
      <circle cx="50" cy="40" r="6" fill={C.tomato} stroke={C.ink} strokeWidth="4" />
      <path d="M8 62 H92" stroke={C.ink} strokeWidth="6" />
    </svg>
  )
}

/** A drawn keyboard key, for on-screen hints. */
export const Keycap = ({ label }: { label: string }) => <kbd className="keycap">{label}</kbd>

/** Kahoot-style answer shapes: A triangle, B diamond, C circle, D square. */
export function Shape({ id, size = 44, fill = C.white }: { id: string; size?: number; fill?: string }) {
  const s = { fill, stroke: C.ink, strokeWidth: 7, strokeLinejoin: 'round' as const }
  return (
    <svg width={size} height={size} viewBox="0 0 60 60" aria-hidden="true" style={{ flex: 'none' }}>
      {id === 'a' && <polygon points="30,6 55,52 5,52" {...s} />}
      {id === 'b' && <polygon points="30,4 56,30 30,56 4,30" {...s} />}
      {id === 'c' && <circle cx="30" cy="30" r="24" {...s} />}
      {id === 'd' && <rect x="7" y="7" width="46" height="46" rx="4" {...s} />}
    </svg>
  )
}

// ---------- people ----------

export const AvatarFace = ({ avatar, size = 56, dim = false }: { avatar: Avatar; size?: number; dim?: boolean }) =>
  <Face face={avatar.face} color={avatar.color} size={size} dim={dim} />

/** A row of overlapping faces. */
export function FaceRow({ players, size = 48, max = 6, dimIds }: { players: PlayerSummary[]; size?: number; max?: number; dimIds?: Set<string> }) {
  const shown = players.slice(0, max)
  return (
    <span className="face-row" style={{ '--overlap': `${-size * 0.28}px` } as CSSProperties}>
      {shown.map((p) => <Face key={p.id} face={p.avatar.face} color={p.avatar.color} size={size} dim={dimIds ? dimIds.has(p.id) : !p.connected} />)}
      {players.length > max && <span className="face-more" style={{ width: size, height: size, fontSize: size * 0.36 }}>+{players.length - max}</span>}
    </span>
  )
}

// ---------- the host ----------

export type Mood = 'happy' | 'shocked' | 'smug'

/** Brainy, the show's host: a pink brain with googly eyes, sipping through a bendy straw. */
export function Brainy({ mood = 'happy', size = 220, className = '' }: { mood?: Mood; size?: number; className?: string }) {
  const px = mood === 'smug' ? 4 : 2
  const py = mood === 'shocked' ? -2 : 4
  return (
    <svg className={`brainy ${className}`} width={size} height={size * 0.9} viewBox="-6 -14 226 212" aria-hidden="true">
      <g className="brainy-straw">
        <path d="M150 58 L158 10 L196 -4" fill="none" stroke={C.ink} strokeWidth="20" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M150 58 L158 10 L196 -4" fill="none" stroke={C.white} strokeWidth="10" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M150 58 L158 10 L196 -4" fill="none" stroke={C.tomato} strokeWidth="10" strokeDasharray="7 9" strokeLinejoin="round" />
      </g>
      <path d="M45 124 C15 122 12 84 36 74 C30 46 60 30 84 42 C92 18 132 16 142 38 C166 26 196 46 188 72 C212 82 208 120 184 126 C186 150 160 164 140 154 C128 172 92 172 82 156 C60 166 36 152 45 124 Z"
        fill={C.bubblegum} stroke={C.ink} strokeWidth="7" strokeLinejoin="round" />
      <g fill="none" stroke={C.ink} strokeWidth="4.5" strokeLinecap="round" opacity=".5">
        <path d="M62 66 C72 56 88 60 92 72" /><path d="M118 48 C130 42 144 48 146 60" /><path d="M160 84 C170 80 182 86 182 98" />
        <path d="M44 104 C52 96 62 98 66 106" /><path d="M150 140 C160 138 168 130 168 122" />
      </g>
      <ellipse cx="80" cy="130" rx="11" ry="6" fill={C.tomato} opacity=".45" />
      <ellipse cx="158" cy="130" rx="11" ry="6" fill={C.tomato} opacity=".45" />
      <g className="brainy-eyes">
        <circle cx="100" cy="102" r="17" fill={C.white} stroke={C.ink} strokeWidth="5" />
        <circle cx="138" cy="102" r="17" fill={C.white} stroke={C.ink} strokeWidth="5" />
        <circle cx={100 + px} cy={102 + py} r={mood === 'shocked' ? 5 : 7.5} fill={C.ink} />
        <circle cx={138 + px} cy={102 + py} r={mood === 'shocked' ? 5 : 7.5} fill={C.ink} />
        {mood === 'smug' && <path d="M84 92 L116 95 M122 95 L154 92" stroke={C.ink} strokeWidth="5" strokeLinecap="round" />}
      </g>
      {mood === 'happy' && <path d="M104 134 Q119 152 134 134 Z" fill={C.white} stroke={C.ink} strokeWidth="5" strokeLinejoin="round" />}
      {mood === 'shocked' && <ellipse cx="119" cy="140" rx="9" ry="12" fill={C.ink} />}
      {mood === 'smug' && <path d="M106 140 Q124 146 136 132" fill="none" stroke={C.ink} strokeWidth="5" strokeLinecap="round" />}
    </svg>
  )
}

/** Brainy says something: the host reacting to the room. */
export function HostSays({ line, mood = 'happy', size = 190, className = '' }: { line?: string | null; mood?: Mood; size?: number; className?: string }) {
  if (!line) return null
  return (
    <motion.div className={`host-says ${className}`} key={line} initial={{ y: 40, opacity: 0 }} animate={{ y: 0, opacity: 1 }} transition={{ ...POP }}>
      <Brainy mood={mood} size={size} />
      <Bubble tail="left"><span>{line}</span></Bubble>
    </motion.div>
  )
}

// ---------- the clock ----------

/** An alarm clock whose face drains like a pie; it rattles through the last five seconds. */
export function Timer({ deadline, total, frozen, size = 170 }: { deadline: number | null; total: number; frozen?: number | null; size?: number }) {
  const wedge = useRef<SVGPathElement>(null)
  const wrap = useRef<HTMLDivElement>(null)
  const [secs, setSecs] = useState(0)
  useEffect(() => {
    let raf = 0
    const cx = 80, cy = 96, r = 56
    const frame = () => {
      const left = frozen ?? (deadline ? Math.max(0, deadline - Date.now()) : 0)
      const frac = total > 0 ? Math.min(1, left / total) : 0
      if (wedge.current) {
        const a = frac * Math.PI * 2
        const x = cx + Math.sin(a) * r, y = cy - Math.cos(a) * r
        wedge.current.setAttribute('d', frac >= 0.999 ? `M${cx} ${cy - r} A${r} ${r} 0 1 1 ${cx - 0.01} ${cy - r} Z`
          : frac <= 0 ? '' : `M${cx} ${cy} L${cx} ${cy - r} A${r} ${r} 0 ${frac > 0.5 ? 1 : 0} 1 ${x.toFixed(2)} ${y.toFixed(2)} Z`)
        wedge.current.style.fill = left < 5000 ? 'var(--tomato)' : 'var(--sun)'
      }
      wrap.current?.classList.toggle('hot', left > 0 && left < 5000 && frozen == null)
      setSecs(Math.ceil(left / 1000))
      if (frozen == null) raf = requestAnimationFrame(frame)
    }
    frame()
    return () => cancelAnimationFrame(raf)
  }, [deadline, total, frozen])
  return (
    <div className="timer" ref={wrap} style={{ width: size, height: size * 1.05 }}>
      <svg viewBox="0 0 160 168" width={size} height={size * 1.05} aria-hidden="true">
        <path d="M40 150 L30 164 M120 150 L130 164" stroke={C.ink} strokeWidth="9" strokeLinecap="round" />
        <circle cx="34" cy="34" r="20" fill={C.tomato} stroke={C.ink} strokeWidth="6" />
        <circle cx="126" cy="34" r="20" fill={C.tomato} stroke={C.ink} strokeWidth="6" />
        <circle cx="80" cy="96" r="66" fill={C.paper} />
        <path ref={wedge} stroke="none" />
        <circle cx="80" cy="96" r="66" fill="none" stroke={C.ink} strokeWidth="8" />
      </svg>
      <span style={{ fontSize: size * 0.3 }}>{secs}</span>
    </div>
  )
}

// ---------- motion vocabulary ----------

export const SLAM = { type: 'spring', stiffness: 520, damping: 13 } as const
export const POP = { type: 'spring', stiffness: 700, damping: 15 } as const
const DEAL = { type: 'spring', stiffness: 340, damping: 20 } as const

export const Slam = ({ delay = 0, from = 1.8, tilt = -8, children, className, style }: { delay?: number; from?: number; tilt?: number; children: ReactNode; className?: string; style?: CSSProperties }) => (
  <motion.div className={className} style={style} initial={{ scale: from, rotate: tilt, opacity: 0 }} animate={{ scale: 1, rotate: 0, opacity: 1 }}
    transition={{ ...SLAM, delay, opacity: { duration: 0.1, delay } }}>{children}</motion.div>
)
/** Squash-and-stretch entrance. */
export const Pop = ({ delay = 0, children, className, style }: { delay?: number; children: ReactNode; className?: string; style?: CSSProperties }) => (
  <motion.div className={className} style={style} initial={{ scaleX: 0.3, scaleY: 1.4, opacity: 0 }} animate={{ scaleX: 1, scaleY: 1, opacity: 1 }}
    transition={{ ...POP, delay, opacity: { duration: 0.1, delay } }}>{children}</motion.div>
)
export const Deal = ({ i = 0, children, className, style }: { i?: number; children: ReactNode; className?: string; style?: CSSProperties }) => (
  <motion.div className={className} style={style} initial={{ y: 160, rotate: i % 2 ? -8 : 8, opacity: 0 }} animate={{ y: 0, rotate: 0, opacity: 1 }}
    transition={{ ...DEAL, delay: i * 0.07, opacity: { duration: 0.12, delay: i * 0.07 } }}>{children}</motion.div>
)
/** Idle sway for anything waiting on players. */
export const Wobble = ({ children, className }: { children: ReactNode; className?: string }) => <div className={`wobble ${className ?? ''}`}>{children}</div>

/** Counts up in ≤24 steps; onStep drives tick sounds. */
export function CountUp({ from, to, delay = 0, duration = 900, onStep, className }: { from: number; to: number; delay?: number; duration?: number; onStep?: (n: number) => void; className?: string }) {
  const [n, setN] = useState(from)
  useEffect(() => {
    let raf = 0, last = from
    setN(from)
    const start = performance.now() + delay
    const step = Math.max(1, Math.round(Math.abs(to - from) / 24))
    const tick = (now: number) => {
      const p = Math.min(1, Math.max(0, (now - start) / duration))
      const eased = 1 - Math.pow(1 - p, 3)
      const v = p >= 1 ? to : from + Math.round(((to - from) * eased) / step) * step
      if (v !== last) { last = v; setN(v); onStep?.(v) }
      if (p < 1) raf = requestAnimationFrame(tick)
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [from, to])
  return <span className={className}>{n.toLocaleString()}</span>
}

// ---------- confetti ----------

/** Resolves a token (e.g. --tomato, written in OKLCH) to hex for canvas-confetti. */
function tokenHex(name: string): string {
  const ctx = document.createElement('canvas').getContext('2d', { willReadFrequently: true })
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  if (!ctx || !value) return '#ffcc33'
  ctx.fillStyle = value
  ctx.fillRect(0, 0, 1, 1)
  const [r, g, b] = ctx.getImageData(0, 0, 1, 1).data
  return `#${[r, g, b].map((v) => v.toString(16).padStart(2, '0')).join('')}`
}

let palette: string[] | null = null
const confettiColors = () => (palette ??= ['--sun', '--tomato', '--blueberry', '--lime', '--grape', '--bubblegum', '--paper'].map(tokenHex))

/** Confetti cannons from the bottom corners. */
export function fireConfetti(big = false) {
  const colors = confettiColors()
  const shot = (x: number, angle: number) => confetti({ particleCount: big ? 140 : 80, angle, spread: 55, startVelocity: big ? 75 : 60, origin: { x, y: 1 }, colors, ticks: 260, scalar: 1.3, disableForReducedMotion: true })
  shot(0.02, 62); shot(0.98, 118)
  if (big) setTimeout(() => { shot(0.2, 80); shot(0.8, 100) }, 350)
}

/** A shower of drawn gold coins (circles, not emoji) for jackpots and heists. */
export function coinShower(origin = { x: 0.5, y: 0.3 }) {
  confetti({ shapes: ['circle'], colors: [tokenHex('--sun'), tokenHex('--tangerine')], scalar: 2.4, particleCount: 60, spread: 120, startVelocity: 45, gravity: 1.1, origin, ticks: 300, disableForReducedMotion: true })
}
