// controller/src/tv/turf3d/TurfScene.tsx
import type { TurfTv } from '../types'
import { Board3D } from './scene/Board3D'
import { CameraRig } from './scene/CameraRig'
import { Dice } from './scene/Dice'
import { Effects } from './scene/Effects'
import { Flags } from './scene/Flags'
import { Houses } from './scene/Houses'
import { Lights } from './scene/Lights'
import { CardFlips, CoinFx } from './scene/Moments'
import { Pieces } from './scene/Pieces'
import { Post } from './scene/Post'
import { Table } from './scene/Table'
import type { Craft } from './useChoreography'

/** The whole tabletop. Render it inside an R3F Canvas that is wrapped in QualityContext.Provider. */
export function TurfScene({ tv, craft }: { tv: TurfTv; craft: Craft }) {
  return (
    <>
      <color attach="background" args={['#241710']} />
      <CameraRig shot={craft.shot} focus={craft.focus} landedN={craft.landed?.n ?? 0} />
      <Lights />
      <Table />
      <Board3D tv={tv} />
      <Houses tv={tv} />
      <Flags tv={tv} />
      <Pieces tv={tv} craft={craft} />
      <Effects craft={craft} />
      <Dice dice={craft.dice} />
      <CoinFx craft={craft} />
      <CardFlips craft={craft} />
      <Post />
    </>
  )
}
