import { useState } from 'react'
import type { GameListing, HostCommand, PhoneState } from '../protocol'
import { Face } from '../theme/Face'
import { Crown } from '../tv/toon'

const buzz = (ms: number | number[]) => { try { navigator.vibrate?.(ms) } catch { /* not supported */ } }
const TEAM_CHOICES = [0, 2, 3, 4, 5, 6]

/** Lobby settings as the server holds them (the TV shows the same values). */
function settingsOf(view: PhoneState) {
  const s = view.settings ?? {}
  return { rounds: s.rounds ?? 5, teams: s.teams ?? 0, drinks: (s.drinks ?? 1) === 1, game: s.game ?? 0 }
}

/** The captain's lobby: pick the game and its settings, then start it. Everything shows up live on the TV. */
export function CaptainLobby({ view, games, host }: { view: PhoneState; games: GameListing[]; host(c: HostCommand): void }) {
  const s = settingsOf(view)
  const game = games[Math.min(s.game, Math.max(0, games.length - 1))]
  const trivia = game?.id === 'trivia'
  const set = (key: 'rounds' | 'teams' | 'drinks' | 'game', value: number) => { buzz(12); host({ t: 'setOption', key, value }) }
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
        <Stepper label={trivia ? 'Questions per round' : 'Rounds'} value={String(s.rounds)}
          onDown={() => set('rounds', Math.max(3, s.rounds - 1))} onUp={() => set('rounds', Math.min(8, s.rounds + 1))} />
        {trivia && (
          <Stepper label="Teams" value={s.teams === 0 ? 'Auto' : String(s.teams)}
            onDown={() => set('teams', TEAM_CHOICES[Math.max(0, TEAM_CHOICES.indexOf(s.teams) - 1)])}
            onUp={() => set('teams', TEAM_CHOICES[Math.min(TEAM_CHOICES.length - 1, TEAM_CHOICES.indexOf(s.teams) + 1)])} />
        )}
        {trivia && (
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
