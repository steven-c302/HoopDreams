// controller/src/tv/turf3d/ui/PanelHost.tsx
import type { ReactNode } from 'react'
import type { TurfTv } from '../../types'
import { Dais } from './Dais'
import { Frame } from './Frame'
import type { Hud } from './hud'
import { ChoosePanel } from './panels/ChoosePanel'
import { JailPanel } from './panels/JailPanel'
import { ManagePanel } from './panels/ManagePanel'
import { RollPanel } from './panels/RollPanel'
import { panelFor, panelSize, type PanelName } from './panels'

function panelBody(name: PanelName, tv: TurfTv, hud: Hud): ReactNode {
  switch (name) {
    case 'roll': return <RollPanel tv={tv} hud={hud} />
    case 'move': return <RollPanel tv={tv} hud={hud} quiet />
    case 'manage': return <ManagePanel tv={tv} />
    case 'jail': return <JailPanel />
    case 'choose': return <ChoosePanel tv={tv} hud={hud} />
    default: return null
  }
}

/** The 3D card for the current phase, or nothing (the DOM well shows) when the phase is not ported. */
export function PanelHost({ tv, hud, visible, onReady }: { tv: TurfTv; hud: Hud; visible: boolean; onReady: () => void }) {
  return (
    <Dais panelKey={panelFor(tv.phase)} visible={visible}>
      {(key) => (
        <Frame tv={tv} hud={hud} size={panelSize(key as PanelName)} onReady={onReady}>
          {panelBody(key as PanelName, tv, hud)}
        </Frame>
      )}
    </Dais>
  )
}
