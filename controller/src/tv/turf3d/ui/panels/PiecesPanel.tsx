// controller/src/tv/turf3d/ui/panels/PiecesPanel.tsx
import type { TurfTv } from '../../../types'
import { DRINK_NAMES } from '../../drinkSpecs'
import { drinkFor } from '../../pieces'
import { DrinkIcon } from '../DrinkIcon'
import { Label } from '../Label'
import { PIECE_ORDER } from '../panels'
import { MUTED } from '../theme'

const GHOST = '#cbbf9f'

/** Six drinks in a grid: free ones pale, taken ones in the owner's colour with the owner's name. */
export function PiecesPanel({ tv }: { tv: TurfTv }) {
  return (
    <group>
      <Label px={60} kind="title" y={196}>GRAB YOUR PIECE!</Label>
      {PIECE_ORDER.map((p, k) => {
        const owner = tv.tokens.find((t) => t.piece === p)
        const x = (k % 3 - 1) * 240, y = k < 3 ? 66 : -92
        return (
          <group key={p}>
            <DrinkIcon piece={p} color={owner ? owner.color : GHOST} size={88} x={x} y={y + 8} />
            <Label px={24} kind="label" x={x} y={y - 56} maxWidth={220} font="bodyBold" color={owner ? undefined : MUTED}>{owner ? owner.name : DRINK_NAMES[drinkFor(p)]}</Label>
          </group>
        )
      })}
      <Label px={28} kind="body" y={-204} color={MUTED} font="bodyBold">First tap on your phone wins</Label>
    </group>
  )
}
