// controller/src/tv/turf3d/ui/panels/DebtPanel.tsx
import type { TurfTv } from '../../../types'
import { Plate } from '../Card'
import { debtLine, debtTo } from '../copy'
import { DrinkIcon } from '../DrinkIcon'
import { Label } from '../Label'
import { GREEN, INK, LED_BG, MUTED, RED } from '../theme'

/** Someone owes money: who, how much, to whom and why, their cash against it, and what happens next. */
export function DebtPanel({ tv }: { tv: TurfTv }) {
  const d = tv.debt
  const t = d ? tv.tokens[d.token] : undefined
  if (!d || !t) return null
  const tone = t.cash >= d.amount ? GREEN : RED
  return (
    <group>
      <DrinkIcon piece={t.piece} color={t.color} size={52} x={-262} y={62} />
      <Label px={56} kind="title" x={26} y={62} maxWidth={470}>{debtLine(t.name, d.amount)}</Label>
      <Label px={28} kind="body" y={14} maxWidth={540} font="bodyBold" color={MUTED}>{`to ${debtTo(d, tv.tokens)} for ${d.why}`}</Label>
      <group position={[0, -42, 0]}>
        <Label px={24} kind="label" font="bodyBold" x={-150} color={MUTED}>CASH</Label>
        <Plate w={230} h={58} r={10} color={INK} x={50} z={1} />
        <Plate w={222} h={50} r={8} color={LED_BG} x={50} z={2} />
        <Label px={40} kind="title" font="display" color={tone} x={-14} z={3}>$</Label>
        <Label px={40} kind="body" font="led" color={tone} x={78} z={3} anchorX="center">{String(t.cash)}</Label>
      </group>
      <Label px={22} kind="label" y={-98} color={MUTED} maxWidth={540}>Sell or mortgage to cover it, or go bankrupt</Label>
    </group>
  )
}
