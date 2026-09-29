import { AnimatePresence, motion, useAnimationControls } from 'motion/react'
import { useEffect, useState } from 'react'
import type { PlayerSummary, ScoreRow, StageInfo } from '../protocol'
import { sfx } from './audio'
import { hostLine } from './hottypeHost'
import { Tutorial, useStep } from './Shared'
import { useTimerScale } from './timerScale'
import { AvatarFace } from './toon'
import type { HotTypeTv } from './types'
import './hottype.css'

type Clock = { deadline: number | null; frozen: number | null }

const PHASE_MS = { ready: 5_000, hunt: 90_000, press: 2_500, reveal: 22_000, scores: 8_000, podium: 15_000 }

/** Milliseconds left on the phase clock, refreshed every frame. Only small components call it, so the board never re-renders. */
function useLeft(clock: Clock, total: number): number {
  const [left, setLeft] = useState(total)
  useEffect(() => {
    let raf = 0
    const frame = () => {
      setLeft(clock.frozen ?? (clock.deadline ? Math.max(0, clock.deadline - Date.now()) : total))
      if (clock.frozen == null) raf = requestAnimationFrame(frame)
    }
    frame()
    return () => cancelAnimationFrame(raf)
  }, [clock.deadline, clock.frozen, total])
  return left
}

const fmt = (ms: number) => {
  const s = Math.ceil(ms / 1000)
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}

export function HotTypeStage({ stage, players, scores, clock }: { stage: StageInfo; players: PlayerSummary[]; scores: ScoreRow[]; clock: Clock }) {
  const scale = useTimerScale()
  if (stage.tutorial) {
    return (
      <div className="ht-stage">
        <div className="ht-pad"><Tutorial cards={stage.tutorial.cards} acked={stage.tutorial.acked} players={players} /></div>
      </div>
    )
  }
  const g = stage.game as unknown as HotTypeTv
  const total = g.phase === 'hunt' ? PHASE_MS.hunt * scale : PHASE_MS[g.phase]
  const who = new Map(players.map((p) => [p.id, p]))
  return (
    <div className="ht-stage" data-phase={g.phase}>
      {(g.phase === 'ready' || g.phase === 'hunt' || g.phase === 'press') && <PressBar g={g} clock={clock} total={total} />}
      {g.phase === 'ready' && <Ready clock={clock} total={total} />}
      {(g.phase === 'hunt' || g.phase === 'press') && <Hunt g={g} who={who} />}
      {g.phase === 'reveal' && <Reveal g={g} who={who} scores={scores} paused={stage.paused} />}
      {g.phase === 'scores' && <Recap g={g} who={who} scores={scores} />}
      {g.phase === 'podium' && <Final scores={scores} who={who} />}
      <HostBar line={hostLine(g, (id) => who.get(id)?.name ?? '?')} />
    </div>
  )
}

/** The press: a platen that fills the bar as the hunt runs out. It turns red, and pulses slowly, in the last 10 seconds. */
function PressBar({ g, clock, total }: { g: HotTypeTv; clock: Clock; total: number }) {
  const left = useLeft(clock, total)
  const frac = Math.min(1, Math.max(0, 1 - left / total))
  const hot = g.phase === 'hunt' && left > 0 && left <= 10_000
  const label = g.phase === 'podium' ? 'Final edition' : `Round ${g.round} of ${g.totalRounds}${g.finalRound ? ' · double points' : ''}`
  useEffect(() => { if (g.phase === 'press') sfx.htPlaten() }, [g.phase, g.round])
  return (
    <div className={`ht-press ${hot ? 'hot' : ''}`} role="timer" aria-label={`${Math.ceil(left / 1000)} seconds left`}>
      <div className="ht-head"><span className="ht-label">{label}</span><span className="ht-time">{fmt(left)}</span></div>
      <div className="ht-bar"><div className="ht-bar-fill" style={{ width: `${frac * 100}%` }} /></div>
    </div>
  )
}

