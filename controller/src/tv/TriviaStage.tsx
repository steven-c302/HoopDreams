import './trivia.css'
import { motion } from 'motion/react'
import { createContext, useContext, useEffect, useMemo, useRef, type CSSProperties, type ReactNode } from 'react'
import type { PlayerSummary, ScoreRow, StageInfo } from '../protocol'
import { GameScene } from '../theme/GameScene'
import { gameThemeOf, useGameTheme } from '../theme/gameTheme'
import { sfx } from './audio'
import { Tutorial, useLater } from './Shared'
import {
  ANSWER_COLOR, AvatarFace, Brainy, Bubble, Burst, C, Chip, CountUp, Crown, Deal, FaceRow, HostSays, Keycap, Panel, Pop, Scene, Shape, Slam, Timer,
  coinShower, fireConfetti, inkOn, type Mood,
} from './toon'
import { ROUND_RULES, ROUND_TITLES, waterNote, type TriviaTeam, type TriviaTv } from './types'

type Clock = { deadline: number | null; frozen: number | null }
interface Props { stage: StageInfo; players: PlayerSummary[]; scores: ScoreRow[]; clock: Clock }
type ById = Map<string, PlayerSummary>

const SCENE: Record<string, string> = { teamup: C.sun, quick: C.sun, ballpark: C.sky, sides: C.paper, heist: C.lime, write: C.tangerine, gauntlet: C.tomato }
const NO_DIM = new Set<string>()

/** BRAIN DRAIN on the TV: one comic panel per beat, Brainy hosting, teams along the bottom. */
export function TriviaStage({ stage, players, clock }: Props) {
  const g = stage.game as TriviaTv | undefined
  const byId = useMemo(() => new Map(players.map((p) => [p.id, p])), [players])
  const pub = gameThemeOf(stage.gameId) === 'writeitdown'
  if (stage.tutorial || !g) {
    return (
      <ShowName.Provider value={stage.title}>
        <Room pub={pub} color={C.sun}>
          <div className="stage-pad">
            <ShowTitle />
            <Tutorial cards={stage.tutorial?.cards ?? []} acked={stage.tutorial?.acked ?? []} players={players} />
          </div>
        </Room>
      </ShowName.Provider>
    )
  }
  const color = g.phase === 'standings' ? C.paper : g.phase === 'podium' ? C.sun : g.phase === 'awards' ? C.grape : SCENE[g.format] ?? C.sun
  const split: [string, string] | undefined = g.format === 'sides' && (g.phase === 'question' || g.phase === 'reveal') ? [C.bubblegum, C.blueberry] : undefined
  return (
    <ShowName.Provider value={stage.title}>
      <Room pub={pub} color={color} split={split}>
        <div className="stage-pad trivia">
          <Beat g={g} byId={byId} clock={clock} />
        </div>
      </Room>
    </ShowName.Provider>
  )
}

/** Brain Drain's comic panel, recoloured every beat. Write It Down played on its own is a pub quiz instead. */
function Room({ pub, color, split, children }: { pub: boolean; color: string; split?: [string, string]; children: ReactNode }) {
  return pub ? <GameScene game="writeitdown">{children}</GameScene> : <Scene color={color} split={split}>{children}</Scene>
}

function Beat({ g, byId, clock }: { g: TriviaTv; byId: ById; clock: Clock }) {
  switch (g.phase) {
    case 'teamup': return <TeamUp g={g} byId={byId} clock={clock} />
    case 'intro': return <Intro g={g} byId={byId} />
    case 'standings': return <Standings g={g} byId={byId} />
    case 'podium': return <TeamPodium g={g} byId={byId} />
    case 'awards': return <Awards g={g} byId={byId} />
    case 'victim': return <Victim g={g} byId={byId} clock={clock} />
    case 'steal': return <Steal g={g} byId={byId} />
    default:
      if (g.format === 'ballpark') return <Ballpark g={g} byId={byId} clock={clock} />
      if (g.format === 'sides') return <Sides g={g} byId={byId} clock={clock} />
      if (g.format === 'write') return <Write g={g} byId={byId} clock={clock} />
      if (g.format === 'gauntlet') return <Gauntlet g={g} byId={byId} clock={clock} />
      return <Quick g={g} byId={byId} clock={clock} />
  }
}

// ---------- shared pieces ----------

/** The game this stage is running ("Brain Drain" or "Write It Down"), for the logo. */
const ShowName = createContext('Brain Drain')

/** The show's logo in two stacked lines: the last word drops to the second line (BRAIN / DRAIN, WRITE IT / DOWN). */
function ShowTitle({ small = false }: { small?: boolean }) {
  const words = useContext(ShowName).toUpperCase().split(' ')
  const last = words.pop() ?? ''
  return <div className={`show-title ${small ? 'small' : ''}`}>{words.length > 0 && <b>{words.join(' ')}</b>}<b>{last}</b></div>
}

