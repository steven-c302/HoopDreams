import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import type { PlayerSummary, ScoreRow, StageInfo } from '../protocol'
import { GameMark } from '../theme/GameScene'
import { useTimerScale } from './timerScale'
import { sfx } from './audio'
import { Led } from './Casino'
import { GameHeader, Podium, Tutorial } from './Shared'
import { RES_FILL, ResourceIcon } from './SprawlArt'
import { SprawlMap, mapBox } from './SprawlMap'
import { AvatarFace, Burst, C, CountUp, Crown, Deal, Panel, Pop, Stamp, Timer, fireConfetti } from './toon'
import { Die } from './TurfArt'
import { waterNote, type SprawlBeat, type SprawlTv } from './types'
import './sprawl.css'

type Clock = { deadline: number | null; frozen: number | null }

const DECISION_MS: Record<string, number> = { setup: 30_000, roll: 20_000, main: 60_000, discard: 20_000, robber: 15_000, steal: 10_000, road2: 15_000, pick: 15_000, trade: 30_000 }
const sipText = (n: number) => (n >= 99 ? 'FINISH YOUR DRINK' : n === 1 ? '1 SIP' : `${n} SIPS`)
const latest = (beats: SprawlBeat[]) => beats.reduce((m, b) => Math.max(m, b.seq), 0)

export function SprawlStage({ stage, players, clock }: { stage: StageInfo; players: PlayerSummary[]; scores: ScoreRow[]; clock: Clock }) {
  if (stage.tutorial) {
    return (
      <div className="stage-pad">
        <GameHeader title="Sprawl" stage={stage} total={30_000} clock={clock} chips={[['HOW TO PLAY', C.paper]]} />
        <Tutorial cards={stage.tutorial.cards} acked={stage.tutorial.acked} players={players} />
      </div>
    )
  }
  const g = stage.game as unknown as SprawlTv | undefined
  if (!g) return null
  if (g.phase === 'podium') {
    const people = new Map(players.map((p) => [p.id, p]))
    const rows: ScoreRow[] = g.tally.slice().sort((a, b) => a.rank - b.rank).map((r) => {
      const s = g.seats[r.seat]
      return { id: s.player, name: s.name, avatar: people.get(s.player)?.avatar ?? { face: 'p:00', color: s.color }, score: r.vp }
    })
    return (
      <div className="stage-pad">
        <GameHeader title="Sprawl" stage={stage} total={15_000} clock={clock} chips={[['MOST POINTS WINS', C.paper]]} />
        <Podium scores={rows} unit=" pts" />
      </div>
    )
  }
  return <Island g={g} stage={stage} players={players} clock={clock} />
}

function Island({ g, stage, players, clock }: { g: SprawlTv; stage: StageInfo; players: PlayerSummary[]; clock: Clock }) {
  const people = useMemo(() => new Map(players.map((p) => [p.id, p])), [players])
  const flash = useFlashes(g, people)
  const { hot, gains } = useHarvest(g)
  useBeatSounds(g)
  const left = g.seats.map((_, i) => i).filter((i) => i % 2 === 0)
  const right = g.seats.map((_, i) => i).filter((i) => i % 2 === 1)
  const box = useMemo(() => mapBox(g.map), [g.map])
  return (
    <div className="sp-stage">
      <div className="sp-rail"><GameMark game="sprawl" />
        <TurnCard g={g} people={people} stage={stage} clock={clock} />
        {left.map((i) => <SeatCard key={i} g={g} i={i} people={people} gain={gains[i]} />)}
      </div>
      <div className="sp-board" style={{ aspectRatio: `${box.w} / ${box.h}` }}>
        <SprawlMap map={g.map} robber={g.robber} vOwner={g.vOwner} vLevel={g.vLevel} eOwner={g.eOwner} colors={g.seats.map((s) => s.color)}
          peek={g.peek} peekKind={g.peekKind} hot={hot} names />
        <AnimatePresence mode="wait">
          <Overlay key={`${g.phase}-${g.trade?.id ?? ''}`} g={g} people={people} />
        </AnimatePresence>
      </div>
      <div className="sp-rail">
        <ClockCard g={g} clock={clock} />
        {right.map((i) => <SeatCard key={i} g={g} i={i} people={people} gain={gains[i]} />)}
        <div className="sp-ticker">{g.ticker.slice(-3).map((line, k, all) => <p key={line + k} className={k === all.length - 1 ? 'latest' : ''}>{line}</p>)}</div>
      </div>
      <AnimatePresence>{flash && <FlashView key={flash.id} f={flash} />}</AnimatePresence>
    </div>
  )
}

