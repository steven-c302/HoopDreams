import { motion } from 'motion/react'
import type { CSSProperties, ReactNode } from 'react'
import { Buildings, Piece } from './TurfArt'
import type { TurfTv } from './types'

/** The board: a 1000px square, 124px corners, nine spaces per side, Payday at bottom right, going clockwise. */
export const BOARD = 1000
export const CORNER = 124
export const EDGE = (BOARD - 2 * CORNER) / 9
export const WELL = BOARD - 2 * CORNER

type Side = 'b' | 'l' | 't' | 'r' | 'corner'
export interface Rect { x: number; y: number; w: number; h: number; side: Side }

export function spaceRect(i: number): Rect {
  const far = BOARD - CORNER
  if (i === 0) return { x: far, y: far, w: CORNER, h: CORNER, side: 'corner' }
  if (i < 10) return { x: far - i * EDGE, y: far, w: EDGE, h: CORNER, side: 'b' }
  if (i === 10) return { x: 0, y: far, w: CORNER, h: CORNER, side: 'corner' }
  if (i < 20) return { x: 0, y: far - (i - 10) * EDGE, w: CORNER, h: EDGE, side: 'l' }
  if (i === 20) return { x: 0, y: 0, w: CORNER, h: CORNER, side: 'corner' }
  if (i < 30) return { x: CORNER + (i - 21) * EDGE, y: 0, w: EDGE, h: CORNER, side: 't' }
  if (i === 30) return { x: far, y: 0, w: CORNER, h: CORNER, side: 'corner' }
  return { x: far, y: CORNER + (i - 31) * EDGE, w: CORNER, h: EDGE, side: 'r' }
}

export function spaceCenter(i: number) {
  const r = spaceRect(i)
  return { x: r.x + r.w / 2, y: r.y + r.h / 2 }
}

/** Where a resting piece sits: the space's outer strip, clear of the name (which hugs the colour band). */
function restPoint(i: number, size: number) {
  const r = spaceRect(i)
  const pad = size / 2 + 6
  switch (r.side) {
    case 'b': return { x: r.x + r.w / 2, y: r.y + r.h - pad }
    case 't': return { x: r.x + r.w / 2, y: r.y + pad }
    case 'l': return { x: r.x + (r.w - 26) / 2, y: r.y + r.h - pad + 4 }
    case 'r': return { x: r.x + 26 + (r.w - 26) / 2, y: r.y + r.h - pad + 4 }
    default: return spaceCenter(i)
  }
}

/** Where piece [k] of [n] on the same space sits, so a crowd doesn't stack into one coin. */
function crowdOffset(k: number, n: number, size: number) {
  if (n <= 1) return { dx: 0, dy: 0 }
  const cols = n <= 2 ? 2 : 3
  const col = k % cols, row = Math.floor(k / cols)
  const rows = Math.ceil(n / cols)
  return { dx: (col - (cols - 1) / 2) * size * 0.62, dy: (row - (rows - 1) / 2) * size * 0.62 }
}

const CORNER_ICON: Record<string, ReactNode> = {
  payday: <svg viewBox="0 0 60 40" width="74" aria-hidden="true"><path d="M52 20 H10 M22 8 L8 20 L22 32" fill="none" stroke="var(--tomato)" strokeWidth="8" strokeLinecap="round" strokeLinejoin="round" /></svg>,
  jail: <svg viewBox="0 0 60 50" width="62" aria-hidden="true"><rect x="4" y="4" width="52" height="42" rx="6" fill="var(--sky)" stroke="var(--ink)" strokeWidth="5" />{[16, 26, 36, 46].map((x) => <path key={x} d={`M${x} 6 V44`} stroke="var(--ink)" strokeWidth="4" />)}</svg>,
  couch: <svg viewBox="0 0 70 44" width="76" aria-hidden="true"><rect x="10" y="8" width="50" height="20" rx="6" fill="var(--bubblegum)" stroke="var(--ink)" strokeWidth="4" /><rect x="4" y="20" width="62" height="16" rx="6" fill="var(--bubblegum)" stroke="var(--ink)" strokeWidth="4" /><path d="M10 36 V42 M60 36 V42" stroke="var(--ink)" strokeWidth="4" strokeLinecap="round" /></svg>,
  gotojail: <svg viewBox="0 0 60 50" width="62" aria-hidden="true"><circle cx="30" cy="22" r="16" fill="var(--blueberry)" stroke="var(--ink)" strokeWidth="5" /><rect x="14" y="34" width="32" height="10" rx="3" fill="var(--ink)" /><circle cx="30" cy="22" r="6" fill="var(--white)" /></svg>,
}