function Header({ g, clock, extra }: { g: TriviaTv; clock: Clock; extra?: ReactNode }) {
  const total = g.durationMs ?? 0
  const timed = clock.deadline != null || clock.frozen != null
  return (
    <div className="trivia-header">
      <ShowTitle small />
      <div className="row" style={{ flex: 1, flexWrap: 'wrap' }}>
        <Chip>{ROUND_TITLES[g.format]?.toUpperCase()}</Chip>
        {g.qTotal > 0 && g.q > 0 && <Chip fill={C.paper} ink={C.ink}>{g.format === 'sides' ? `CALL ${g.q} / ${g.qTotal}` : `Q ${g.q} / ${g.qTotal}`}</Chip>}
        {g.category && g.format !== 'sides' && <Chip fill={C.white} ink={C.ink}>{g.category}</Chip>}
        {g.credit && <span className="credit">from {g.credit} · CC BY-SA 4.0</span>}
        {extra}
      </div>
      {g.phase === 'question' && g.expected > 0 && <span className="answered"><b>{g.answered}</b>/{g.expected} in</span>}
      {timed && total > 0 && <Timer deadline={clock.deadline} frozen={clock.frozen} total={total} size={150} />}
    </div>
  )
}

function members(team: TriviaTeam, byId: ById) {
  return team.members.map((id) => byId.get(id)).filter((p): p is PlayerSummary => !!p)
}

/** A team's badge: their colour, name and faces. */
function TeamBadge({ team, byId, size = 'md', score, delta, tilt = 0, faces = true, style }: {
  team: TriviaTeam; byId: ById; size?: 'sm' | 'md' | 'lg'; score?: boolean; delta?: number; tilt?: number; faces?: boolean; style?: CSSProperties
}) {
  const faceSize = size === 'lg' ? 64 : size === 'md' ? 44 : 32
  return (
    <Panel className={`team-badge ${size}`} fill={team.color} tilt={tilt} style={{ color: inkOn(team.color), ...style }}>
      <span className="team-name">{team.name}</span>
      {faces && <FaceRow players={members(team, byId)} size={faceSize} max={size === 'sm' ? 4 : 6} dimIds={NO_DIM} />}
      {score && <span className="team-score"><CountUp from={team.score - (delta ?? 0)} to={team.score} delay={400} /></span>}
      {delta != null && delta !== 0 && <Pop delay={0.2} className="team-delta"><span className={delta < 0 ? 'neg' : ''}>{delta > 0 ? '+' : ''}{delta.toLocaleString()}</span></Pop>}
    </Panel>
  )
}

/** Teams along the bottom: who has answered (pips) and the running scores. */
function TeamStrip({ g, byId, deltas }: { g: TriviaTv; byId: ById; deltas?: Record<string, number> }) {
  return (
    <div className="team-strip">
      {g.teams.map((t, i) => (
        <div key={t.id} className="strip-team">
          <TeamBadge team={t} byId={byId} size="sm" score delta={deltas?.[t.id]} tilt={i % 2 ? 0.8 : -0.8} />
          {g.phase === 'bet' && g.bet?.locked.includes(t.id) && <span className="bet-in">BET IN</span>}
          {g.phase === 'question' && (
            <div className="pips">{t.members.map((id, k) => <i key={id} className={k < t.answered ? 'on' : ''} style={{ '--team': t.color } as CSSProperties} />)}</div>
          )}
        </div>
      ))}
    </div>
  )
}

function deltasOf(g: TriviaTv): Record<string, number> {
  const out: Record<string, number> = {}
  for (const a of g.reveal?.answers ?? []) {
    const d = a.points + (a.bet?.delta ?? 0)
    if (d) out[a.team] = d
  }
  return out
}

function moodFor(g: TriviaTv): Mood {
  const answers = g.reveal?.answers ?? []
  if (!answers.length) return 'happy'
  const right = answers.filter((a) => a.correct).length
  return right === 0 ? 'shocked' : right === answers.length ? 'smug' : 'happy'
}

function DrinkCall({ g, byId, style }: { g: TriviaTv; byId: ById; style?: CSSProperties }) {
  const call = g.drink
  useLater(call ? `${g.phase}-${g.round}-${g.q}-${call.teams.join()}` : null, 900, () => { if (call) sfx.drinkCall() })
  if (!call) return null
  const teams = g.teams.filter((t) => call.teams.includes(t.id))
  const why = call.reason === 'robbed' ? 'You got robbed.' : call.reason === 'last place' ? 'Last place pays.' : "Didn't escape."
  return (
    <Slam delay={0.9} from={2} tilt={8} className="drink-call" style={style}>
      <Panel fill={C.tomato} tilt={3} style={{ color: C.white }}>
        <svg className="glass" width="90" height="120" viewBox="0 0 90 120" aria-hidden="true">
          <path d="M10 10 H80 L70 112 H20 Z" fill={C.paper} stroke={C.ink} strokeWidth="6" strokeLinejoin="round" />
          <path d="M16 40 H74 L68 106 H22 Z" fill={C.sun} />
          <path d="M12 26 Q30 16 45 26 Q60 36 78 26 L80 10 H10 Z" fill={C.white} stroke={C.ink} strokeWidth="5" strokeLinejoin="round" />
          <path d="M10 10 H80 L70 112 H20 Z" fill="none" stroke={C.ink} strokeWidth="6" strokeLinejoin="round" />
        </svg>
        <div>
          <b className="display">DRINK!</b>
          <p>{teams.map((t) => t.name).join(' + ')}: {call.sips} {call.sips === 1 ? 'sip' : 'sips'}</p>
          <small>{why} Water counts.{waterNote(teams.flatMap((t) => members(t, byId)).filter((p) => p.water).map((p) => p.name))}</small>
        </div>
      </Panel>
    </Slam>
  )
}

