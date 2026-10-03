import type { ReactNode, SVGProps } from 'react'
import { SprawlProps } from './SprawlArt'

/** Lobby covers: each game is its own prop scene (a deed and cash, fanned cards, letter tiles...) on its own backdrop.
 *  The label strip under the scene is shared (tv.css `.cover .title`), so the picker stays consistent while the art varies. */

type Pattern = 'grid' | 'dots' | 'diamond' | 'stripes' | 'lines' | 'checker'
type Backdrop = { fill: string; pattern: Pattern; tint: string; size: number }

const BACKDROPS: Record<string, Backdrop> = {
  turf: { fill: '#2b5568', pattern: 'grid', tint: '#3d7189', size: 34 },
  sprawl: { fill: '#f2d38a', pattern: 'dots', tint: '#e2bb63', size: 26 },
  trivia: { fill: 'var(--bubblegum)', pattern: 'dots', tint: 'rgba(255,255,255,.38)', size: 26 },
  writeitdown: { fill: 'var(--tangerine)', pattern: 'lines', tint: 'rgba(255,255,255,.4)', size: 30 },
  bluff: { fill: '#e23b2e', pattern: 'checker', tint: '#ee594c', size: 44 },
  blackjack: { fill: '#1f4d36', pattern: 'diamond', tint: '#2a634a', size: 34 },
  hottype: { fill: '#eadfc2', pattern: 'grid', tint: '#d6c79c', size: 32 },
  jeopardy: { fill: '#1b1f57', pattern: 'grid', tint: '#2d3384', size: 34 },
  doodle: { fill: '#2f8f9c', pattern: 'dots', tint: '#5bb0bb', size: 24 },
  songdrop: { fill: '#2a1450', pattern: 'dots', tint: '#4b2a86', size: 26 },
  imposter: { fill: '#2c3138', pattern: 'stripes', tint: '#3a4048', size: 30 },
}
const FALLBACK: Backdrop = { fill: 'var(--grape)', pattern: 'dots', tint: 'rgba(255,255,255,.3)', size: 26 }

function PatternTile({ pattern, tint, size }: Pick<Backdrop, 'pattern' | 'tint' | 'size'>) {
  const s = size
  switch (pattern) {
    case 'grid': return <path d={`M${s} 0H0V${s}`} fill="none" stroke={tint} strokeWidth="2" />
    case 'dots': return <circle cx={s / 2} cy={s / 2} r={s / 5} fill={tint} />
    case 'diamond': return <path d={`M${s / 2} 3L${s - 3} ${s / 2}L${s / 2} ${s - 3}L3 ${s / 2}Z`} fill={tint} />
    case 'stripes': return <rect width={s / 2} height={s} fill={tint} />
    case 'lines': return <rect y={s - 3} width={s} height="3" fill={tint} />
    case 'checker': return <><rect width={s / 2} height={s / 2} fill={tint} /><rect x={s / 2} y={s / 2} width={s / 2} height={s / 2} fill={tint} /></>
  }
}

/** Fills the scene at any size: no viewBox, so the tile stays the same size in pixels. */
function Ground({ id, b }: { id: string; b: Backdrop }) {
  const pid = `cover-pat-${id}`
  return (
    <svg className="ground" aria-hidden="true">
      <defs>
        <pattern id={pid} width={b.size} height={b.size} patternUnits="userSpaceOnUse" patternTransform={b.pattern === 'stripes' ? 'rotate(-35)' : undefined}>
          <PatternTile pattern={b.pattern} tint={b.tint} size={b.size} />
        </pattern>
      </defs>
      <rect width="100%" height="100%" fill={b.fill} />
      <rect width="100%" height="100%" fill={`url(#${pid})`} />
    </svg>
  )
}

const ST = { stroke: 'var(--ink)', strokeWidth: 3, strokeLinejoin: 'round' } as const

const cardText: SVGProps<SVGTextElement> = { fontFamily: "'Playfair Display', Georgia, serif", fontWeight: 900, fontSize: 26 }

