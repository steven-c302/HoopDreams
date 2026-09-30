// controller/src/tv/turf3d/ui/panels/DealPanel.tsx
import type { TurfTv } from '../../../types'
import { DrinkIcon } from '../DrinkIcon'
import { Label } from '../Label'
import { MUTED } from '../theme'

/** Each player's drink, name and the starter places dealt to them (two columns of up to three). */
export function DealPanel({ tv }: { tv: TurfTv }) {
  const dealt = tv.tokens.map((_, i) => tv.beats.filter((b) => b.kind === 'deal' && b.token === i).flatMap((b) => b.tokens))
  return (
    <group>
      <Label px={60} kind="title" y={200}>STARTER PLACES</Label>
      {tv.tokens.slice(0, 6).map((t, i) => {
        const x = (i % 2 === 0 ? -1 : 1) * 190, y = 96 - Math.floor(i / 2) * 104
        const places = dealt[i].map((s) => tv.board[s]?.name).filter((n): n is string => !!n).join(', ')
        return (
          <group key={i}>
            <DrinkIcon piece={t.piece} color={t.color} size={58} x={x - 150} y={y - 10} />
            <Label px={30} kind="body" font="bodyBold" anchorX="left" align="left" x={x - 112} y={y + 18} maxWidth={250}>{t.name}</Label>
            <Label px={22} kind="label" anchorX="left" align="left" x={x - 112} y={y - 20} maxWidth={250} color={MUTED}>{places || '...'}</Label>
          </group>
        )
      })}
      <Label px={28} kind="body" y={-206} color={MUTED} font="bodyBold">Paid for out of everyone's $1,500</Label>
    </group>
  )
}
