// controller/src/tv/turf3d/ui/Pill.tsx
import { Plate } from './Card'
import { Label } from './Label'
import { INK, PAPER } from './theme'

/** A small rounded tag with ink outline. */
export function Pill({ w, h = 40, fill = PAPER, text, textColor = INK, px = 22, x = 0, y = 0 }: { w: number; h?: number; fill?: string; text: string; textColor?: string; px?: number; x?: number; y?: number }) {
  return (
    <group position={[x, y, 0]}>
      <Plate w={w + 6} h={h + 6} r={(h + 6) / 2} color={INK} z={1} />
      <Plate w={w} h={h} r={h / 2} color={fill} z={2} />
      <Label px={px} kind="label" color={textColor} z={3} maxWidth={w - 16} font="bodyBold">{text}</Label>
    </group>
  )
}
