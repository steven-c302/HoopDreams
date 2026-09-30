// controller/src/tv/turf3d/ui/panels/RollPanel.tsx
import type { TurfTv } from '../../../types'
import { Plate } from '../Card'
import type { Hud } from '../hud'
import { Label } from '../Label'
import { rollCall } from '../panels'
import { bodyOf, fitFont } from '../sizing'
import { GOLD, GREEN, INK, RED } from '../theme'

/** The roll turn, and (with nothing to say) the move phase, whose dice are on the table. */
export function RollPanel({ tv, hud, quiet }: { tv: TurfTv; hud: Hud; quiet?: boolean }) {
  if (quiet) return null
  const body = bodyOf('std')
  const call = rollCall(tv.doubles, hud.seatName)
  return (
    <group>
      {[[-46, RED], [0, GOLD], [46, GREEN]].map(([x, c], k) => (
        <group key={k} position={[x as number, 62, 0]}>
          <Plate w={38} h={38} r={6} color={INK} z={1} />
          <Plate w={30} h={30} r={4} color={c as string} z={2} />
        </group>
      ))}
      <Label px={fitFont(call.length, body.w, 96, 72)} kind="hero" y={-14} maxWidth={body.w}>{call}</Label>
    </group>
  )
}
