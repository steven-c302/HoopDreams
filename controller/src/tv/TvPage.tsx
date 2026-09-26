import '../theme/tokens.css'
import './tv.css'
import QRCode from 'qrcode'
import { AnimatePresence, motion } from 'motion/react'
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Connection, browserSocket, browserSocketUrl, type Status } from '../net/connection'
import { rejectMessage, type GameListing, type HostCommand, type PlayerSummary, type TvState } from '../protocol'
import { audioRunning, loadMix, setMix, sfx, unlockAudio, type Mix } from './audio'
import { BlackjackStage } from './BlackjackStage'
import { BluffStage } from './BluffStage'
import { Gallery } from './Gallery'
import { TriviaStage } from './TriviaStage'
import { useCueDirector, useDeadline } from './director'
import { Paused } from './Shared'
import { SuitSprite } from './Suits'
import { AvatarFace, Brainy, Burst, C, Keycap, Panel, Pop, Scene, Slam } from './toon'

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

/** Brain Drain options chosen in the lobby. teams 0 = auto. */
interface ShowOptions { teams: number; drinks: boolean }

function Show({ session }: { session: TvSession }) {
  const [tv, setTv] = useState<TvState | null>(null)
  const [games, setGames] = useState<GameListing[]>([])
  const [status, setStatus] = useState<Status>('connecting')
  const [toast, setToast] = useState<string | null>(null)
  const [overlay, setOverlay] = useState(false)
  const [live, setLive] = useState(() => unlockAudio())
  const [rounds, setRounds] = useState(5)
  const [opts, setOpts] = useState<ShowOptions>({ teams: 0, drinks: true })
  const [mix, setMixState] = useState<Mix>(loadMix)
  const [idle, setIdle] = useState(false)
  const conn = useRef<Connection | null>(null)

  useEffect(() => { fetch('/api/games').then((r) => r.json()).then(setGames).catch(() => setGames([])) }, [])
  useEffect(() => {
    const c = new Connection(browserSocketUrl(`host=${encodeURIComponent(session.hostToken)}`), browserSocket, {
      onStatus: setStatus,
      onMessage: (m) => {
        if (m.t === 'tv') setTv(m.tv)
        if (m.t === 'reject') { const t = rejectMessage(m.code); if (t) setToast(t) }
      },
    })
    conn.current = c
    c.start()
    return () => c.stop()
  }, [session.hostToken])
  useEffect(() => { if (!toast) return; const id = setTimeout(() => setToast(null), 3200); return () => clearTimeout(id) }, [toast])

  const cmd = useCallback((c: HostCommand) => conn.current?.host(c), [])
  const clock = useDeadline(tv)
  useCueDirector(live ? tv : null, clock.deadline)

  // Hide the mouse when it stops moving: this is a TV.
  useEffect(() => {
    let id: ReturnType<typeof setTimeout>
    const wake = () => { setIdle(false); clearTimeout(id); id = setTimeout(() => setIdle(true), 2500) }
    window.addEventListener('mousemove', wake); wake()
    return () => { window.removeEventListener('mousemove', wake); clearTimeout(id) }
  }, [])

  const goLive = () => {
    unlockAudio()
    setTimeout(() => setLive(audioRunning()), 60)
    setLive(true)
    document.documentElement.requestFullscreen?.().catch(() => undefined)
    sfx.showOpen()
  }

  const stage = tv?.stage
  const players = tv?.players ?? []
  const start = (g: GameListing) => {
    sfx.select()
    cmd({ t: 'start', gameId: g.id, rounds, options: g.id === 'trivia' ? { teams: opts.teams, drinks: opts.drinks ? 1 : 0 } : {} })
  }

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (!live) { if (e.key === 'Enter' || e.key === ' ') goLive(); return }
      if (e.key === 'Escape' || e.key === 'Backspace') { setOverlay((o) => { sfx[o ? 'back' : 'select'](); return !o }); e.preventDefault() }
      else if (e.key.toLowerCase() === 'f') document.documentElement.requestFullscreen?.().catch(() => undefined)
      else if (e.key.toLowerCase() === 'm') { const m = { ...mix, on: !mix.on }; setMix(m); setMixState(m) }
      else if (e.key.toLowerCase() === 'p' && stage) cmd({ t: stage.paused ? 'resume' : 'pause' })
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  const updateMix = (m: Mix) => { setMix(m); setMixState(m) }

  return (
    <div style={{ position: 'absolute', inset: 0, cursor: idle && !overlay ? 'none' : 'default' }}>
      <AnimatePresence mode="wait">
        <motion.div key={stage ? `game-${stage.gameId}` : 'lobby'} style={{ position: 'absolute', inset: 0 }}
          initial={{ clipPath: 'circle(0% at 50% 50%)' }} animate={{ clipPath: 'circle(75% at 50% 50%)' }} exit={{ opacity: 0 }} transition={{ duration: 0.6, ease: [0.7, 0, 0.2, 1] }}>
          {!tv ? <Scene color={C.sun} /> : stage ? (
            <>
              {stage.gameId === 'trivia' ? <TriviaStage stage={stage} players={players} scores={tv.scores} clock={clock} />
                : stage.gameId === 'blackjack' ? <Scene color={C.tangerine}><BlackjackStage stage={stage} players={players} scores={tv.scores} clock={clock} /></Scene>
                : <Scene color={C.bubblegum}><BluffStage stage={stage} players={players} scores={tv.scores} clock={clock} /></Scene>}
              {stage.paused && <Paused reason={stage.pauseReason} />}
            </>
          ) : (
            <Lobby tv={tv} session={session} games={games} rounds={rounds} setRounds={setRounds} opts={opts} setOpts={setOpts} onStart={start} />
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
      {overlay && tv && <HostOverlay tv={tv} cmd={cmd} mix={mix} setMix={updateMix} rounds={rounds} setRounds={setRounds} onClose={() => setOverlay(false)} />}
      {live && !overlay && <div className="hint"><Keycap label="Esc" /> host <Keycap label="P" /> pause <Keycap label="M" /> mute <Keycap label="F" /> full screen</div>}
    </div>
  )
}

function PartyLogo() {
  return <div className="logo"><b>PARTY</b><b>OS</b></div>
}

const TEAM_CHOICES = [0, 2, 3, 4, 5, 6]

function Lobby({ tv, session, games, rounds, setRounds, opts, setOpts, onStart }: {
  tv: TvState; session: TvSession; games: GameListing[]; rounds: number; setRounds(n: number): void
  opts: ShowOptions; setOpts(o: ShowOptions): void; onStart(g: GameListing): void
}) {
  const gamePlayers = tv.players.filter((p) => p.role === 'PLAYER')
  const online = gamePlayers.filter((p) => p.connected).length
  const audience = tv.players.length - gamePlayers.length
  const qr = useRef<HTMLCanvasElement>(null)
  const [focus, setFocus] = useState(0)
  const focused = games[focus]
  const trivia = focused?.id === 'trivia'
  useEffect(() => {
    if (qr.current && session.joinUrl) QRCode.toCanvas(qr.current, session.joinUrl, { width: 330, margin: 0, errorCorrectionLevel: 'M' })
  }, [session.joinUrl])
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const k = e.key.toLowerCase()
      if (e.key === 'ArrowRight') { setFocus((f) => Math.min(games.length - 1, f + 1)); sfx.focus() }
      else if (e.key === 'ArrowLeft') { setFocus((f) => Math.max(0, f - 1)); sfx.focus() }
      else if (e.key === 'ArrowUp') { setRounds(Math.min(8, rounds + 1)); sfx.focus() }
      else if (e.key === 'ArrowDown') { setRounds(Math.max(3, rounds - 1)); sfx.focus() }
      else if (k === 't' && trivia) { setOpts({ ...opts, teams: TEAM_CHOICES[(TEAM_CHOICES.indexOf(opts.teams) + 1) % TEAM_CHOICES.length] }); sfx.focus() }
      else if (k === 'd' && trivia) { setOpts({ ...opts, drinks: !opts.drinks }); sfx.focus() }
      else if (e.key === 'Enter' && focused) onStart(focused)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })
  return (
    <Scene color={C.sun}>
      <div className="lobby">
        <div className="lobby-left">
          <PartyLogo />
          <Panel className="qr-panel" fill={C.white} tilt={-2}>
            {session.joinUrl ? <canvas ref={qr} /> : <p style={{ width: 330, fontSize: 30 }}>Connect this computer to Wi-Fi to show the join code</p>}
            <span className="room-code">{tv.roomCode}</span>
            <Burst text="SCAN ME" width={250} height={150} size={36} fill={C.tomato} ink={C.white} tilt={14} spikes={12} className="scan" />
          </Panel>
          {session.joinUrl && <p className="join-url">{session.joinUrl.replace('http://', '')}</p>}
          <p className="lobby-help">Same Wi-Fi as this computer. Guest networks often block phones.</p>
        </div>
        <div className="lobby-right">
          <div className="lobby-head">
            <h1>{online === 0 ? 'WHO’S PLAYING?' : online === 1 ? '1 PLAYER' : `${online} PLAYERS`}</h1>
            {audience > 0 && <span className="aud">+ {audience} watching</span>}
          </div>
          <div className={`cast ${gamePlayers.length > 8 ? 'dense' : ''}`}>
            {gamePlayers.length === 0 && (
              <div className="cast-empty"><Brainy size={200} /><Panel fill={C.paper} tilt={-1} style={{ padding: '22px 30px' }}>Scan the code, type your name, draw your face.</Panel></div>
            )}
            {gamePlayers.map((p, i) => <CastCard key={p.id} p={p} i={i} small={gamePlayers.length > 8} />)}
          </div>
          {tv.lastResult && <p className="last-game">Last game: {tv.lastResult.title}. {tv.lastResult.highlights[0] ?? `Winner: ${tv.lastResult.standings[0]?.name ?? '-'}`}</p>}
          <div className="controls-row">
            <span className="stepper"><Keycap label="↑" /><Keycap label="↓" /> {trivia ? 'Questions per round' : 'Rounds'} <b>{rounds}</b></span>
            {trivia && <span className="stepper"><Keycap label="T" /> Teams <b>{opts.teams === 0 ? 'AUTO' : opts.teams}</b></span>}
            {trivia && <span className="stepper"><Keycap label="D" /> Drink calls <b>{opts.drinks ? 'ON' : 'OFF'}</b></span>}
            <span style={{ flex: 1 }} />
            <span className="stepper"><Keycap label="←" /><Keycap label="→" /> pick <Keycap label="Enter" /> start</span>
          </div>
          <div className="picker">
            {games.map((g, i) => {
              const enough = online >= g.minPlayers
              return (
                <button key={g.id} className={`cover ${focus === i ? 'focused' : ''}`} onClick={() => onStart(g)} onMouseEnter={() => setFocus(i)}>
                  <CoverArt id={g.id} />
                  <span className="issue">No. {i + 1}</span>
                  <div className="title">
                    <h2>{g.title.toUpperCase()}</h2>
                    <p className={enough ? '' : 'need'}>{enough ? g.tagline : `Needs ${g.minPlayers} players`}</p>
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

function CastCard({ p, i, small }: { p: PlayerSummary; i: number; small: boolean }) {
  return (
    <Pop>
      <Panel className={`cast-card ${p.connected ? '' : 'away'}`} fill={C.paper} tilt={[-2, 1.5, -1, 2][i % 4]}>
        <AvatarFace avatar={p.avatar} size={small ? 52 : 72} dim={!p.connected} />
        <span>{p.name}</span>
      </Panel>
    </Pop>
  )
}

/** Code-drawn covers: Brainy for Brain Drain, a liar's grin for Bluff Battle, a fanned hand for Drunk Blackjack. */
function CoverArt({ id }: { id: string }) {
  const rays = (fill: string) => (
    <g opacity=".22">{Array.from({ length: 12 }, (_, k) => <path key={k} d="M400 170 L1100 -40 L1100 120 Z" fill={fill} transform={`rotate(${k * 30} 400 170)`} />)}</g>
  )
  if (id === 'trivia') {
    return (
      <svg className="art" viewBox="0 0 560 330" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
        <rect width="560" height="330" fill="var(--bubblegum)" />{rays('var(--white)')}
        <g transform="translate(250 0) scale(1.35)"><BrainyInline /></g>
      </svg>
    )
  }
  if (id === 'blackjack') {
    return (
      <svg className="art" viewBox="0 0 560 330" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
        <rect width="560" height="330" fill="var(--felt)" />{rays('var(--sun)')}
        {[-18, 0, 18].map((r, k) => (
          <g key={r} transform={`translate(${390 + k * 40} 150) rotate(${r})`}>
            <rect x="-55" y="-78" width="110" height="156" rx="12" fill="var(--white)" stroke="var(--ink)" strokeWidth="6" />
            <text x="-38" y="-40" fontFamily="Rammetto One" fontSize="32" fill={k === 1 ? 'var(--tomato)' : 'var(--ink)'}>{['A', 'K', '7'][k]}</text>
          </g>
        ))}
      </svg>
    )
  }
  return (
    <svg className="art" viewBox="0 0 560 330" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
      <rect width="560" height="330" fill="var(--grape)" />{rays('var(--white)')}
      <g transform="translate(420 140)">
        <path d="M-110 -40 Q0 -120 110 -40 Q120 60 0 90 Q-120 60 -110 -40 Z" fill="var(--sun)" stroke="var(--ink)" strokeWidth="7" />
        <path d="M-80 -30 Q-45 -60 -10 -30 Q-45 -10 -80 -30 Z M10 -30 Q45 -60 80 -30 Q45 -10 10 -30 Z" fill="var(--ink)" />
        <path d="M-60 30 Q0 70 60 30" fill="none" stroke="var(--ink)" strokeWidth="7" strokeLinecap="round" />
        <path d="M0 -8 L0 20 L80 60" fill="none" stroke="var(--ink)" strokeWidth="7" strokeLinecap="round" strokeLinejoin="round" />
      </g>
    </svg>
  )
}

/** Brainy at cover scale (the host component draws an <svg>, so the cover inlines its own copy). */
function BrainyInline() {
  return (
    <g>
      <path d="M150 58 L158 10 L196 -4" fill="none" stroke="var(--ink)" strokeWidth="20" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M150 58 L158 10 L196 -4" fill="none" stroke="var(--white)" strokeWidth="10" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M150 58 L158 10 L196 -4" fill="none" stroke="var(--tomato)" strokeWidth="10" strokeDasharray="7 9" strokeLinejoin="round" />
      <path d="M45 124 C15 122 12 84 36 74 C30 46 60 30 84 42 C92 18 132 16 142 38 C166 26 196 46 188 72 C212 82 208 120 184 126 C186 150 160 164 140 154 C128 172 92 172 82 156 C60 166 36 152 45 124 Z"
        fill="var(--paper)" stroke="var(--ink)" strokeWidth="7" strokeLinejoin="round" />
      <circle cx="100" cy="102" r="17" fill="var(--white)" stroke="var(--ink)" strokeWidth="5" /><circle cx="138" cy="102" r="17" fill="var(--white)" stroke="var(--ink)" strokeWidth="5" />
      <circle cx="102" cy="106" r="7.5" fill="var(--ink)" /><circle cx="140" cy="106" r="7.5" fill="var(--ink)" />
      <path d="M104 134 Q119 152 134 134 Z" fill="var(--white)" stroke="var(--ink)" strokeWidth="5" strokeLinejoin="round" />
    </g>
  )
}

function HostOverlay({ tv, cmd, mix, setMix, rounds, setRounds, onClose }: {
  tv: TvState; cmd(c: HostCommand): void; mix: Mix; setMix(m: Mix): void; rounds: number; setRounds(n: number): void; onClose(): void
}) {
  const stage = tv.stage
  const [confirmEnd, setConfirmEnd] = useState(false)
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
              <button className="tv-btn small quiet" onClick={() => setRounds(Math.max(3, rounds - 1))}>−</button>
              <b className="display" style={{ fontSize: 40, fontWeight: 400 }}>{rounds}</b>
              <button className="tv-btn small quiet" onClick={() => setRounds(Math.min(8, rounds + 1))}>+</button></div>
          )}
          <div className="row" style={{ flexWrap: 'wrap', gap: 30 }}>
            <button className="tv-btn small" onClick={() => setMix({ ...mix, on: !mix.on })}>{mix.on ? 'SOUND ON' : 'SOUND OFF'}</button>
            <label className="slider">Music <input type="range" min={0} max={1} step={0.05} value={mix.music} onChange={(e) => setMix({ ...mix, music: Number(e.target.value) })} /></label>
            <label className="slider">Effects <input type="range" min={0} max={1} step={0.05} value={mix.sfx} onChange={(e) => setMix({ ...mix, sfx: Number(e.target.value) })} /></label>
          </div>
          <h2 style={{ fontSize: 32 }}>PLAYERS</h2>
          <div className="host-players">
            {tv.players.map((p) => (
              <div key={p.id}>
                <AvatarFace avatar={p.avatar} size={48} dim={!p.connected} />
                <span className="name">{p.name}{p.role === 'SPECTATOR' ? ' (watching)' : ''}</span>
                <button className="tv-btn small danger" onClick={() => { if (confirm(`Remove ${p.name}?`)) cmd({ t: 'kick', playerId: p.id }) }}>REMOVE</button>
              </div>
            ))}
          </div>
        </Panel>
      </Slam>
    </div>
  )
}
