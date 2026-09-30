// controller/src/tv/turf3d/ui/panels/AuctionPanel.tsx
import type { TurfTv } from '../../../types'
import { Bump } from '../Bump'
import { Plate } from '../Card'
import { auctionHint, deedFacts } from '../copy'
import { Label } from '../Label'
import { GOLD, INK, LED_BG, MUTED } from '../theme'
import { DeedHeader } from './DeedHeader'

/** The auction: the place, the top bid (pops on every new bid), who leads, and how it works. */
export function AuctionPanel({ tv }: { tv: TurfTv }) {
  const a = tv.auction
  const space = a ? tv.board[a.space] : undefined
  if (!a || !space) return null
  const leader = a.leader >= 0 ? tv.tokens[a.leader] : undefined
  return (
    <group>
      <DeedHeader facts={deedFacts(space)} y={68} />
      <Label px={22} kind="label" y={22} color={MUTED} font="bodyBold">AUCTION! TOP BID</Label>
      <Bump k={a.top}>
        <group position={[0, -20, 0]}>
          <Plate w={250} h={58} r={10} color={INK} z={1} />
          <Plate w={242} h={50} r={8} color={LED_BG} z={2} />
          <Label px={44} kind="title" font="display" color={GOLD} x={-92} z={3}>$</Label>
          <Label px={44} kind="body" font="led" color={GOLD} x={16} z={3}>{String(a.top)}</Label>
        </group>
      </Bump>
      <Label px={28} kind="body" y={-70} font="bodyBold" maxWidth={520}>{leader ? `${leader.name} leads` : 'Nobody yet. Bid on your phone!'}</Label>
      <Label px={22} kind="label" y={-100} color={MUTED}>{auctionHint(a.bids)}</Label>
    </group>
  )
}