/** Props are drawn in a 240x112 box; the scene fits it in the space above the label. */
const SCENES: Record<string, ReactNode> = {
  turf: (
    <>
      <g transform="rotate(-6 80 55)">
        <rect x="34" y="8" width="84" height="92" rx="5" fill="var(--paper)" {...ST} />
        <rect x="34" y="8" width="84" height="26" rx="5" fill="var(--tomato)" {...ST} />
        <path d="M60 78L76 62L92 78V92H60Z" fill="var(--sun)" {...ST} />
        <path d="M46 46H106M46 54H92" stroke="var(--ink)" strokeWidth="3" strokeLinecap="round" />
      </g>
      <g transform="rotate(5 170 60)">
        <rect x="140" y="30" width="62" height="34" rx="4" fill="var(--lime)" {...ST} /><circle cx="171" cy="47" r="9" fill="#bff0cf" {...ST} />
        <rect x="146" y="60" width="62" height="34" rx="4" fill="var(--lime)" {...ST} /><circle cx="177" cy="77" r="9" fill="#bff0cf" {...ST} />
      </g>
    </>
  ),
  sprawl: <g transform="translate(120 56) scale(.45) translate(-405 -172)"><SprawlProps /></g>,
  trivia: <g transform="translate(120 56) scale(.58) translate(-110 -84)"><BrainyInline /></g>,
  writeitdown: (
    <>
      <g transform="rotate(-5 100 56)">
        <rect x="52" y="4" width="98" height="102" rx="5" fill="var(--white)" {...ST} />
        {[30, 50, 70, 90].map((y) => <path key={y} d={`M62 ${y}H140`} stroke="var(--sky)" strokeWidth="3" strokeLinecap="round" />)}
        <path d="M62 28Q80 14 98 30T134 26" fill="none" stroke="var(--ink)" strokeWidth="3.5" strokeLinecap="round" />
        <path d="M62 48Q84 36 104 50" fill="none" stroke="var(--ink)" strokeWidth="3.5" strokeLinecap="round" />
      </g>
      <g transform="rotate(38 188 50)">
        <rect x="180" y="-10" width="16" height="84" fill="var(--sun)" {...ST} />
        <rect x="180" y="-24" width="16" height="16" rx="3" fill="var(--bubblegum)" {...ST} />
        <path d="M180 74L188 92L196 74Z" fill="var(--paper)" {...ST} /><path d="M185 84L188 92L191 84Z" fill="var(--ink)" />
      </g>
    </>
  ),
  bluff: (
    <g transform="translate(120 52) scale(.62) translate(0 6)">
      <path d="M-110 -40Q0 -120 110 -40Q120 60 0 90Q-120 60-110-40Z" fill="var(--sun)" stroke="var(--ink)" strokeWidth="7" />
      <path d="M-80 -30Q-45 -60 -10 -30Q-45 -10 -80 -30ZM10 -30Q45 -60 80 -30Q45 -10 10 -30Z" fill="var(--ink)" />
      <path d="M-60 30Q0 70 60 30" fill="none" stroke="var(--ink)" strokeWidth="7" strokeLinecap="round" />
      <path d="M0 -8L0 20L80 60" fill="none" stroke="var(--ink)" strokeWidth="7" strokeLinecap="round" strokeLinejoin="round" />
    </g>
  ),
  blackjack: (
    <>
      <g transform="rotate(-14 100 60)"><rect x="60" y="10" width="62" height="86" rx="7" fill="#fffbe8" {...ST} /><text x="70" y="40" {...cardText} fill="var(--ink)">K</text></g>
      <g transform="rotate(8 130 60)">
        <rect x="98" y="8" width="62" height="86" rx="7" fill="#fffbe8" {...ST} /><text x="108" y="38" {...cardText} fill="var(--tomato)">A</text><circle cx="129" cy="62" r="14" fill="var(--tomato)" />
      </g>
      <g>
        <ellipse cx="196" cy="86" rx="24" ry="9" fill="var(--tomato)" {...ST} /><rect x="172" y="70" width="48" height="16" fill="var(--tomato)" />
        <ellipse cx="196" cy="70" rx="24" ry="9" fill="#fff" {...ST} /><ellipse cx="196" cy="54" rx="24" ry="9" fill="var(--tomato)" {...ST} />
      </g>
    </>
  ),
  hottype: (
    <>
      <g transform="rotate(-5 120 55)">
        {(['H', 'O', 'T'] as const).map((ch, i) => (
          <g key={ch}>
            <rect x={24 + i * 60} y="24" width="52" height="52" rx="6" fill="#f6e7b9" {...ST} />
            <text x={50 + i * 60} y="62" textAnchor="middle" fontFamily="'Alfa Slab One', Georgia, serif" fontSize="34" fill="var(--ink)">{ch}</text>
          </g>
        ))}
      </g>
      <g transform="rotate(-12 180 78)">
        <rect x="140" y="62" width="82" height="30" rx="4" fill="none" stroke="#c4432f" strokeWidth="4" />
        <text x="181" y="84" textAnchor="middle" fontFamily="Anton, Impact, sans-serif" fontSize="20" fill="#c4432f">+12</text>
      </g>
    </>
  ),
  jeopardy: (
    <>
      <rect x="28" y="8" width="184" height="88" rx="8" fill="#0e1240" {...ST} />
      <g fontFamily="Anton, Impact, sans-serif" fontSize="19" fill="#f2c14e" textAnchor="middle">
        {[0, 1].flatMap((row) => [0, 1, 2, 3].map((col) => {
          const x = 36 + col * 46
          const y = 16 + row * 38
          const hot = row === 1 && col === 1
          return (
            <g key={`${row}-${col}`}>
              <rect x={x} y={y} width={col === 3 ? 30 : 40} height="34" rx="3" fill={hot ? '#f2c14e' : '#2b31a0'} />
              <text x={x + (col === 3 ? 15 : 20)} y={y + 25} fill={hot ? 'var(--ink)' : '#f2c14e'} fontSize={col === 3 ? 13 : 19}>{hot ? '?' : `$${(col + 1) * 200}`}</text>
            </g>
          )
        }))}
      </g>
    </>
  ),
  doodle: (
    <>
      <g transform="rotate(4 120 55)">
        <rect x="50" y="6" width="112" height="92" rx="5" fill="#fffaf0" {...ST} />
        <g fill="var(--ink)">{[22, 44, 66, 88].map((y) => <circle key={y} cx="50" cy={y} r="4" />)}</g>
        <path d="M70 74C74 30 88 30 92 56S112 84 140 30" fill="none" stroke="var(--ink)" strokeWidth="5" strokeLinecap="round" />
      </g>
      <g transform="rotate(38 200 50)"><rect x="192" y="0" width="16" height="84" fill="var(--tangerine)" {...ST} /><path d="M192 84L200 100L208 84Z" fill="#fff7e6" {...ST} /></g>
    </>
  ),
  songdrop: (
    <>
      <g transform="rotate(-8 90 60)">
        <circle cx="90" cy="60" r="46" fill="#15091f" {...ST} />
        <circle cx="90" cy="60" r="34" fill="none" stroke="#3a2158" strokeWidth="3" />
        <circle cx="90" cy="60" r="22" fill="none" stroke="#3a2158" strokeWidth="3" />
        <circle cx="90" cy="60" r="14" fill="#ff4fa3" {...ST} />
        <circle cx="90" cy="60" r="3" fill="var(--ink)" />
      </g>
      <g transform="rotate(10 178 52)">
        <path d="M168 78V30L196 24V72" fill="none" stroke="var(--paper)" strokeWidth="7" strokeLinecap="round" strokeLinejoin="round" />
        <ellipse cx="160" cy="80" rx="12" ry="9" fill="var(--paper)" {...ST} />
        <ellipse cx="188" cy="74" rx="12" ry="9" fill="var(--paper)" {...ST} />
      </g>
    </>
  ),
  imposter: (
    <>
      <path d="M36 22H90L100 10H196Q204 10 204 18V96H36Z" fill="#e0bf7d" {...ST} />
      <rect x="48" y="30" width="146" height="60" rx="3" fill="#fffaf0" {...ST} />
      <rect x="58" y="42" width="70" height="9" fill="var(--ink)" /><rect x="58" y="58" width="100" height="9" fill="var(--ink)" /><rect x="58" y="74" width="54" height="9" fill="var(--ink)" />
      <g transform="rotate(-10 170 70)"><circle cx="170" cy="66" r="24" fill="#d9a441" {...ST} /><text x="170" y="77" textAnchor="middle" fontFamily="Anton, Impact, sans-serif" fontSize="32" fill="var(--ink)">?</text></g>
    </>
  ),
}

