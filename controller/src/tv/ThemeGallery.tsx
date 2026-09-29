import { useLayoutEffect, useMemo, useState, type CSSProperties } from 'react'
import type { InkStore } from '../ink/store'
import type { PhoneState, PlayerSummary, ScoreRow, StageInfo } from '../protocol'
import { ScreenView, teamOf } from '../screens/ScreenView'
import { Face } from '../theme/Face'
import { GameScene } from '../theme/GameScene'
import { gameThemeOf, type GameTheme } from '../theme/gameTheme'
import { BlackjackStage } from './BlackjackStage'
import { BluffStage } from './BluffStage'
import { DoodleStage } from './DoodleStage'
import { ImposterStage } from './ImposterStage'
import { SprawlStage } from './SprawlStage'
import { TriviaStage } from './TriviaStage'
import { TurfStage } from './TurfStage'
import blackjack from './fixtures/blackjack-theme.json'
import bluff from './fixtures/bluff-theme.json'
import doodle, { demoInk } from './fixtures/doodle-theme'
import imposter from './fixtures/imposter-theme'
import sprawl from './fixtures/sprawl-theme.json'
import turf from './fixtures/turf-theme.json'
import writeitdown from './fixtures/writeitdown-theme.json'

// Captured from separate local rehearsals with six fictional players. Fixtures contain rendered game views only;
// no host or player session tokens. Home Turf and Sprawl hold one beat; the others hold several by name.
interface Beat { stage: StageInfo; scores?: ScoreRow[]; phone: Pick<PhoneState, 'me' | 'screen'> }
interface Fixture { players: PlayerSummary[]; beats: Record<string, Beat> }
type Raw = { players: PlayerSummary[] } & ({ beats: Record<string, Beat> } | Beat)
const raw = { turf, sprawl, blackjack, bluff, writeitdown, imposter, doodle } as unknown as Record<GameTheme, Raw>
const fixtureOf = (game: GameTheme): Fixture => {
  const f = raw[game]
  return 'beats' in f ? f : { players: f.players, beats: { roll: { stage: f.stage, scores: f.scores, phone: f.phone } } }
}

type StageProps = { stage: StageInfo; players: PlayerSummary[]; scores: ScoreRow[]; clock: { deadline: number | null; frozen: number | null }; ink?: InkStore }
/** The same wrapping the live TV uses (TvPage): Write It Down builds its own room inside TriviaStage. */
function Stage({ game, ink, ...props }: StageProps & { game: GameTheme }) {
  switch (game) {
    case 'turf': return <GameScene game="turf"><TurfStage {...props} /></GameScene>
    case 'sprawl': return <GameScene game="sprawl"><SprawlStage {...props} /></GameScene>
    case 'blackjack': return <GameScene game="blackjack"><BlackjackStage {...props} /></GameScene>
    case 'bluff': return <GameScene game="bluff"><BluffStage {...props} /></GameScene>
    case 'imposter': return <GameScene game="imposter"><ImposterStage {...props} /></GameScene>
    case 'doodle': return <GameScene game="doodle"><DoodleStage {...props} ink={ink ?? demoInk()} /></GameScene>
    case 'writeitdown': return <TriviaStage {...props} />
  }
}

/**
 * Stable TV/phone review surfaces: /tv?gallery=themes&game=<game>&beat=<beat>[&view=phone]. The countdown is frozen at
 * 18 seconds. Actions never reach a party server.
 */
export function ThemeGallery() {
  const params = new URLSearchParams(location.search)
  const game = gameThemeOf(params.get('game')) ?? 'sprawl'
  const fixture = fixtureOf(game)
  const names = Object.keys(fixture.beats)
  const beat = fixture.beats[params.get('beat') ?? ''] ?? fixture.beats[names[0]]
  const ink = useMemo(() => (game === 'doodle' ? demoInk() : undefined), [game])
  const [scale, setScale] = useState(1)
  const [action, setAction] = useState(`Design preview · ${names.length > 1 ? `beats: ${names.join(', ')}` : 'six players'}`)
  useLayoutEffect(() => {
    const fit = () => setScale(Math.min(innerWidth / 1920, innerHeight / 1080))
    fit(); window.addEventListener('resize', fit)
    return () => window.removeEventListener('resize', fit)
  }, [])
  if (params.get('view') === 'phone') {
    const { me, screen } = beat.phone
    const team = teamOf(screen)
    return (
      <main className="page play" data-game-theme={game} style={team ? { '--team': team.color } as CSSProperties : undefined}>
        <header className="topbar">
          <span className="me"><Face face={me.avatar.face} color={me.avatar.color} size={40} />{me.name}</span>
          <span className="room-chip">{beat.stage.title}</span><span className="timer">18</span>
        </header>
        {team && <div className="team-band" style={{ background: team.color }}><span>{team.name}</span></div>}
        <section className="screen"><ScreenView screen={screen} disabled={false} meId={me.id} people={new Map((beat.scores ?? []).map((s) => [s.id, s]))} onAction={() => setAction('Preview only · no action sent')} /></section>
        <p className="theme-preview-note" role="status">{action}</p>
      </main>
    )
  }
  return <div className="tv-root"><div className="tv-stage" style={{ transform: `scale(${scale}) translate(-50%, -50%)` }}>
    <Stage game={game} ink={ink} stage={beat.stage} players={fixture.players} scores={beat.scores ?? []} clock={{ deadline: null, frozen: 18_000 }} />
  </div></div>
}
