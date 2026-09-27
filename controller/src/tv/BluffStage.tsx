import { useEffect, type ReactNode } from 'react'
import type { PlayerSummary, ScoreRow, StageInfo } from '../protocol'
import { sfx } from './audio'
import { Fill, GameHeader, Podium, ScoreBoard, Tutorial, useStep } from './Shared'
import { AvatarFace, Bubble, Burst, C, Deal, fireConfetti, Panel, Pop, Slam, Stamp } from './toon'
import type { BluffReveal, BluffTv } from './types'

const WRITE_MS = 60_000, PICK_MS = 30_000, REVEAL_STEP_MS = 2_500, REVEAL_TAIL_MS = 2_000, SCORES_MS = 8_000, PODIUM_MS = 15_000

export function BluffStage({ stage, players, scores, clock }: { stage: StageInfo; players: PlayerSummary[]; scores: ScoreRow[]; clock: { deadline: number | null; frozen: number | null } }) {
  if (stage.tutorial) {
    return (
      <div className="stage-pad">
        <GameHeader title="Bluff Battle" stage={stage} total={30_000} clock={clock} chips={[['HOW TO PLAY', C.paper]]} />
        <Tutorial cards={stage.tutorial.cards} acked={stage.tutorial.acked} players={players} />
      </div>
    )
  }
  const g = stage.game as unknown as BluffTv
  const chips: [string, string][] = [[g.phase === 'podium' ? 'FINAL RESULTS' : `ROUND ${g.round} OF ${g.totalRounds}`, C.paper]]
  if (g.finalRound && ['write', 'pick', 'reveal'].includes(g.phase)) chips.push(['FINAL ROUND: DOUBLE POINTS', C.sun])
  const total = { write: WRITE_MS, pick: PICK_MS, scores: SCORES_MS, podium: PODIUM_MS, reveal: REVEAL_STEP_MS * g.reveal.length + REVEAL_TAIL_MS }[g.phase]
  const status = g.phase === 'write' ? `${g.submitted}/${g.expected} BLUFFS IN` : g.phase === 'pick' ? `${g.submitted}/${g.expected} PICKED` : null
  return (
    <div className="stage-pad">
      <GameHeader title="Bluff Battle" stage={stage} total={total} clock={clock} chips={chips} status={status} />
      <Fill>
        {g.phase === 'write' && <Write g={g} />}
        {g.phase === 'pick' && <Pick g={g} seq={stage.phaseSeq} />}
        {g.phase === 'reveal' && <Reveal g={g} seq={stage.phaseSeq} paused={stage.paused} players={players} />}
        {g.phase === 'scores' && <ScoreBoard scores={scores} deltas={Object.fromEntries(g.deltas.map((d) => [d.id, d.points]))} />}
        {g.phase === 'podium' && <Podium scores={scores} />}
      </Fill>
    </div>
  )
}

/** The prompt runs as the day's headline. */
function Prompt({ text, big }: { text: string; big: boolean }) {
  return <Bubble tail="none" className={`prompt-bubble ${big ? '' : 'small'}`}>{text}</Bubble>
}

/** A sticky note from the editor, standing in for the host. */
function EditorNote({ children }: { children: ReactNode }) {
  return <div className="editor-note"><small>FROM THE EDITOR</small><p>{children}</p></div>
}

function Write({ g }: { g: BluffTv }) {
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 56 }}>
      <Slam from={1.4} tilt={-3} style={{ width: '100%' }}><Prompt text={g.prompt} big /></Slam>
      <EditorNote>Write a fake answer that sounds true.</EditorNote>
      <div className="chips">
        {Array.from({ length: g.expected }, (_, i) => i < g.submitted
          ? <Pop key={`y${i}`}><div className="chip-dot" style={{ background: C.sun }} /></Pop>
          : <div key={`n${i}`} className="chip-dot" style={{ background: C.paper, opacity: 0.6 }} />)}
      </div>
    </div>
  )
}

