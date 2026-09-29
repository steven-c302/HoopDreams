import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { blankCells } from '../ink/blanks'
import type { InkStore } from '../ink/store'
import type { PlayerSummary, ScoreRow, StageInfo } from '../protocol'
import { InkCanvas } from './InkCanvas'
import { Deal, AvatarFace, Bubble, C, Panel, Pop, Slam, fireConfetti } from './toon'
import { Fill, GameHeader, Podium, ScoreBoard, Tutorial } from './Shared'
import { useTimerScale } from './timerScale'
import type { DoodleTv } from './types'
import './doodle.css'

const PICK_MS = 12_000, REVEAL_MS = 7_000, SCORES_MS = 6_000, PODIUM_MS = 24_000
/** The podium gives way to the gallery when this much of the phase is left. */
const GALLERY_LEFT_MS = 13_000
const LEVEL = ['', 'EASY', 'MEDIUM', 'HARD']
const LEVEL_FILL = ['', C.lime, C.sun, C.tomato]

type Clock = { deadline: number | null; frozen: number | null }
type Who = Map<string, PlayerSummary>

export function DoodleStage({ stage, players, scores, clock, ink }: { stage: StageInfo; players: PlayerSummary[]; scores: ScoreRow[]; clock: Clock; ink: InkStore }) {
  const scale = useTimerScale()
  const who = useMemo(() => new Map(players.map((p) => [p.id, p])), [players])
  if (stage.tutorial) {
    return (
      <div className="stage-pad">
        <GameHeader title="Doodle Dash" stage={stage} total={30_000} clock={clock} chips={[['HOW TO PLAY', C.paper]]} />
        <Tutorial cards={stage.tutorial.cards} acked={stage.tutorial.acked} players={players} />
      </div>
    )
  }
  const g = stage.game as unknown as DoodleTv
  const chips: [string, string][] = [[g.phase === 'podium' ? 'FINAL RESULTS' : `TURN ${g.turn} OF ${g.totalTurns}`, C.paper]]
  if (g.finalTurn && g.phase !== 'podium') chips.push(['LAST TURN: DOUBLE POINTS', C.sun])
  if (g.phase === 'draw' && g.difficulty) chips.push([LEVEL[g.difficulty], LEVEL_FILL[g.difficulty]])
  const total = { pick: PICK_MS * scale, draw: g.drawMs, reveal: REVEAL_MS, scores: SCORES_MS, podium: PODIUM_MS }[g.phase]
  // A draw runs in hint stages; the stage clock only knows the current one, so add what is left after it.
  const shown: Clock = g.phase === 'draw'
    ? { deadline: clock.deadline != null ? clock.deadline + g.tailMs : null, frozen: clock.frozen != null ? clock.frozen + g.tailMs : null }
    : clock
  const status = g.phase === 'draw' ? `${g.guessed}/${g.expected} GOT IT` : null
  return (
    <div className="stage-pad dd">
      <GameHeader title="Doodle Dash" stage={stage} total={total} clock={shown} chips={chips} status={status} />
      <Fill>
        {g.phase === 'pick' && <Picking g={g} who={who} />}
        {g.phase === 'draw' && <Drawing g={g} who={who} ink={ink} />}
        {g.phase === 'reveal' && <Reveal g={g} who={who} ink={ink} />}
        {g.phase === 'scores' && <ScoreBoard scores={scores} deltas={Object.fromEntries(g.deltas.map((d) => [d.id, d.points]))} />}
        {g.phase === 'podium' && <Finale g={g} scores={scores} stage={stage} ink={ink} />}
      </Fill>
    </div>
  )
}

function Picking({ g, who }: { g: DoodleTv; who: Who }) {
  const d = g.drawer ? who.get(g.drawer) : undefined
  return (
    <div className="dd-center">
      <div className="dd-cards" aria-hidden="true">
        {[0, 1, 2].map((i) => (
          <Deal key={i} i={i}>
            <Panel fill={[C.lime, C.sun, C.tomato][i]} tilt={[-5, 2, 6][i]} className="dd-card"><b>?</b></Panel>
          </Deal>
        ))}
      </div>
      <div className="dd-picker">
        {d && <AvatarFace avatar={d.avatar} size={120} />}
        <p className="dd-line"><b>{g.drawerName}</b> is picking a word</p>
      </div>
    </div>
  )
}

function Cells({ blanks }: { blanks: string }) {
  return (
    <div className="dd-blanks" role="img" aria-label="The word, hidden">
      {blankCells(blanks).map((word, wi) => (
        <span key={wi} className="blank-word">
          {word.map((c, ci) => <span key={ci} className={`blank-cell ${c === '_' ? '' : 'shown'}`}>{c === '_' ? '' : c}</span>)}
        </span>
      ))}
    </div>
  )
}

function Easel({ children }: { children: ReactNode }) {
  return (
    <div className="dd-frame">
      <div className="dd-paper">{children}</div>
    </div>
  )
}

