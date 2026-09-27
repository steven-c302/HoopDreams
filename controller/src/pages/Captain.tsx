import { useState } from 'react'
import type { GameListing, HostCommand, OptionKey, PhoneState } from '../protocol'
import { Face } from '../theme/Face'
import { Crown } from '../tv/toon'
import { isTrivia } from '../tv/types'

const buzz = (ms: number | number[]) => { try { navigator.vibrate?.(ms) } catch { /* not supported */ } }
const TEAM_CHOICES = [0, 2, 3, 4, 5, 6]
/** Home Turf: game clock in minutes (0 = no limit) and play modes. */
const TURF_MINUTES = [30, 45, 60, 90, 0]
const TURF_MODES = ['Auto', 'Solo', 'Teams']

/** Lobby settings as the server holds them (the TV shows the same values). */
function settingsOf(view: PhoneState) {
  const s = view.settings ?? {}
  return {
    rounds: s.rounds ?? 5, teams: s.teams ?? 0, drinks: (s.drinks ?? 1) === 1, game: s.game ?? 0,
    turfMode: s.turfMode ?? 0, minutes: s.minutes ?? 45, vp: s.vp ?? 8,
  }
}

/** The captain's lobby: pick the game and its settings, then start it. Everything shows up live on the TV. */
export function CaptainLobby({ view, games, host }: { view: PhoneState; games: GameListing[]; host(c: HostCommand): void }) {
  const s = settingsOf(view)
  const game = games[Math.min(s.game, Math.max(0, games.length - 1))]
  const trivia = isTrivia(game?.id)
  const turf = game?.id === 'turf'
  const sprawl = game?.id === 'sprawl'
  const set = (key: OptionKey, value: number) => { buzz(12); host({ t: 'setOption', key, value }) }
  const minuteAt = Math.max(0, TURF_MINUTES.indexOf(s.minutes))
  const teamAt = Math.max(1, TEAM_CHOICES.indexOf(s.teams))
  return (
    <div className="captain stack">
      <div className="captain-head">
        <Crown size={54} />
        <div><h1>You have the crown</h1><p className="muted">Pick the game. The TV follows your phone.</p></div>
      </div>
      <div className="game-picks" role="radiogroup" aria-label="Game">
        {games.map((g, i) => (
          <button key={g.id} role="radio" aria-checked={i === s.game} className={`game-pick ${i === s.game ? 'on' : ''}`} onClick={() => set('game', i)}>
            <b>{g.title}</b><span>{g.tagline}</span>
          </button>
        ))}
      </div>
      <div className="settings-card">
        {game?.id === 'blackjack'
          ? <div className="setting-row"><span>Everyone deals once</span><b>1 hand each</b></div>
          : turf ? (
            <>
              <Stepper label="Play" value={TURF_MODES[s.turfMode] ?? 'Auto'}
                onDown={() => set('turfMode', (s.turfMode + 2) % 3)} onUp={() => set('turfMode', (s.turfMode + 1) % 3)} />
              {s.turfMode === 2 && (
                <Stepper label="Teams" value={s.teams >= 2 ? String(s.teams) : 'Auto'}
                  onDown={() => set('teams', TEAM_CHOICES[Math.max(0, teamAt - 1)])} onUp={() => set('teams', TEAM_CHOICES[Math.min(TEAM_CHOICES.length - 1, teamAt + 1)])} />
              )}
              <Stepper label="Game clock" value={s.minutes === 0 ? 'No limit' : `${s.minutes} min`}
                onDown={() => set('minutes', TURF_MINUTES[Math.max(0, minuteAt - 1)])} onUp={() => set('minutes', TURF_MINUTES[Math.min(TURF_MINUTES.length - 1, minuteAt + 1)])} />
            </>
          )
          : sprawl ? (
            <>
              <Stepper label="Points to win" value={String(s.vp)} onDown={() => set('vp', 8)} onUp={() => set('vp', 10)} />
              <Stepper label="Game clock" value={s.minutes === 0 ? 'No limit' : `${s.minutes} min`}
                onDown={() => set('minutes', TURF_MINUTES[Math.max(0, minuteAt - 1)])} onUp={() => set('minutes', TURF_MINUTES[Math.min(TURF_MINUTES.length - 1, minuteAt + 1)])} />
            </>
          )
          : <Stepper label={trivia ? 'Questions per round' : 'Rounds'} value={String(s.rounds)}
              onDown={() => set('rounds', Math.max(3, s.rounds - 1))} onUp={() => set('rounds', Math.min(8, s.rounds + 1))} />}
        {trivia && (
          <Stepper label="Teams" value={s.teams === 0 ? 'Auto' : String(s.teams)}
            onDown={() => set('teams', TEAM_CHOICES[Math.max(0, TEAM_CHOICES.indexOf(s.teams) - 1)])}
            onUp={() => set('teams', TEAM_CHOICES[Math.min(TEAM_CHOICES.length - 1, TEAM_CHOICES.indexOf(s.teams) + 1)])} />
        )}
        {(trivia || turf || sprawl) && (
          <div className="setting-row">
            <span>Drink calls</span>
            <button className={`toggle ${s.drinks ? 'on' : ''}`} role="switch" aria-checked={s.drinks} onClick={() => set('drinks', s.drinks ? 0 : 1)}>{s.drinks ? 'On' : 'Off'}</button>
          </div>
        )}
      </div>
      <button className="primary big" disabled={!game} onClick={() => { buzz([30, 40, 30]); if (game) host({ t: 'start', gameId: game.id, options: {} }) }}>
        Everybody's in! Start {game?.title ?? ''}
      </button>
      <PassCrown view={view} host={host} />
    </div>
  )
}