// ---- production: the hexes that paid glow, and each seat card shows what it got --------------------

function useHarvest(g: SprawlTv) {
  const [hot, setHot] = useState<number[]>([])
  const [gains, setGains] = useState<Record<number, { id: number; cards: number[] }>>({})
  const seen = useRef(latest(g.beats))
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  useEffect(() => {
    const fresh = g.beats.filter((b) => b.seq > seen.current && b.kind === 'harvest')
    seen.current = Math.max(seen.current, latest(g.beats))
    const b = fresh[fresh.length - 1]
    if (!b) return
    setHot(b.targets)
    const next: Record<number, { id: number; cards: number[] }> = {}
    b.gains.forEach((cards, i) => { if (cards.some((n) => n > 0)) next[i] = { id: b.seq, cards } })
    setGains(next)
    if (timer.current) clearTimeout(timer.current)
    timer.current = setTimeout(() => { setHot([]); setGains({}) }, 2600)
  }, [g])
  useEffect(() => () => { if (timer.current) clearTimeout(timer.current) }, [])
  return { hot, gains }
}

// ---- callouts, one at a time ------------------------------------------------------------------------

interface Flash { id: number; text: string; sub?: string; fill: string; ink?: string; ms: number }

function flashFor(b: SprawlBeat, g: SprawlTv, people: Map<string, PlayerSummary>): Flash | null {
  const name = (i: number) => g.seats[i]?.name ?? ''
  const dev = (k?: string) => (k ? g.map.dev[k] ?? k : '')
  switch (b.kind) {
    case 'roll': return b.amount === 7 ? { id: b.seq, text: 'SEVEN!', sub: `${g.map.landlord} is coming`, fill: C.ink, ink: C.sun, ms: 1800 } : null
    case 'steal': return { id: b.seq, text: 'ROBBED!', sub: `${name(b.seat)} took a card from ${name(b.other)}`, fill: C.grape, ink: C.white, ms: 1700 }
    case 'longest': return b.seat >= 0 ? { id: b.seq, text: (g.map.awards.road ?? 'Longest Road').toUpperCase() + '!', sub: name(b.seat), fill: C.lime, ms: 2200 } : null
    case 'army': return b.seat >= 0 ? { id: b.seq, text: (g.map.awards.army ?? 'Most Bouncers').toUpperCase() + '!', sub: name(b.seat), fill: C.lime, ms: 2200 } : null
    case 'city': return { id: b.seq, text: 'CITY!', sub: `${name(b.seat)} moves on up`, fill: C.sun, ms: 1500 }
    case 'mono': return { id: b.seq, text: dev('mono').toUpperCase() + '!', sub: `${name(b.seat)} took all the ${g.map.resources[b.target]} (${b.amount})`, fill: C.tomato, ink: C.white, ms: 2200 }
    case 'play': return b.text === 'knight' ? { id: b.seq, text: dev('knight').toUpperCase() + '!', sub: `${name(b.seat)} sends ${g.map.landlord} packing`, fill: C.grape, ink: C.white, ms: 1500 } : null
    case 'traded': return { id: b.seq, text: 'DEAL!', sub: `${name(b.seat)} and ${name(b.other)}`, fill: C.sun, ms: 1500 }
    case 'drink': {
      if (!g.drinks) return null
      const alive = g.seats.filter((s) => !s.gone).length
      const who = b.seats.length >= alive - 1 && alive > 2 ? `Everyone but ${name(g.seats.findIndex((s, i) => !s.gone && !b.seats.includes(i)))}` : b.seats.map(name).join(' + ')
      const water = b.seats.filter((i) => g.seats[i] && people.get(g.seats[i].player)?.water).map(name)
      return { id: b.seq, text: 'DRINK!', sub: `${who}: ${sipText(b.sips)}${waterNote(water)}`, fill: C.bubblegum, ms: 2200 }
    }
    case 'lastround': return { id: b.seq, text: 'LAST ROUND!', sub: "Time's up: one more lap of the table", fill: C.tomato, ink: C.white, ms: 2400 }
    case 'win': return { id: b.seq, text: `${name(b.seat).toUpperCase()} WINS!`, sub: `${g.vpTarget} points`, fill: C.sun, ms: 3000 }
    default: return null
  }
}

