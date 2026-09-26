import '@fontsource/bungee'
import '@fontsource/bungee-shade'
import '@fontsource-variable/rubik'
import './tv.css'
import QRCode from 'qrcode'
import { AnimatePresence, motion } from 'motion/react'
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Connection, browserSocket, browserSocketUrl, type Status } from '../net/connection'
import { rejectMessage, type GameListing, type HostCommand, type PlayerSummary, type TvState } from '../protocol'
import { audioRunning, loadMix, setMix, sfx, unlockAudio, type Mix } from './audio'
import { BlackjackStage } from './BlackjackStage'
import { BluffStage } from './BluffStage'
import { Led } from './Casino'
import { Gallery } from './Gallery'
import { useCueDirector, useDeadline } from './director'
import { Paused } from './Shared'
import { SuitSprite } from './Suits'
import { AvatarDot, C, Marquee, OnAir, Pop, Slam, StudioBackdrop } from './Studio'

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
      else setError(r.status === 403 ? 'Open this page on the computer that is running PARTY OS (it is the TV).' : `The party server answered ${r.status}.`)
    }).catch(() => setError('Cannot reach the PARTY OS server. Start it with “Start Party OS.command”.'))
  }, [])
  const scale = useStageScale()
  return (
    <div className="tv-root">
      <SuitSprite />
      <div className="tv-stage" style={{ transform: `scale(${scale}) translate(-50%, -50%)` }}>
        {session ? <Show session={session} /> : (
          <StudioBackdrop>
            <div style={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center' }}>
              <Marquee><span className="marquee-font" style={{ fontSize: 110, color: C.gold }}>PARTY OS</span>
                <p style={{ fontSize: 40, fontWeight: 700, marginTop: 20, maxWidth: 1100, textAlign: 'center' }}>{error ?? 'Warming up the studio…'}</p></Marquee>
            </div>
          </StudioBackdrop>
        )}
      </div>
    </div>
  )
}

function Show({ session }: { session: TvSession }) {
  const [tv, setTv] = useState<TvState | null>(null)
  const [games, setGames] = useState<GameListing[]>([])
  const [status, setStatus] = useState<Status>('connecting')
  const [toast, setToast] = useState<string | null>(null)
  const [overlay, setOverlay] = useState(false)
  const [live, setLive] = useState(() => unlockAudio())
  const [rounds, setRounds] = useState(5)
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
  const start = (g: GameListing) => { sfx.select(); cmd({ t: 'start', gameId: g.id, rounds }) }

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
    <div className={idle && !overlay ? 'tv-idle' : ''} style={{ position: 'absolute', inset: 0, cursor: idle && !overlay ? 'none' : 'default' }}>
      <AnimatePresence mode="wait">
        <motion.div key={stage ? `game-${stage.gameId}` : 'lobby'} style={{ position: 'absolute', inset: 0 }}
          initial={{ clipPath: 'inset(0 50% 0 50%)' }} animate={{ clipPath: 'inset(0 0% 0 0%)' }} exit={{ opacity: 0 }} transition={{ duration: 0.55, ease: [0.7, 0, 0.2, 1] }}>
          {!tv ? <StudioBackdrop /> : stage ? (
            <StudioBackdrop floor={stage.gameId === 'blackjack' ? C.gold : C.felt} rays={stage.gameId === 'blackjack' ? C.pink : C.brass} carpet={stage.gameId === 'blackjack'}>
              {stage.gameId === 'blackjack'
                ? <BlackjackStage stage={stage} players={players} scores={tv.scores} clock={clock} />
                : <BluffStage stage={stage} players={players} scores={tv.scores} clock={clock} />}
              {stage.paused && <Paused reason={stage.pauseReason} />}
            </StudioBackdrop>
          ) : (
            <Lobby tv={tv} session={session} games={games} rounds={rounds} setRounds={setRounds} onStart={start} />
          )}
        </motion.div>
      </AnimatePresence>
      {status !== 'online' && tv && <div className="toast-tv" style={{ top: 30, bottom: 'auto' }}>Reconnecting to the party server…</div>}
      {toast && <div className="toast-tv">{toast}</div>}
      {!live && (
        <div className="gate">
          <button onClick={goLive} autoFocus>
            <Slam><Marquee><span className="marquee-font" style={{ fontSize: 120, color: C.gold }}>GO LIVE</span>
              <p style={{ fontSize: 40, fontWeight: 800 }}>Click or press Enter · full screen + sound</p></Marquee></Slam>
          </button>
        </div>
      )}
      {overlay && tv && <HostOverlay tv={tv} cmd={cmd} mix={mix} setMix={updateMix} rounds={rounds} setRounds={setRounds} onClose={() => setOverlay(false)} />}
      {live && !overlay && <div className="hint">ESC host controls · P pause · M mute · F full screen</div>}
    </div>
  )
}

