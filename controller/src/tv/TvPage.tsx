import '../theme/tokens.css'
import './tv.css'
import QRCode from 'qrcode'
import { AnimatePresence, motion } from 'motion/react'
import { useCallback, useEffect, useLayoutEffect, useRef, useState, type CSSProperties } from 'react'
import { Connection, browserSocket, browserSocketUrl, type Status } from '../net/connection'
import { rejectMessage, type GameListing, type HostCommand, type OptionKey, type PlayerSummary, type TvState } from '../protocol'
import { GameScene } from '../theme/GameScene'
import { loadMix, setMix, sfx, unlockAudio, whenAudioRuns, type Mix } from './audio'
import { BlackjackStage } from './BlackjackStage'
import { BluffStage } from './BluffStage'
import { Gallery } from './Gallery'
import { InkStore } from '../ink/store'
import { DoodleStage } from './DoodleStage'
import { ImposterStage } from './ImposterStage'
import { HotTypeStage } from './HotTypeStage'
import { JeopardyStage } from './JeopardyStage'
import { NightReview } from './NightReview'
import { TriviaStage } from './TriviaStage'
import { SprawlStage } from './SprawlStage'
import { CoverArt } from './CoverArt'
import { TurfStage } from './TurfStage'
import { useCueDirector, useDeadline } from './director'
import { nowPlaying, useSpotify } from './spotify'
import { TIMER_NAMES, TIMER_SCALES, TimerScale } from './timerScale'
import { isTrivia, type TriviaTv } from './types'
import { Paused } from './Shared'
import { SuitSprite } from './Suits'
import { AvatarFace, Brainy, Burst, C, Crown, Keycap, Panel, Pop, Scene, Slam } from './toon'

interface TvSession { hostToken: string; room: string; joinUrl: string | null }

/** Keeps a 1920×1080 stage fitted to the window, letterboxed. */
function useStageScale() {
  const [scale, setScale] = useState(1)
  useLayoutEffect(() => {
    const fit = () => setScale(Math.min(window.innerWidth / 1920, window.innerHeight / 1080))
    fit()
    window.addEventListener('resize', fit)
    return () => window.removeEventListener('resize', fit)
  }, [])
  return scale
}

export function TvPage() {
  if (location.search.includes('gallery')) return <Gallery />
  return <TvShow />
}

function TvShow() {
  const [session, setSession] = useState<TvSession | null>(null)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    fetch('/api/tv/session').then(async (r) => {
      if (r.ok) setSession(await r.json())
      else setError(r.status === 403 ? 'Open this page on the computer running PARTY OS. It is the TV.' : `The party server answered ${r.status}.`)
    }).catch(() => setError('Cannot reach the PARTY OS server. Start it with Start Party OS.command.'))
  }, [])
  const scale = useStageScale()
  return (
    <div className="tv-root">
      <SuitSprite />
      <div className="tv-stage" style={{ transform: `scale(${scale}) translate(-50%, -50%)` }}>
        {session ? <Show session={session} /> : (
          <Scene color={C.sun}>
            <div style={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', gap: 40, alignContent: 'center' }}>
              <PartyLogo />
              <Panel fill={C.paper} tilt={-1} style={{ padding: '24px 40px', fontSize: 40, maxWidth: 1200, textAlign: 'center' }}>{error ?? 'Warming up…'}</Panel>
            </div>
          </Scene>
        )}
      </div>
    </div>
  )
}

/**
 * Lobby settings live on the server so the TV and the captain's phone always agree. teams 0 = auto. Home Turf:
 * turfMode 0 auto / 1 solo / 2 teams, minutes = its game clock (0 = no limit).
 */