// ---------- team up ----------

function TeamUp({ g, byId, clock }: { g: TriviaTv; byId: ById; clock: Clock }) {
  const placed = new Set(g.teams.flatMap((t) => t.members))
  const unplaced = [...byId.values()].filter((p) => p.role === 'PLAYER' && p.connected && !placed.has(p.id))
  return (
    <>
      <div className="trivia-header">
        <ShowTitle />
        <div style={{ flex: 1, paddingLeft: 20 }}><HostSays line={g.hostLine ?? 'Pick a team on your phone.'} size={150} /></div>
        {clock.deadline != null && <Timer deadline={clock.deadline} frozen={clock.frozen} total={g.durationMs ?? 45000} size={150} />}
      </div>
      <div className="teamup-grid" style={{ gridTemplateColumns: `repeat(${Math.min(g.teams.length, 3)}, 1fr)` }}>
        {g.teams.map((t, i) => (
          <Deal key={t.id} i={i}>
            <Panel className="teamup-col" fill={t.color} tilt={[-1.2, 0.8, -0.4][i % 3]} style={{ color: inkOn(t.color) }}>
              <h2>{t.name}</h2>
              <div className="teamup-faces">
                {members(t, byId).map((p) => (
                  <Pop key={p.id} className="teamup-member"><AvatarFace avatar={p.avatar} size={g.teams.length > 3 ? 70 : 96} /><span>{p.name}</span></Pop>
                ))}
                {t.members.length === 0 && <span className="empty">Tap this colour to join</span>}
              </div>
            </Panel>
          </Deal>
        ))}
      </div>
      <div className="teamup-foot">
        {unplaced.length > 0
          ? <><span>Still picking</span><FaceRow players={unplaced} size={56} max={10} dimIds={NO_DIM} /></>
          : <span>Everyone has a team. First teammate to type a name names it.</span>}
        <span className="shuffle-hint"><Keycap label="S" /> or the captain&rsquo;s phone shuffles teams evenly</span>
      </div>
    </>
  )
}

// ---------- intro ----------

function Intro({ g, byId }: { g: TriviaTv; byId: ById }) {
  const pub = useGameTheme() === 'writeitdown'
  useEffect(() => { sfx.roundStart(g.format === 'gauntlet') }, [g.format])
  return (
    <div className="intro">
      <Chip fill={C.ink}>ROUND {g.round} OF {g.totalRounds}</Chip>
      {pub ? <Slam from={1.3} tilt={-2}><h2 className="round-card">{ROUND_TITLES[g.format]}</h2></Slam>
        : <Burst text={ROUND_TITLES[g.format].toUpperCase()} width={1240} height={470} size={g.format === 'gauntlet' ? 112 : 124} fill={g.format === 'gauntlet' ? C.sun : C.white} tilt={-4} spikes={22} />}
      <HostSays line={g.format === 'gauntlet' ? (g.hostLine ?? ROUND_RULES.gauntlet) : ROUND_RULES[g.format]} mood={g.format === 'heist' ? 'smug' : 'happy'} size={160} />
      {g.format === 'gauntlet' && <Track g={g} byId={byId} compact />}
    </div>
  )
}

// ---------- quick draw + heist ----------

function Quick({ g, byId, clock }: { g: TriviaTv; byId: ById; clock: Clock }) {
  const revealed = g.phase === 'reveal'
  const correct = new Set(g.reveal?.correct ?? [])
  const pickers = (id: string) => (g.reveal?.answers ?? []).filter((a) => a.choice === id)
    .map((a) => g.teams.find((t) => t.id === a.team)).filter((t): t is TriviaTeam => !!t)
  useLater(revealed ? `r${g.round}-${g.q}` : null, 150, () => {
    if (!revealed) return
    const right = (g.reveal?.answers ?? []).filter((a) => a.correct).length
    if (right > 0) { sfx.correct(); sfx.applause(1.6) } else { sfx.wrong(); sfx.ooh(2) }
  })
  return (
    <>
      <Header g={g} clock={clock} extra={g.format === 'heist' && <Chip fill={C.ink} ink={C.lime}>FASTEST TEAM ROBS SOMEBODY</Chip>} />
      <Bubble tail="none" className={`q-bubble ${revealed ? 'small' : ''}`}>{g.prompt}</Bubble>
      <div className="answers">
        {g.options.map((o, i) => {
          const state = !revealed ? '' : correct.has(o.id) ? 'right' : 'wrong'
          return (
            <Deal key={o.id} i={i}>
              <div className={`answer ${state}`} style={{ '--ans': ANSWER_COLOR[o.id] } as CSSProperties}>
                <Shape id={o.id} size={62} />
                <span className="answer-text">{o.text}</span>
                {revealed && <div className="answer-teams">{pickers(o.id).map((t) => <span key={t.id} className="flag" style={{ background: t.color, color: inkOn(t.color) }}>{t.name}</span>)}</div>}
                {state === 'wrong' && <Scribble />}
                {state === 'right' && <Burst text="RIGHT!" width={260} height={160} size={48} fill={C.sun} tilt={10} delay={0.35} spikes={14} className="answer-burst" />}
              </div>
            </Deal>
          )
        })}
      </div>
      <div className="trivia-foot">
        {revealed ? <RevealTalk g={g} /> : <div />}
        <TeamStrip g={g} byId={byId} deltas={revealed ? deltasOf(g) : undefined} />
      </div>
    </>
  )
}

