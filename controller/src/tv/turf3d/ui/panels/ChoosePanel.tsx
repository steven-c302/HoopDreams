// controller/src/tv/turf3d/ui/panels/ChoosePanel.tsx
import type { TurfTv } from '../../../types'
import type { Hud } from '../hud'
import { Label } from '../Label'
import { chooseCall } from '../panels'
import { bodyOf, fitFont } from '../sizing'
import { MUTED } from '../theme'

export function ChoosePanel({ tv, hud }: { tv: TurfTv; hud: Hud }) {
  const body = bodyOf('std')
  const call = chooseCall(tv.choose)
  return (
    <group>
      <Label px={fitFont(call.length, body.w, 84, 72)} kind="hero" y={34} maxWidth={body.w}>{call}</Label>
      <Label px={28} kind="body" color={MUTED} y={-80} font="bodyBold" maxWidth={body.w}>{`${hud.seatName} is choosing`}</Label>
    </group>
  )
}
