import type { ReactNode } from 'react'
import { MotionConfig } from 'motion/react'
import { GameThemeContext, type GameTheme } from './gameTheme'

/** A game's environment; the party shell and player identity colors stay independent. */
export function GameScene({ game, children }: { game: GameTheme; children: ReactNode }) {
  return (
    <GameThemeContext.Provider value={game}>
      <MotionConfig reducedMotion="user"><div className="game-scene" data-game-theme={game}>{children}</div></MotionConfig>
    </GameThemeContext.Provider>
  )
}

export function GameMark({ game }: { game: 'turf' | 'sprawl' }) {
  return (
    <header className="game-mark">
      <span className="edition">PARTY OS / {game === 'turf' ? 'THE NEIGHBORHOOD' : 'THE ISLAND'}</span>
      <h1>{game === 'turf' ? <>HOME<span>TURF</span></> : 'Sprawl'}</h1>
      <p>{game === 'turf' ? 'Good neighbors. Bad landlords.' : 'A little island. Big ambitions.'}</p>
    </header>
  )
}

/** Original architectural illustration. Decorative only: ownership is always shown on the board. */
export function Neighborhood() {
  return (
    <svg className="neighborhood" viewBox="0 0 600 190" aria-hidden="true">
      <ellipse cx="300" cy="169" rx="264" ry="17" fill="#172c3c" opacity=".1" />
      <path d="M30 153 L287 109 L576 149 L308 187 Z" fill="#d1cbb6" />
      <path d="M53 151 L307 169 L552 148" fill="none" stroke="#fff8e7" strokeWidth="4" strokeDasharray="12 10" />
      {[
        [99, 55, 62, 95, '#ad5a45'], [183, 20, 70, 120, '#eadbc1'],
        [289, 54, 82, 82, '#698c7b'], [404, 7, 65, 130, '#b88958'],
      ].map(([x, y, w, h, color], i) => (
        <g key={i} transform={`translate(${x} ${y})`}>
          <path d={`M0 12 L${w} 0 L${Number(w) + 20} 12 L20 24 Z`} fill="#233b47" />
          <path d={`M${w} 0 V${h} L${Number(w) + 20} ${Number(h) + 12} V12 Z`} fill="#233b47" opacity=".65" />
          <path d={`M0 12 L${w} 0 V${h} L0 ${Number(h) + 12} Z`} fill={String(color)} />
          {[0, 1, 2].map((r) => [0, 1].map((c) => <rect key={`${r}-${c}`} x={12 + c * 26} y={28 + r * 23} width="13" height="13" rx="1" fill="#fff1c8" />))}
          <path d={`M0 ${Number(h) - 13} L${w} ${Number(h) - 25}`} stroke="#233b47" strokeWidth="7" />
        </g>
      ))}
      <g transform="translate(317 127)">
        <rect width="69" height="28" rx="7" fill="#bb6549" /><rect x="38" y="4" width="23" height="15" rx="2" fill="#f8e7c6" />
        <rect x="8" y="5" width="23" height="12" rx="2" fill="#233b47" />
        <circle cx="14" cy="28" r="7" fill="#233b47" /><circle cx="54" cy="28" r="7" fill="#233b47" />
      </g>
      {[65, 265, 509].map((x) => <g key={x} transform={`translate(${x} 124)`}><path d="M0 0 V29" stroke="#8e6848" strokeWidth="5" /><ellipse cy="-9" rx="18" ry="26" fill="#527b65" /><path d="M0 -29 V8" stroke="#c2cfad" strokeWidth="2" /></g>)}
    </svg>
  )
}
