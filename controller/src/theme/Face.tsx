import type { CSSProperties, ReactNode } from 'react'

/** How many preset faces exist (`p:00`..`p:15`); keep in step with PRESET_FACES in PartyEngine.kt. */
export const PRESET_FACES = 16

type Eyes = 'dots' | 'happy' | 'wide' | 'sleepy' | 'wink' | 'shades' | 'angry' | 'surprised'
type Mouth = 'smile' | 'grin' | 'o' | 'flat' | 'tongue' | 'smirk' | 'teeth' | 'wavy'
type Extra = 'blush' | 'freckles' | 'unibrow' | 'mustache' | null

const PRESETS: [Eyes, Mouth, Extra][] = [
  ['dots', 'smile', 'blush'], ['happy', 'grin', null], ['wide', 'o', null], ['shades', 'smirk', null],
  ['wink', 'tongue', null], ['sleepy', 'flat', null], ['angry', 'teeth', null], ['dots', 'wavy', 'freckles'],
  ['wide', 'grin', 'unibrow'], ['happy', 'tongue', 'blush'], ['surprised', 'o', null], ['dots', 'smirk', 'mustache'],
  ['shades', 'grin', null], ['wide', 'wavy', null], ['happy', 'smile', 'freckles'], ['sleepy', 'smirk', 'blush'],
]

const line = { fill: 'none', stroke: 'var(--ink)', strokeWidth: 5, strokeLinecap: 'round', strokeLinejoin: 'round' } as const
const solid = { fill: 'var(--ink)' } as const

function eyes(kind: Eyes): ReactNode {
  switch (kind) {
    case 'dots': return <><circle cx="37" cy="43" r="5.5" {...solid} /><circle cx="63" cy="43" r="5.5" {...solid} /></>
    case 'happy': return <><path d="M29 46 Q37 35 45 46" {...line} /><path d="M55 46 Q63 35 71 46" {...line} /></>
    case 'wide': return <><circle cx="37" cy="42" r="10" fill="var(--white)" stroke="var(--ink)" strokeWidth="4" /><circle cx="63" cy="42" r="10" fill="var(--white)" stroke="var(--ink)" strokeWidth="4" />
      <circle cx="39" cy="44" r="4.5" {...solid} /><circle cx="61" cy="44" r="4.5" {...solid} /></>
    case 'sleepy': return <><path d="M29 44 Q37 50 45 44" {...line} /><path d="M55 44 Q63 50 71 44" {...line} /></>
    case 'wink': return <><circle cx="37" cy="43" r="5.5" {...solid} /><path d="M55 45 Q63 37 71 45" {...line} /></>
    case 'shades': return <><rect x="24" y="36" width="23" height="14" rx="6" {...solid} /><rect x="53" y="36" width="23" height="14" rx="6" {...solid} />
      <path d="M47 41 L53 41" {...line} /><path d="M28 39 L34 39" stroke="var(--white)" strokeWidth="3" strokeLinecap="round" /></>
    case 'angry': return <><circle cx="37" cy="46" r="5" {...solid} /><circle cx="63" cy="46" r="5" {...solid} /><path d="M28 34 L44 40" {...line} /><path d="M72 34 L56 40" {...line} /></>
    case 'surprised': return <><circle cx="37" cy="45" r="4.5" {...solid} /><circle cx="63" cy="45" r="4.5" {...solid} /><path d="M29 32 Q37 27 45 32" {...line} /><path d="M55 32 Q63 27 71 32" {...line} /></>
  }
}

function mouth(kind: Mouth): ReactNode {
  switch (kind) {
    case 'smile': return <path d="M34 62 Q50 76 66 62" {...line} />
    case 'grin': return <path d="M31 59 Q50 84 69 59 Z" fill="var(--white)" stroke="var(--ink)" strokeWidth="5" strokeLinejoin="round" />
    case 'o': return <ellipse cx="50" cy="67" rx="7" ry="8.5" {...solid} />
    case 'flat': return <path d="M38 66 L62 66" {...line} />
    case 'tongue': return <><path d="M44 68 Q44 80 51 80 Q58 80 58 68 Z" fill="var(--bubblegum)" stroke="var(--ink)" strokeWidth="4" /><path d="M33 63 Q50 74 67 63" {...line} /></>
    case 'smirk': return <path d="M38 67 Q56 72 66 60" {...line} />
    case 'teeth': return <><rect x="34" y="60" width="32" height="14" rx="4" fill="var(--white)" stroke="var(--ink)" strokeWidth="4" /><path d="M45 60 V74 M55 60 V74" stroke="var(--ink)" strokeWidth="3" /></>
    case 'wavy': return <path d="M34 66 L40 62 L46 67 L52 62 L58 67 L64 62" {...line} strokeWidth={4.5} />
  }
}

function extra(kind: Extra): ReactNode {
  switch (kind) {
    case 'blush': return <><ellipse cx="26" cy="58" rx="7" ry="4.5" fill="var(--bubblegum)" opacity=".8" /><ellipse cx="74" cy="58" rx="7" ry="4.5" fill="var(--bubblegum)" opacity=".8" /></>
    case 'freckles': return <>{[[27, 55], [32, 59], [23, 60], [73, 55], [68, 59], [77, 60]].map(([x, y]) => <circle key={`${x}-${y}`} cx={x} cy={y} r="1.8" {...solid} />)}</>
    case 'unibrow': return <path d="M27 29 Q50 22 73 29" {...line} strokeWidth={6} />
    case 'mustache': return <path d="M50 60 Q42 54 33 60 Q40 66 50 61 Q60 66 67 60 Q58 54 50 60 Z" {...solid} />
    default: return null
  }
}

export function presetIndex(face: string): number | null {
  const m = /^p:(\d{2})$/.exec(face)
  return m ? Number(m[1]) % PRESET_FACES : null
}

/** A player's face: a filled circle in their colour, ink outline, with a preset cartoon face or their own doodle. */
export function Face({ face, color, size = 56, dim = false, className = '', style }: {
  face: string; color: string; size?: number; dim?: boolean; className?: string; style?: CSSProperties
}) {
  const drawn = face.startsWith('d:') ? face.slice(2) : null
  const [e, m, x] = PRESETS[presetIndex(face) ?? 0]
  return (
    <svg className={`face ${className}`} viewBox="-4 -4 108 108" width={size} height={size} aria-hidden="true"
      style={{ flex: 'none', opacity: dim ? 0.35 : 1, overflow: 'visible', ...style }}>
      <circle cx="50" cy="50" r="48" fill={color} stroke="var(--ink)" strokeWidth="6" />
      {drawn ? <path d={drawn} {...line} strokeWidth={4.5} /> : <>{extra(x)}{eyes(e)}{mouth(m)}</>}
    </svg>
  )
}
