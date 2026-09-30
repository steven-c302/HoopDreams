import type { CSSProperties } from 'react'
import { SPEED_BUS, SPEED_SCOUT } from './types'

/**
 * Home Turf's drawn props, shared by the TV and phones: the six pieces (party objects, never emoji), dice with the
 * speed die's bus and scout faces, houses and hotels. Everything is flat fill + ink outline, sized by [size].
 */

const INK = 'var(--ink)'

export const PIECE_NAMES: Record<string, string> = {
  cup: 'Red Cup', pizza: 'Pizza Slice', sneaker: 'Sneaker', boombox: 'Boombox', cone: 'Traffic Cone', duck: 'Rubber Duck',
}

function PieceDrawing({ piece }: { piece: string }) {
  const s = { stroke: INK, strokeWidth: 5, strokeLinejoin: 'round' as const, strokeLinecap: 'round' as const }
  switch (piece) {
    case 'cup': return (
      <g>
        <path d="M31 34 L69 34 L63 78 L37 78 Z" fill="var(--tomato)" {...s} />
        <path d="M34 50 H66 M35 58 H65" fill="none" {...s} strokeWidth={3} opacity=".55" />
        <rect x="27" y="26" width="46" height="10" rx="4" fill="var(--white)" {...s} />
      </g>
    )
    case 'pizza': return (
      <g>
        <path d="M26 32 Q50 20 74 32 L50 80 Z" fill="var(--sun)" {...s} />
        <path d="M26 32 Q50 20 74 32 L71 39 Q50 28 29 39 Z" fill="var(--tangerine)" {...s} strokeWidth={4} />
        <circle cx="45" cy="47" r="6" fill="var(--tomato)" {...s} strokeWidth={3} />
        <circle cx="58" cy="44" r="5" fill="var(--tomato)" {...s} strokeWidth={3} />
        <circle cx="51" cy="62" r="5" fill="var(--tomato)" {...s} strokeWidth={3} />
      </g>
    )
    case 'sneaker': return (
      <g>
        <path d="M20 66 L27 38 Q37 44 47 36 L58 50 Q76 52 80 62 L80 66 Z" fill="var(--sky)" {...s} />
        <rect x="18" y="64" width="64" height="12" rx="5" fill="var(--white)" {...s} />
        <path d="M40 44 L46 50 M46 41 L52 47" fill="none" {...s} strokeWidth={3} />
      </g>
    )
    case 'boombox': return (
      <g>
        <path d="M36 32 Q50 18 64 32" fill="none" {...s} />
        <rect x="18" y="32" width="64" height="40" rx="7" fill="var(--grape)" {...s} />
        <circle cx="34" cy="54" r="10" fill="var(--paper)" {...s} strokeWidth={4} />
        <circle cx="66" cy="54" r="10" fill="var(--paper)" {...s} strokeWidth={4} />
        <circle cx="34" cy="54" r="3.5" fill={INK} /><circle cx="66" cy="54" r="3.5" fill={INK} />
        <rect x="44" y="39" width="12" height="6" rx="2" fill="var(--sun)" {...s} strokeWidth={3} />
      </g>
    )
    case 'cone': return (
      <g>
        <path d="M50 18 L69 72 H31 Z" fill="var(--tangerine)" {...s} />
        <path d="M42 42 H58 L61 52 H39 Z" fill="var(--white)" {...s} strokeWidth={3} />
        <rect x="24" y="70" width="52" height="10" rx="3" fill="var(--tangerine)" {...s} />
      </g>
    )
    default: return ( // duck
      <g>
        <path d="M24 56 Q24 76 50 76 Q78 76 78 58 Q70 62 62 56 Q66 44 56 36 Q46 30 38 38 Q32 46 38 54 Q30 52 24 56 Z" fill="var(--sun)" {...s} />
        <path d="M36 42 L24 44 L34 49 Z" fill="var(--tangerine)" {...s} strokeWidth={4} />
        <circle cx="45" cy="42" r="3.5" fill={INK} />
        <path d="M52 62 Q60 66 68 60" fill="none" {...s} strokeWidth={3} />
      </g>
    )
  }
}