interface Lobby { rounds: number; teams: number; drinks: boolean; game: number; phones: boolean; turfMode: number; minutes: number; vp: number; timers: number; show: number; grid: number; pace: number }
function lobbyOf(tv: TvState | null): Lobby {
  const s = tv?.settings ?? {}
  return {
    rounds: s.rounds ?? 5, teams: s.teams ?? 0, drinks: (s.drinks ?? 1) === 1, game: s.game ?? 0, phones: (s.captain ?? 1) === 1,
    turfMode: s.turfMode ?? 0, minutes: s.minutes ?? 45, vp: s.vp ?? 8, timers: s.timers ?? 0, show: s.show ?? 0, grid: s.grid ?? 0, pace: s.pace ?? 0,
  }
}
type SetOption = (key: OptionKey, value: number) => void

/** Home Turf's game clock choices, in minutes (0 = no limit). */
export const TURF_MINUTES = [30, 45, 60, 90, 0]
/** Home Turf's T key walks through Auto, Solo, then Teams of 2 to 6. */
const TURF_MODES: [number, number][] = [[0, 0], [1, 0], [2, 2], [2, 3], [2, 4], [2, 5], [2, 6]]
export const turfModeText = (l: { turfMode: number; teams: number }) =>
  l.turfMode === 1 ? 'SOLO' : l.turfMode === 2 ? `${l.teams >= 2 ? l.teams : 'AUTO'} TEAMS` : 'AUTO'
export const turfMinutesText = (m: number) => (m === 0 ? 'NO LIMIT' : `${m} MIN`)