function RevealTalk({ g }: { g: TriviaTv }) {
  return (
    <div className="reveal-talk">
      <HostSays line={g.hostLine} mood={moodFor(g)} size={120} />
      {g.fact && <Pop delay={0.8}><Panel className="fact" fill={C.paper} tilt={-1}><b>FUN FACT</b> {g.fact}</Panel></Pop>}
    </div>
  )
}

/** An ink X scrawled over a wrong answer: two strokes wiped on left to right. */
function Scribble() {
  return (
    <motion.svg className="scribble" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true"
      initial={{ clipPath: 'inset(0 100% 0 0)' }} animate={{ clipPath: 'inset(0 0% 0 0)' }} transition={{ duration: 0.3, delay: 0.2, ease: 'easeOut' }}>
      <path d="M2 10 C30 30 62 62 98 90" stroke={C.ink} strokeWidth="10" strokeLinecap="round" fill="none" vectorEffect="non-scaling-stroke" />
      <path d="M98 8 C66 34 34 60 2 92" stroke={C.ink} strokeWidth="10" strokeLinecap="round" fill="none" vectorEffect="non-scaling-stroke" />
    </motion.svg>
  )
}

// ---------- ballpark ----------

function Ballpark({ g, byId, clock }: { g: TriviaTv; byId: ById; clock: Clock }) {
  const revealed = g.phase === 'reveal' && g.reveal?.number != null
  return (
    <>
      <Header g={g} clock={clock} />
      <Bubble tail="none" className={`q-bubble ${revealed ? 'small' : ''}`}>{g.prompt}</Bubble>
      {g.phase === 'bet' ? <BetLine g={g} /> : !revealed ? (
        <div className="ballpark-wait">
          <Panel className="unit-card" fill={C.paper} tilt={-2}>
            <span>Type a number on your phone</span>
            {g.unit && <b className="display">in {g.unit}</b>}
            <small>Your team's guess is the middle of everyone's.</small>
          </Panel>
          <Brainy mood="happy" size={230} />
        </div>
      ) : <NumberLine g={g} />}
      <div className="trivia-foot">
        {revealed ? <RevealTalk g={g} /> : g.phase === 'bet' ? <HostSays line={g.hostLine} mood="smug" size={120} /> : <div />}
        <TeamStrip g={g} byId={byId} deltas={revealed ? deltasOf(g) : undefined} />
      </div>
    </>
  )
}

const fmt = (n: number) => (Number.isInteger(n) ? n.toLocaleString() : n.toLocaleString(undefined, { maximumFractionDigits: 2 }))

/** Every team's guess planted on the number line with its odds; the answer stays hidden. */
function BetLine({ g }: { g: TriviaTv }) {
  const line = g.bet?.line ?? []
  const locked = g.bet?.locked ?? []
  useEffect(() => { if (locked.length > 0) sfx.stamp() }, [locked.length])
  const nums = line.map((l) => l.number)
  let lo = Math.min(...nums), hi = Math.max(...nums)
  if (hi === lo) { lo -= Math.max(1, Math.abs(lo) * 0.1); hi += Math.max(1, Math.abs(hi) * 0.1) }
  const pad = (hi - lo) * 0.1
  lo -= pad; hi += pad
  const x = (v: number) => `${Math.min(76, Math.max(14, ((v - lo) / (hi - lo)) * 100))}%`
  return (
    <div className="numberline bet">
      <div className="nl-axis" />
      {line.map((l, i) => {
        const t = g.teams.find((tt) => tt.id === l.team)
        if (!t) return null
        return (
          <motion.div key={l.team} className="nl-flag" style={{ left: x(l.number), top: 20 + (i % 3) * 80 }}
            initial={{ y: -300, opacity: 0 }} animate={{ y: 0, opacity: 1 }} transition={{ type: 'spring', stiffness: 380, damping: 18, delay: 0.12 * i }}>
            <span className="pole" style={{ height: 250 - (i % 3) * 80 }} />
            <span className="nl-team" style={{ background: t.color, color: inkOn(t.color) }}>{t.name}<em>{fmt(l.number)}</em><i className="odds">×{l.odds}</i></span>
          </motion.div>
        )
      })}
    </div>
  )
}