/** A piece as a coin: the token's colour ring around a paper face with the drawing. */
export function Piece({ piece, color, size = 56, style, className = '' }: { piece?: string; color: string; size?: number; style?: CSSProperties; className?: string }) {
  return (
    <svg className={`turf-piece ${className}`} width={size} height={size} viewBox="0 0 100 100" style={style} aria-label={piece ? PIECE_NAMES[piece] : 'piece'}>
      <circle cx="50" cy="54" r="46" fill={INK} />
      <circle cx="50" cy="50" r="46" fill={color} stroke={INK} strokeWidth="6" />
      <circle cx="50" cy="50" r="34" fill="var(--paper)" stroke={INK} strokeWidth="4" />
      <g transform="translate(50 50) scale(.72) translate(-50 -50)">{piece ? <PieceDrawing piece={piece} /> : <text x="50" y="64" textAnchor="middle" fontFamily="Rammetto One" fontSize="40" fill={INK}>?</text>}</g>
    </svg>
  )
}

const PIPS: Record<number, [number, number][]> = {
  1: [[50, 50]], 2: [[30, 30], [70, 70]], 3: [[28, 28], [50, 50], [72, 72]],
  4: [[30, 30], [70, 30], [30, 70], [70, 70]], 5: [[28, 28], [72, 28], [50, 50], [28, 72], [72, 72]],
  6: [[30, 26], [70, 26], [30, 50], [70, 50], [30, 74], [70, 74]],
}

/** A die. The speed die is tomato and has a bus and a scout (a double arrow: jump ahead) instead of 4-6. */
export function Die({ value, speed = false, size = 96, style, className = '' }: { value: number; speed?: boolean; size?: number; style?: CSSProperties; className?: string }) {
  const face = speed ? 'var(--tomato)' : 'var(--white)'
  const pip = speed ? 'var(--white)' : INK
  return (
    <svg className={`turf-die ${className}`} width={size} height={size} viewBox="0 0 100 100" style={style} aria-label={speed && value === SPEED_BUS ? 'bus' : speed && value === SPEED_SCOUT ? 'scout' : String(value)}>
      <rect x="6" y="10" width="88" height="88" rx="18" fill={INK} />
      <rect x="4" y="4" width="88" height="88" rx="18" fill={face} stroke={INK} strokeWidth="6" />
      <g transform="translate(-2 -2)">
        {speed && value === SPEED_BUS ? (
          <g stroke={INK} strokeWidth="4" strokeLinejoin="round">
            <rect x="20" y="30" width="60" height="36" rx="6" fill="var(--sun)" />
            <rect x="26" y="36" width="14" height="12" fill="var(--sky)" /><rect x="44" y="36" width="14" height="12" fill="var(--sky)" /><rect x="62" y="36" width="12" height="12" fill="var(--sky)" />
            <circle cx="34" cy="68" r="7" fill={INK} /><circle cx="66" cy="68" r="7" fill={INK} />
          </g>
        ) : speed && value === SPEED_SCOUT ? (
          <path d="M26 30 L48 50 L26 70 M52 30 L74 50 L52 70" fill="none" stroke={pip} strokeWidth="10" strokeLinecap="round" strokeLinejoin="round" />
        ) : (PIPS[value] ?? []).map(([x, y], i) => <circle key={i} cx={x} cy={y} r="8.5" fill={pip} />)}
      </g>
    </svg>
  )
}

export function House({ size = 18 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 20 20" aria-hidden="true" style={{ flex: 'none' }}>
      <path d="M2 10 L10 3 L18 10 V18 H2 Z" fill="var(--lime)" stroke={INK} strokeWidth="2.5" strokeLinejoin="round" />
    </svg>
  )
}

export function Hotel({ size = 18 }: { size?: number }) {
  return (
    <svg width={size * 1.8} height={size} viewBox="0 0 36 20" aria-hidden="true" style={{ flex: 'none' }}>
      <path d="M2 8 L18 2 L34 8 V18 H2 Z" fill="var(--tomato)" stroke={INK} strokeWidth="2.5" strokeLinejoin="round" />
      <rect x="15" y="11" width="6" height="7" fill={INK} />
    </svg>
  )
}

/** Houses (1-3) or the hotel (level 4) on a place. */
export function Buildings({ level, size = 18 }: { level: number; size?: number }) {
  if (level <= 0) return null
  return <span className="turf-buildings">{level >= 4 ? <Hotel size={size} /> : Array.from({ length: level }, (_, i) => <House key={i} size={size} />)}</span>
}
