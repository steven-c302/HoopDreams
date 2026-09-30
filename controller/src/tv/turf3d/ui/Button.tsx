// controller/src/tv/turf3d/ui/Button.tsx
import { Plate } from './Card'
import { Label } from './Label'
import { GOLD, INK, SHADOW } from './theme'

/** A chunky button with a hard shadow. Display only: the TV has no pointer; it shows what players can pick on their phones. */
export function Button({ w, label, fill = GOLD, x = 0, y = 0 }: { w: number; label: string; fill?: string; x?: number; y?: number }) {
  const h = 52
  return (
    <group position={[x, y, 0]}>
      <Plate w={w + 6} h={h + 6} r={14} color={SHADOW} x={5} y={-5} z={1} />
      <Plate w={w + 6} h={h + 6} r={14} color={INK} z={2} />
      <Plate w={w} h={h} r={11} color={fill} z={3} />
      <Label px={24} kind="label" z={4} maxWidth={w - 16} font="bodyBold">{label}</Label>
    </group>
  )
}