function Show({ session }: { session: TvSession }) {
  const [tv, setTv] = useState<TvState | null>(null)
  const [games, setGames] = useState<GameListing[]>([])
  const [status, setStatus] = useState<Status>('connecting')
  const [toast, setToast] = useState<string | null>(null)
  const [overlay, setOverlay] = useState(false)
  const [recap, setRecap] = useState(false)
  const [live, setLive] = useState(() => unlockAudio())
  const [mix, setMixState] = useState<Mix>(loadMix)
  const [idle, setIdle] = useState(false)
  const conn = useRef<Connection | null>(null)
  const ink = useRef(new InkStore()).current
  const spotify = useSpotify(mix.spotify || overlay)

  useEffect(() => { fetch('/api/games').then((r) => r.json()).then(setGames).catch(() => setGames([])) }, [])
  useEffect(() => {
    const c = new Connection(browserSocketUrl(`host=${encodeURIComponent(session.hostToken)}`), browserSocket, {
      onStatus: setStatus,
      onMessage: (m) => {
        if (m.t === 'tv') setTv(m.tv)
        if (m.t === 'ink') ink.applyEvent(m)
        if (m.t === 'inkSync') ink.applySync(m)
        if (m.t === 'reject') { const t = rejectMessage(m.code); if (t) setToast(t) }
      },
    })
    conn.current = c
    c.start()
    return () => c.stop()
  }, [session.hostToken, ink])
  useEffect(() => { if (!toast) return; const id = setTimeout(() => setToast(null), 3200); return () => clearTimeout(id) }, [toast])

  const cmd = useCallback((c: HostCommand) => conn.current?.host(c), [])
  const setOption: SetOption = useCallback((key, value) => cmd({ t: 'setOption', key, value }), [cmd])
  const lobby = lobbyOf(tv)
  const clock = useDeadline(tv)
  // The drawings belong to the game that just ended.
  const gameOver = !!tv && !tv.stage
  useEffect(() => { if (gameOver) ink.reset() }, [gameOver, ink])
  // Home Turf only counts down real decisions: a hop or a card reveal shouldn't tick.
  const untimed = (tv?.stage?.gameId === 'turf' || tv?.stage?.gameId === 'sprawl') && !(tv.stage.game as { timed?: boolean } | undefined)?.timed
  useCueDirector(live ? tv : null, untimed ? null : clock.deadline)

  // Hide the mouse when it stops moving: this is a TV.
  useEffect(() => {
    let id: ReturnType<typeof setTimeout>
    const wake = () => { setIdle(false); clearTimeout(id); id = setTimeout(() => setIdle(true), 2500) }
    window.addEventListener('mousemove', wake); wake()
    return () => { window.removeEventListener('mousemove', wake); clearTimeout(id) }
  }, [])

  const goLive = () => {
    unlockAudio()
    // A press that didn't unlock sound brings the gate back, but only once resume() has had time to finish.
    void whenAudioRuns(1500).then((ok) => { if (!ok) setLive(false) })
    setLive(true)
    document.documentElement.requestFullscreen?.().catch(() => undefined)
    sfx.showOpen()
  }

  const stage = tv?.stage
  const players = tv?.players ?? []
  // The recap is a lobby card: a game starting (or the party being reset) puts it away.
  const night = !stage ? tv?.night : undefined
  const recapOpen = recap && !!night
  useEffect(() => { if (recap && !night) setRecap(false) }, [recap, night])
  const start = (g: GameListing) => {
    sfx.select()
    cmd({ t: 'start', gameId: g.id, options: {} })
  }

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      // A focused button (the GO LIVE gate) already clicks on Enter/Space; going live twice doubles the intro sting.
      if (!live) { if ((e.key === 'Enter' || e.key === ' ') && !(e.target instanceof HTMLButtonElement)) goLive(); return }
      if (recapOpen && (e.key === 'Escape' || e.key === 'Backspace' || e.key.toLowerCase() === 'a')) { setRecap(false); sfx.back(); e.preventDefault() }
      else if (e.key === 'Escape' || e.key === 'Backspace') { setOverlay((o) => { sfx[o ? 'back' : 'select'](); return !o }); e.preventDefault() }
      else if (e.key.toLowerCase() === 'a' && night && !overlay) { setRecap(true); sfx.select() }
      else if (e.key.toLowerCase() === 'f') document.documentElement.requestFullscreen?.().catch(() => undefined)
      else if (e.key.toLowerCase() === 'm') { const m = { ...mix, on: !mix.on }; setMix(m); setMixState(m) }
      else if (e.key.toLowerCase() === 'n' && mix.spotify) void spotify.send('next')
      else if (e.key.toLowerCase() === 'p' && stage) cmd({ t: stage.paused ? 'resume' : 'pause' })
      else if (e.key.toLowerCase() === 's' && (stage?.game as { phase?: string } | undefined)?.phase === 'teamup') { cmd({ t: 'gameAction', action: 'shuffle' }); sfx.select() }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  const updateMix = (m: Mix) => { setMix(m); setMixState(m) }
  // Spotify takes over from the score: start it playing when switched on, pause it when the score comes back.
  const toggleSpotify = () => {
    const on = !mix.spotify
    updateMix({ ...mix, spotify: on })
    void spotify.send(on ? 'play' : 'pause')
  }
  const playing = mix.spotify ? nowPlaying(spotify.status) : null

  return (
    <div style={{ position: 'absolute', inset: 0, cursor: idle && !overlay ? 'none' : 'default' }}>
      <AnimatePresence mode="wait">
        <motion.div key={stage ? `game-${stage.gameId}` : 'lobby'} style={{ position: 'absolute', inset: 0 }}
          initial={{ clipPath: 'circle(0% at 50% 50%)' }} animate={{ clipPath: 'circle(75% at 50% 50%)' }} exit={{ opacity: 0 }} transition={{ duration: 0.6, ease: [0.7, 0, 0.2, 1] }}>
          {!tv ? <Scene color={C.sun} /> : stage ? (
            <TimerScale.Provider value={TIMER_SCALES[lobby.timers] ?? 1}>
              {isTrivia(stage.gameId) ? <TriviaStage stage={stage} players={players} scores={tv.scores} clock={clock} />
                : stage.gameId === 'blackjack' ? <GameScene game="blackjack"><BlackjackStage stage={stage} players={players} scores={tv.scores} clock={clock} /></GameScene>
                : stage.gameId === 'turf' ? <GameScene game="turf"><TurfStage stage={stage} players={players} scores={tv.scores} clock={clock} /></GameScene>
                : stage.gameId === 'sprawl' ? <GameScene game="sprawl"><SprawlStage stage={stage} players={players} scores={tv.scores} clock={clock} /></GameScene>
                : stage.gameId === 'jeopardy' ? <GameScene game="jeopardy"><JeopardyStage stage={stage} players={players} scores={tv.scores} clock={clock} cmd={cmd} /></GameScene>
                : stage.gameId === 'imposter' ? <GameScene game="imposter"><ImposterStage stage={stage} players={players} scores={tv.scores} clock={clock} /></GameScene>
                : stage.gameId === 'doodle' ? <GameScene game="doodle"><DoodleStage stage={stage} players={players} scores={tv.scores} clock={clock} ink={ink} /></GameScene>
                : stage.gameId === 'hottype' ? <GameScene game="hottype"><HotTypeStage stage={stage} players={players} scores={tv.scores} clock={clock} /></GameScene>
                : <GameScene game="bluff"><BluffStage stage={stage} players={players} scores={tv.scores} clock={clock} /></GameScene>}
              {stage.paused && <Paused reason={stage.pauseReason} />}
            </TimerScale.Provider>
          ) : (
            <LobbyScreen tv={tv} session={session} games={games} lobby={lobby} setOption={setOption} onStart={start} keys={live && !overlay && !recapOpen} playing={playing} />
          )}
        </motion.div>
      </AnimatePresence>
      {status !== 'online' && tv && <div className="toast-tv" style={{ top: 40, bottom: 'auto' }}>Reconnecting to the party server…</div>}
      {toast && <div className="toast-tv">{toast}</div>}
      {!live && (
        <div className="gate">
          <button onClick={goLive} autoFocus>
            <Brainy size={260} />
            <Burst text="GO LIVE" width={760} height={330} size={120} fill={C.sun} tilt={-5} />
            <p>Click or press Enter for full screen and sound</p>
          </button>
        </div>
      )}
      {recapOpen && night && <NightReview night={night} onClose={() => setRecap(false)} />}
      {overlay && tv && <HostOverlay tv={tv} cmd={cmd} mix={mix} setMix={updateMix} lobby={lobby} setOption={setOption} onClose={() => setOverlay(false)}
        spotify={spotify} toggleSpotify={toggleSpotify} />}
    </div>
  )
}

