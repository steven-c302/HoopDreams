/** One shared sprite of vector suits (spade, heart, diamond, club), so every card draws crisp, identical pips. */
export const SUIT_PATHS = [
  // spade
  'M50 3C58 17 70 26 82 36C92 44 97 52 97 62C97 76 86 85 73 85C64 85 57 81 53 74C54 84 58 92 67 97H33C42 92 46 84 47 74C43 81 36 85 27 85C14 85 3 76 3 62C3 52 8 44 18 36C30 26 42 17 50 3Z',
  // heart
  'M50 94C44 86 31 75 20 63C10 52 3 43 3 30C3 15 14 5 28 5C38 5 45 11 50 20C55 11 62 5 72 5C86 5 97 15 97 30C97 43 90 52 80 63C69 75 56 86 50 94Z',
  // diamond
  'M50 2C60 20 74 36 92 50C74 64 60 80 50 98C40 80 26 64 8 50C26 36 40 20 50 2Z',
  // club
  'M50 4A21 21 0 0 1 69 34A21 21 0 1 1 55 71C56 82 60 90 68 97H32C40 90 44 82 45 71A21 21 0 1 1 31 34A21 21 0 0 1 50 4Z',
]

export function SuitSprite() {
  return (
    <svg width="0" height="0" style={{ position: 'absolute' }} aria-hidden>
      <defs>
        {SUIT_PATHS.map((d, i) => <symbol key={i} id={`suit-${i}`} viewBox="0 0 100 100"><path d={d} fill="currentColor" /></symbol>)}
        <linearGradient id="card-face" x1="0" y1="0" x2="0.4" y2="1"><stop offset="0" stopColor="#FFFEFA" /><stop offset="1" stopColor="#F3EBD8" /></linearGradient>
        <linearGradient id="card-gloss" x1="0" y1="0" x2="1" y2="0.8">
          <stop offset="0" stopColor="#fff" stopOpacity="0.7" /><stop offset="0.28" stopColor="#fff" stopOpacity="0" />
          <stop offset="0.85" stopColor="#000" stopOpacity="0" /><stop offset="1" stopColor="#000" stopOpacity="0.07" />
        </linearGradient>
        <pattern id="card-lattice" width="8" height="8" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
          <rect width="8" height="8" fill="var(--tomato)" />
          <path d="M0 4H8M4 0V8" stroke="var(--paper)" strokeOpacity="0.55" strokeWidth="0.7" />
          <circle cx="4" cy="4" r="1" fill="var(--sun)" />
        </pattern>
        <pattern id="court-weave" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
          <path d="M0 3H6" stroke="currentColor" strokeOpacity="0.12" strokeWidth="1.4" />
        </pattern>
      </defs>
    </svg>
  )
}

export const Suit = ({ s, x, y, size, flip = false, color }: { s: number; x: number; y: number; size: number; flip?: boolean; color?: string }) => (
  <use href={`#suit-${s}`} x={x - size / 2} y={y - size / 2} width={size} height={size} color={color}
    transform={flip ? `rotate(180 ${x} ${y})` : undefined} />
)