function useFlashes(g: SprawlTv, people: Map<string, PlayerSummary>) {
  const [flash, setFlash] = useState<Flash | null>(null)
  const queue = useRef<Flash[]>([])
  const seen = useRef(latest(g.beats))
  const showing = useRef(false)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const next = () => {
    const f = queue.current.shift() ?? null
    setFlash(f)
    showing.current = !!f
    if (f) timer.current = setTimeout(next, f.ms)
  }
  useEffect(() => {
    const fresh = g.beats.filter((b) => b.seq > seen.current)
    seen.current = Math.max(seen.current, latest(g.beats))
    for (const b of fresh) { const f = flashFor(b, g, people); if (f) queue.current.push(f) }
    if (!showing.current && queue.current.length) next()
  }, [g]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => () => { if (timer.current) clearTimeout(timer.current) }, [])
  return flash
}

function FlashView({ f }: { f: Flash }) {
  return (
    <motion.div className="sp-flash" exit={{ opacity: 0, scale: 0.8 }} transition={{ duration: 0.2 }}>
      <Burst text={f.text} sub={f.sub} fill={f.fill} ink={f.ink ?? C.ink} width={900} height={400} size={f.text.length > 14 ? 76 : 100} tilt={-4} />
    </motion.div>
  )
}

function useBeatSounds(g: SprawlTv) {
  const seen = useRef(latest(g.beats))
  useEffect(() => {
    const fresh = g.beats.filter((b) => b.seq > seen.current)
    seen.current = Math.max(seen.current, latest(g.beats))
    fresh.forEach((b, k) => {
      const at = (fn: () => void) => setTimeout(fn, k * 120)
      switch (b.kind) {
        case 'roll': at(sfx.diceRoll); break
        case 'harvest': { const n = b.gains.flat().reduce((a, c) => a + c, 0); if (n > 0) at(() => sfx.harvest(n)); break }
        case 'settle': at(sfx.settle); break
        case 'road': at(sfx.hammer); break
        case 'city': at(sfx.cityUp); break
        case 'buyDev': at(sfx.cardDraw); break
        case 'play': at(sfx.cardFlip); break
        case 'robber': at(sfx.landlord); break
        case 'steal': case 'mono': at(sfx.steal); break
        case 'discard': at(() => sfx.cardFlick(k)); break
        case 'longest': if (b.seat >= 0) at(sfx.longRoad); break
        case 'army': if (b.seat >= 0) at(sfx.bigCrew); break
        case 'plenty': case 'bank': at(() => sfx.chipClack(3)); break
        case 'offer': case 'counter': at(sfx.offerPing); break
        case 'traded': at(sfx.dealDone); break
        case 'rejected': case 'cancelled': case 'expired': at(sfx.womp); break
        case 'pass': at(sfx.pop); break
        case 'drink': if (g.drinks) at(sfx.drinkCall); break
        case 'lastround': at(sfx.lastLap); break
        case 'win': at(() => { sfx.sprawlWin(); fireConfetti(true) }); break
        case 'tally': at(() => sfx.drumroll(1.6)); break
        case 'turn': case 'setup': at(sfx.focus); break
        case 'left': at(sfx.leave); break
      }
    })
  }, [g])
}

// ---- the rails --------------------------------------------------------------------------------

function usePrevious<T>(v: T): T {
  const r = useRef(v)
  const prev = r.current
  useEffect(() => { r.current = v }, [v])
  return prev
}

