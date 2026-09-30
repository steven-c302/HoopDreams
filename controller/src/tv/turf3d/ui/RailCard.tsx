// controller/src/tv/turf3d/ui/RailCard.tsx
import { useEffect, useMemo, useRef, useState } from 'react'
import * as THREE from 'three'
import type { TurfTv } from '../../types'
import { Card, Plate, roundedRect } from './Card'
import { CountText } from './CountText'
import { money, type Person } from './copy'
import { DrinkIcon } from './DrinkIcon'
import { FaceIcon } from './FaceIcon'
import { SETUP_PHASES } from './hud'
import { Label } from './Label'
import { Pill } from './Pill'
import { RAIL, badgesFor, changeNote, flowPills, pipPos } from './rails'
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

const BADGE_FILL: Record<string, string> = { 'IN TIMEOUT': '#3b5bdb' }

/** One player's card on a rail: who, cash (counting), worth, places, badges; gold behind it on their turn, dimmed and stamped OUT when bankrupt. */
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
  const members = t.members.map((id) => people.find((p) => p.id === id)).filter((p): p is Person => !!p)
  const owned = tv.owner.map((o, s) => (o === i ? s : -1)).filter((s) => s >= 0)
  const badges = badgesFor(t)
  const flow = flowPills(badges, RAIL.w - 48)
  const left = -RAIL.w / 2 + 24
  return (
    <group position={[0, y, 0]}>
      {active && <Plate w={RAIL.w + 30} h={RAIL.h + 30} r={32} color={GOLD} z={-4} />}
      <Card w={RAIL.w} h={RAIL.h}>
        <Plate w={70} h={70} r={35} color={INK} x={-152} y={78} z={1} />
        <Plate w={62} h={62} r={31} color={t.color} x={-152} y={78} z={2} />
        <DrinkIcon piece={t.piece} color={t.color} size={44} x={-152} y={72} />
        <Label px={40} kind="body" font="display" anchorX="left" align="left" x={-102} y={96} maxWidth={250}>{t.name}</Label>
        {tv.teams
          ? members.slice(0, 5).map((p, k) => <FaceIcon key={p.id} face={p.face} color={p.color} size={p.id === t.seat ? 40 : 30} dim={!p.connected} x={-82 + k * 38} y={58} />)
          : seat && <FaceIcon face={seat.face} color={seat.color} size={40} dim={!seat.connected} x={-82} y={58} />}
        <CountText value={t.cash} px={60} x={left} y={-2} />
        <Label px={22} kind="label" font="bodyBold" color={MUTED} anchorX="right" align="right" x={RAIL.w / 2 - 24} y={-8}>{`WORTH ${money(t.worth)}`}</Label>
        {note && <Label px={28} kind="body" font="bodyBold" color={note.tone === 'up' ? GREEN : RED} anchorX="right" align="right" x={RAIL.w / 2 - 24} y={24}>{note.text}</Label>}
        {owned.length === 0 && <Label px={22} kind="label" color={MUTED} anchorX="left" align="left" x={left} y={-50}>No places yet</Label>}
        {owned.slice(0, 40).map((s, k) => {
          const p = pipPos(k)
          return <Pip key={s} x={left + 7 + p.x} y={-50 - p.row * 19} color={tv.board[s]?.color ?? '#2b2b2b'} hollow={tv.mortgaged.includes(s)} />
        })}
        {badges.map((b, k) => (
          <Pill key={b} w={flow[k].w} h={30} text={b} px={22} fill={BADGE_FILL[b] ?? (b.startsWith('GET') ? PAPER_DARK : GOLD)} textColor={BADGE_FILL[b] ? '#ffffff' : INK} x={left + flow[k].x + flow[k].w / 2} y={-104 - flow[k].row * 36} />
        ))}
        {t.bankrupt && (
          <>
            <Plate w={RAIL.w} h={RAIL.h} r={20} color={PAPER} opacity={0.62} z={20} />
            <group rotation-z={0.17} position={[70, -20, 22]}><Label px={96} kind="hero" font="display" color={RED} outline={INK}>OUT</Label></group>
          </>
        )}
      </Card>
    </group>
  )
}
