import { useEffect } from 'react'
import type { PlayerSummary, ScoreRow, StageInfo } from '../protocol'
import { sfx } from './audio'
import { Fill, GameHeader, Podium, ScoreBoard, Tutorial, useStep } from './Shared'
import { AvatarDot, C, Deal, fireConfetti, Neon, Pop, Prop, Slam, Stamp, Wobble } from './Studio'
import type { BluffReveal, BluffTv } from './types'

const WRITE_MS = 60_000, PICK_MS = 30_000, REVEAL_STEP_MS = 2_500, REVEAL_TAIL_MS = 2_000, SCORES_MS = 8_000, PODIUM_MS = 15_000

export function BluffStage({ stage, players, scores, clock }: { stage: StageInfo; players: PlayerSummary[]; scores: ScoreRow[]; clock: { deadline: number | null; frozen: number | null } }) {
  if (stage.tutorial) {
    return (
      <div className="stage-pad">
        <GameHeader title="BLUFF BATTLE" titleColor={C.brass} stage={stage} total={30_000} clock={clock} chips={[['HOW TO PLAY', C.cream], ['TAP “GOT IT” ON YOUR PHONE', C.pink]]} />
        <Tutorial cards={stage.tutorial.cards} acked={stage.tutorial.acked} players={players} accent={C.brass} card={C.felt} />
      </div>
    )
  }
  const g = stage.game as unknown as BluffTv
  const chips: [string, string][] = [[g.phase === 'podium' ? 'FINAL RESULTS' : `ROUND ${g.round} OF ${g.totalRounds}`, C.cream]]
  if (g.finalRound && ['write', 'pick', 'reveal'].includes(g.phase)) chips.push(['FINAL ROUND · DOUBLE POINTS', C.pink])
  const total = { write: WRITE_MS, pick: PICK_MS, scores: SCORES_MS, podium: PODIUM_MS, reveal: REVEAL_STEP_MS * g.reveal.length + REVEAL_TAIL_MS }[g.phase]
  const status = g.phase === 'write' ? `${g.submitted}/${g.expected} BLUFFS IN` : g.phase === 'pick' ? `${g.submitted}/${g.expected} PICKED` : null
  return (
    <div className="stage-pad">
      <GameHeader title="BLUFF BATTLE" titleColor={C.brass} stage={stage} total={total} clock={clock} chips={chips} status={status} />
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

function Prompt({ text, big }: { text: string; big: boolean }) {
  return (
    <div className={`felt-card ${big ? 'big' : 'small'}`}>
      {big && <><span className="suit-pip" style={{ left: 28, top: 14 }}>♠</span><span className="suit-pip" style={{ right: 28, bottom: 14 }}>♦</span></>}
      {text}
    </div>
  )
}

function Write({ g }: { g: BluffTv }) {
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 56 }}>
      <Slam from={1.4} tilt={-3} style={{ width: '100%' }}><Prompt text={g.prompt} big /></Slam>
      <Wobble><span style={{ fontSize: 52, fontWeight: 900, color: C.pink, display: 'inline-block', transform: 'rotate(-1.5deg)' }}>Write a fake answer that sounds TRUE</span></Wobble>
      <div className="chips">
        {Array.from({ length: g.expected }, (_, i) => i < g.submitted
          ? <Pop key={`y${i}`}><div className="chip" style={{ background: C.brass, borderColor: C.gold }} /></Pop>
          : <div key={`n${i}`} className="chip" style={{ background: C.felt, borderColor: C.feltDeep }} />)}
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
            <Prop className={`play-card ${dense ? 'dense' : ''}`} shadow={C.brass} tilt={i % 2 ? 0.8 : -1} style={{ boxShadow: `${dense ? 8 : 12}px ${dense ? 8 : 12}px 0 ${C.brass}` }}>
              <span className="pip">{String.fromCharCode(65 + i)}</span>
              <span>{o}</span>
            </Prop>
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
            : <Wobble><span style={{ fontSize: 52, fontWeight: 900, color: C.gold }}>Let's see who got fooled…</span></Wobble>}
        </div>
        <Neon text="APPLAUSE" lit={truth} color={C.pink} />
      </div>
      <div className="row" style={{ flexWrap: 'wrap', gap: 20, marginTop: 36 }}>
        {g.reveal.slice(0, Math.max(0, shown - 1)).map((r) => <span key={r.text} className="past-chip">{r.text} · {r.fooled.length} fooled</span>)}
      </div>
    </>
  )
}

function RevealCard({ item, players }: { item: BluffReveal; players: PlayerSummary[] }) {
  const truth = item.kind === 'truth'
  const [stamp, color] = truth ? ['THE TRUTH', C.felt] : item.kind === 'decoy' ? ['HOUSE FAKE', C.sky] : ['FAKE!', C.red]
  const byName = new Map(players.map((p) => [p.name, p]))
  return (
    <Prop className="reveal-card" fill={truth ? C.gold : C.cream} shadow={truth ? C.mint : C.pink} style={{ boxShadow: `18px 18px 0 ${truth ? C.mint : C.pink}` }}>
      <h2>{item.text}</h2>
      {item.kind === 'fake' && item.authors.length > 0 && <p style={{ fontSize: 40, fontWeight: 700, color: '#0b0716b0' }}>written by {item.authors.join(' & ')}</p>}
      <div className="fooled-row">
        <span className="display" style={{ fontSize: 30 }}>
          {item.fooled.length === 0 ? (truth ? 'NOBODY FOUND IT!' : 'FOOLED NOBODY') : truth ? 'FOUND IT' : 'FOOLED'}
        </span>
        {item.fooled.map((name, i) => {
          const p = byName.get(name)
          return (
            <Pop key={name} delay={0.35 + i * 0.11}>
              <span className="name-pill">{p && <AvatarDot emoji={p.avatar.emoji} color={p.avatar.color} size={56} />}{name}</span>
            </Pop>
          )
        })}
      </div>
      <div style={{ position: 'absolute', right: 40, top: 30 }}><Stamp text={stamp} color={color} /></div>
    </Prop>
  )
}

export { Deal }
