import { useEffect, useMemo, useRef, useState } from 'react'
import type { HostCommand, PlayerSummary, ScoreRow, StageInfo } from '../protocol'
import { Fill, GameHeader, Podium, Tutorial } from './Shared'
import { AvatarFace, C, Panel, Pop } from './toon'
import { useClipPlayer } from './useClipPlayer'
import type { SongDropTv } from './types'
import './songdrop.css'

type Clock = { deadline: number | null; frozen: number | null }
type Who = Map<string, PlayerSummary>
const CLIP_SECONDS = [2, 4, 8, 15]
const LOAD_MS = 10_000, REVEAL_MS = 9_000, PODIUM_MS = 20_000

/** [still]: draw the beat without starting a YouTube player (the design gallery). */
export function SongDropStage({ stage, players, scores, clock, cmd, still = false }: {
  stage: StageInfo; players: PlayerSummary[]; scores: ScoreRow[]; clock: Clock; cmd(c: HostCommand): void; still?: boolean
}) {
  if (stage.tutorial) {
    return (
      <div className="stage-pad">
        <GameHeader title="Song Drop" stage={stage} total={30_000} clock={clock} chips={[['HOW TO PLAY', C.paper]]} />
        <Tutorial cards={stage.tutorial.cards} acked={stage.tutorial.acked} players={players} />
      </div>
    )
  }
  return <Live stage={stage} players={players} scores={scores} clock={clock} cmd={cmd} still={still} />
}

function Live({ stage, players, scores, clock, cmd, still }: {
  stage: StageInfo; players: PlayerSummary[]; scores: ScoreRow[]; clock: Clock; cmd(c: HostCommand): void; still: boolean
}) {
  const g = stage.game as unknown as SongDropTv
  const who = useMemo(() => new Map(players.map((p) => [p.id, p])), [players])
  const host = useRef<HTMLDivElement>(null)
  // The cover is one layer over the player. V takes it off, to see what YouTube is doing (or when a clip won't start).
  const [showVideo, setShowVideo] = useState(false)
  useEffect(() => {
    const on = (e: KeyboardEvent) => { if (e.key.toLowerCase() === 'v') setShowVideo((v) => !v) }
    window.addEventListener('keydown', on)
    return () => window.removeEventListener('keydown', on)
  }, [])
  useClipPlayer(host, g, stage.paused, clock, cmd, !still)

  const total = { load: LOAD_MS, stage: g.clipMs, reveal: REVEAL_MS, podium: PODIUM_MS, dead: 8_000 }[g.phase]
  const chips: [string, string][] = [[g.phase === 'podium' || g.phase === 'dead' ? 'FINAL RESULTS' : `SONG ${g.song} OF ${g.totalSongs}`, C.paper]]
  if (g.finalSong && g.phase !== 'podium' && g.phase !== 'dead') chips.push(['LAST SONG: DOUBLE POINTS', C.sun])
  const status = g.phase === 'stage' ? `${g.answered}/${g.expected} LOCKED IN` : null
  const inSong = g.phase === 'load' || g.phase === 'stage' || g.phase === 'reveal'
  return (
    <div className="stage-pad sd">
      <GameHeader title="Song Drop" stage={stage} total={total} clock={clock} chips={chips} status={status} />
      <Fill>
        <div className={`sd-deck ${inSong ? '' : 'away'}`}>
          <div className="sd-player" ref={host} />
          {!showVideo && (
            <div className="sd-cover">
              {g.phase === 'load' && <Loading g={g} />}
              {g.phase === 'stage' && <Listening g={g} who={who} />}
              {g.phase === 'reveal' && <Reveal g={g} who={who} />}
            </div>
          )}
        </div>
        {g.phase === 'podium' && <Finale g={g} scores={scores} />}
        {g.phase === 'dead' && (
          <div className="sd-dead">
            <Panel fill={C.paper} tilt={-1} style={{ padding: '34px 46px' }}>
              <div className="sd-big">YOUTUBE ISN'T PLAYING</div>
              <p className="sd-sub">Check the internet on this Mac, then start Song Drop again. Press V to see the player.</p>
            </Panel>
          </div>
        )}
      </Fill>
    </div>
  )
}

function Loading({ g }: { g: SongDropTv }) {
  return (
    <>
      <div className="sd-big">GET READY…</div>
      <div className="sd-sub">{g.stage === 0 ? `Song ${g.song} of ${g.totalSongs}` : `Longer clip: ${CLIP_SECONDS[g.stage]} seconds`}</div>
    </>
  )
}

function Ticker({ g }: { g: SongDropTv }) {
  return (
    <div className="sd-pips" aria-label="Clip length">
      {CLIP_SECONDS.slice(0, g.stages).map((s, i) => <span key={s} className={`sd-pip ${i < g.stage ? 'done' : i === g.stage ? 'now' : ''}`}>{s}s</span>)}
    </div>
  )
}

function Listening({ g, who }: { g: SongDropTv; who: Who }) {
  const faces = (ids: string[], out = false) => ids.map((id) => {
    const p = who.get(id)
    return p ? <span key={id} className={`sd-face-wrap ${out ? 'out' : ''}`}><AvatarFace avatar={p.avatar} size={44} dim={out} />{p.name}</span> : null
  })
  return (
    <>
      <Ticker g={g} />
      <div className="sd-big">NAME THAT SONG</div>
      <div className="sd-faces">{faces(g.solvers.map((s) => s.id))}{faces(g.lockedOut, true)}</div>
    </>
  )
}

function Reveal({ g, who }: { g: SongDropTv; who: Who }) {
  const [artOk, setArtOk] = useState(true)
  const card = g.card
  if (!card) return null
  return (
    <Pop>
      <div className="sd-reveal">
        {artOk
          ? <img className="sd-art" alt="" src={`https://i.ytimg.com/vi/${card.videoId}/hqdefault.jpg`} onError={() => setArtOk(false)} />
          : <div className="sd-art" />}
        <div>
          <div className="sd-title">{card.title}</div>
          <div className="sd-artist">{card.artist}</div>
          <div className="sd-year">{card.year}</div>
          <div className="sd-solvers">
            {g.solvers.map((s) => <span key={s.id} className="sd-solver">{who.get(s.id) && <AvatarFace avatar={who.get(s.id)!.avatar} size={40} />}{s.name}<b>+{s.points ?? 0}</b></span>)}
          </div>
          {g.solvers.length === 0 && <div className="sd-note">Nobody got it.</div>}
          {g.drinks.length > 0 && <div className="sd-drinks">{g.drinks.slice(0, 6).map((d) => d.name).join(', ')}{g.drinks.length > 6 ? ` +${g.drinks.length - 6}` : ''} drink 1 sip</div>}
        </div>
      </div>
    </Pop>
  )
}

function Finale({ g, scores }: { g: SongDropTv; scores: ScoreRow[] }) {
  return (
    <div className="sd-podium">
      <Podium scores={scores} />
      <div className="sd-songs">{g.gallery.slice(0, 8).map((c, i) => <span key={i}>{c.title}</span>)}</div>
    </div>
  )
}