/** The last game's headline, or its winner; nothing when it was ended before anyone scored. */
function lastWord(r: NonNullable<TvState['lastResult']>) {
  const top = r.standings[0]
  return r.highlights[0] ?? (top && top.score > 0 ? `Winner: ${top.name}` : '')
}

function PartyLogo() {
  return <div className="logo"><b>PARTY</b><b>OS</b></div>
}

const TEAM_CHOICES = [0, 2, 3, 4, 5, 6]

/**
 * [keys]: the lobby is in front, so the TV keyboard drives it (not while the GO LIVE gate or host controls are up).
 * [playing]: the Spotify song under the lobby, if any.
 */
function LobbyScreen({ tv, session, games, lobby, setOption, onStart, keys, playing }: {
  tv: TvState; session: TvSession; games: GameListing[]; lobby: Lobby; setOption: SetOption; onStart(g: GameListing): void; keys: boolean
  playing: string | null
}) {
  const gamePlayers = tv.players.filter((p) => p.role === 'PLAYER')
  // Everyone has to fit above the game covers: two rows of big cards, three rows up to 12, then four rows of 4.
  const crowd = gamePlayers.length > 12 ? 'packed' : gamePlayers.length > 8 ? 'dense' : ''
  const online = gamePlayers.filter((p) => p.connected).length
  const audience = tv.players.length - gamePlayers.length
  const qr = useRef<HTMLCanvasElement>(null)
  const focus = Math.min(lobby.game, Math.max(0, games.length - 1))
  const setFocus = (i: number) => { if (i !== focus) setOption('game', i) }
  const focused = games[focus]
  const captain = tv.players.find((p) => p.id === tv.captain)
  const trivia = isTrivia(focused?.id)
  const turf = focused?.id === 'turf'
  const sprawl = focused?.id === 'sprawl'
  const jeopardy = focused?.id === 'jeopardy'
  const hottype = focused?.id === 'hottype'
  useEffect(() => {
    if (qr.current && session.joinUrl) QRCode.toCanvas(qr.current, session.joinUrl, { width: 360, margin: 4, errorCorrectionLevel: 'Q' })
  }, [session.joinUrl])
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (!keys) return
      const k = e.key.toLowerCase()
      if (e.key === 'ArrowRight') { setFocus(Math.min(games.length - 1, focus + 1)); sfx.focus() }
      else if (e.key === 'ArrowLeft') { setFocus(Math.max(0, focus - 1)); sfx.focus() }
      else if ((e.key === 'ArrowUp' || e.key === 'ArrowDown') && (turf || sprawl)) {
        const at = Math.max(0, TURF_MINUTES.indexOf(lobby.minutes))
        setOption('minutes', TURF_MINUTES[Math.min(TURF_MINUTES.length - 1, Math.max(0, at + (e.key === 'ArrowUp' ? 1 : -1)))]); sfx.focus()
      }
      else if (e.key === 'ArrowUp' && focused?.id !== 'blackjack' && !jeopardy) { setOption('rounds', Math.min(8, lobby.rounds + 1)); sfx.focus() }
      else if (e.key === 'ArrowDown' && focused?.id !== 'blackjack' && !jeopardy) { setOption('rounds', Math.max(3, lobby.rounds - 1)); sfx.focus() }
      else if (k === 't' && turf) {
        const at = TURF_MODES.findIndex(([m, t]) => m === lobby.turfMode && (m !== 2 || t === lobby.teams))
        const [mode, teams] = TURF_MODES[(at + 1) % TURF_MODES.length]
        setOption('turfMode', mode); if (mode === 2) setOption('teams', teams); sfx.focus()
      }
      else if (k === 'v' && sprawl) { setOption('vp', lobby.vp === 10 ? 8 : 10); sfx.focus() }
      else if (k === 's' && jeopardy) { setOption('show', lobby.show ? 0 : 1); sfx.focus() }
      else if (k === 's' && turf) { setOption('pace', lobby.pace ? 0 : 1); sfx.focus() }
      else if (k === 'g' && hottype) { setOption('grid', lobby.grid === 1 ? 0 : 1); sfx.focus() }
      else if (k === 'r') { setOption('timers', (lobby.timers + 1) % TIMER_SCALES.length); sfx.focus() }
      else if (k === 't' && trivia) { setOption('teams', TEAM_CHOICES[(TEAM_CHOICES.indexOf(lobby.teams) + 1) % TEAM_CHOICES.length] ?? 0); sfx.focus() }
      else if (k === 'd' && (trivia || turf || sprawl || jeopardy || hottype)) { setOption('drinks', lobby.drinks ? 0 : 1); sfx.focus() }
      else if (e.key === 'Enter' && focused) onStart(focused)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })
  return (
    <Scene color={C.sun}>
      <div className={`lobby ${crowd} ${captain || tv.lastResult ? 'notes' : ''}`}>
        <div className="lobby-left">
          <PartyLogo />
          <Panel className="qr-panel" fill={C.white} tilt={-2}>
            {session.joinUrl ? <canvas ref={qr} /> : <p style={{ width: 330, fontSize: 30 }}>Connect this computer to Wi-Fi to show the join code</p>}
            <span className="room-code">{tv.roomCode}</span>
            <Burst text="SCAN ME" width={280} height={112} size={38} fill={C.tomato} ink={C.white} tilt={-3} spikes={14} className="scan" />
          </Panel>
          {session.joinUrl && <p className="join-url">{session.joinUrl.replace('http://', '')}</p>}
          <p className="lobby-help">Same Wi-Fi as this computer. Guest networks often block phones.</p>
        </div>
        <div className="lobby-right">
          <div className="lobby-head">
            <h1>{online === 0 ? 'WHO’S PLAYING?' : online === 1 ? '1 PLAYER' : `${online} PLAYERS`}</h1>
            {audience > 0 && <span className="aud">+ {audience} watching</span>}
            {playing && <span className="now-playing"><b>ON SPOTIFY</b><span className="song">{playing}</span><Keycap label="N" /> next</span>}
          </div>
          <div className="cast">
            {gamePlayers.length === 0 && (
              <div className="cast-empty"><Brainy size={200} /><Panel fill={C.paper} tilt={-1} style={{ padding: '22px 30px' }}>Scan the code, type your name, draw your face.</Panel></div>
            )}
            {gamePlayers.map((p, i) => <CastCard key={p.id} p={p} i={i} size={crowd} captain={p.id === tv.captain} />)}
          </div>
          {(captain || tv.lastResult) && (
            <div className="lobby-notes">
              {captain && <p className="crown-note"><Crown size={40} /><span><b>{captain.name}</b> has the crown and can run the show from their phone.</span></p>}
              {tv.lastResult && <p className="last-game">Last game: {tv.lastResult.title}. {lastWord(tv.lastResult)}</p>}
            </div>
          )}
          <div className="controls-row">
            {focused?.id === 'blackjack'
              ? <span className="stepper">Everyone deals once: <b>one hand per player</b></span>
              : turf || sprawl ? <span className="stepper"><Keycap label="↑" /><Keycap label="↓" /> Game clock <b>{turfMinutesText(lobby.minutes)}</b></span>
              : jeopardy ? <span className="stepper"><Keycap label="S" /> Show <b>{lobby.show ? 'FULL' : 'SHORT'}</b></span>
              : <span className="stepper"><Keycap label="↑" /><Keycap label="↓" /> {trivia ? 'Questions per round' : 'Rounds'} <b>{lobby.rounds}</b></span>}
            {trivia && <span className="stepper"><Keycap label="T" /> Teams <b>{lobby.teams === 0 ? 'AUTO' : lobby.teams}</b></span>}
            {turf && <span className="stepper"><Keycap label="T" /> Play <b>{turfModeText(lobby)}</b></span>}
            {turf && <span className="stepper"><Keycap label="S" /> Show <b>{lobby.pace ? 'QUICK' : 'THEATRE'}</b></span>}
            {sprawl && <span className="stepper"><Keycap label="V" /> First to <b>{lobby.vp} POINTS</b></span>}
            {hottype && <span className="stepper"><Keycap label="G" /> Board <b>{lobby.grid === 1 ? '5×5' : '4×4'}</b></span>}
            {(trivia || turf || sprawl || jeopardy || hottype) && <span className="stepper"><Keycap label="D" /> Drink calls <b>{lobby.drinks ? 'ON' : 'OFF'}</b></span>}
            <span className="stepper"><Keycap label="R" /> Timers <b>{TIMER_NAMES[lobby.timers] ?? TIMER_NAMES[0]}</b></span>
            {tv.night && <span className="stepper"><Keycap label="A" /> Night in Review</span>}
            <span style={{ flex: 1 }} />
            {/* The TV's other keys (P pause, F full screen) are on the pause card and in the README. */}
            <span className="stepper"><Keycap label="Esc" /> host <Keycap label="M" /> mute</span>
            <span className="stepper"><Keycap label="←" /><Keycap label="→" /> pick <Keycap label="Enter" /> start</span>
          </div>
          {/* Up to five games side by side, six in two rows of three, and seven or more in two rows: a third row would
              squeeze the players onto the settings above it. */}
          <div className={`picker ${games.length > 3 ? 'four' : ''} ${games.length === 5 ? 'five' : ''} ${games.length > 5 ? 'six' : ''}`}
            style={{ gridTemplateColumns: `repeat(${games.length > 6 ? Math.ceil(games.length / 2) : games.length > 5 ? 3 : Math.max(1, games.length)}, minmax(0, 1fr))` }}>
            {games.map((g, i) => {
              const enough = online >= g.minPlayers
              return (
                <button key={g.id} className={`cover cover-${g.id} ${focus === i ? 'focused' : ''} ${enough ? '' : 'short'}`} style={{ '--len': Math.max(...g.title.split(' ').map((w) => w.length)) } as CSSProperties} onClick={() => onStart(g)}>
                  <CoverArt id={g.id} />
                  <div className="title">
                    <h2>{g.title.toUpperCase()}</h2>
                    <p>{g.tagline}</p>
                  </div>
                </button>
              )
            })}
          </div>
        </div>
      </div>
    </Scene>
  )
}

