import gsap from 'gsap'
import { MotionPathPlugin } from 'gsap/MotionPathPlugin'
import { useLayoutEffect, useRef } from 'react'
import '@fontsource/dseg7-classic/700.css'
import './casino.css'

gsap.registerPlugin(MotionPathPlugin)

const CHIP = (bet: number) =>
  bet >= 1000 ? { body: 'var(--sun)', insert: 'var(--ink)', ink: 'var(--ink)' }
    : bet >= 500 ? { body: 'var(--grape)', insert: 'var(--paper)', ink: 'var(--paper)' }
      : bet >= 250 ? { body: 'var(--bubblegum)', insert: 'var(--paper)', ink: 'var(--paper)' }
        : { body: 'var(--blueberry)', insert: 'var(--paper)', ink: 'var(--paper)' }

/** A clay casino chip: coloured body, six edge inserts, an inlay ring and the value. */
export function Chip({ value, size = 44, label = true }: { value: number; size?: number; label?: boolean }) {
  const c = CHIP(value)
  const text = value >= 1000 ? `${Math.round(value / 100) / 10}K` : String(value)
  return (
    <svg width={size} height={size} viewBox="0 0 100 100" className="chip-svg">
      <circle cx="50" cy="50" r="48" fill={c.body} stroke="var(--ink)" strokeWidth="2" />
      {Array.from({ length: 6 }, (_, i) => (
        <rect key={i} x="44" y="2" width="12" height="17" rx="2.5" fill={c.insert} transform={`rotate(${i * 60 + 30} 50 50)`} />
      ))}
      <circle cx="50" cy="50" r="31" fill="none" stroke={c.insert} strokeWidth="2.5" strokeDasharray="5 4" opacity="0.8" />
      <circle cx="50" cy="50" r="25" fill={c.body} stroke="var(--ink)" strokeWidth="1.5" />
      {label && <text x="50" y="58" textAnchor="middle" fontFamily="Rammetto One" fontSize={text.length > 3 ? 19 : 23} fill={c.ink}>{text}</text>}
      <ellipse cx="38" cy="28" rx="22" ry="10" fill="var(--white)" opacity="0.18" transform="rotate(-25 38 28)" />
    </svg>
  )
}

/** A short stack for a bet: more and bigger chips for bigger bets. */
export function ChipStack({ bet, size = 44 }: { bet: number; size?: number }) {
  const ref = useRef<HTMLSpanElement>(null)
  const n = bet >= 1000 ? 5 : bet >= 500 ? 4 : bet >= 250 ? 3 : 2
  useLayoutEffect(() => {
    const ctx = gsap.context(() => {
      gsap.from('.stacked', { y: -size * 1.6, opacity: 0, duration: 0.42, stagger: 0.06, ease: 'bounce.out' })
    }, ref)
    return () => ctx.revert()
  }, [bet, size])
  return (
    <span ref={ref} className="chip-stack" style={{ width: size, height: size + n * size * 0.1 }}>
      {Array.from({ length: n }, (_, i) => (
        <span key={i} className="stacked" style={{ bottom: i * size * 0.1, zIndex: i }}><Chip value={bet} size={size} label={i === n - 1} /></span>
      ))}
    </span>
  )
}

/** Seven-segment readout with the unlit "8"s ghosted behind, like a real casino display. */
export function Led({ value, tone = 'green', size = 44, digits = 2 }: { value: number | string; tone?: 'green' | 'gold' | 'red'; size?: number; digits?: number }) {
  const v = String(value)
  return (
    <span className={`led7 ${tone}`} style={{ fontSize: size }}>
      <span className="ghost">{'8'.repeat(Math.max(digits, v.length))}</span>
      <span className="lit">{v}</span>
    </span>
  )
}

