// controller/src/tv/turf3d/ui/PanelHost.tsx
import type { ReactNode } from 'react'
import type { TurfTv } from '../../types'
import { Dais } from './Dais'
import { Frame } from './Frame'
import type { Person } from './copy'
import type { Hud } from './hud'
import { AuctionPanel } from './panels/AuctionPanel'
import { BuyPanel } from './panels/BuyPanel'
import { CardPanel } from './panels/CardPanel'
import { DebtPanel } from './panels/DebtPanel'
import { ChoosePanel } from './panels/ChoosePanel'
import { DealPanel } from './panels/DealPanel'
import { JailPanel } from './panels/JailPanel'
import { ManagePanel } from './panels/ManagePanel'
import { PiecesPanel } from './panels/PiecesPanel'
import { RollPanel } from './panels/RollPanel'
import { TallyPanel } from './panels/TallyPanel'
import { TeamUpPanel } from './panels/TeamUpPanel'
import { TradePanel } from './panels/TradePanel'
import { panelFor, panelSize, type PanelName } from './panels'

function panelBody(name: PanelName, tv: TurfTv, hud: Hud, people: Person[]): ReactNode {
  switch (name) {
    case 'roll': return <RollPanel tv={tv} hud={hud} />
    case 'move': return <RollPanel tv={tv} hud={hud} quiet />
    case 'manage': return <ManagePanel tv={tv} />
    case 'jail': return <JailPanel />
    case 'choose': return <ChoosePanel tv={tv} hud={hud} />
    case 'pieces': return <PiecesPanel tv={tv} />
    case 'deal': return <DealPanel tv={tv} />
    case 'buy': return <BuyPanel tv={tv} />
    case 'auction': return <AuctionPanel tv={tv} />
    case 'card': return <CardPanel tv={tv} />
    case 'debt': return <DebtPanel tv={tv} />
    case 'tally': return <TallyPanel tv={tv} />
    case 'trade': return <TradePanel tv={tv} />
    case 'teamup': return <TeamUpPanel tv={tv} people={people} />
    default: return null
  }
}

/** The 3D card for the current phase, or nothing (the DOM well shows) when the phase is not ported. */
export function PanelHost({ tv, hud, people, visible, onReady }: { tv: TurfTv; hud: Hud; people: Person[]; visible: boolean; onReady: () => void }) {
  return (
    <Dais panelKey={panelFor(tv.phase)} visible={visible}>
      {(key) => (
        <Frame tv={tv} hud={hud} size={panelSize(key as PanelName)} onReady={onReady}>
          {panelBody(key as PanelName, tv, hud, people)}
        </Frame>
      )}
    </Dais>
  )
}
