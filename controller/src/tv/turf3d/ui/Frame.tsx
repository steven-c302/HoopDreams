// controller/src/tv/turf3d/ui/Frame.tsx
import type { ReactNode } from 'react'
import type { TurfTv } from '../../types'
import { Card, Plate } from './Card'
import { DrinkIcon } from './DrinkIcon'
import type { Hud } from './hud'
import { Label } from './Label'
import { CARD, FOOT_H, HEAD_H, bodyOf, type CardSize } from './sizing'
import { GOLD, INK, LED_BG, MUTED, PAPER, RED } from './theme'
import { TimerRing } from './TimerRing'

/**
 * The one card every ported panel sits on: a header (who is up, the game clock, the decision timer), the panel itself
 * in the body, and the last few ticker lines in the footer. [onReady] fires when the first text has been laid out,
 * which is when the DOM well can safely get out of the way.
 */
export function Frame({ tv, hud, size, onReady, children }: { tv: TurfTv; hud: Hud; size: CardSize; onReady?: () => void; children?: ReactNode }) {
  void tv
  const { w, h } = CARD[size]
  const body = bodyOf(size)
  const headY = h / 2 - HEAD_H / 2
  const footY = -h / 2 + FOOT_H / 2
  const left = -w / 2 + 34
  const right = w / 2 - 34
  const clockTone = hud.clock.tone === 'red' ? RED : hud.clock.tone === 'gold' ? GOLD : PAPER
  const ledW = hud.clock.tone === 'plain' ? 0 : 148
  const timerW = hud.timer ? 84 : 0
  const ledX = right - timerW - ledW / 2 - 4
  return (
    <Card w={w} h={h}>
      {/* header */}
      <group position={[0, headY, 0]}>
        {hud.showTurn ? (
          <>
            <Plate w={64} h={64} r={32} color={INK} x={left + 26} z={1} />
            <Plate w={56} h={56} r={28} color={hud.color} x={left + 26} z={2} />
            <DrinkIcon piece={hud.piece} color={hud.color} size={40} x={left + 26} y={-6} />
            <Label px={38} kind="title" font="display" anchorX="left" align="left" x={left + 74} maxWidth={w - 74 - 34 - 160 - timerW} onSync={onReady}>{hud.name.toUpperCase()}</Label>
          </>
        ) : (
          <Label px={44} kind="title" font="display" anchorX="left" align="left" x={left} onSync={onReady}>HOME TURF</Label>
        )}
        {ledW > 0 ? (
          <>
            <Plate w={ledW} h={52} r={9} color={INK} x={ledX} z={1} />
            <Plate w={ledW - 6} h={46} r={7} color={LED_BG} x={ledX} z={2} />
            <Label px={34} kind="body" font="led" color={clockTone} x={ledX} z={3}>{hud.clock.text}</Label>
          </>
        ) : (
          <Label px={22} kind="label" color={MUTED} anchorX="right" align="right" x={right - timerW} font="bodyBold">{hud.clock.text}</Label>
        )}
        {hud.timer && <TimerRing timer={hud.timer} x={right - 34} />}
      </group>
      <Plate w={w - 40} h={3} r={1.5} color={INK} y={h / 2 - HEAD_H} z={1} />
      {/* body */}
      <group position={[0, body.cy, 0]}>{children}</group>
      {/* footer */}
      {size === 'std' && hud.ticker.length > 0 && (
        <>
          <Plate w={w - 40} h={3} r={1.5} color={INK} y={-h / 2 + FOOT_H} z={1} />
          <group position={[0, footY, 0]}>
            {hud.ticker.map((line, k, all) => (
              <Label key={k} px={22} kind="label" color={k === all.length - 1 ? INK : MUTED} font={k === all.length - 1 ? 'bodyBold' : 'body'} anchorX="left" align="left" x={left - 6} y={(all.length - 1) * 12 - k * 25} maxWidth={w - 60}>{line}</Label>
            ))}
          </group>
        </>
      )}
    </Card>
  )
}