export const MugIcon = ({ size = 30 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden>
    <path d="M6 9h15v16a3 3 0 0 1-3 3H9a3 3 0 0 1-3-3z" fill="var(--sun)" stroke="var(--ink)" strokeWidth="2.4" strokeLinejoin="round" />
    <path d="M21 13h3a3 3 0 0 1 3 3v3a3 3 0 0 1-3 3h-3" fill="none" stroke="var(--ink)" strokeWidth="2.4" />
    <path d="M5 9c0-3 2.5-5 5-4 1-2 4-3 6-1 2-1 5 0 5 3v2z" fill="var(--paper)" stroke="var(--ink)" strokeWidth="2.2" strokeLinejoin="round" />
    <path d="M11 14v9M16 14v9" stroke="var(--ink)" strokeWidth="1.8" strokeLinecap="round" opacity=".5" />
  </svg>
)

export const CheersIcon = ({ size = 30 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden>
    <g stroke="var(--ink)" strokeWidth="2.2" strokeLinejoin="round" strokeLinecap="round">
      <path d="M4 7l8-2 2 9a4.5 4.5 0 0 1-8 2z" fill="var(--paper)" /><path d="M10 18l2 8M8.5 27l5-1.3" fill="none" />
      <path d="M28 7l-8-2-2 9a4.5 4.5 0 0 0 8 2z" fill="var(--paper)" /><path d="M22 18l-2 8M23.5 27l-5-1.3" fill="none" />
      <path d="M13 3l3-2M16 5h3" fill="none" />
    </g>
  </svg>
)

export interface Flight { from: { x: number; y: number }; to: { x: number; y: number }; value: number; delay: number }

/** Chips that arc across the felt (payouts to winners, losing bets to the House), then disappear. */
export function ChipFlights({ flights, onEach }: { flights: Flight[]; onEach?: () => void }) {
  const ref = useRef<HTMLDivElement>(null)
  useLayoutEffect(() => {
    const ctx = gsap.context(() => {
      gsap.utils.toArray<HTMLElement>('.flying-chip').forEach((el, i) => {
        const f = flights[i]
        const mid = { x: (f.from.x + f.to.x) / 2, y: Math.min(f.from.y, f.to.y) - 140 }
        gsap.timeline({ delay: f.delay })
          .set(el, { x: f.from.x, y: f.from.y, opacity: 1, scale: 0.8 })
          .to(el, { motionPath: { path: [f.from, mid, f.to], curviness: 1.2 }, scale: 1, rotation: 540, duration: 0.7, ease: 'power2.inOut', onComplete: onEach })
          .to(el, { opacity: 0, scale: 0.6, duration: 0.25 })
      })
    }, ref)
    return () => ctx.revert()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [flights])
  return (
    <div ref={ref} className="chip-flights">
      {flights.map((f, i) => <span key={i} className="flying-chip"><Chip value={f.value} size={46} label={false} /></span>)}
    </div>
  )
}

export const ShotIcon = ({ size = 30 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden>
    <path d="M7 5h18l-3 22a2 2 0 0 1-2 2h-8a2 2 0 0 1-2-2z" fill="var(--paper)" stroke="var(--ink)" strokeWidth="2.4" strokeLinejoin="round" />
    <path d="M9.2 12h13.6l-2 14.5a1.5 1.5 0 0 1-1.5 1.3h-6.6a1.5 1.5 0 0 1-1.5-1.3z" fill="var(--tangerine)" />
    <path d="M10 13.5h12" stroke="var(--white)" strokeWidth="1.2" opacity=".6" />
  </svg>
)

/** A bet in drinks: beer mugs for sips, a shot glass for a shot. */
export function DrinkBet({ sips, size = 30 }: { sips: number; size?: number }) {
  const shots = Math.floor(sips / 5), rest = sips % 5
  return (
    <span className="drink-bet">
      {Array.from({ length: shots }, (_, i) => <ShotIcon key={`s${i}`} size={size} />)}
      {rest > 0 && (rest <= 3
        ? Array.from({ length: rest }, (_, i) => <span key={`m${i}`} style={{ marginLeft: i ? -size * 0.45 : 0 }}><MugIcon size={size} /></span>)
        : <><MugIcon size={size} /><b>×{rest}</b></>)}
    </span>
  )
}
