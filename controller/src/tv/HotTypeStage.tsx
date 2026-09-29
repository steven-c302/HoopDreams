import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useState } from 'react'
import type { PlayerSummary, ScoreRow, StageInfo } from '../protocol'
import { sfx } from './audio'
import { hostLine } from './hottypeHost'
import { Tutorial } from './Shared'
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

export function HotTypeStage({ stage, players, clock }: { stage: StageInfo; players: PlayerSummary[]; scores: ScoreRow[]; clock: Clock }) {
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
      <PressBar g={g} clock={clock} total={total} />
      {g.phase === 'ready' && <Ready clock={clock} total={total} />}
      {(g.phase === 'hunt' || g.phase === 'press') && <Hunt g={g} who={who} />}
      {/* Reveal, scores and the podium render from Task 12 onward. */}
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