function Lobby({ tv, session, games, rounds, setRounds, onStart }: { tv: TvState; session: TvSession; games: GameListing[]; rounds: number; setRounds(n: number): void; onStart(g: GameListing): void }) {
  const gamePlayers = tv.players.filter((p) => p.role === 'PLAYER')
  const online = gamePlayers.filter((p) => p.connected).length
  const audience = tv.players.length - gamePlayers.length
  const qr = useRef<HTMLCanvasElement>(null)
  const [focus, setFocus] = useState(0)
  useEffect(() => {
    if (qr.current && session.joinUrl) QRCode.toCanvas(qr.current, session.joinUrl, { width: 380, margin: 0, errorCorrectionLevel: 'M' })
  }, [session.joinUrl])
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'ArrowRight') { setFocus((f) => Math.min(games.length - 1, f + 1)); sfx.focus() }
      if (e.key === 'ArrowLeft') { setFocus((f) => Math.max(0, f - 1)); sfx.focus() }
      if (e.key === 'ArrowUp') { setRounds(Math.min(8, rounds + 1)); sfx.focus() }
      if (e.key === 'ArrowDown') { setRounds(Math.max(3, rounds - 1)); sfx.focus() }
      if (e.key === 'Enter' && games[focus]) onStart(games[focus])
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })
  return (
    <StudioBackdrop>
      <div className="lobby">
        <div className="lobby-left">
          <Marquee style={{ width: '100%' }}>
            <span className="display" style={{ fontSize: 44, color: C.gold }}>SCAN TO JOIN</span>
            <div className="qr" style={{ marginTop: 20 }}>{session.joinUrl ? <canvas ref={qr} /> : <p style={{ color: C.ink, width: 380, fontSize: 32 }}>Connect this computer to Wi-Fi to show the join code</p>}</div>
            <span className="display" style={{ fontSize: 26, color: '#fff4d6b0', marginTop: 20, letterSpacing: '.2em' }}>ROOM</span>
            <span className="room-code">{tv.roomCode}</span>
          </Marquee>
          {session.joinUrl && <p style={{ fontSize: 34, fontWeight: 700, marginTop: 20 }}>{session.joinUrl.replace('http://', '')}</p>}
          <div style={{ flex: 1 }} />
          <p style={{ fontSize: 28, color: C.muted, textAlign: 'center' }}>Same Wi-Fi as this computer. Guest networks often block phones from reaching it.</p>
        </div>
        <div className="lobby-right">
          <div className="row">
            <span className="display" style={{ fontSize: 64 }}>{online === 1 ? '1 CONTESTANT' : `${online} CONTESTANTS`}</span>
            {audience > 0 && <span style={{ fontSize: 40, color: C.muted, fontWeight: 700 }}>+ {audience} in the audience</span>}
            <div style={{ flex: 1 }} />
            <OnAir lit={online > 0} />
          </div>
          <div className="nametags">
            {gamePlayers.map((p, i) => <NameTag key={p.id} p={p} i={i} />)}
          </div>
          {tv.lastResult && <p style={{ fontSize: 34, color: C.muted, marginBottom: 20 }}>Last game: {tv.lastResult.title} · winner {tv.lastResult.standings[0]?.name ?? '-'}</p>}
          <div className="row" style={{ marginBottom: 20 }}>
            <span className="rounds-pill">ROUNDS <Led value={rounds} tone="gold" size={30} digits={1} /> <span style={{ color: C.muted, fontSize: 24 }}>↑↓ to change · ←→ pick · Enter to start</span></span>
          </div>
          <div className="picker">
            {games.map((g, i) => {
              const enough = online >= g.minPlayers
              return (
                <button key={g.id} className={`game-card ${g.id === "blackjack" ? "bj" : ""} ${focus === i ? "focused" : ""}`} onClick={() => onStart(g)} onMouseEnter={() => setFocus(i)}>
                  <GameArt id={g.id} />
                  <div className="title">
                    <h2>{g.title.toUpperCase()}</h2>
                    <p className={enough ? '' : 'need'}>{enough ? `${g.tagline} · press Enter` : `Needs ${g.minPlayers} players (${online} ready)`}</p>
                  </div>
                </button>
              )
            })}
          </div>
        </div>
      </div>
    </StudioBackdrop>
  )
}

function NameTag({ p, i }: { p: PlayerSummary; i: number }) {
  const tilt = [-2, 1.5, -1, 2][i % 4]
  return (
    <Pop>
      <div className={`nametag ${p.connected ? '' : 'away'}`} style={{ transform: `rotate(${tilt}deg)`, boxShadow: `10px 10px 0 ${p.avatar.color}` }}>
        <div className="hello" style={{ background: p.avatar.color }}>HELLO</div>
        <div className="who"><AvatarDot emoji={p.avatar.emoji} color={p.avatar.color} size={64} dim={!p.connected} />{p.name}</div>
      </div>
    </Pop>
  )
}

