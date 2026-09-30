// controller/src/tv/turf3d/ui/RailStack.tsx
import type { TurfTv } from '../../types'
import type { Person } from './copy'
import { Dais } from './Dais'
import { RailCard } from './RailCard'
import { RAIL, railSlots, railY } from './rails'

function Rail({ tv, people, side, ready }: { tv: TurfTv; people: Person[]; side: 'left' | 'right'; ready: boolean }) {
  const slots = railSlots(tv.tokens.length)
  const idx = side === 'left' ? slots.left : slots.right
  return (
    <Dais panelKey="rail" visible={ready && idx.length > 0} dist={9.4} offsetX={side === 'left' ? -RAIL.dx : RAIL.dx}>
      {() => <>{idx.map((tok, k) => <RailCard key={tok} tv={tv} i={tok} people={people} y={railY(k, idx.length)} />)}</>}
    </Dais>
  )
}

/** Both rails of player cards, hung on the camera at the screen edges; they appear once the 3D text is ready. */
export function Rails({ tv, people, ready }: { tv: TurfTv; people: Person[]; ready: boolean }) {
  return (
    <>
      <Rail tv={tv} people={people} side="left" ready={ready} />
      <Rail tv={tv} people={people} side="right" ready={ready} />
    </>
  )
}