const FALLBACK_SCENE = (
  <g transform="rotate(-6 120 56)">
    <rect x="80" y="8" width="80" height="96" rx="8" fill="var(--paper)" {...ST} />
    <text x="120" y="76" textAnchor="middle" fontFamily="var(--font-display)" fontSize="56" fill="var(--ink)">?</text>
  </g>
)

export function CoverArt({ id }: { id: string }) {
  return (
    <div className="scene" aria-hidden="true">
      <Ground id={id} b={BACKDROPS[id] ?? FALLBACK} />
      <svg className="props" viewBox="0 0 240 112" preserveAspectRatio="xMidYMid meet">{SCENES[id] ?? FALLBACK_SCENE}</svg>
    </div>
  )
}

/** Brainy at cover scale (the host component draws an <svg>, so the cover inlines its own copy). */
function BrainyInline() {
  return (
    <g>
      <path d="M150 58 L158 10 L196 -4" fill="none" stroke="var(--ink)" strokeWidth="20" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M150 58 L158 10 L196 -4" fill="none" stroke="var(--white)" strokeWidth="10" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M150 58 L158 10 L196 -4" fill="none" stroke="var(--tomato)" strokeWidth="10" strokeDasharray="7 9" strokeLinejoin="round" />
      <path d="M45 124 C15 122 12 84 36 74 C30 46 60 30 84 42 C92 18 132 16 142 38 C166 26 196 46 188 72 C212 82 208 120 184 126 C186 150 160 164 140 154 C128 172 92 172 82 156 C60 166 36 152 45 124 Z"
        fill="var(--paper)" stroke="var(--ink)" strokeWidth="7" strokeLinejoin="round" />
      <circle cx="100" cy="102" r="17" fill="var(--white)" stroke="var(--ink)" strokeWidth="5" /><circle cx="138" cy="102" r="17" fill="var(--white)" stroke="var(--ink)" strokeWidth="5" />
      <circle cx="102" cy="106" r="7.5" fill="var(--ink)" /><circle cx="140" cy="106" r="7.5" fill="var(--ink)" />
      <path d="M104 134 Q119 152 134 134 Z" fill="var(--white)" stroke="var(--ink)" strokeWidth="5" strokeLinejoin="round" />
    </g>
  )
}