function Stepper({ label, value, onDown, onUp }: { label: string; value: string; onDown(): void; onUp(): void }) {
  return (
    <div className="setting-row">
      <span>{label}</span>
      <span className="stepper-btns">
        <button aria-label={`Less ${label}`} onClick={onDown}>−</button>
        <b>{value}</b>
        <button aria-label={`More ${label}`} onClick={onUp}>+</button>
      </span>
    </div>
  )
}

/** Hand the crown to someone else (e.g. you're going to the bar). */
function PassCrown({ view, host }: { view: PhoneState; host(c: HostCommand): void }) {
  const [open, setOpen] = useState(false)
  const others = view.crew.filter((p) => p.id !== view.me.id && p.connected)
  if (!others.length) return null
  return (
    <div className="pass-crown">
      <button className="ghost" onClick={() => setOpen(!open)}>{open ? 'Keep the crown' : 'Pass the crown'}</button>
      {open && (
        <div className="crew">
          {others.map((p) => (
            <button key={p.id} onClick={() => { buzz(30); host({ t: 'makeCaptain', playerId: p.id }); setOpen(false) }}>
              <Face face={p.avatar.face} color={p.avatar.color} size={34} /><span>{p.name}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

/** Brain Drain's Team Up is on this phone: it's picking or naming a team. */
export const inTeamUp = (view: PhoneState) =>
  (view.screen.t === 'choice' && view.screen.kind === 'team') || (view.screen.t === 'text' && view.screen.kind === 'teamName') ||
  (view.screen.t === 'turf' && view.screen.prompt.kind === 'teamup')

const shuffle = (host: (c: HostCommand) => void) => { buzz([20, 40, 20]); host({ t: 'gameAction', action: 'shuffle' }) }

/** Team Up, captain only: deal everyone evenly across the teams (friends always pile onto one). */
export function ShuffleTeams({ host }: { host(c: HostCommand): void }) {
  return <button className="ghost shuffle-teams" onClick={() => shuffle(host)}>Uneven teams? Shuffle evenly</button>
}

/** During a game: a crown button that opens the show controls (pause, skip ahead, end). */
export function CaptainControls({ view, host }: { view: PhoneState; host(c: HostCommand): void }) {
  const [open, setOpen] = useState(false)
  const [confirmEnd, setConfirmEnd] = useState(false)
  const close = () => { setOpen(false); setConfirmEnd(false) }
  return (
    <>
      <button className="crown-fab" aria-label="Captain controls" onClick={() => { buzz(15); setOpen(true) }}><Crown size={40} /></button>
      {open && (
        <div className="sheet-scrim" onClick={close}>
          <div className="sheet" role="dialog" aria-label="Captain controls" onClick={(e) => e.stopPropagation()}>
            <div className="captain-head"><Crown size={44} /><h2>Captain controls</h2></div>
            {inTeamUp(view) && <button className="big" onClick={() => { shuffle(host); close() }}>Shuffle teams evenly</button>}
            {view.paused
              ? <button className="primary big" onClick={() => { host({ t: 'resume' }); close() }}>Resume</button>
              : <button className="big" onClick={() => { host({ t: 'pause' }); close() }}>Pause</button>}
            <button className="big" onClick={() => { host({ t: 'skip' }); close() }}>Skip ahead</button>
            {confirmEnd
              ? <button className="big danger" onClick={() => { host({ t: 'end' }); close() }}>Really end the game?</button>
              : <button className="big" onClick={() => setConfirmEnd(true)}>End game</button>}
            <PassCrown view={view} host={host} />
            <button className="ghost" onClick={close}>Close</button>
          </div>
        </div>
      )}
    </>
  )
}
