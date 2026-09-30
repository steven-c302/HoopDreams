// controller/src/tv/turf3d/ui/panels/BuyPanel.tsx
import type { TurfTv } from '../../../types'
import { buyCall, deedFacts } from '../copy'
import { Label } from '../Label'
import { Pill } from '../Pill'
import { MUTED } from '../theme'
import { DeedHeader } from './DeedHeader'

/** A place is on offer: what it is, what it pays, and the question. */
export function BuyPanel({ tv }: { tv: TurfTv }) {
  const space = tv.board[tv.buy]
  if (!space) return null
  const facts = deedFacts(space, tv.owner[tv.buy] >= 0 ? tv.tokens[tv.owner[tv.buy]]?.name : undefined)
  const w = facts.pills.length === 3 ? 158 : 200
  const xs = facts.pills.length === 3 ? [-170, 0, 170] : [-105, 105]
  return (
    <group>
      <DeedHeader facts={facts} y={68} />
      {facts.pills.map((p, i) => <Pill key={p} w={w} text={p} x={xs[i]} y={6} />)}
      <Label px={60} kind="title" y={-52}>{buyCall(space)}</Label>
      <Label px={28} kind="body" y={-100} color={MUTED} font="bodyBold">or it goes to auction</Label>
    </group>
  )
}
