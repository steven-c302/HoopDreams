import { useLayoutEffect, useState } from 'react'
import type { PhoneState, PlayerSummary, StageInfo } from '../protocol'
import { ScreenView } from '../screens/ScreenView'
import { Face } from '../theme/Face'
import { GameScene } from '../theme/GameScene'
import { SprawlStage } from './SprawlStage'
import { TurfStage } from './TurfStage'
import sprawl from './fixtures/sprawl-theme.json'
import turf from './fixtures/turf-theme.json'

// Captured from a separate local rehearsal with six fictional players, after setup.
// Fixtures contain rendered game views only; no host or player session tokens.
interface Fixture { players: PlayerSummary[]; stage: StageInfo; phone: Pick<PhoneState, 'me' | 'screen'> }
const fixtures = { turf, sprawl } as unknown as Record<'turf' | 'sprawl', Fixture>

/** Stable TV/phone review surfaces. Actions never reach a party server. */
export function ThemeGallery() {
  const params = new URLSearchParams(location.search)
  const game = params.get('game') === 'turf' ? 'turf' : 'sprawl'
  const fixture = fixtures[game]
  const [scale, setScale] = useState(1)
  const [action, setAction] = useState('Design preview · six players')
  useLayoutEffect(() => {
    const fit = () => setScale(Math.min(innerWidth / 1920, innerHeight / 1080))
    fit(); window.addEventListener('resize', fit)
    return () => window.removeEventListener('resize', fit)
  }, [])
  if (params.get('view') === 'phone') {
    const { me, screen } = fixture.phone
    return (
      <main className="page play" data-game-theme={game}>
        <header className="topbar">
          <span className="me"><Face face={me.avatar.face} color={me.avatar.color} size={40} />{me.name}</span>
          <span className="room-chip">{fixture.stage.title}</span><span className="timer">18</span>
        </header>
        <section className="screen"><ScreenView screen={screen} disabled={false} meId={me.id} people={new Map()} onAction={() => setAction('Preview only · no action sent')} /></section>
        <p className="theme-preview-note" role="status">{action}</p>
      </main>
    )
  }
  const Stage = game === 'turf' ? TurfStage : SprawlStage
  return <div className="tv-root"><div className="tv-stage" style={{ transform: `scale(${scale}) translate(-50%, -50%)` }}>
    <GameScene game={game}><Stage stage={fixture.stage} players={fixture.players} scores={[]} clock={{ deadline: null, frozen: 18_000 }} /></GameScene>
  </div></div>
}