function Drawing({ g, who, ink }: { g: DoodleTv; who: Who; ink: InkStore }) {
  const d = g.drawer ? who.get(g.drawer) : undefined
  return (
    <div className="dd-draw">
      <aside className="dd-side">
        <Panel fill={C.paper} tilt={-1.5} className="dd-drawer">
          {d && <AvatarFace avatar={d.avatar} size={150} />}
          <span className="label">NOW DRAWING</span>
          <span className="name">{g.drawerName}</span>
        </Panel>
      </aside>
      <div className="dd-easel">
        <Cells blanks={g.blanks} />
        <Easel><InkCanvas store={ink} turn={g.turn} mode="live" /></Easel>
      </div>
      <aside className="dd-side dd-right">
        <div className="dd-solvers">
          <AnimatePresence>
            {g.solvers.map((s) => {
              const p = who.get(s.id)
              return p ? (
                <Pop key={s.id}><div className="dd-solver"><AvatarFace avatar={p.avatar} size={72} /><span className="sticker">GOT IT</span></div></Pop>
              ) : null
            })}
          </AnimatePresence>
        </div>
        <div className="dd-bubbles">
          <AnimatePresence initial={false}>
            {g.wrong.map((m, i) => {
              const p = who.get(m.id)
              const key = g.missTotal - (g.wrong.length - i)
              return (
                <motion.div key={key} layout className="dd-bubble" initial={{ opacity: 0, x: 50, scale: 0.8 }} animate={{ opacity: 1, x: 0, scale: 1 }} exit={{ opacity: 0, x: -30 }} transition={{ type: 'spring', stiffness: 500, damping: 28 }}>
                  {p && <AvatarFace avatar={p.avatar} size={52} />}
                  <Bubble tail="left"><span>{m.text}</span></Bubble>
                </motion.div>
              )
            })}
          </AnimatePresence>
        </div>
      </aside>
    </div>
  )
}

function Reveal({ g, who, ink }: { g: DoodleTv; who: Who; ink: InkStore }) {
  useEffect(() => { if (g.expected > 0 && g.guessed === g.expected) fireConfetti() }, [g.turn]) // eslint-disable-line react-hooks/exhaustive-deps
  return (
    <div className="dd-draw dd-reveal">
      <aside className="dd-side">
        <Slam from={2} tilt={-6}><Panel fill={C.sun} tilt={-3} className="dd-word"><small>IT WAS</small><b>{g.word?.toUpperCase()}</b></Panel></Slam>
      </aside>
      <div className="dd-easel"><Easel><InkCanvas store={ink} turn={g.turn} mode="replay" /></Easel></div>
      <aside className="dd-side dd-right">
        {g.solvers.length === 0 && <p className="dd-line">Nobody got it!</p>}
        <ol className="dd-results">
          {g.solvers.map((s, i) => {
            const p = who.get(s.id)
            return (
              <Deal key={s.id} i={i}>
                <li>{p && <AvatarFace avatar={p.avatar} size={56} />}<span className="who">{s.name}</span>{s.points != null && <span className="pts">+{s.points.toLocaleString()}</span>}</li>
              </Deal>
            )
          })}
        </ol>
        <ul className="dd-drinks">{g.drinks.map((d) => <li key={d.id}>{d.text.startsWith(d.name) ? d.text : <><b>{d.name}</b>: {d.text}</>}</li>)}</ul>
      </aside>
    </div>
  )
}

function Finale({ g, scores, stage, ink }: { g: DoodleTv; scores: ScoreRow[]; stage: StageInfo; ink: InkStore }) {
  const wait = Math.max(0, (stage.remainingMs ?? PODIUM_MS) - GALLERY_LEFT_MS)
  const [gallery, setGallery] = useState(wait === 0)
  useEffect(() => {
    if (wait === 0) { setGallery(true); return }
    const id = setTimeout(() => setGallery(true), wait)
    return () => clearTimeout(id)
  }, [wait, stage.phaseSeq])
  if (!gallery || g.gallery.length === 0) return <Podium scores={scores} />
  return (
    <div className="dd-gallery-wrap">
      <h2 className="dd-gallery-title">THE GALLERY</h2>
      <div className="dd-gallery">
        {g.gallery.map((s, i) => (
          <Deal key={s.turn} i={i}>
            <motion.figure className="dd-shot" animate={{ rotate: [(i % 2 ? 1 : -1) * 1.2, (i % 2 ? -1 : 1) * 1.2] }} transition={{ repeat: Infinity, repeatType: 'mirror', duration: 3 + (i % 3) * 0.6, ease: 'easeInOut' }}>
              <div className="dd-still"><InkCanvas store={ink} turn={s.turn} mode="still" /></div>
              <figcaption><b>{s.word}</b><small>by {s.drawerName}{s.firstName ? ` · first: ${s.firstName}` : ' · nobody got it'}</small></figcaption>
            </motion.figure>
          </Deal>
        ))}
      </div>
    </div>
  )
}