function SeatCard({ g, i, people, gain }: { g: SprawlTv; i: number; people: Map<string, PlayerSummary>; gain?: { id: number; cards: number[] } }) {
  const s = g.seats[i]
  const before = usePrevious(s.vp)
  const active = g.turn === i && g.phase !== 'tally'
  const p = people.get(s.player)
  return (
    <Panel className={`sp-seat ${active ? 'active' : ''} ${s.gone ? 'out' : ''}`} fill={C.paper} tilt={0}
      style={{ '--seat': s.color } as CSSProperties} animate={{ scale: 1 }} transition={{ type: 'spring', stiffness: 300, damping: 20 }}>
      <div className="top">
        {p ? <AvatarFace avatar={p.avatar} size={64} dim={!p.connected} /> : <span className="dot" />}
        <div className="who"><h3>{s.name}</h3>{active && <span className="now">{g.phase === 'setup' ? 'PLACING' : 'THEIR TURN'}</span>}</div>
        <div className="vp"><CountUp from={before} to={s.vp} duration={600} /><small>PTS</small></div>
      </div>
      <div className="stats">
        <span className={`stat ${s.cards > 7 ? 'hot' : ''}`} title="cards"><CardsGlyph />{s.cards}</span>
        <span className="stat" title="development cards"><DevGlyph />{s.dev}</span>
        <span className="stat" title="bouncers played"><BouncerGlyph />{s.knights}</span>
        <span className="stat" title="longest road"><RoadGlyph color={s.color} />{s.road}</span>
      </div>
      {(s.longest || s.army) && (
        <div className="ribbons">
          {s.longest && <span className="ribbon">{g.map.awards.road ?? 'Longest Road'} +2</span>}
          {s.army && <span className="ribbon">{g.map.awards.army ?? 'Most Bouncers'} +2</span>}
        </div>
      )}
      {g.phase === 'discard' && s.discard > 0 && <span className="owes">DISCARD {s.discard}</span>}
      <AnimatePresence>
        {gain && (
          <motion.div key={gain.id} className="sp-gain" initial={{ opacity: 0, y: 20, scale: 0.6 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: -20 }} transition={{ type: 'spring', stiffness: 500, damping: 18 }}>
            {gain.cards.flatMap((n, r) => Array.from({ length: n }, (_, k) => <ResourceIcon key={`${r}-${k}`} res={r} size={40} />))}
          </motion.div>
        )}
      </AnimatePresence>
      {s.gone && <Stamp text="LEFT" color={C.tomato} size={44} tilt={-10} className="corner-stamp" />}
    </Panel>
  )
}

const CardsGlyph = () => <svg viewBox="0 0 30 30" width="26" aria-hidden="true"><rect x="3" y="6" width="15" height="20" rx="3" fill="var(--paper)" stroke="var(--ink)" strokeWidth="2.5" transform="rotate(-10 10 16)" /><rect x="11" y="4" width="15" height="20" rx="3" fill="var(--sun)" stroke="var(--ink)" strokeWidth="2.5" /></svg>
const DevGlyph = () => <svg viewBox="0 0 30 30" width="26" aria-hidden="true"><rect x="7" y="3" width="17" height="24" rx="3" fill="var(--grape)" stroke="var(--ink)" strokeWidth="2.5" /><circle cx="15.5" cy="15" r="4" fill="var(--sun)" stroke="var(--ink)" strokeWidth="2" /></svg>
const BouncerGlyph = () => <svg viewBox="0 0 30 30" width="26" aria-hidden="true"><circle cx="15" cy="10" r="6" fill="var(--ink)" /><path d="M5 27 Q6 16 15 16 Q24 16 25 27 Z" fill="var(--ink)" /><rect x="9" y="8" width="12" height="3" fill="var(--paper)" /></svg>
const RoadGlyph = ({ color }: { color: string }) => <svg viewBox="0 0 30 30" width="26" aria-hidden="true"><path d="M5 24 L25 6" stroke="var(--ink)" strokeWidth="10" strokeLinecap="round" /><path d="M5 24 L25 6" stroke={color} strokeWidth="5" strokeLinecap="round" /></svg>

// ---- the top bar: whose turn, the dice, the clocks -------------------------------------------------

function useGameClock(g: SprawlTv, clock: Clock): number | null {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => { const id = setInterval(() => setNow(Date.now()), 500); return () => clearInterval(id) }, [])
  if (g.clockLeftMs == null) return null
  if (g.phase === 'tally') return Math.max(0, g.clockLeftMs)
  const left = clock.frozen ?? (clock.deadline ? Math.max(0, clock.deadline - now) : g.phaseMs ?? 0)
  return Math.max(0, g.clockLeftMs - Math.max(0, (g.phaseMs ?? 0) - left))
}

