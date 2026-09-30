// controller/src/tv/turf3d/ui/DeedTag.tsx
import { Plate } from './Card'
import { Label } from './Label'
import { INK, PAPER } from './theme'

/** A place as a tag: its name on paper, with a strip of its colour band on the left. */
export function DeedTag({ w, name, band, x = 0, y = 0 }: { w: number; name: string; band: string | null; x?: number; y?: number }) {
  return (
    <group position={[x, y, 0]}>
      <Plate w={w + 6} h={40} r={10} color={INK} z={1} />
      <Plate w={w} h={34} r={8} color={PAPER} z={2} />
      <Plate w={14} h={34} r={5} color={band ?? '#2b2b2b'} x={-w / 2 + 9} z={3} />
      <Label px={22} kind="label" font="bodyBold" anchorX="left" align="left" x={-w / 2 + 26} z={4} maxWidth={w - 34}>{name}</Label>
    </group>
  )
}
