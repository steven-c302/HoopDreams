// controller/src/tv/turf3d/ui/panels/DeedHeader.tsx
import { Plate } from '../Card'
import type { DeedFacts } from '../copy'
import { Label } from '../Label'
import { GOLD, INK, PAPER_DARK } from '../theme'

/** A place's name on its colour band, with the kind above it in small type. */
export function DeedHeader({ facts, y = 0, w = 500 }: { facts: DeedFacts; y?: number; w?: number }) {
  const band = facts.band ?? '#2b2b2b'
  const light = band.length === 7 && parseInt(band.slice(1, 3), 16) * 0.3 + parseInt(band.slice(3, 5), 16) * 0.59 + parseInt(band.slice(5, 7), 16) * 0.11 > 150
  const text = light ? INK : '#ffffff'
  return (
    <group position={[0, y, 0]}>
      <Plate w={w + 8} h={64} r={12} color={INK} z={1} />
      <Plate w={w} h={56} r={9} color={band} z={2} />
      <Label px={40} kind="title" color={text} z={3} maxWidth={w - 24}>{facts.name.toUpperCase()}</Label>
      <Plate w={96} h={26} r={13} color={facts.band ? PAPER_DARK : GOLD} x={-w / 2 + 70} y={40} z={3} />
      <Label px={22} kind="label" font="bodyBold" x={-w / 2 + 70} y={40} z={4}>{facts.kind}</Label>
    </group>
  )
}