function GameClock({ g, clock }: { g: SprawlTv; clock: Clock }) {
  const left = useGameClock(g, clock)
  if (g.lastRound) return <span className="sp-lastround">LAST ROUND</span>
  if (left == null) return <span className="sp-clock-label">NO TIME LIMIT</span>
  const m = Math.floor(left / 60_000), s = Math.floor((left % 60_000) / 1000)
  return <span className="sp-clock"><Led value={`${m}:${String(s).padStart(2, '0')}`} tone={left < 5 * 60_000 ? 'red' : 'gold'} size={36} digits={5} /></span>
}

function call(g: SprawlTv): string {
  const who = g.seats[g.turn]?.name ?? ''
  switch (g.phase) {
    case 'setup': return `${who} places a ${g.setupPiece === 'road' ? 'road' : 'settlement'}`
    case 'roll': return `${who} rolls`
    case 'main': return `${who} is building`
    case 'discard': return 'Big hands: discard half'
    case 'robber': return `${who} moves ${g.map.landlord}`
    case 'steal': return `${who} picks who to rob`
    case 'road2': return `${who}: ${g.map.dev.road ?? 'Road Trip'}`
    case 'pick': return `${who}: ${g.map.dev[g.pick ?? ''] ?? ''}`
    case 'trade': return 'Deal on the table'
    case 'tally': return 'Final tally'
    default: return ''
  }
}

/** Whose turn it is and what they're doing, with the dice. */
function TurnCard({ g, people, stage, clock }: { g: SprawlTv; people: Map<string, PlayerSummary>; stage: StageInfo; clock: Clock }) {
  const scale = useTimerScale()
  const s = g.seats[g.turn]
  const p = s ? people.get(s.player) : undefined
  return (
    <div className="sp-turn" style={{ '--seat': s?.color ?? C.paper } as CSSProperties}>
      <div className="who">
        {p && <AvatarFace avatar={p.avatar} size={56} />}<b>{call(g)}</b>
        {g.timed && (clock.deadline != null || clock.frozen != null) && (
          <Timer deadline={stage.paused ? null : clock.deadline} frozen={stage.paused ? clock.frozen ?? 0 : clock.frozen} total={(DECISION_MS[g.phase] ?? 20_000) * scale} size={76} />
        )}
      </div>
      {g.dice.length > 0 && g.phase !== 'setup' && (
        <div className="dice">
          {g.dice.map((d, k) => (
            // Keyed on whose roll it is too, so the next player rolling the same numbers still tumbles.
            <motion.div key={`${k}-${g.turn}-${d}`} initial={{ rotate: -200, y: -60, opacity: 0 }} animate={{ rotate: k % 2 ? 6 : -6, y: 0, opacity: 1 }} transition={{ type: 'spring', stiffness: 260, damping: 14, delay: k * 0.06 }}>
              <Die value={d} size={70} />
            </motion.div>
          ))}
          <span className={`sum ${g.dice[0] + g.dice[1] === 7 ? 'seven' : ''}`}>{g.dice[0] + g.dice[1]}</span>
        </div>
      )}
    </div>
  )
}

/** The target and the game clock (or LAST ROUND). */
function ClockCard({ g, clock }: { g: SprawlTv; clock: Clock }) {
  return (
    <div className="sp-clockcard">
      <span className="goal">FIRST TO {g.vpTarget}</span>
      <GameClock g={g} clock={clock} />
    </div>
  )
}

// ---- overlays on the board ---------------------------------------------------------------------------

function Tiles({ cards, names }: { cards: number[]; names: string[] }) {
  if (!cards.some((n) => n > 0)) return <span className="hint">nothing</span>
  return (
    <div className="sp-tiles">
      {cards.map((n, r) => n > 0 && (
        <span key={r} className="sp-tv-tile" style={{ '--res': RES_FILL[r] } as CSSProperties}><ResourceIcon res={r} size={54} /><b>×{n}</b><small>{names[r]}</small></span>
      ))}
    </div>
  )
}