function NumberLine({ g }: { g: TriviaTv }) {
  const r = g.reveal!
  const truth = r.number!
  const guesses = r.answers.filter((a) => a.number != null).sort((a, b) => (a.rank ?? 9) - (b.rank ?? 9))
  const values = [truth, ...guesses.map((a) => a.number!)]
  let lo = Math.min(...values), hi = Math.max(...values)
  if (hi === lo) { lo -= Math.max(1, Math.abs(lo) * 0.1); hi += Math.max(1, Math.abs(hi) * 0.1) }
  const pad = (hi - lo) * 0.1
  lo -= pad; hi += pad
  // Clamped so labels at the extremes stay on screen.
  const x = (v: number) => `${Math.min(90, Math.max(10, ((v - lo) / (hi - lo)) * 100))}%`
  // Flags drop to make room for their bet chips; a crowd backing one guess wraps to a second row, so drop a bit more.
  const mostBacked = Math.max(0, ...guesses.map((a) => r.answers.filter((b) => b.bet?.on === a.team).length))
  const lift = mostBacked === 0 ? 0 : mostBacked > 3 ? 72 : 50
  const bull = guesses.some((a) => a.bullseye)
  const longShot = r.answers.some((a) => a.bet?.won && a.bet.odds === 3)
  useLater(`bp${g.q}`, 1300, () => { sfx.stamp(); if (bull || longShot) sfx.jackpot() })
  return (
    <div className="numberline">
      <div className="nl-axis" />
      {guesses.map((a, i) => {
        const t = g.teams.find((tt) => tt.id === a.team)
        if (!t) return null
        return (
          <motion.div key={a.team} className="nl-flag" style={{ left: x(a.number!), top: 20 + lift + (i % 3) * 80 }}
            initial={{ y: -300, opacity: 0 }} animate={{ y: 0, opacity: 1 }} transition={{ type: 'spring', stiffness: 380, damping: 18, delay: 0.12 * i }}>
            <span className="pole" style={{ height: 250 - lift - (i % 3) * 80 }} />
            <span className="nl-team" style={{ background: t.color, color: inkOn(t.color) }}>
              {a.rank === 1 && <Crown size={34} />}{t.name}<em>{fmt(a.number!)}</em>
            </span>
            <div className="bet-chips">
              {r.answers.filter((b) => b.bet?.on === a.team).map((b) => {
                const bt = g.teams.find((tt) => tt.id === b.team)
                const d = b.bet!.delta
                return (
                  <Pop key={b.team} delay={1.9}>
                    <span className={`bet-chip ${b.bet!.won ? 'won' : 'lost'}`}><i style={{ background: bt?.color }} />{b.bet!.won ? `+${d.toLocaleString()}` : d ? d.toLocaleString() : 'LOST'}</span>
                  </Pop>
                )
              })}
            </div>
          </motion.div>
        )
      })}
      <motion.div className="nl-truth" style={{ left: x(truth) }} initial={{ y: -700 }} animate={{ y: 0 }} transition={{ type: 'spring', stiffness: 260, damping: 12, delay: 1.1 }}>
        <Anvil />
        <b className="display">{r.answerText}</b>
      </motion.div>
      {bull && <Burst text="BULLSEYE!" width={420} height={230} size={54} fill={C.sun} delay={1.5} className="nl-bull" />}
    </div>
  )
}

function Anvil() {
  return (
    <svg width="150" height="100" viewBox="0 0 150 100" aria-hidden="true">
      <path d="M10 14 H140 Q124 36 104 40 V62 H120 V90 H30 V62 H46 V40 Q26 36 10 14 Z" fill={C.inkSoft} stroke={C.ink} strokeWidth="6" strokeLinejoin="round" />
      <path d="M24 20 H120" stroke={C.paper} strokeWidth="4" strokeLinecap="round" opacity=".5" />
    </svg>
  )
}

// ---------- write it down ----------

/** No options: the question, then the real answer and what each team wrote, stamped right or wrong. */
function Write({ g, byId, clock }: { g: TriviaTv; byId: ById; clock: Clock }) {
  const pub = useGameTheme() === 'writeitdown'
  const revealed = g.phase === 'reveal' && !!g.reveal
  useLater(revealed ? `w${g.round}-${g.q}` : null, 150, () => {
    if (!revealed) return
    const right = (g.reveal?.answers ?? []).filter((a) => a.correct).length
    if (right > 0) { sfx.correct(); sfx.applause(1.6) } else { sfx.wrong(); sfx.ooh(2) }
  })
  return (
    <>
      <Header g={g} clock={clock} />
      <Bubble tail="none" className={`q-bubble ${revealed ? 'small' : ''}`}>{g.prompt}</Bubble>
      {!revealed ? (
        <div className="ballpark-wait">
          <Panel className="unit-card" fill={C.paper} tilt={-2}>
            <span>Type the answer on your phone</span>
            <b className="display">No options!</b>
            <small>Your team's most-written answer counts. Close spelling is fine.</small>
          </Panel>
          {!pub && <Brainy mood="smug" size={230} />}
        </div>
      ) : (
        <div className="write-reveal">
          <Slam tilt={-3} from={1.8}>
            <Panel className="write-answer" fill={C.white} tilt={-2}><small>THE ANSWER</small><b className="display">{g.reveal!.answerText}</b></Panel>
          </Slam>
          <div className="write-teams">
            {g.teams.map((t, i) => {
              const a = g.reveal!.answers.find((x) => x.team === t.id)
              return (
                <Deal key={t.id} i={i + 2}>
                  <div className={`write-team ${a?.correct ? 'right' : 'wrong'}`}>
                    <span className="flag" style={{ background: t.color, color: inkOn(t.color) }}>{t.name}</span>
                    <span className="write-text">{a?.text ? `“${a.text}”` : 'No answer'}</span>
                    <span className="write-mark">{a?.correct ? <Check /> : <Cross />}</span>
                  </div>
                </Deal>
              )
            })}
          </div>
        </div>
      )}
      <div className="trivia-foot">
        {revealed ? <RevealTalk g={g} /> : <div />}
        <TeamStrip g={g} byId={byId} deltas={revealed ? deltasOf(g) : undefined} />
      </div>
    </>
  )
}

