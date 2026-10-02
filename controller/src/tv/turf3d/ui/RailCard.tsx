// controller/src/tv/turf3d/ui/RailCard.tsx
import { useEffect, useMemo, useRef, useState } from 'react'
import * as THREE from 'three'
import type { TurfTv } from '../../types'
import { Card, Plate, roundedRect } from './Card'
import { CountText } from './CountText'
import type { Person } from './copy'
import { DrinkIcon } from './DrinkIcon'
import { FaceIcon } from './FaceIcon'
import { SETUP_PHASES } from './hud'
import { Label } from './Label'
import { Pill } from './Pill'
import { RAIL, badgesFor, changeNote, pillWidth, pipPos, placeRow } from './rails'
import { GOLD, GREEN, INK, MUTED, PAPER, PAPER_DARK, RED } from './theme'

let pipGeo: THREE.ShapeGeometry | null = null
/** One square geometry shared by every owned-place square on every card. */
const sharedPip = () => (pipGeo ??= roundedRect(14, 14, 3))

function Pip({ x, y, color, hollow }: { x: number; y: number; color: string; hollow: boolean }) {
  const geo = useMemo(sharedPip, [])
  return (
    <group position={[x, y, 2]}>
      <mesh geometry={geo} scale={1.22}><meshBasicMaterial color={INK} toneMapped={false} /></mesh>
      <mesh geometry={geo} position={[0, 0, 0.3]}><meshBasicMaterial color={hollow ? PAPER : color} toneMapped={false} /></mesh>
    </group>
  )
}

const BADGE_FILL: Record<string, string> = { TIMEOUT: '#3b5bdb' }

/**
 * One player's card on a rail, kept light on purpose: their drink, name and face, big cash, a row of place squares and,
 * only when it matters, one badge (Timeout or a Get Out card). Gold behind it on their turn; dimmed and stamped OUT when bankrupt.
 */
export function RailCard({ tv, i, people, y }: { tv: TurfTv; i: number; people: Person[]; y: number }) {
  const t = tv.tokens[i]
  const prev = useRef(t?.cash ?? 0)
  const [note, setNote] = useState<{ text: string; tone: 'up' | 'down' } | null>(null)
  useEffect(() => {
    if (!t) return
    const n = changeNote(prev.current, t.cash)
    prev.current = t.cash
    if (!n) return
    setNote(n)
    const id = setTimeout(() => setNote(null), 1500)
    return () => clearTimeout(id)
  }, [t?.cash]) // eslint-disable-line react-hooks/exhaustive-deps
  if (!t) return null
  const active = tv.turn === i && !SETUP_PHASES.has(tv.phase)
  const seat = t.seat ? people.find((p) => p.id === t.seat) : undefined
  const owned = tv.owner.map((o, s) => (o === i ? s : -1)).filter((s) => s >= 0)
  const row = placeRow(owned)
  const badge = badgesFor(t)[0]
  const bw = badge ? pillWidth(badge) : 0
  const half = RAIL.w / 2
  return (
    <group position={[0, y, 0]}>
      {active && <Plate w={RAIL.w + 28} h={RAIL.h + 28} r={30} color={GOLD} z={-4} />}
      <Card w={RAIL.w} h={RAIL.h}>
        <Plate w={64} h={64} r={32} color={INK} x={-112} y={14} z={1} />
        <Plate w={56} h={56} r={28} color={t.color} x={-112} y={14} z={2} />
        <DrinkIcon piece={t.piece} color={t.color} size={38} x={-112} y={8} />
        <Label px={34} kind="body" font="display" anchorX="left" align="left" x={-70} y={44} maxWidth={170}>{t.name}</Label>
        {seat && <FaceIcon face={seat.face} color={seat.color} size={40} dim={!seat.connected} x={half - 34} y={40} />}
        <CountText value={t.cash} px={56} x={-70} y={-4} />
        {note && <Label px={28} kind="body" font="bodyBold" color={note.tone === 'up' ? GREEN : RED} anchorX="right" align="right" x={half - 16} y={-4}>{note.text}</Label>}
        {owned.length === 0 && <Label px={22} kind="label" color={MUTED} anchorX="left" align="left" x={-140} y={-46}>No places yet</Label>}
        {row.shown.map((s, k) => {
          const p = pipPos(k)
          return <Pip key={s} x={-132 + p.x} y={-46} color={tv.board[s]?.color ?? '#2b2b2b'} hollow={tv.mortgaged.includes(s)} />
        })}
        {row.more > 0 && <Label px={22} kind="label" font="bodyBold" color={MUTED} anchorX="left" align="left" x={-132 + row.shown.length * 14 - 4} y={-46}>{`+${row.more}`}</Label>}
        {badge && <Pill w={bw} h={28} text={badge} px={22} fill={BADGE_FILL[badge] ?? PAPER_DARK} textColor={BADGE_FILL[badge] ? '#ffffff' : INK} x={half - 12 - bw / 2} y={-46} />}
        {t.bankrupt && (
          <>
            <Plate w={RAIL.w} h={RAIL.h} r={20} color={PAPER} opacity={0.62} z={20} />
            <group rotation-z={0.17} position={[50, -6, 22]}><Label px={72} kind="hero" font="display" color={RED} outline={INK}>OUT</Label></group>
          </>
        )}
      </Card>
    </group>
  )
}