/** Code-drawn key art: Bluff Battle's brass mask on felt; Drunk Blackjack's fanned ace and king on velvet. */
function GameArt({ id }: { id: string }) {
  if (id === 'blackjack') {
    return (
      <svg className="art" viewBox="0 0 700 280" preserveAspectRatio="xMaxYMid slice">
        <defs><radialGradient id="bjart" cx="70%" cy="40%" r="80%"><stop offset="0" stopColor="#7A1446" /><stop offset="1" stopColor="#1a0612" /></radialGradient></defs>
        <rect width="700" height="280" fill="url(#bjart)" />
        <g transform="translate(560 150) rotate(-14)"><rect x="-55" y="-78" width="110" height="156" rx="12" fill="#FFF8EC" stroke="#0B0716" strokeWidth="4" />
          <text x="-40" y="-44" fontFamily="Rubik Variable" fontWeight="900" fontSize="30" fill="#17121F">A</text><text x="0" y="22" textAnchor="middle" fontSize="64" fill="#17121F">♠</text></g>
        <g transform="translate(625 160) rotate(12)"><rect x="-55" y="-78" width="110" height="156" rx="12" fill="#FFF8EC" stroke="#0B0716" strokeWidth="4" />
          <text x="-40" y="-44" fontFamily="Rubik Variable" fontWeight="900" fontSize="30" fill="#D8283F">K</text><text x="0" y="26" textAnchor="middle" fontFamily="Bungee Shade" fontSize="60" fill="#D8283F">K</text></g>
        <circle cx="470" cy="70" r="26" fill="none" stroke="#FFD23F" strokeWidth="9" strokeDasharray="8 8" />
      </svg>
    )
  }
  return (
    <svg className="art" viewBox="0 0 700 280" preserveAspectRatio="xMaxYMid slice">
      <defs><linearGradient id="felt" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#0E5A3A" /><stop offset="1" stopColor="#06301F" /></linearGradient></defs>
      <rect width="700" height="280" fill="url(#felt)" />
      <circle cx="600" cy="100" r="76" fill="none" stroke="#D9A441" strokeWidth="10" />
      <path d="M528 92 C555 55 588 70 600 92 C612 70 645 55 672 92 C660 130 618 125 600 112 C582 125 540 130 528 92 Z" fill="#D9A441" />
      <ellipse cx="572" cy="92" rx="16" ry="9" fill="#06301F" /><ellipse cx="628" cy="92" rx="16" ry="9" fill="#06301F" />
    </svg>
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
        <div className="host-panel" onClick={(e) => e.stopPropagation()}>
          <div className="row"><h2 style={{ flex: 1 }}>HOST CONTROLS</h2><button className="tv-btn small" onClick={onClose}>CLOSE · ESC</button></div>
          {stage ? (
            <div className="row" style={{ flexWrap: 'wrap' }}>
              {stage.paused ? <button className="tv-btn" onClick={() => cmd({ t: 'resume' })}>RESUME</button> : <button className="tv-btn" onClick={() => cmd({ t: 'pause' })}>PAUSE</button>}
              <button className="tv-btn" onClick={() => cmd({ t: 'skip' })}>SKIP PHASE</button>
              {confirmEnd ? <button className="tv-btn danger" onClick={() => { cmd({ t: 'end' }); onClose() }}>REALLY END?</button>
                : <button className="tv-btn danger" onClick={() => setConfirmEnd(true)}>END GAME</button>}
            </div>
          ) : (
            <div className="row"><span className="rounds-pill">ROUNDS</span>
              <button className="tv-btn small" onClick={() => setRounds(Math.max(3, rounds - 1))}>−</button>
              <Led value={rounds} tone="gold" size={30} digits={1} />
              <button className="tv-btn small" onClick={() => setRounds(Math.min(8, rounds + 1))}>+</button></div>
          )}
          <div className="row" style={{ flexWrap: 'wrap', gap: 30 }}>
            <button className="tv-btn small" onClick={() => setMix({ ...mix, on: !mix.on })}>{mix.on ? 'SOUND ON' : 'SOUND OFF'}</button>
            <label className="slider">MUSIC <input type="range" min={0} max={1} step={0.05} value={mix.music} onChange={(e) => setMix({ ...mix, music: Number(e.target.value) })} /></label>
            <label className="slider">EFFECTS <input type="range" min={0} max={1} step={0.05} value={mix.sfx} onChange={(e) => setMix({ ...mix, sfx: Number(e.target.value) })} /></label>
          </div>
          <h2 style={{ fontSize: 32 }}>PLAYERS</h2>
          <div className="host-players">
            {tv.players.map((p) => (
              <div key={p.id}>
                <AvatarDot emoji={p.avatar.emoji} color={p.avatar.color} size={48} dim={!p.connected} />
                <span className="name">{p.name}{p.role === 'SPECTATOR' ? ' (audience)' : ''}</span>
                <button className="tv-btn small danger" onClick={() => { if (confirm(`Remove ${p.name}?`)) cmd({ t: 'kick', playerId: p.id }) }}>REMOVE</button>
              </div>
            ))}
          </div>
        </div>
      </Slam>
    </div>
  )
}