/** [size]: '' for up to 8 players, 'dense' up to 12, 'packed' up to 16. */
function CastCard({ p, i, size, captain }: { p: PlayerSummary; i: number; size: '' | 'dense' | 'packed'; captain: boolean }) {
  const [face, crown, crownTop] = size === 'packed' ? [42, 30, -18] : size === 'dense' ? [52, 34, -20] : [72, 44, -26]
  return (
    <Pop>
      <Panel className={`cast-card ${p.connected ? '' : 'away'} ${captain ? 'captain' : ''}`} fill={C.paper} tilt={[-2, 1.5, -1, 2][i % 4]}>
        {captain && <Crown size={crown} style={{ position: 'absolute', left: size ? 12 : 16, top: crownTop, transform: 'rotate(-14deg)' }} />}
        <AvatarFace avatar={p.avatar} size={face} dim={!p.connected} />
        <span>{p.name}</span>
      </Panel>
    </Pop>
  )
}

/** Music under the show: the composed score, or Spotify on this Mac with play/pause/skip. */
function SpotifyRow({ mix, spotify, toggle }: { mix: Mix; spotify: ReturnType<typeof useSpotify>; toggle(): void }) {
  const s = spotify.status
  if (s && !s.available) {
    return <div className="row"><h2 style={{ fontSize: 32, flex: 1 }}>MUSIC</h2><span className="stepper">Install Spotify on this Mac to play it under the show</span></div>
  }
  const song = nowPlaying(s)
  const note = spotify.failed
    ? 'Spotify didn’t answer. If macOS asked to let “java” control Spotify, click OK, then try again.'
    : !mix.spotify ? null : s?.running ? (song ?? 'Pick a playlist in Spotify') : 'Opening Spotify…'
  return (
    <div className="stack" style={{ gap: 12 }}>
      <div className="row" style={{ flexWrap: 'wrap' }}>
        <h2 style={{ fontSize: 32, flex: 1 }}>MUSIC</h2>
        <button className={`tv-btn small ${mix.spotify ? '' : 'quiet'}`} onClick={toggle}>{mix.spotify ? 'SPOTIFY' : 'PARTY OS SCORE'}</button>
      </div>
      {mix.spotify && (
        <div className="row" style={{ flexWrap: 'wrap' }}>
          <button className="tv-btn small quiet" onClick={() => void spotify.send('previous')}>BACK</button>
          <button className="tv-btn small" onClick={() => void spotify.send(s?.playing ? 'pause' : 'play')}>{s?.playing ? 'PAUSE' : 'PLAY'}</button>
          <button className="tv-btn small quiet" onClick={() => void spotify.send('next')}>NEXT SONG</button>
        </div>
      )}
      {note && <span className="stepper" style={{ fontSize: 24 }}>{note}</span>}
    </div>
  )
}

