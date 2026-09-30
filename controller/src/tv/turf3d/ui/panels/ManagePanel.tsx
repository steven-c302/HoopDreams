// controller/src/tv/turf3d/ui/panels/ManagePanel.tsx
import type { TurfTv } from '../../../types'
import { Label } from '../Label'
import { bankLine } from '../panels'
import { bodyOf } from '../sizing'
import { MUTED } from '../theme'

export function ManagePanel({ tv }: { tv: TurfTv }) {
  const body = bodyOf('std')
  return (
    <group>
      <Label px={72} kind="hero" y={28} maxWidth={body.w}>BUILD, TRADE, OR END</Label>
      <Label px={28} kind="body" color={MUTED} y={-78} font="bodyBold">{bankLine(tv)}</Label>
    </group>
  )
}
