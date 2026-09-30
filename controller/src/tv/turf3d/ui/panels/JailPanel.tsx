// controller/src/tv/turf3d/ui/panels/JailPanel.tsx
import { Button } from '../Button'
import { Label } from '../Label'
import { bodyOf } from '../sizing'
import { GOLD, PAPER_DARK } from '../theme'

export function JailPanel() {
  const body = bodyOf('std')
  return (
    <group>
      <Label px={84} kind="hero" y={44} maxWidth={body.w}>IN TIMEOUT</Label>
      <Button w={140} label="PAY $50" x={-200} y={-52} />
      <Button w={165} label="USE A CARD" x={-27.5} y={-52} fill={PAPER_DARK} />
      <Button w={195} label="ROLL DOUBLES" x={172.5} y={-52} fill={GOLD} />
    </group>
  )
}