// ---------- pick a side ----------

function Sides({ g, byId, clock }: { g: TriviaTv; byId: ById; clock: Clock }) {
  const info = g.sides!
  const revealed = g.phase === 'reveal'
  const side = g.reveal?.correct[0]
  const last = info.history[info.history.length - 1]
  useLater(revealed ? `s${g.q}` : null, 60, () => { if (revealed) sfx.whoosh() })
  const pile = (s: 'left' | 'right') => info.history.filter((h) => h.side === s && !(revealed && h === last))
  return (
    <>
      <Header g={g} clock={clock} />
      <div className="sides">
        {(['left', 'right'] as const).map((s) => (
          <div key={s} className={`side-col ${s}`}>
            <h2>{s === 'left' ? info.left : info.right}</h2>
            <div className="side-pile">{pile(s).map((h) => <span key={h.text}>{h.text}</span>)}</div>
          </div>
        ))}
        <motion.div key={g.q} className="side-card" initial={{ scale: 2, rotate: -10, opacity: 0 }}
          animate={revealed ? { x: side === 'left' ? -430 : 430, rotate: side === 'left' ? -6 : 6, scale: 0.9, opacity: 1 } : { scale: 1, rotate: -2, opacity: 1 }}
          transition={{ type: 'spring', stiffness: 420, damping: 18 }}>
          <Panel fill={C.white} className="side-card-inner">
            <small>{g.category}</small>
            <b>{g.prompt}</b>
            {revealed && last && (
              <div className="side-teams">
                {last.teamsRight.length === 0 ? <span className="flag" style={{ background: C.ink, color: C.paper }}>Nobody!</span>
                  : last.teamsRight.map((id) => { const t = g.teams.find((tt) => tt.id === id); return t ? <span key={id} className="flag" style={{ background: t.color, color: inkOn(t.color) }}>{t.name}</span> : null })}
              </div>
            )}
          </Panel>
        </motion.div>
      </div>
      <div className="trivia-foot"><div /><TeamStrip g={g} byId={byId} /></div>
    </>
  )
}

// ---------- the heist ----------

function Victim({ g, byId, clock }: { g: TriviaTv; byId: ById; clock: Clock }) {
  const thief = g.teams.find((t) => t.id === g.heist?.thief)
  if (!thief) return null
  const targets = g.teams.filter((t) => t.id !== thief.id)
  return (
    <>
      <Header g={g} clock={clock} />
      <div className="heist">
        <Slam tilt={-6}><TeamBadge team={thief} byId={byId} size="lg" tilt={-3} /></Slam>
        <Burst text="WHO GETS ROBBED?" width={1000} height={300} size={70} fill={C.white} tilt={-3} delay={0.2} />
        <div className="targets">
          {targets.map((t, i) => (
            <Deal key={t.id} i={i}><div className="target"><TeamBadge team={t} byId={byId} size="md" score tilt={i % 2 ? 2 : -2} /><LootSack /></div></Deal>
          ))}
        </div>
      </div>
    </>
  )
}

function Steal({ g, byId }: { g: TriviaTv; byId: ById }) {
  const h = g.heist
  const thief = g.teams.find((t) => t.id === h?.thief)
  const victim = g.teams.find((t) => t.id === h?.victim)
  useEffect(() => { sfx.steal(); const id = setTimeout(() => coinShower({ x: 0.72, y: 0.45 }), 900); return () => clearTimeout(id) }, [])
  if (!thief || !victim || !h) return null
  return (
    <>
      <div className="trivia-header"><ShowTitle small /><div style={{ flex: 1 }} /></div>
      <div className="steal">
        <TeamBadge team={victim} byId={byId} size="lg" score delta={-h.amount} tilt={3} />
        <motion.div className="sack-fly" initial={{ x: -480, y: 0, rotate: -20 }} animate={{ x: 470, y: [0, -240, -150], rotate: 12, opacity: [1, 1, 0] }} transition={{ duration: 1.1, ease: 'easeInOut', delay: 0.3, opacity: { duration: 1.4, delay: 0.3, times: [0, 0.8, 1] } }}>
          <LootSack big />
        </motion.div>
        <TeamBadge team={thief} byId={byId} size="lg" score delta={h.amount} tilt={-3} />
      </div>
      <div className="steal-foot">
        <Burst text={h.amount > 0 ? 'ROBBED!' : 'NOTHING TO TAKE'} width={900} height={340} size={110} fill={C.sun} tilt={-5} delay={0.2} />
        <HostSays line={g.hostLine} mood="smug" size={140} />
      </div>
      <DrinkCall g={g} byId={byId} style={{ right: 70, top: 40 }} />
    </>
  )
}

