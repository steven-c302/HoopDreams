import type { CSSProperties } from 'react'

const INK = 'var(--ink)'

/** Terrain fills: muted flat colours so the players' loud pieces pop. 0 hills, 1 forest, 2 pasture, 3 fields, 4 mountains, 5 desert. */
export const TERRAIN_FILL = ['var(--sp-hills)', 'var(--sp-forest)', 'var(--sp-pasture)', 'var(--sp-fields)', 'var(--sp-mountains)', 'var(--sp-desert)']
/** Each resource's own colour (its tile on phones, its chip on the TV). */
export const RES_FILL = ['var(--sp-brick)', 'var(--sp-wood)', 'var(--sp-sheep)', 'var(--sp-wheat)', 'var(--sp-ore)']

/** One resource drawn in ink on a 100×100 grid (no background), for use inside other SVGs. */
export function ResourceGlyph({ res }: { res: number }) {
  const s = { stroke: INK, strokeWidth: 6, strokeLinejoin: 'round' as const, strokeLinecap: 'round' as const }
  switch (res) {
    case 0: return ( // brick: a little wall
      <g {...s}>
        <rect x="14" y="54" width="34" height="20" rx="3" fill="var(--sp-brick)" /><rect x="52" y="54" width="34" height="20" rx="3" fill="var(--sp-brick)" />
        <rect x="32" y="30" width="36" height="20" rx="3" fill="var(--sp-brick)" />
      </g>
    )
    case 1: return ( // wood: a log with rings
      <g {...s}>
        <path d="M20 40 H72 V68 H20 Z" fill="var(--sp-wood)" />
        <ellipse cx="72" cy="54" rx="10" ry="14" fill="var(--paper)" />
        <path d="M72 48 Q78 54 72 60" fill="none" strokeWidth={4} />
        <path d="M32 48 H56 M28 60 H50" strokeWidth={4} />
      </g>
    )
    case 2: return ( // sheep: a cloud of wool with a dark face
      <g {...s}>
        <path d="M30 70 V82 M44 72 V84 M60 72 V84 M74 70 V82" />
        <path d="M24 56 Q18 40 34 38 Q38 24 54 30 Q66 22 74 36 Q90 38 84 54 Q88 70 72 70 H32 Q18 70 24 56 Z" fill="var(--white)" />
        <ellipse cx="22" cy="50" rx="11" ry="13" fill={INK} />
        <circle cx="19" cy="47" r="2.5" fill="var(--white)" stroke="none" />
      </g>
    )
    case 3: return ( // wheat: a sheaf
      <g {...s}>
        <path d="M50 88 V36 M50 70 L32 36 M50 70 L68 36" fill="none" />
        {[[50, 26], [30, 28], [70, 28]].map(([x, y], i) => <ellipse key={i} cx={x} cy={y} rx="8" ry="15" fill="var(--sp-wheat)" />)}
        <path d="M38 70 H62" strokeWidth={8} stroke="var(--tomato)" />
      </g>
    )
    default: return ( // ore: a faceted rock
      <g {...s}>
        <path d="M16 72 L28 38 L52 24 L78 36 L86 70 L60 82 Z" fill="var(--sp-ore)" />
        <path d="M28 38 L46 52 L52 24 M46 52 L60 82 M46 52 L86 70" fill="none" strokeWidth={4} />
      </g>
    )
  }
}

/** A resource as a standalone icon. */
export function ResourceIcon({ res, size = 40, style }: { res: number; size?: number; style?: CSSProperties }) {
  return (
    <svg width={size} height={size} viewBox="0 0 100 100" style={style} aria-hidden="true" className="sp-res">
      <ResourceGlyph res={res} />
    </svg>
  )
}

/** A settlement: a little house in the seat's colour, centred on (0, 0) in board units. */
export function SettlementShape({ color, scale = 1 }: { color: string; scale?: number }) {
  return (
    <g transform={`scale(${scale})`}>
      <path d="M-22 26 V-4 L0 -26 L22 -4 V26 Z" fill={INK} transform="translate(4 5)" />
      <path d="M-22 26 V-4 L0 -26 L22 -4 V26 Z" fill={color} stroke={INK} strokeWidth="6" strokeLinejoin="round" />
      <rect x="-6" y="8" width="12" height="18" fill={INK} />
    </g>
  )
}