function Pick({ g, seq }: { g: BluffTv; seq: number }) {
  const n = g.options.length
  const cols = n <= 4 ? 2 : n <= 9 ? 3 : 4
  const dense = n > 9
  useEffect(() => {
    const ids = g.options.map((_, i) => setTimeout(() => sfx.deal(i), 80 + i * 70))
    return () => ids.forEach(clearTimeout)
  }, [seq]) // eslint-disable-line react-hooks/exhaustive-deps
  return (
    <>
      <div style={{ marginTop: 28 }}><Prompt text={g.prompt} big={false} /></div>
      <div className="play-cards" style={{ gridTemplateColumns: `repeat(${cols}, 1fr)`, gap: dense ? 18 : 28 }}>
        {g.options.map((o, i) => (
          <Deal key={o} i={i}>
            <Panel className={`play-card ${dense ? 'dense' : ''}`} fill={C.paper} tilt={i % 2 ? 0.8 : -1}>
              <span className="pip">{String.fromCharCode(65 + i)}</span>
              <span>{o}</span>
            </Panel>
          </Deal>
        ))}
      </div>
    </>
  )
}

function Reveal({ g, seq, paused, players }: { g: BluffTv; seq: number; paused: boolean; players: PlayerSummary[] }) {
  const shown = useStep(g.reveal.length, seq, 600, REVEAL_STEP_MS, paused)
  const current = g.reveal[shown - 1]
  const truth = current?.kind === 'truth'
  useEffect(() => {
    if (!current) return
    sfx.stamp()
    const ids: ReturnType<typeof setTimeout>[] = []
    if (truth) ids.push(setTimeout(() => { sfx.fanfare(); sfx.applause(); fireConfetti() }, 250))
    else if (current.fooled.length > 0) {
      ids.push(setTimeout(() => { sfx.womp(); sfx.ooh(current.fooled.length) }, 300))
      if (current.fooled.length >= 3) ids.push(setTimeout(() => sfx.laugh(), 1300))
    }
    current.fooled.forEach((_, i) => ids.push(setTimeout(() => sfx.pop(), 350 + i * 110)))
    return () => ids.forEach(clearTimeout)
  }, [shown]) // eslint-disable-line react-hooks/exhaustive-deps
  return (
    <>
      <div style={{ marginTop: 28 }}><Prompt text={g.prompt} big={false} /></div>
      <div className="row" style={{ alignItems: 'flex-start', marginTop: 40, gap: 48 }}>
        <div style={{ flex: 1 }}>
          {current ? <Slam key={shown} from={1.3} tilt={-4}><RevealCard item={current} players={players} /></Slam>
            : <EditorNote>Let's see who got fooled.</EditorNote>}
        </div>
        {truth && <Burst text="THE TRUTH!" width={440} height={260} size={58} fill={C.sun} tilt={8} delay={0.2} />}
      </div>
      <div className="row" style={{ flexWrap: 'wrap', gap: 16, marginTop: 36 }}>
        {g.reveal.slice(0, Math.max(0, shown - 1)).map((r) => (
          <span key={r.text} className="past-chip">{r.kind === 'truth' ? r.text : <s>{r.text}</s>} <small>{r.fooled.length === 1 ? '1 fooled' : `${r.fooled.length} fooled`}</small></span>
        ))}
      </div>
    </>
  )
}

function RevealCard({ item, players }: { item: BluffReveal; players: PlayerSummary[] }) {
  const truth = item.kind === 'truth'
  const [stamp, color] = truth ? ['THE TRUTH', C.felt] : item.kind === 'decoy' ? ['HOUSE FAKE', C.blueberry] : ['FAKE!', C.tomato]
  const byName = new Map(players.map((p) => [p.name, p]))
  return (
    <Panel className="reveal-card" fill={truth ? C.sun : C.paper} tilt={-1}>
      <h2>{item.text}</h2>
      {item.kind === 'fake' && item.authors.length > 0 && <p style={{ fontSize: 38, fontWeight: 800, color: 'var(--ink-soft)' }}>written by {item.authors.join(' & ')}</p>}
      <div className="fooled-row">
        <span className="display" style={{ fontSize: 30 }}>
          {item.fooled.length === 0 ? (truth ? 'NOBODY FOUND IT!' : 'FOOLED NOBODY') : truth ? 'FOUND IT' : 'FOOLED'}
        </span>
        {item.fooled.map((name, i) => {
          const p = byName.get(name)
          return (
            <Pop key={name} delay={0.35 + i * 0.11}>
              <span className="name-pill">{p && <AvatarFace avatar={p.avatar} size={56} />}{name}</span>
            </Pop>
          )
        })}
      </div>
      <div style={{ position: 'absolute', right: 40, top: 30 }}><Stamp text={stamp} color={color} /></div>
    </Panel>
  )
}

export { Deal }