function LootSack({ big = false }: { big?: boolean }) {
  const s = big ? 170 : 90
  return (
    <svg width={s} height={s} viewBox="0 0 100 100" aria-hidden="true" className="sack">
      <path d="M36 22 Q50 30 64 22 L60 34 Q86 50 84 74 Q82 94 50 94 Q18 94 16 74 Q14 50 40 34 Z" fill={C.tangerine} stroke={C.ink} strokeWidth="6" strokeLinejoin="round" />
      <path d="M38 34 Q50 40 62 34" stroke={C.ink} strokeWidth="6" fill="none" strokeLinecap="round" />
      <text x="50" y="78" textAnchor="middle" fontFamily="Rammetto One" fontSize="30" fill={C.ink}>$</text>
    </svg>
  )
}

// ---------- the gauntlet ----------

function Gauntlet({ g, byId, clock }: { g: TriviaTv; byId: ById; clock: Clock }) {
  const revealed = g.phase === 'reveal'
  const fits = new Set(g.reveal?.correct ?? [])
  useLater(revealed ? `g${g.q}` : null, 200, () => { if (revealed) sfx.whoosh() })
  return (
    <>
      <Header g={g} clock={clock} />
      <Bubble tail="none" className="q-bubble small">{g.prompt}</Bubble>
      <div className="g-options">
        {g.options.map((o, i) => (
          <Deal key={o.id} i={i}>
            <div className={`g-option ${revealed ? (fits.has(o.id) ? 'fit' : 'miss') : ''}`}>
              {revealed && <span className="mark">{fits.has(o.id) ? <Check /> : <Cross />}</span>}
              {o.text}
            </div>
          </Deal>
        ))}
      </div>
      <Track g={g} byId={byId} />
      <div className="g-talk">{revealed ? <HostSays line={g.hostLine} mood="happy" size={110} /> : <span className="g-help">Select every answer that fits, then lock in.</span>}</div>
    </>
  )
}

function Track({ g, byId, compact = false }: { g: TriviaTv; byId: ById; compact?: boolean }) {
  const moved = new Map((g.reveal?.answers ?? []).map((a) => [a.team, a.moved ?? 0]))
  const cells = g.finishLine + 1
  return (
    <div className={`track ${compact ? 'compact' : ''}`}>
      {g.teams.map((t) => {
        const m = moved.get(t.id) ?? 0
        return (
          <div key={t.id} className="lane" style={{ '--team': t.color } as CSSProperties}>
            <span className="lane-name" style={{ background: t.color, color: inkOn(t.color) }}>{t.name}</span>
            <div className="lane-cells" style={{ gridTemplateColumns: `repeat(${cells}, 1fr)` }}>
              {Array.from({ length: cells }, (_, i) => <i key={i} className={i === cells - 1 ? 'finish' : ''} />)}
              <motion.div className="runner" initial={false} animate={{ left: `${(t.position / cells) * 100}%` }} transition={{ type: 'spring', stiffness: 160, damping: 15, delay: 0.3 }}
                style={{ width: `${100 / cells}%` }}>
                <FaceRow players={members(t, byId)} size={compact ? 36 : 56} max={3} dimIds={NO_DIM} />
                {m !== 0 && <Pop delay={0.5} className={`moved ${m < 0 ? 'neg' : ''}`}><span>{m > 0 ? `+${m}` : m}</span></Pop>}
              </motion.div>
            </div>
          </div>
        )
      })}
    </div>
  )
}

const Check = () => <svg width="44" height="44" viewBox="0 0 40 40" aria-hidden="true"><path d="M7 21 L16 30 L33 9" stroke={C.ink} strokeWidth="7" fill="none" strokeLinecap="round" strokeLinejoin="round" /></svg>
const Cross = () => <svg width="40" height="40" viewBox="0 0 40 40" aria-hidden="true"><path d="M9 9 L31 31 M31 9 L9 31" stroke={C.ink} strokeWidth="7" strokeLinecap="round" /></svg>

// ---------- standings + podium ----------

