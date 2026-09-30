// controller/src/tv/turf3d/ui/panels/TradePanel.tsx
import { useEffect, useMemo, type ReactNode } from 'react'
import * as THREE from 'three'
import type { TurfTv } from '../../../types'
import { deedTags, money, tradeTitle } from '../copy'
import { DeedTag } from '../DeedTag'
import { DrinkIcon } from '../DrinkIcon'
import { Label } from '../Label'
import { Pill } from '../Pill'
import { GOLD, INK, MUTED, PAPER_DARK } from '../theme'

/** A chunky arrow pointing right (or left when [dir] is -1), drawn from a shape so no font glyph is involved. */
function Arrow({ dir, y }: { dir: 1 | -1; y: number }) {
  const geo = useMemo(() => {
    const s = new THREE.Shape()
    const pts: [number, number][] = [[-34, -9], [8, -9], [8, -24], [36, 0], [8, 24], [8, 9], [-34, 9]]
    pts.forEach(([x, py], i) => { const px = x * dir; if (i === 0) s.moveTo(px, py); else s.lineTo(px, py) })
    s.closePath()
    return new THREE.ShapeGeometry(s)
  }, [dir])
  useEffect(() => () => geo.dispose(), [geo])
  return <mesh geometry={geo} position={[0, y, 2]}><meshBasicMaterial color={INK} toneMapped={false} side={THREE.DoubleSide} /></mesh>
}

function Side({ tv, token, deeds, cash, cards, x }: { tv: TurfTv; token: number; deeds: number[]; cash: number; cards: number; x: number }) {
  const t = tv.tokens[token]
  if (!t) return null
  const { tags, more } = deedTags(deeds, tv.board, tv.mortgaged)
  const rows: ReactNode[] = []
  let y = 62
  tags.forEach((tag) => { rows.push(<DeedTag key={tag.name} w={300} name={tag.name} band={tag.band} y={y} />); y -= 42 })
  if (more > 0) { rows.push(<Pill key="more" w={130} h={34} text={`+${more} more`} fill={PAPER_DARK} y={y} />); y -= 42 }
  if (cash > 0) { rows.push(<Pill key="cash" w={150} h={34} text={money(cash)} fill={GOLD} y={y} />); y -= 42 }
  if (cards > 0) { rows.push(<Pill key="cards" w={230} h={34} text={`Get Out card x${cards}`} fill={PAPER_DARK} y={y} />); y -= 42 }
  if (rows.length === 0) rows.push(<Label key="none" px={28} kind="body" y={62} color={MUTED}>nothing</Label>)
  return (
    <group position={[x, 0, 0]}>
      <DrinkIcon piece={t.piece} color={t.color} size={46} x={-150} y={114} />
      <Label px={30} kind="body" font="bodyBold" anchorX="left" align="left" x={-116} y={116} maxWidth={250}>{`${t.name} gives`}</Label>
      {rows}
    </group>
  )
}

/** A trade offer: what each side gives, with an arrow each way between them. */
export function TradePanel({ tv }: { tv: TurfTv }) {
  const t = tv.trade
  if (!t) return null
  return (
    <group>
      <Label px={60} kind="title" y={196}>{tradeTitle(t.counters)}</Label>
      <Side tv={tv} token={t.from} deeds={t.give} cash={t.giveCash} cards={t.giveCards} x={-208} />
      <Arrow dir={1} y={40} />
      <Arrow dir={-1} y={-30} />
      <Side tv={tv} token={t.to} deeds={t.get} cash={t.getCash} cards={t.getCards} x={208} />
      <Label px={28} kind="body" y={-208} color={MUTED} font="bodyBold" maxWidth={740}>{`${tv.tokens[t.to]?.name ?? ''} decides on their phone. Heckle freely.`}</Label>
    </group>
  )
}