function SpaceGlyph({ kind }: { kind: string }) {
  switch (kind) {
    case 'chance': return <span className="glyph q">?</span>
    case 'chest': return <svg className="glyph" viewBox="0 0 40 34" width="36" aria-hidden="true"><path d="M4 4 H36 V24 H16 L8 31 V24 H4 Z" fill="var(--bubblegum)" stroke="var(--ink)" strokeWidth="4" strokeLinejoin="round" /><circle cx="13" cy="14" r="2.6" fill="var(--ink)" /><circle cx="20" cy="14" r="2.6" fill="var(--ink)" /><circle cx="27" cy="14" r="2.6" fill="var(--ink)" /></svg>
    case 'railroad': return <svg className="glyph" viewBox="0 0 44 30" width="40" aria-hidden="true"><rect x="3" y="4" width="38" height="18" rx="4" fill="var(--sun)" stroke="var(--ink)" strokeWidth="3.5" /><rect x="8" y="8" width="8" height="6" fill="var(--sky)" /><rect x="20" y="8" width="8" height="6" fill="var(--sky)" /><circle cx="12" cy="24" r="4" fill="var(--ink)" /><circle cx="32" cy="24" r="4" fill="var(--ink)" /></svg>
    case 'utility': return <svg className="glyph" viewBox="0 0 40 30" width="38" aria-hidden="true"><path d="M4 12 Q20 -2 36 12 M10 18 Q20 9 30 18" fill="none" stroke="var(--grape)" strokeWidth="4.5" strokeLinecap="round" /><circle cx="20" cy="24" r="4" fill="var(--grape)" /></svg>
    case 'tax': return <span className="glyph q">$</span>
    default: return null
  }
}

/** One space: colour band on the inside edge, label, price, owner coin, buildings, mortgage hatch. */
function SpaceTile({ tv, i, hot }: { tv: TurfTv; i: number; hot: boolean }) {
  const s = tv.board[i]
  const r = spaceRect(i)
  const owner = tv.owner[i] ?? -1
  const tok = owner >= 0 ? tv.tokens[owner] : undefined
  const mortgaged = tv.mortgaged.includes(i)
  const style: CSSProperties = { left: r.x, top: r.y, width: r.w, height: r.h }
  if (r.side === 'corner') {
    return (
      <div className={`turf-space corner ${hot ? 'hot' : ''}`} style={style}>
        {CORNER_ICON[s.kind]}
        <span className="label">{s.label}</span>
        {s.kind === 'jail' && <span className="sub">just visiting</span>}
        {s.kind === 'payday' && <span className="sub">collect $200</span>}
      </div>
    )
  }
  return (
    <div className={`turf-space side-${r.side} ${hot ? 'hot' : ''} ${mortgaged ? 'mortgaged' : ''}`} style={style}>
      {s.kind === 'street' && (
        <div className="band" style={{ background: s.color }}>
          <Buildings level={tv.level[i] ?? 0} size={r.side === 'l' || r.side === 'r' ? 15 : 16} />
        </div>
      )}
      <div className="body">
        <SpaceGlyph kind={s.kind} />
        <span className="label">{s.label}</span>
        {tok ? <Piece piece={tok.piece} color={tok.color} size={24} className="owner-coin" />
          : s.price > 0 && <span className="price">${s.price}</span>}
        {s.kind === 'tax' && <span className="price">${s.tax}</span>}
      </div>
      {mortgaged && <span className="mortgage-tag">M</span>}
    </div>
  )
}

/**
 * The whole board. [display] is each piece's shown space (it hops ahead of the server during a move); [zoom] is the
 * space the camera closes in on; [hot] highlights a space (on offer, just landed); [children] fills the middle well.
 */
export function TurfBoard({ tv, display, zoom, hot, children }: { tv: TurfTv; display: number[]; zoom: number | null; hot?: number; children?: ReactNode }) {
  const focus = zoom != null ? spaceCenter(zoom) : { x: BOARD / 2, y: BOARD / 2 }
  const crowd = new Map<number, number[]>()
  tv.tokens.forEach((t, k) => { if (!t.bankrupt) crowd.set(display[k] ?? t.pos, [...(crowd.get(display[k] ?? t.pos) ?? []), k]) })
  const pieceSize = 42
  return (
    <div className="turf-viewport" style={{ width: BOARD, height: BOARD }}>
      <motion.div className="turf-board" style={{ width: BOARD, height: BOARD, transformOrigin: `${focus.x}px ${focus.y}px` }}
        animate={{ scale: zoom != null ? 1.55 : 1 }} transition={{ type: 'spring', stiffness: 140, damping: 22 }}>
        {tv.board.map((_, i) => <SpaceTile key={i} tv={tv} i={i} hot={hot === i} />)}
        <div className="turf-well" style={{ left: CORNER, top: CORNER, width: WELL, height: WELL }}>{children}</div>
        {tv.tokens.map((t, k) => {
          if (t.bankrupt) return null
          const at = display[k] ?? t.pos
          const c = restPoint(at, pieceSize)
          const here = crowd.get(at) ?? [k]
          const { dx, dy } = crowdOffset(here.indexOf(k), here.length, pieceSize)
          return (
            <motion.div key={k} className={`turf-token ${tv.turn === k ? 'active' : ''}`}
              initial={false}
              animate={{ left: c.x + dx - pieceSize / 2, top: c.y + dy - pieceSize / 2 }}
              transition={{ type: 'spring', stiffness: 520, damping: 30 }}>
              {/* Remounts on every new space, so each step is one hop. */}
              <motion.div key={at} initial={{ y: 0 }} animate={{ y: [0, -26, 0] }} transition={{ duration: 0.24, ease: 'easeOut' }}>
                <Piece piece={t.piece} color={t.color} size={pieceSize} />
              </motion.div>
              {t.jailed && <span className="bars" />}
            </motion.div>
          )
        })}
      </motion.div>
    </div>
  )
}