function HostOverlay({ tv, cmd, mix, setMix, lobby, setOption, onClose, spotify, toggleSpotify }: {
  tv: TvState; cmd(c: HostCommand): void; mix: Mix; setMix(m: Mix): void; lobby: Lobby; setOption: SetOption; onClose(): void
  spotify: ReturnType<typeof useSpotify>; toggleSpotify(): void
}) {
  const stage = tv.stage
  const [confirmEnd, setConfirmEnd] = useState(false)
  const teams = stage && isTrivia(stage.gameId) ? (stage.game as unknown as TriviaTv | undefined)?.teams ?? [] : []
  return (
    <div className="host-overlay" onClick={onClose}>
      <Slam from={1.2} tilt={-2}>
        <Panel className="host-panel" fill={C.paper} onClick={(e) => e.stopPropagation()}>
          <div className="row"><h2 style={{ flex: 1 }}>HOST CONTROLS</h2><button className="tv-btn small quiet" onClick={onClose}>CLOSE</button></div>
          {stage ? (
            <div className="row" style={{ flexWrap: 'wrap' }}>
              {stage.paused ? <button className="tv-btn" onClick={() => cmd({ t: 'resume' })}>RESUME</button> : <button className="tv-btn" onClick={() => cmd({ t: 'pause' })}>PAUSE</button>}
              <button className="tv-btn quiet" onClick={() => cmd({ t: 'skip' })}>SKIP AHEAD</button>
              {confirmEnd ? <button className="tv-btn danger" onClick={() => { cmd({ t: 'end' }); onClose() }}>REALLY END?</button>
                : <button className="tv-btn danger" onClick={() => setConfirmEnd(true)}>END GAME</button>}
            </div>
          ) : (
            <div className="row"><span className="stepper" style={{ fontSize: 28 }}>Rounds</span>
              <button className="tv-btn small quiet" onClick={() => setOption('rounds', Math.max(3, lobby.rounds - 1))}>−</button>
              <b className="display" style={{ fontSize: 40, fontWeight: 400 }}>{lobby.rounds}</b>
              <button className="tv-btn small quiet" onClick={() => setOption('rounds', Math.min(8, lobby.rounds + 1))}>+</button></div>
          )}
          <div className="row" style={{ flexWrap: 'wrap', gap: 30 }}>
            <button className="tv-btn small" onClick={() => setMix({ ...mix, on: !mix.on })}>{mix.on ? 'SOUND ON' : 'SOUND OFF'}</button>
            {!mix.spotify && <label className="slider">Music <input type="range" min={0} max={1} step={0.05} value={mix.music} onChange={(e) => setMix({ ...mix, music: Number(e.target.value) })} /></label>}
            <label className="slider">Effects <input type="range" min={0} max={1} step={0.05} value={mix.sfx} onChange={(e) => setMix({ ...mix, sfx: Number(e.target.value) })} /></label>
          </div>
          <SpotifyRow mix={mix} spotify={spotify} toggle={toggleSpotify} />
          <div className="row" style={{ flexWrap: 'wrap' }}>
            <h2 style={{ fontSize: 32, flex: 1 }}>PHONE CONTROL</h2>
            <button className={`tv-btn small ${lobby.phones ? '' : 'quiet'}`} onClick={() => setOption('captain', lobby.phones ? 0 : 1)}>
              {lobby.phones ? 'CAPTAIN CAN RUN THE SHOW' : 'TV ONLY'}
            </button>
          </div>
          {teams.length > 0 && (
            <>
              <h2 style={{ fontSize: 32 }}>TEAM NAMES</h2>
              <div className="host-players">
                {teams.map((t) => (
                  <div key={t.id}>
                    <i className="team-swatch" style={{ background: t.color }} />
                    <span className="name">{t.name}</span>
                    {/* The host's veto: a typed name goes back to the team's kit name (and can be renamed in Team Up). */}
                    <button className="tv-btn small quiet" onClick={() => cmd({ t: 'gameAction', action: `unname:${t.id}` })}>RESET NAME</button>
                  </div>
                ))}
              </div>
            </>
          )}
          <h2 style={{ fontSize: 32 }}>PLAYERS</h2>
          <div className="host-players">
            {tv.players.map((p) => (
              <div key={p.id}>
                <AvatarFace avatar={p.avatar} size={48} dim={!p.connected} />
                <span className="name">{p.name}{p.role === 'SPECTATOR' ? ' (watching)' : ''}</span>
                {lobby.phones && (p.id === tv.captain
                  ? <Crown size={40} />
                  : <button className="tv-btn small quiet" onClick={() => cmd({ t: 'makeCaptain', playerId: p.id })}>CROWN</button>)}
                <button className="tv-btn small danger" onClick={() => { if (confirm(`Remove ${p.name}?`)) cmd({ t: 'kick', playerId: p.id }) }}>REMOVE</button>
              </div>
            ))}
          </div>
        </Panel>
      </Slam>
    </div>
  )
}