function Overlay({ g, people }: { g: SprawlTv; people: Map<string, PlayerSummary> }) {
  const wrap = (child: ReactNode, className = '') => (
    <motion.div className={`sp-overlay ${className}`} initial={{ opacity: 0, y: 40, scale: 0.9 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: -20 }} transition={{ type: 'spring', stiffness: 320, damping: 22 }}>
      {child}
    </motion.div>
  )
  if (g.phase === 'trade' && g.trade) {
    const t = g.trade
    const from = g.seats[t.from], to = t.to >= 0 ? g.seats[t.to] : undefined
    return wrap(
      <Panel fill={C.paper} tilt={-1} className="sp-trade-panel">
        <p className="call">{t.counters > 0 ? `COUNTER-OFFER #${t.counters}` : 'TRADE OFFER!'}</p>
        <div className="cols">
          <div className="side" style={{ '--seat': from.color } as CSSProperties}><b>{from.name} gives</b><Tiles cards={t.give} names={g.map.resources} /></div>
          <svg className="swap" viewBox="0 0 80 80" width="80" aria-hidden="true"><path d="M12 28 H64 M52 16 L66 28 L52 40 M68 52 H16 M28 40 L14 52 L28 64" fill="none" stroke="var(--ink)" strokeWidth="7" strokeLinecap="round" strokeLinejoin="round" /></svg>
          <div className="side" style={{ '--seat': to?.color ?? C.sun } as CSSProperties}><b>{to ? `${to.name} gives` : 'Anyone gives'}</b><Tiles cards={t.get} names={g.map.resources} /></div>
        </div>
        <p className="hint">{to ? `${to.name} decides on their phone.` : `First to accept gets it.${t.passed.length ? ` Passed: ${t.passed.map((i) => g.seats[i].name).join(', ')}` : ''}`}</p>
      </Panel>,
    )
  }
  if (g.phase === 'discard') {
    const owing = g.seats.map((s, i) => ({ s, i })).filter(({ s }) => s.discard > 0)
    return wrap(
      <Panel fill={C.tomato} tilt={1} className="sp-small-panel">
        <p className="call white">DISCARD HALF!</p>
        <div className="owing">{owing.map(({ s, i }) => {
          const p = people.get(s.player)
          return <span key={i}>{p && <AvatarFace avatar={p.avatar} size={48} />}<b>{s.name}</b><em>{s.discard}</em></span>
        })}</div>
      </Panel>,
    )
  }
  if (g.phase === 'pick') {
    return wrap(
      <Panel fill={C.sun} tilt={-1} className="sp-small-panel">
        <p className="call">{(g.map.dev[g.pick ?? ''] ?? '').toUpperCase()}!</p>
        <p className="hint">{g.pick === 'mono' ? 'Naming a resource: everyone hands over all of it' : 'Taking 2 from the bank'}</p>
      </Panel>,
    )
  }
  if (g.phase === 'tally') return wrap(<TallyPanel g={g} people={people} />, 'tally')
  return null
}

function TallyPanel({ g, people }: { g: SprawlTv; people: Map<string, PlayerSummary> }) {
  const rows = g.tally.slice().sort((a, b) => a.rank - b.rank)
  const max = Math.max(1, ...rows.map((r) => r.vp))
  useEffect(() => { const id = setTimeout(() => fireConfetti(true), 2400); return () => clearTimeout(id) }, [])
  return (
    <Panel fill={C.paper} tilt={-1} className="sp-tally">
      <p className="call">FINAL TALLY</p>
      {rows.map((r, k) => {
        const s = g.seats[r.seat]
        const p = people.get(s.player)
        return (
          <Deal key={r.seat} i={k}>
            <div className="row">
              {p && <AvatarFace avatar={p.avatar} size={52} />}
              <span className="name">{s.name}{r.seat === g.winner && <Crown size={38} />}</span>
              <span className="bar"><motion.span style={{ background: s.color }} initial={{ width: 0 }} animate={{ width: `${(r.vp / max) * 100}%` }} transition={{ duration: 1.1, delay: 0.4 + k * 0.2 }} /></span>
              <b><CountUp from={0} to={r.vp} delay={400 + k * 200} duration={1100} /></b>
              {r.vpCards > 0 && <Pop delay={1.6 + k * 0.2}><span className="cards">incl. {r.vpCards} {g.map.dev.vp ?? 'VP'}</span></Pop>}
            </div>
          </Deal>
        )
      })}
      <p className="hint">Settlements 1 · cities 2 · awards 2 · secret cards 1</p>
    </Panel>
  )
}