/** A city: a tall block with a house wing, centred on (0, 0). */
export function CityShape({ color, scale = 1 }: { color: string; scale?: number }) {
  const d = 'M-34 28 V-2 L-18 -16 L-4 -2 V-34 H30 V28 Z'
  return (
    <g transform={`scale(${scale})`}>
      <path d={d} fill={INK} transform="translate(5 6)" />
      <path d={d} fill={color} stroke={INK} strokeWidth="6" strokeLinejoin="round" />
      {[[6, -24], [18, -24], [6, -8], [18, -8]].map(([x, y]) => <rect key={`${x}${y}`} x={x} y={y} width="7" height="9" fill={INK} />)}
      <rect x="-24" y="10" width="11" height="18" fill={INK} />
    </g>
  )
}

/** The Landlord: a top-hatted silhouette with a monocle. Centred on (0, 0). */
export function LandlordShape({ scale = 1 }: { scale?: number }) {
  return (
    <g transform={`scale(${scale})`} className="sp-landlord">
      <ellipse cx="4" cy="46" rx="34" ry="10" fill={INK} opacity=".35" />
      <path d="M-26 44 Q-28 14 -12 6 Q-22 -6 -16 -20 Q-4 -34 12 -24 Q22 -14 14 4 Q30 14 28 44 Z" fill={INK} />
      <rect x="-22" y="-40" width="40" height="8" rx="3" fill={INK} stroke="var(--paper)" strokeWidth="3" />
      <rect x="-14" y="-72" width="24" height="34" rx="3" fill={INK} stroke="var(--paper)" strokeWidth="3" />
      <rect x="-14" y="-50" width="24" height="7" fill="var(--tomato)" />
      <circle cx="5" cy="-14" r="7" fill="none" stroke="var(--sun)" strokeWidth="3.5" />
      <path d="M11 -9 Q14 2 10 10" fill="none" stroke="var(--sun)" strokeWidth="2" />
    </g>
  )
}

/** The lobby cover: three hexes, a house, a road and The Landlord. */
export function SprawlCover() {
  const hex = (cx: number, cy: number, fill: string) => {
    const pts = [[0, -60], [52, -30], [52, 30], [0, 60], [-52, 30], [-52, -30]].map(([x, y]) => `${cx + x},${cy + y}`).join(' ')
    return <polygon points={pts} fill={fill} stroke={INK} strokeWidth="7" strokeLinejoin="round" />
  }
  return (
    <svg className="art" viewBox="0 0 560 330" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
      <rect width="560" height="330" fill="var(--sky)" />
      <g opacity=".22">{Array.from({ length: 12 }, (_, k) => <path key={k} d="M400 170 L1100 -40 L1100 120 Z" fill="var(--white)" transform={`rotate(${k * 30} 400 170)`} />)}</g>
      <g transform="translate(20 10)">
        {hex(330, 110, 'var(--sp-fields)')}{hex(434, 110, 'var(--sp-forest)')}{hex(382, 200, 'var(--sp-hills)')}
        <g transform="translate(330 110) scale(.7) translate(-50 -50)"><ResourceGlyph res={3} /></g>
        <g transform="translate(434 110) scale(.7) translate(-50 -50)"><ResourceGlyph res={1} /></g>
        <path d="M382 140 L434 170" stroke={INK} strokeWidth="22" strokeLinecap="round" />
        <path d="M382 140 L434 170" stroke="var(--tomato)" strokeWidth="12" strokeLinecap="round" />
        <g transform="translate(382 140)"><SettlementShape color="var(--blueberry)" scale={1.3} /></g>
        <g transform="translate(470 238)"><LandlordShape scale={0.9} /></g>
      </g>
    </svg>
  )
}
