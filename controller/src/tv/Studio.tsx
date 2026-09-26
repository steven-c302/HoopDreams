import { motion, type HTMLMotionProps } from 'motion/react'
import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import confetti from 'canvas-confetti'

export const C = {
  ink: '#0B0716', night: '#160D2B', velvet: '#3B0A2A', velvetHi: '#7A1446', cream: '#FFF4D6', gold: '#FFD23F',
  pink: '#FF4D8D', mint: '#3DDC97', red: '#FF5A5A', sky: '#2EC4F1', orange: '#FF8A3D', felt: '#0E5A3A', feltDeep: '#06301F', brass: '#D9A441',
  muted: '#A99BD0',
}

/** The stage: velvet wall, turning sunburst, swaying spotlights, floor glow. [floor] tints the light per game. */
export function StudioBackdrop({ floor = C.pink, rays = C.gold, carpet = false, children }: { floor?: string; rays?: string; carpet?: boolean; children?: ReactNode }) {
  return (
    <div className="studio" style={{ '--floor': floor, '--rays': rays } as CSSProperties}>
      <div className="studio-wall" />
      <div className="studio-burst" />
      {carpet && <div className="studio-carpet" />}
      <div className="studio-spot left" />
      <div className="studio-spot right" />
      <div className="studio-glow" />
      <div className="studio-content">{children}</div>
    </div>
  )
}

function useSize<T extends HTMLElement>() {
  const ref = useRef<T>(null)
  const [size, setSize] = useState({ w: 0, h: 0 })
  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    const ro = new ResizeObserver(() => setSize({ w: el.offsetWidth, h: el.offsetHeight }))
    ro.observe(el)
    setSize({ w: el.offsetWidth, h: el.offsetHeight })
    return () => ro.disconnect()
  }, [])
  return [ref, size] as const
}

/** A plate ringed with chasing marquee bulbs (SVG dashes with round caps, stepped along the rim). */
export function Marquee({ children, className = '', style, plate = C.velvet }: { children: ReactNode; className?: string; style?: CSSProperties; plate?: string }) {
  const [ref, { w, h }] = useSize<HTMLDivElement>()
  const inset = 11, gap = 44
  return (
    <div ref={ref} className={`marquee ${className}`} style={{ ...style, background: plate }}>
      {w > 0 && (
        <svg className="marquee-bulbs" width={w} height={h}>
          <rect x={inset} y={inset} width={w - inset * 2} height={h - inset * 2} rx={30} className="bulbs-off" style={{ strokeDasharray: `0 ${gap / 3}` }} />
          <rect x={inset} y={inset} width={w - inset * 2} height={h - inset * 2} rx={30} className="bulbs-on" style={{ strokeDasharray: `0 ${gap}` }} />
        </svg>
      )}
      <div className="marquee-body">{children}</div>
    </div>
  )
}

/** Chunky prop: flat fill, ink outline, hard offset shadow. */
export function Prop({ fill = C.cream, shadow = C.gold, tilt = 0, className = '', style, children, ...rest }: HTMLMotionProps<'div'> & { fill?: string; shadow?: string; tilt?: number }) {
  return (
    <motion.div className={`prop ${className}`} style={{ background: fill, boxShadow: `10px 10px 0 ${shadow}`, rotate: tilt, ...style }} {...rest}>
      {children}
    </motion.div>
  )
}

export function Neon({ text, lit, color, className = '' }: { text: string; lit: boolean; color: string; className?: string }) {
  return <div className={`neon ${lit ? 'lit' : ''} ${className}`} style={{ '--neon': color } as CSSProperties}>{text}</div>
}

export const OnAir = ({ lit }: { lit: boolean }) => <Neon text="ON AIR" lit={lit} color={C.red} className={lit ? 'breathe' : ''} />

export const Badge = ({ text, fill, ink = C.ink }: { text: string; fill: string; ink?: string }) => <span className="badge" style={{ background: fill, color: ink }}>{text}</span>

// ---------- motion vocabulary ----------

const slam = { type: 'spring', stiffness: 520, damping: 13 } as const
const pop = { type: 'spring', stiffness: 700, damping: 16 } as const
const deal = { type: 'spring', stiffness: 340, damping: 20 } as const