function Standings({ g, byId }: { g: TriviaTv; byId: ById }) {
  const sorted = [...g.teams].sort((a, b) => b.score - a.score)
  const max = Math.max(1, ...sorted.map((t) => t.score))
  return (
    <>
      <div className="trivia-header"><ShowTitle small /><Chip fill={C.ink}>AFTER ROUND {g.round}</Chip><div style={{ flex: 1 }} /></div>
      <h1 className="standings-title display">STANDINGS</h1>
      <div className="bars">
        {sorted.map((t, i) => (
          <Deal key={t.id} i={i} className="bar-row">
            <span className="rank-dot">{i + 1}</span>
            <div className="bar-track">
              <motion.div className="bar" style={{ background: t.color, color: inkOn(t.color) }} initial={{ width: '8%' }} animate={{ width: `${Math.max(16, (t.score / max) * 100)}%` }}
                transition={{ type: 'spring', stiffness: 90, damping: 16, delay: 0.3 + i * 0.12 }}>
                <span className="bar-name">{t.name}</span>
                <FaceRow players={members(t, byId)} size={44} max={5} dimIds={NO_DIM} />
              </motion.div>
            </div>
            <span className="bar-score display"><CountUp from={0} to={t.score} delay={400 + i * 120} duration={1100} /></span>
            <span className="bar-crown">{i === 0 && <Crown size={70} />}</span>
          </Deal>
        ))}
      </div>
      <div className="trivia-foot"><HostSays line={g.hostLine} mood="smug" size={140} /><div /></div>
      {/* Top right: the bottom holds Brainy's line. */}
      <DrinkCall g={g} byId={byId} style={{ right: 70, top: 40 }} />
    </>
  )
}

function TeamPodium({ g, byId }: { g: TriviaTv; byId: ById }) {
  const order = g.podium.map((id) => g.teams.find((t) => t.id === id)).filter((t): t is TriviaTeam => !!t)
  const top = order.slice(0, 3)
  const slots = [1, 0, 2].filter((r) => r < top.length)
  const lands = [2.4, 1.3, 0.4]
  const fired = useRef(false)
  useEffect(() => {
    const ids = [setTimeout(() => sfx.crash(), 400), setTimeout(() => sfx.crash(), 1300),
      setTimeout(() => { if (!fired.current) { fired.current = true; sfx.fanfare(); sfx.applause(4); fireConfetti(true) } }, 2500)]
    return () => ids.forEach(clearTimeout)
  }, [])
  return (
    <>
      <div className="trivia-header"><ShowTitle /><div style={{ flex: 1, paddingLeft: 20 }}><HostSays line={g.hostLine} mood="smug" size={150} /></div></div>
      <div className="podium">
        {slots.map((rank) => {
          const t = top[rank]
          return (
            <div key={t.id} className="podium-slot">
              <Slam delay={lands[rank]} from={2.2} tilt={rank === 1 ? 8 : -8} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 10 }}>
                {rank === 0 && <Crown size={120} />}
                <div className={`podium-faces ${rank === 0 ? 'winner' : ''}`}>{members(t, byId).map((p) => <AvatarFace key={p.id} avatar={p.avatar} size={rank === 0 ? 92 : 72} />)}</div>
                <TeamBadge team={t} byId={byId} size="md" tilt={rank === 1 ? -2 : 2} faces={false} score style={{ minWidth: 340 }} />
              </Slam>
              <Deal i={0}>
                <div className="podium-block panel" style={{ height: [360, 250, 170][rank], background: t.color, boxShadow: 'var(--shadow-tv) 0 0 var(--ink)' }}>
                  <span style={{ color: inkOn(t.color) }}>{rank + 1}</span>
                </div>
              </Deal>
            </div>
          )
        })}
      </div>
      <DrinkCall g={g} byId={byId} style={{ right: 60, top: 230 }} />
    </>
  )
}

// ---------- awards ----------

const AWARD_GAP = 1.2

/** The end-of-show shout-outs, one card at a time: the drawn face, the title in a burst, the damning number. */
function Awards({ g, byId }: { g: TriviaTv; byId: ById }) {
  const pub = useGameTheme() === 'writeitdown'
  const awards = g.awards ?? []
  useEffect(() => {
    sfx.drumroll(0.9)
    const ids = awards.map((a, i) => setTimeout(() => (a.roast ? sfx.womp() : sfx.stamp()), (0.9 + i * AWARD_GAP) * 1000))
    ids.push(setTimeout(() => { sfx.applause(3); fireConfetti() }, (0.9 + awards.length * AWARD_GAP) * 1000))
    return () => ids.forEach(clearTimeout)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  return (
    <>
      <div className="trivia-header"><ShowTitle /><div style={{ flex: 1, paddingLeft: 20 }}><HostSays line={g.hostLine} mood="smug" size={150} /></div></div>
      <div className="awards" style={{ gridTemplateColumns: `repeat(${Math.max(1, awards.length)}, minmax(0, 1fr))` }}>
        {awards.map((a, i) => {
          const p = byId.get(a.player)
          const at = 0.9 + i * AWARD_GAP
          return (
            <Slam key={a.title} delay={at} from={2.1} tilt={i % 2 ? 7 : -7}>
              <Panel className={`award ${a.roast ? 'roast' : ''}`} fill={a.roast ? C.tomato : C.white} tilt={[-2, 1.5, -1, 2][i % 4]}>
                {pub ? <b className="award-title">{a.title}</b>
                  : <Burst text={a.title.toUpperCase()} width={400} height={180} size={48} fill={a.roast ? C.white : C.sun} tilt={i % 2 ? 4 : -4} spikes={14} delay={at + 0.15} />}
                {p ? <AvatarFace avatar={p.avatar} size={170} /> : !pub && <Brainy mood="shocked" size={170} />}
                <b className="award-name">{p?.name ?? 'Someone who left'}</b>
                <p>{a.line}</p>
              </Panel>
            </Slam>
          )
        })}
      </div>
    </>
  )
}