function Ready({ clock, total }: { clock: Clock; total: number }) {
  const left = useLeft(clock, total)
  const n = Math.ceil(left / 1000)
  const text = n > 3 ? 'LOCK UP THE FORME' : String(Math.max(1, n))
  useEffect(() => { if (n >= 1 && n <= 3) sfx.countTick(3 - n) }, [n])
  return (
    <div className="ht-ready">
      <motion.div key={text} className={`ht-ready-word ${text.length > 1 ? 'phrase' : ''}`}
        initial={{ scale: 1.5, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} transition={{ type: 'spring', stiffness: 520, damping: 18 }}>
        {text}
      </motion.div>
      <div className="ht-ready-sub">Same board for everyone. Swipe on your phone.</div>
    </div>
  )
}

function Hunt({ g, who }: { g: HotTypeTv; who: Map<string, PlayerSummary> }) {
  useEffect(() => { if (g.phase === 'hunt') sfx.htFlip() }, []) // eslint-disable-line react-hooks/exhaustive-deps
  return (
    <>
      <Board g={g} />
      <Rail g={g} who={who} />
      <BigFind g={g} />
      <div className="ht-stats">{g.wordsFound} words found · longest so far {g.longest || '–'}</div>
    </>
  )
}

/** The shared board. The tiles flip in like a split-flap board, over about 600 ms. */
function Board({ g }: { g: HotTypeTv }) {
  const n = g.size
  const tile = n === 5 ? 132 : 170
  const gap = n === 5 ? 12 : 16
  const pad = (768 - (tile * n + gap * (n - 1))) / 2
  return (
    <div className="ht-board" role="img" aria-label="The board">
      {g.tiles.map((t, i) => (
        <motion.div
          key={`${g.round}-${i}`} className="ht-tile"
          style={{ left: pad + (i % n) * (tile + gap), top: pad + Math.floor(i / n) * (tile + gap), width: tile, height: tile, fontSize: n === 5 ? 76 : 96 }}
          initial={{ rotateX: 90, opacity: 0 }} animate={{ rotateX: 0, opacity: 1 }}
          transition={{ delay: (i / g.tiles.length) * 0.4, duration: 0.25, ease: [0.2, 0.8, 0.2, 1] }}
        >
          {t}
        </motion.div>
      ))}
    </div>
  )
}

/** One row per player: a pill per found word sized by its length. The words themselves stay secret. */
function Rail({ g, who }: { g: HotTypeTv; who: Map<string, PlayerSummary> }) {
  const compact = g.rail.length > 5
  return (
    <div className={`ht-rail ${compact ? 'compact' : ''}`}>
      {g.rail.map((r) => {
        const p = who.get(r.id)
        return (
          <div key={r.id} className="ht-row">
            {p ? <AvatarFace avatar={p.avatar} size={compact ? 64 : 88} /> : <span className="ht-face">{r.name[0]}</span>}
            <span className="ht-name">{r.name}</span>
            {compact
              ? <span className="ht-count">{r.count} WORDS</span>
              : <span className="ht-pills">{r.lengths.map((l, i) => (
                  <motion.i key={i} initial={{ scaleX: 0 }} animate={{ scaleX: 1 }} style={{ width: l * 13, transformOrigin: 'left' }} />
                ))}</span>}
            <span className="ht-score">{r.score.toLocaleString()}</span>
          </div>
        )
      })}
    </div>
  )
}

/** A word of 6 or more letters fires a comic burst with the finder's name and the length. Never the word. */
function BigFind({ g }: { g: HotTypeTv }) {
  const key = g.bigFind ? `${g.round}:${g.bigFind.seq}` : null
  const [active, setActive] = useState<string | null>(null)
  useEffect(() => {
    if (!key) return
    setActive(key)
    sfx.ooh(3)
    const t = setTimeout(() => setActive((cur) => (cur === key ? null : cur)), 1500)
    return () => clearTimeout(t)
  }, [key])
  return (
    <AnimatePresence>
      {active && g.bigFind && (
        <motion.div key={active} className="ht-burst"
          initial={{ scale: 0.3, rotate: -14, opacity: 0 }} animate={{ scale: 1, rotate: -5, opacity: 1 }} exit={{ scale: 0.6, opacity: 0 }}
          transition={{ type: 'spring', stiffness: 520, damping: 14 }}>
          <div className="big">{g.bigFind.letters} LETTERS</div>
          <div className="sub">{g.bigFind.name}</div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}

function HostBar({ line }: { line: string }) {
  if (!line) return null
  return <div className="ht-host"><b>HOST</b><span>{line}</span></div>
}

const MAST = (g: HotTypeTv, sub: string) => (
  <div className="ht-mast">
    <span className="ht-mast-title">THE DAILY FORME</span>
    <span className="ht-mast-sub">Round {g.round} of {g.totalRounds}<br />{sub}</span>
  </div>
)

/** The words are stamped onto the front page one at a time, longest last, then the one that got away. */
function Reveal({ g, who, scores, paused }: { g: HotTypeTv; who: Map<string, PlayerSummary>; scores: ScoreRow[]; paused: boolean }) {
  const total = g.page.length
  const shown = useStep(total + 1, `${g.round}`, 1400, 1700, paused)
  const shake = useAnimationControls()
  useEffect(() => { sfx.htPaper() }, [g.round])
  useEffect(() => {
    if (shown === 0 || shown > total) return
    sfx.htStamp()
    const w = g.page[shown - 1]
    // A small screen shake, only for the longest word and any word of 7 or more letters.
    if (w.longest || w.word.length >= 7) void shake.start({ x: [0, -8, 7, -4, 3, 0], y: [0, 4, -3, 2, 0, 0], transition: { duration: 0.24 } })
  }, [shown]) // eslint-disable-line react-hooks/exhaustive-deps
  const top = [...scores].sort((a, b) => b.score - a.score).slice(0, 3)
  return (
    <motion.div className="ht-front" animate={shake}>
      {MAST(g, 'Words revealed')}
      <div className="ht-cols">
        <div className="ht-words">
          {g.page.slice(0, shown).map((w) => <StampedWord key={w.word} w={w} who={who} />)}
          {total === 0 && shown > 0 && <div className="ht-none">NO WORDS THIS ROUND</div>}
        </div>
        <aside className="ht-side">
          {shown > total && g.missed && (
            <motion.div className="ht-missed" initial={{ opacity: 0, y: 30 }} animate={{ opacity: 1, y: 0 }} transition={{ type: 'spring', stiffness: 300, damping: 24 }}>
              <div className="ht-lab">The one that got away</div>
              <div className="ht-missed-word">{g.missed.word.toUpperCase()}</div>
              <div className="ht-missed-sub">{g.missed.word.length} letters. Right there the whole time.</div>
            </motion.div>
          )}
          <div className="ht-lab">Scores</div>
          {top.map((s, i) => <div key={s.id} className="ht-total"><span>{i + 1} {s.name}</span><span>{s.score.toLocaleString()}</span></div>)}
        </aside>
      </div>
    </motion.div>
  )
}

function StampedWord({ w, who }: { w: HotTypeTv['page'][number]; who: Map<string, PlayerSummary> }) {
  const unique = w.bonus > 0
  const finders = w.finders.map((id) => who.get(id))
  return (
    <div className={`ht-word ${unique ? 'unique' : 'shared'} ${w.longest ? 'longest' : ''}`}>
      <span className="ht-word-text">{w.word.toUpperCase()}</span>
      <span className="ht-word-pts">+{(w.points + w.bonus).toLocaleString()}</span>
      <span className="ht-word-who">
        {unique
          ? <span className="ht-finder">{finders[0]?.name ?? '?'}</span>
          : finders.map((p, i) => (p ? <span key={i} className="ht-stack"><AvatarFace avatar={p.avatar} size={52} /></span> : null))}
        {unique && <span className="ht-stamp">ONLY YOU</span>}
        {w.longest && <span className="ht-stamp long">LONGEST</span>}
      </span>
    </div>
  )
}

/** The round's points, split into parts, with the running totals and any drink call. */
function Recap({ g, who, scores }: { g: HotTypeTv; who: Map<string, PlayerSummary>; scores: ScoreRow[] }) {
  const top = [...scores].sort((a, b) => b.score - a.score).slice(0, 8)
  return (
    <div className="ht-front">
      {MAST(g, 'The round in figures')}
      <div className="ht-cols">
        <div className="ht-words">
          {g.deltas.map((d) => {
            const p = who.get(d.id)
            return (
              <div key={d.id} className="ht-drow">
                {p ? <AvatarFace avatar={p.avatar} size={64} /> : <span className="ht-face">{d.name[0]}</span>}
                <span className="nm">{d.name}</span>
                <span className="parts">
                  <span>{d.base.toLocaleString()}</span>
                  {d.unique > 0 && <span className="u">+{d.unique.toLocaleString()} ONLY YOU</span>}
                  {d.longest > 0 && <span className="l">+{d.longest.toLocaleString()} LONGEST</span>}
                </span>
                <span className="sum">{d.total.toLocaleString()}</span>
              </div>
            )
          })}
          {g.drinks.map((d) => <div key={d.id} className="ht-drink">{d.name}: {d.text}</div>)}
        </div>
        <aside className="ht-side">
          <div className="ht-lab">Running total</div>
          {top.map((s, i) => <div key={s.id} className="ht-total"><span>{i + 1} {s.name}</span><span>{s.score.toLocaleString()}</span></div>)}
        </aside>
      </div>
    </div>
  )
}

/** The final edition: the winner's face is ejected onto the front page. */
function Final({ scores, who }: { scores: ScoreRow[]; who: Map<string, PlayerSummary> }) {
  const ranked = [...scores].sort((a, b) => b.score - a.score)
  const [winner, ...rest] = ranked
  const face = winner ? who.get(winner.id) : undefined
  return (
    <div className="ht-front">
      <div className="ht-mast">
        <span className="ht-mast-title">THE DAILY FORME</span>
        <span className="ht-mast-sub">Final edition<br />Every round, in print</span>
      </div>
      {winner && (
        <div className="ht-final">
          <motion.div initial={{ y: 500, rotate: -12, opacity: 0 }} animate={{ y: 0, rotate: 0, opacity: 1 }} transition={{ type: 'spring', stiffness: 160, damping: 14 }}>
            {face ? <AvatarFace avatar={face.avatar} size={240} /> : <span className="ht-face">{winner.name[0]}</span>}
          </motion.div>
          <div className="ht-final-name">{winner.name}</div>
          <div className="ht-final-score">TAKES THE FRONT PAGE WITH {winner.score.toLocaleString()}</div>
          <div className="ht-final-rest">{rest.slice(0, 4).map((s, i) => <span key={s.id}>{i + 2} {s.name} {s.score.toLocaleString()}</span>)}</div>
        </div>
      )}
    </div>
  )
}