export const Slam = ({ delay = 0, from = 1.8, tilt = -8, children, className, style }: { delay?: number; from?: number; tilt?: number; children: ReactNode; className?: string; style?: CSSProperties }) => (
  <motion.div className={className} style={style} initial={{ scale: from, rotate: tilt, opacity: 0 }} animate={{ scale: 1, rotate: 0, opacity: 1 }}
    transition={{ ...slam, delay, opacity: { duration: 0.1, delay } }}>{children}</motion.div>
)
export const Pop = ({ delay = 0, children, className, style }: { delay?: number; children: ReactNode; className?: string; style?: CSSProperties }) => (
  <motion.div className={className} style={style} initial={{ scale: 0.3, opacity: 0 }} animate={{ scale: 1, opacity: 1 }}
    transition={{ ...pop, delay, opacity: { duration: 0.1, delay } }}>{children}</motion.div>
)
export const Deal = ({ i = 0, children, className, style }: { i?: number; children: ReactNode; className?: string; style?: CSSProperties }) => (
  <motion.div className={className} style={style} initial={{ y: 160, rotate: i % 2 ? -8 : 8, opacity: 0 }} animate={{ y: 0, rotate: 0, opacity: 1 }}
    transition={{ ...deal, delay: i * 0.07, opacity: { duration: 0.12, delay: i * 0.07 } }}>{children}</motion.div>
)
export const Wobble = ({ children, className }: { children: ReactNode; className?: string }) => <div className={`wobble ${className ?? ''}`}>{children}</div>

export function Stamp({ text, color, tilt = -10, delay = 0.25, size = 56, className = '' }: { text: string; color: string; tilt?: number; delay?: number; size?: number; className?: string }) {
  return (
    <motion.div className={`stamp ${className}`} style={{ color, borderColor: color, fontSize: size, rotate: tilt }}
      initial={{ scale: 2.6, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} transition={{ ...slam, delay, opacity: { duration: 0.06, delay } }}>
      {text}
    </motion.div>
  )
}

/** Counts up in ≤24 steps; onStep drives tick sounds. */
export function CountUp({ from, to, delay = 0, duration = 900, onStep, className }: { from: number; to: number; delay?: number; duration?: number; onStep?: (n: number) => void; className?: string }) {
  const [n, setN] = useState(from)
  useEffect(() => {
    let raf = 0, last = from
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

/** Confetti cannons from the bottom corners. */
export function fireConfetti(big = false) {
  const colors = [C.gold, C.pink, C.mint, C.sky, C.cream, C.orange]
  const shot = (x: number, angle: number) => confetti({ particleCount: big ? 140 : 80, angle, spread: 55, startVelocity: big ? 75 : 60, origin: { x, y: 1 }, colors, ticks: 260, scalar: 1.2, disableForReducedMotion: true })
  shot(0.02, 62); shot(0.98, 118)
  if (big) setTimeout(() => { shot(0.2, 80); shot(0.8, 100) }, 350)
}

/** Casino coin shower for jackpots. */
export function coinShower() {
  const coin = confetti.shapeFromText({ text: '🪙', scalar: 2.2 })
  confetti({ shapes: [coin], scalar: 2.2, particleCount: 60, spread: 120, startVelocity: 45, gravity: 1.1, origin: { x: 0.5, y: 0.25 }, ticks: 300, flat: true })
}

/** Game-show clock: gold ring that drains, red and throbbing for the last five seconds. */
export function Clock({ deadline, total, frozen }: { deadline: number | null; total: number; frozen?: number | null }) {
  const ring = useRef<SVGCircleElement>(null)
  const wrap = useRef<HTMLDivElement>(null)
  const [secs, setSecs] = useState(0)
  useEffect(() => {
    let raf = 0
    const r = 44, circ = 2 * Math.PI * r
    const frame = () => {
      const left = frozen ?? (deadline ? Math.max(0, deadline - Date.now()) : 0)
      const frac = total > 0 ? Math.min(1, left / total) : 0
      if (ring.current) {
        ring.current.style.strokeDashoffset = String(circ * (1 - frac))
        ring.current.style.stroke = left < 5000 ? C.red : C.gold
      }
      if (wrap.current) wrap.current.classList.toggle('hot', left > 0 && left < 5000)
      setSecs(Math.ceil(left / 1000))
      if (frozen == null) raf = requestAnimationFrame(frame)
    }
    frame()
    return () => cancelAnimationFrame(raf)
  }, [deadline, total, frozen])
  const circ = 2 * Math.PI * 44
  return (
    <div className="clock" ref={wrap}>
      <svg viewBox="0 0 100 100">
        <circle cx="50" cy="50" r="50" fill={C.ink} />
        <circle cx="50" cy="50" r="44" fill="none" stroke="#5C4A1E" strokeWidth="11" />
        <circle ref={ring} cx="50" cy="50" r="44" fill="none" strokeWidth="11" strokeLinecap="round" transform="rotate(-90 50 50)" style={{ strokeDasharray: circ }} />
      </svg>
      <span>{secs}</span>
    </div>
  )
}

export function AvatarDot({ emoji, color, size = 56, dim = false }: { emoji: string; color: string; size?: number; dim?: boolean }) {
  return <span className="avatar-dot" style={{ width: size, height: size, background: color, fontSize: size * 0.52, opacity: dim ? 0.35 : 1 }}>{emoji}</span>
}
