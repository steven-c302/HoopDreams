import { AnimatePresence, motion } from 'motion/react'
import { lazy, Suspense, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import type { PlayerSummary, ScoreRow, StageInfo } from '../protocol'
import { Face } from '../theme/Face'
import { GameMark, Neighborhood } from '../theme/GameScene'
import { useTimerScale } from './timerScale'
import { sfx } from './audio'
import { Led } from './Casino'
import { GameHeader, Podium, Tutorial } from './Shared'
import { Die, Piece, PIECE_NAMES } from './TurfArt'
import { TurfBoard } from './TurfBoard'
import { Burst, C, CountUp, Crown, Deal, Panel, Pop, Slam, Stamp, Timer, coinShower, fireConfetti, inkOn } from './toon'
import { waterNote, type TurfBeat, type TurfTv } from './types'
import { ErrorBoundary } from './turf3d/ErrorBoundary'
import { hasWebGL2, wants3d } from './turf3d/webgl'
import { DECISION_MS, SETUP_PHASES, gameClockLeft, type Clock } from './turf3d/ui/hud'
import './turf.css'

const TurfStage3D = lazy(() => import('./turf3d/TurfStage3D').then((m) => ({ default: m.TurfStage3D })))


/** Same as the engine: each hop is 260 ms, and the camera lets go shortly after the last one. */
const HOP_MS = 260
const SETUP = SETUP_PHASES
const RIDE = '#2B2B2B'

const money = (n: number) => `$${n.toLocaleString()}`
const sipText = (n: number) => (n >= 99 ? 'FINISH YOUR DRINK' : n === 5 ? 'A SHOT' : n === 1 ? '1 SIP' : `${n} SIPS`)
const latest = (beats: TurfBeat[]) => beats.reduce((m, b) => Math.max(m, b.seq), 0)

export function TurfStage({ stage, players, clock }: { stage: StageInfo; players: PlayerSummary[]; scores: ScoreRow[]; clock: Clock }) {
  useEffect(() => {
    if (!wants3d(location.search, hasWebGL2())) return
    void import('./turf3d/TurfStage3D') // the board appears the moment the tutorial ends
    void import('./turf3d/diceSim').then((m) => m.loadRapier()).catch(() => undefined) // and the dice are ready for the first roll
  }, [])
  if (stage.tutorial) {
    return (
      <div className="stage-pad">
        <GameHeader title="Home Turf" stage={stage} total={30_000} clock={clock} chips={[['HOW TO PLAY', C.paper]]} />
        <Tutorial cards={stage.tutorial.cards} acked={stage.tutorial.acked} players={players} />
      </div>
    )
  }
  const g = stage.game as unknown as TurfTv | undefined
  if (!g) return null
  if (g.phase === 'podium') {
    return (
      <div className="stage-pad">
        <GameHeader title="Home Turf" stage={stage} total={15_000} clock={clock} chips={[['RICHEST WINS', C.paper]]} />
        <Podium scores={podiumRows(g, players)} />
      </div>
    )
  }
  return <Turf g={g} stage={stage} players={players} clock={clock} />
}

/** Tokens as score rows for the shared podium: a team shows its dice-holder's face. */
function podiumRows(g: TurfTv, players: PlayerSummary[]): ScoreRow[] {
  const people = new Map(players.map((p) => [p.id, p]))
  return g.tally.slice().sort((a, b) => a.rank - b.rank).map((r) => {
    const t = g.tokens[r.token]
    const face = people.get(t.seat ?? '') ?? people.get(t.members[0] ?? '')
    return { id: `t${r.token}`, name: t.name, avatar: face?.avatar ?? { face: 'p:00', color: t.color }, score: r.worth }
  })
}

function Turf({ g, stage, players, clock }: { g: TurfTv; stage: StageInfo; players: PlayerSummary[]; clock: Clock }) {
  const people = useMemo(() => new Map(players.map((p) => [p.id, p])), [players])
  const [lost, setLost] = useState(false)
  const use3d = useMemo(() => !lost && wants3d(location.search, hasWebGL2()), [lost])
  const { display, zoom } = useHops(g, !use3d)
  const flash = useFlashes(g, people)
  useBeatSounds(g)
  const left = g.tokens.map((_, i) => i).filter((i) => i % 2 === 0)
  const right = g.tokens.map((_, i) => i).filter((i) => i % 2 === 1)
  const hot = g.phase === 'buy' ? g.buy : g.phase === 'auction' ? g.auction?.space : undefined
  const timerScale = useTimerScale()
  const curTok = g.tokens[g.turn]
  const seatName = curTok?.seat ? people.get(curTok.seat)?.name ?? curTok.name : curTok?.name ?? ''
  return (
    <div className={`turf-stage ${use3d ? 'is3d' : ''}`}>
      {use3d && (
        <ErrorBoundary onError={() => setLost(true)}>
          <Suspense fallback={null}>
            <TurfStage3D g={g} hud={{ clock, paused: !!stage.paused, timerScale, seatName }} onLost={() => setLost(true)}><Well g={g} stage={stage} clock={clock} people={people} /></TurfStage3D>
          </Suspense>
        </ErrorBoundary>
      )}
      <div className="turf-rail left"><GameMark game="turf" />{left.map((i) => <TokenCard key={i} g={g} i={i} people={people} />)}</div>
      <div className="turf-board-wrap">
        {!use3d && (
          <TurfBoard tv={g} display={display} zoom={zoom} hot={hot}>
            <Well g={g} stage={stage} clock={clock} people={people} />
          </TurfBoard>
        )}
      </div>
      <div className="turf-rail right">{right.map((i) => <TokenCard key={i} g={g} i={i} people={people} />)}</div>
      <AnimatePresence>{flash && <FlashView key={flash.id} f={flash} />}</AnimatePresence>
    </div>
  )
}

// ---- motion: pieces hop, the camera follows ------------------------------------------------------

function useHops(g: TurfTv, enabled: boolean) {
  const [display, setDisplay] = useState<number[]>(() => g.tokens.map((t) => t.pos))
  const [zoom, setZoom] = useState<number | null>(null)
  const seen = useRef(latest(g.beats))
  const busy = useRef(0)
  const timers = useRef<ReturnType<typeof setTimeout>[]>([])
  useEffect(() => {
    const fresh = g.beats.filter((b) => b.seq > seen.current)
    seen.current = Math.max(seen.current, latest(g.beats))
    if (!enabled) { setDisplay(g.tokens.map((t) => t.pos)); return }
    const moves = fresh.filter((b) => b.kind === 'move' && b.path.length > 0)
    for (const b of moves) {
      busy.current++
      setZoom(b.path[b.path.length - 1])
      b.path.forEach((space, k) => timers.current.push(setTimeout(() => {
        setDisplay((d) => d.map((v, i) => (i === b.token ? space : v)))
        sfx.hop(k)
        if (k === b.path.length - 1) {
          timers.current.push(setTimeout(() => { busy.current = Math.max(0, busy.current - 1); if (busy.current === 0) setZoom(null) }, 700))
        }
      }, k * HOP_MS)))
    }
    // Anything else that moved a piece (Timeout, a bankruptcy, a restore) snaps straight there.
    if (moves.length === 0 && busy.current === 0) setDisplay(g.tokens.map((t) => t.pos))
  }, [g, enabled])
  useEffect(() => () => timers.current.forEach(clearTimeout), [])
  return { display, zoom }
}

// ---- the callouts: one at a time, in the order things happened ---------------------------------------

interface Flash { id: number; text: string; sub?: string; fill: string; ink?: string; ms: number; small?: boolean }

function flashFor(b: TurfBeat, g: TurfTv, people: Map<string, PlayerSummary>): Flash | null {
  const name = (i: number) => g.tokens[i]?.name ?? ''
  const alive = g.tokens.filter((t) => !t.bankrupt).length
  switch (b.kind) {
    case 'rent': return { id: b.seq, text: `RENT ${money(b.amount)}`, sub: `${name(b.token)} pays ${name(b.other)}`, fill: C.tomato, ink: C.white, ms: 1700 }
    case 'set': return { id: b.seq, text: 'HOME TURF!', sub: `${name(b.token)} owns the whole set`, fill: C.lime, ms: 2000 }
    case 'drink': {
      if (!g.drinks) return null
      const who = b.tokens.length > 1 && b.tokens.length >= alive - 1 && alive > 2
        ? `Everyone but ${name(g.tokens.findIndex((_, i) => !b.tokens.includes(i) && !g.tokens[i].bankrupt))}`
        : b.tokens.map(name).join(' + ')
      // A token whose players are all on water tonight drinks water; the call itself is the same.
      const water = b.tokens.filter((i) => (g.tokens[i]?.members.length ?? 0) > 0 && g.tokens[i].members.every((m) => people.get(m)?.water)).map(name)
      return { id: b.seq, text: 'DRINK!', sub: `${who}: ${sipText(b.sips)}${waterNote(water)}`, fill: C.bubblegum, ms: 2200 }
    }
    case 'jail': return { id: b.seq, text: 'TIMEOUT!', sub: `${name(b.token)} is off the board`, fill: C.blueberry, ink: C.white, ms: 1700 }
    case 'bankrupt': return { id: b.seq, text: 'BANKRUPT!', sub: `${name(b.token)} is out`, fill: C.ink, ink: C.sun, ms: 2400 }
    case 'won': return { id: b.seq, text: 'SOLD!', sub: `${name(b.token)} for ${money(b.amount)}`, fill: C.sun, ms: 1700 }
    case 'traded': return { id: b.seq, text: 'DEAL!', sub: `${name(b.token)} and ${name(b.other)} shook on it`, fill: C.sun, ms: 1800 }
    case 'lastlap': return { id: b.seq, text: 'LAST LAP!', sub: "Time's up: finish the lap", fill: C.tomato, ink: C.white, ms: 2400 }
    case 'payday': return { id: b.seq, text: '+$200', sub: 'PAYDAY', fill: C.lime, ms: 900, small: true }
    case 'teleport': return { id: b.seq, text: 'TRIPLES!', sub: `${name(b.token)} goes anywhere`, fill: C.grape, ink: C.white, ms: 1400 }
    default: return null
  }
}

function useFlashes(g: TurfTv, people: Map<string, PlayerSummary>) {
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
    <motion.div className={`turf-flash ${f.small ? 'small' : ''}`} exit={{ opacity: 0, scale: 0.8 }} transition={{ duration: 0.2 }}>
      <Burst text={f.text} sub={f.sub} fill={f.fill} ink={f.ink ?? C.ink} width={f.small ? 420 : 860} height={f.small ? 220 : 400} size={f.small ? 64 : 104} tilt={-4} />
    </motion.div>
  )
}

/** Every beat lands twice: a sound for each thing that happened, in step with the pictures. */
function useBeatSounds(g: TurfTv) {
  const seen = useRef(latest(g.beats))
  useEffect(() => {
    const fresh = g.beats.filter((b) => b.seq > seen.current)
    seen.current = Math.max(seen.current, latest(g.beats))
    fresh.forEach((b, k) => {
      const at = (fn: () => void) => setTimeout(fn, k * 120)
      switch (b.kind) {
        case 'roll': at(sfx.diceRoll); break
        case 'payday': at(sfx.payday); break
        case 'buy': case 'rent': case 'tax': case 'paid': at(sfx.register); break
        case 'card': at(sfx.cardDraw); break
        case 'jail': at(sfx.jailClang); break
        case 'build': at(sfx.hammer); break
        case 'sell': case 'mortgage': case 'unmortgage': at(() => sfx.chipClack(3)); break
        case 'auction': at(() => sfx.drumroll(0.8)); break
        case 'bid': at(() => sfx.chipClack(Math.min(12, Math.round(b.amount / 40)))); break
        case 'won': at(() => { sfx.gavel(); sfx.soldTag() }); break
        case 'nobid': case 'rejected': case 'cancelled': case 'expired': at(sfx.womp); break
        case 'set': at(() => { sfx.homeTurf(); coinShower({ x: 0.5, y: 0.35 }) }); break
        case 'offer': case 'counter': at(sfx.offerPing); break
        case 'traded': at(sfx.dealDone); break
        case 'bankrupt': at(sfx.bankruptSting); break
        case 'drink': if (g.drinks) at(sfx.drinkCall); break
        case 'lastlap': at(sfx.lastLap); break
        case 'tally': at(() => sfx.drumroll(1.6)); break
        case 'piece': at(sfx.pop); break
        case 'deal': at(() => sfx.cardFlick(k)); break
        case 'free': at(sfx.resume); break
        case 'bus': at(sfx.busHorn); break
        case 'scout': case 'teleport': at(sfx.whoosh); break
        case 'couch': at(sfx.couch); break
        case 'turn': at(sfx.focus); break
        case 'shuffle': case 'join': at(sfx.boing); break
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

function TokenCard({ g, i, people }: { g: TurfTv; i: number; people: Map<string, PlayerSummary> }) {
  const t = g.tokens[i]
  const before = usePrevious(t.cash)
  const active = g.turn === i && !SETUP.has(g.phase)
  const seat = t.seat ? people.get(t.seat) : undefined
  const members = t.members.map((id) => people.get(id)).filter((p): p is PlayerSummary => !!p)
  const owned = g.owner.map((o, s) => (o === i ? s : -1)).filter((s) => s >= 0)
  return (
    <Panel className={`turf-card ${active ? 'active' : ''} ${t.bankrupt ? 'out' : ''}`} fill={C.paper} tilt={0}
      animate={{ scale: 1 }} transition={{ type: 'spring', stiffness: 300, damping: 20 }}>
      <div className="top">
        <Piece piece={t.piece} color={t.color} size={78} />
        <div className="who">
          <h3>{t.name}</h3>
          {g.teams ? (
            <span className="members">{members.map((p) => <Face key={p.id} face={p.avatar.face} color={p.avatar.color} size={p.id === t.seat ? 44 : 32} dim={!p.connected} />)}</span>
          ) : seat && <span className="members"><Face face={seat.avatar.face} color={seat.avatar.color} size={44} dim={!seat.connected} /></span>}
          {g.teams && seat && <span className="seat">{seat.name} has the dice</span>}
        </div>
      </div>
      <div className="cash"><CountUp from={before} to={t.cash} duration={700} />{t.cash !== before && <Pop key={t.cash}><span className={`delta ${t.cash < before ? 'neg' : ''}`}>{t.cash > before ? '+' : '−'}{money(Math.abs(t.cash - before))}</span></Pop>}</div>
      <div className="deeds">
        {owned.map((s) => <span key={s} className={`deed-pip ${g.mortgaged.includes(s) ? 'mortgaged' : ''}`} style={{ background: g.board[s].color ?? RIDE }} />)}
        {owned.length === 0 && <span className="none">No places yet</span>}
      </div>
      <div className="badges">
        {/* A badge, not a stamp over the name: a long name would disappear under it. */}
        {t.jailed && !t.bankrupt && <span className="badge timeout">IN TIMEOUT</span>}
        {t.sets > 0 && <span className="badge set">{t.sets} SET{t.sets > 1 ? 'S' : ''}</span>}
        {t.jailCards > 0 && <span className="badge card">GET OUT ×{t.jailCards}</span>}
        <span className="badge worth">WORTH {money(t.worth)}</span>
      </div>
      {t.bankrupt && <Stamp text="OUT" color={C.tomato} size={44} tilt={-10} className="corner-stamp" />}
    </Panel>
  )
}

// ---- the middle of the board -------------------------------------------------------------------------

function useGameClock(g: TurfTv, clock: Clock): number | null {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => { const id = setInterval(() => setNow(Date.now()), 500); return () => clearInterval(id) }, [])
  return gameClockLeft(g, clock, now)
}

function GameClock({ g, clock }: { g: TurfTv; clock: Clock }) {
  const left = useGameClock(g, clock)
  if (g.lastLap) return <span className="turf-lastlap">LAST LAP</span>
  if (left == null) return <span className="turf-clock-label">NO TIME LIMIT</span>
  const m = Math.floor(left / 60_000), s = Math.floor((left % 60_000) / 1000)
  return <span className="turf-clock"><Led value={`${m}:${String(s).padStart(2, '0')}`} tone={left < 5 * 60_000 ? 'red' : 'gold'} size={40} digits={5} /></span>
}


function Well({ g, stage, clock, people }: { g: TurfTv; stage: StageInfo; clock: Clock; people: Map<string, PlayerSummary> }) {
  const scale = useTimerScale()
  const cur = g.tokens[g.turn]
  const seat = cur?.seat ? people.get(cur.seat) : undefined
  const showTurn = cur && !SETUP.has(g.phase)
  const total = (g.phase === 'auction' && (g.auction?.bids ?? 0) > 0 ? 6_000 : DECISION_MS[g.phase] ?? 20_000) * scale
  return (
    <div className="well">
      <div className="well-head">
        {showTurn ? (
          <div className="turn">
            <Piece piece={cur.piece} color={cur.color} size={64} />
            <div><b>{cur.name.toUpperCase()}</b>{g.teams && seat && <small>{seat.name} rolls</small>}</div>
          </div>
        ) : <div className="turn"><b>HOME TURF</b></div>}
        <GameClock g={g} clock={clock} />
        {g.timed && (clock.deadline != null || clock.frozen != null) && <Timer deadline={stage.paused ? null : clock.deadline} frozen={stage.paused ? clock.frozen ?? 0 : clock.frozen} total={total} size={96} />}
      </div>
      <div className="well-body">
        <AnimatePresence mode="wait">
          <motion.div key={`${g.phase}-${g.phase === 'card' ? g.card?.text : ''}`} className="well-panel" initial={{ opacity: 0, y: 30 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -20 }} transition={{ duration: 0.25 }}>
            <PhasePanel g={g} people={people} />
          </motion.div>
        </AnimatePresence>
      </div>
      {!SETUP.has(g.phase) && <div className="ticker">{g.ticker.slice(-3).map((line, k, all) => <p key={line + k} className={k === all.length - 1 ? 'latest' : ''}>{line}</p>)}</div>}
    </div>
  )
}

function Dice({ g }: { g: TurfTv }) {
  if (!g.dice.length) return null
  return (
    <div className="dice">
      {g.dice.map((d, k) => (
        <motion.div key={`${k}-${d}-${g.doubles}`} initial={{ rotate: -200, y: -80, opacity: 0 }} animate={{ rotate: k % 2 ? 6 : -6, y: 0, opacity: 1 }} transition={{ type: 'spring', stiffness: 260, damping: 14, delay: k * 0.06 }}>
          <Die value={d} speed={k === 2} size={110} />
        </motion.div>
      ))}
    </div>
  )
}

function PhasePanel({ g, people }: { g: TurfTv; people: Map<string, PlayerSummary> }) {
  const cur = g.tokens[g.turn]
  const seatName = cur?.seat ? people.get(cur.seat)?.name ?? cur.name : cur?.name ?? ''
  switch (g.phase) {
    case 'teamup': return <TeamUp g={g} people={people} />
    case 'pieces': return <Pieces g={g} />
    case 'deal': return <DealPanel g={g} />
    case 'buy': return (
      <div className="stack-center">
        <DeedCard g={g} space={g.buy} />
        <p className="call">BUY IT FOR {money(g.board[g.buy]?.price ?? 0)}?</p>
        <p className="hint">or it goes to auction</p>
      </div>
    )
    case 'auction': return <AuctionPanel g={g} />
    case 'card': return g.card ? <CardPanel g={g} /> : null
    case 'debt': return <DebtPanel g={g} />
    case 'trade': return <TradePanel g={g} />
    case 'tally': return <TallyPanel g={g} />
    case 'choose': return (
      <div className="stack-center"><Dice g={g} /><p className="call">{g.choose === 'bus' ? 'BUS! PICK A MOVE' : 'TRIPLES! GO ANYWHERE'}</p><p className="hint">{seatName} is choosing</p></div>
    )
    case 'jail': return (
      <div className="stack-center"><Dice g={g} /><p className="call">IN TIMEOUT</p><p className="hint">Pay $50, use a card, or roll doubles</p></div>
    )
    case 'manage': return (
      <div className="stack-center">
        <Dice g={g} />
        <p className="call">BUILD, TRADE, OR END</p>
        <p className="hint">Bank: {g.housesLeft} houses · {g.hotelsLeft} hotels</p>
      </div>
    )
    case 'move': return <div className="stack-center"><Dice g={g} /></div>
    default: return (
      <div className="stack-center">
        <Neighborhood />
        <Dice g={g} />
        <p className="call">{g.doubles > 0 ? 'DOUBLES! ROLL AGAIN' : `${seatName.toUpperCase()} ROLLS`}</p>
      </div>
    )
  }
}

// ---- phase panels -----------------------------------------------------------------------------------

function DeedCard({ g, space, compact = false }: { g: TurfTv; space: number; compact?: boolean }) {
  const s = g.board[space]
  if (!s) return null
  const band = s.color ?? RIDE
  const owner = g.owner[space] ?? -1
  const rows: [string, string][] = s.kind === 'street'
    ? [['Rent', money(s.rent[0])], ['Whole set', money(s.rent[0] * 2)], ['1 house', money(s.rent[1])], ['2 houses', money(s.rent[2])], ['3 houses', money(s.rent[3])], ['Hotel', money(s.rent[5])]]
    : s.kind === 'railroad' ? [['1 ride', '$25'], ['2 rides', '$50'], ['3 rides', '$100'], ['All 4', '$200']]
      : [['One', '4 × dice'], ['Both', '10 × dice']]
  return (
    <Slam from={1.3} tilt={-3}>
      <div className={`deed-card ${compact ? 'compact' : ''}`}>
        <div className="deed-band" style={{ background: band, color: inkOn(band.startsWith('#') ? band : '#ffffff') }}>
          <small>{s.kind === 'street' ? 'PLACE' : s.kind === 'railroad' ? 'RIDE HOME' : 'UTILITY'}</small>
          <span>{s.name}</span>
        </div>
        {!compact && <div className="deed-rows">{rows.map(([k, v]) => <div key={k}><span>{k}</span><b>{v}</b></div>)}</div>}
        <div className="deed-foot">
          <span>{s.kind === 'street' ? `House ${money(s.houseCost)}` : owner >= 0 ? `Owner: ${g.tokens[owner].name}` : 'For sale'}</span>
          <b>{money(s.price)}</b>
        </div>
      </div>
    </Slam>
  )
}

function AuctionPanel({ g }: { g: TurfTv }) {
  const a = g.auction
  if (!a) return null
  const leader = a.leader >= 0 ? g.tokens[a.leader] : undefined
  return (
    <div className="auction">
      <DeedCard g={g} space={a.space} compact />
      <div className="auction-bid">
        <span className="label">AUCTION! TOP BID</span>
        <motion.div key={a.top} initial={{ scale: 1.5 }} animate={{ scale: 1 }} transition={{ type: 'spring', stiffness: 500, damping: 14 }}>
          <Led value={`$${a.top}`} tone="gold" size={72} digits={5} />
        </motion.div>
        {leader ? <div className="leader"><Piece piece={leader.piece} color={leader.color} size={56} /><b>{leader.name}</b></div> : <p className="hint">Nobody yet. Bid on your phone!</p>}
        <p className="hint">{a.bids} bid{a.bids === 1 ? '' : 's'} · each bid resets the clock</p>
      </div>
    </div>
  )
}

function CardPanel({ g }: { g: TurfTv }) {
  const c = g.card!
  const fill = c.deck === 'chance' ? C.sky : C.bubblegum
  return (
    <motion.div className="game-card" style={{ background: fill } as CSSProperties} initial={{ rotateY: 180, scale: 0.6 }} animate={{ rotateY: 0, scale: 1 }} transition={{ type: 'spring', stiffness: 180, damping: 16 }}>
      <span className="deck">{c.deckName.toUpperCase()}</span>
      <p>{c.text}</p>
      {g.drinks && c.sips > 0 && <span className="sips">DRINK {sipText(c.sips)}</span>}
    </motion.div>
  )
}

function DebtPanel({ g }: { g: TurfTv }) {
  const d = g.debt
  if (!d) return null
  const t = g.tokens[d.token]
  const to = d.to >= 0 ? g.tokens[d.to]?.name : d.to === -2 ? 'everyone' : 'the bank'
  return (
    <div className="stack-center">
      <Piece piece={t.piece} color={t.color} size={96} />
      <p className="call">{t.name.toUpperCase()} OWES {money(d.amount)}</p>
      <p className="hint">to {to} for {d.why}</p>
      <div className="debt-meter"><span>CASH</span><Led value={`$${t.cash}`} tone={t.cash >= d.amount ? 'green' : 'red'} size={52} digits={5} /></div>
      <p className="hint">Selling and mortgaging to cover it… or going bankrupt</p>
    </div>
  )
}

function Side({ g, token, deeds, cash, cards }: { g: TurfTv; token: number; deeds: number[]; cash: number; cards: number }) {
  const t = g.tokens[token]
  return (
    <div className="trade-side">
      <div className="trade-who"><Piece piece={t.piece} color={t.color} size={52} /><b>{t.name} gives</b></div>
      {deeds.map((s, k) => (
        <Deal key={s} i={k}>
          <span className="trade-deed" style={{ '--band': g.board[s].color ?? RIDE } as CSSProperties}>{g.board[s].name}{g.mortgaged.includes(s) ? ' (M)' : ''}</span>
        </Deal>
      ))}
      {cash > 0 && <span className="trade-cash">{money(cash)}</span>}
      {cards > 0 && <span className="trade-deed card">Get Out card ×{cards}</span>}
      {deeds.length === 0 && cash === 0 && cards === 0 && <span className="hint">nothing</span>}
    </div>
  )
}

function TradePanel({ g }: { g: TurfTv }) {
  const t = g.trade
  if (!t) return null
  return (
    <div className="trade">
      <p className="call">{t.counters > 0 ? `COUNTER-OFFER #${t.counters}` : 'TRADE OFFER!'}</p>
      <div className="trade-cols">
        <Side g={g} token={t.from} deeds={t.give} cash={t.giveCash} cards={t.giveCards} />
        <svg className="swap" viewBox="0 0 80 80" width="80" aria-hidden="true">
          <path d="M12 28 H64 M52 16 L66 28 L52 40 M68 52 H16 M28 40 L14 52 L28 64" fill="none" stroke="var(--ink)" strokeWidth="7" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        <Side g={g} token={t.to} deeds={t.get} cash={t.getCash} cards={t.getCards} />
      </div>
      <p className="hint">{g.tokens[t.to].name} decides on their phone. Heckle freely.</p>
    </div>
  )
}

function TallyPanel({ g }: { g: TurfTv }) {
  const rows = g.tally.slice().sort((a, b) => a.rank - b.rank)
  const max = Math.max(1, ...rows.map((r) => r.worth))
  useEffect(() => { const id = setTimeout(() => fireConfetti(true), 2600); return () => clearTimeout(id) }, [])
  return (
    <div className="tally">
      <p className="call">FINAL TALLY</p>
      {rows.map((r, k) => {
        const t = g.tokens[r.token]
        return (
          <Deal key={r.token} i={k}>
            <div className="tally-row">
              <Piece piece={t.piece} color={t.color} size={50} />
              <span className="name">{t.name}{r.rank === 1 && <Crown size={40} />}</span>
              <span className="bar"><motion.span style={{ background: t.color }} initial={{ width: 0 }} animate={{ width: `${(r.worth / max) * 100}%` }} transition={{ duration: 1.2, delay: 0.4 + k * 0.2 }} /></span>
              <b><CountUp from={0} to={r.worth} delay={400 + k * 200} duration={1200} /></b>
            </div>
          </Deal>
        )
      })}
      <p className="hint">Cash + places (half if mortgaged) + buildings at cost</p>
    </div>
  )
}

function TeamUp({ g, people }: { g: TurfTv; people: Map<string, PlayerSummary> }) {
  return (
    <div className="stack-center">
      <p className="call">TEAMS!</p>
      {g.notice && <p className="hint">{g.notice}</p>}
      <div className="teams">
        {g.tokens.map((t, i) => (
          <Deal key={i} i={i}>
            <div className="team" style={{ borderColor: t.color }}>
              <b style={{ background: t.color, color: inkOn(t.color) }}>{t.name}</b>
              <div className="faces">{t.members.map((id) => people.get(id)).filter((p): p is PlayerSummary => !!p).map((p) => (
                <span key={p.id}><Face face={p.avatar.face} color={p.avatar.color} size={38} />{p.name}</span>
              ))}</div>
            </div>
          </Deal>
        ))}
      </div>
      <p className="hint">Shuffle: S on the TV or the captain's phone</p>
    </div>
  )
}

function Pieces({ g }: { g: TurfTv }) {
  const all = ['cup', 'pizza', 'sneaker', 'boombox', 'cone', 'duck']
  return (
    <div className="stack-center">
      <p className="call">GRAB YOUR PIECE!</p>
      <div className="piece-grid">
        {all.map((p) => {
          const owner = g.tokens.find((t) => t.piece === p)
          return (
            <div key={p} className={`piece-slot ${owner ? 'taken' : 'free'}`}>
              {owner ? <Pop key={owner.name}><Piece piece={p} color={owner.color} size={96} /></Pop> : <Piece piece={p} color="var(--paper-2)" size={96} />}
              <span>{owner ? owner.name : PIECE_NAMES[p]}</span>
            </div>
          )
        })}
      </div>
      <p className="hint">First tap on your phone wins</p>
    </div>
  )
}

function DealPanel({ g }: { g: TurfTv }) {
  const dealt = g.tokens.map((_, i) => g.beats.filter((b) => b.kind === 'deal' && b.token === i).flatMap((b) => b.tokens))
  return (
    <div className="stack-center">
      <p className="call">STARTER PLACES</p>
      <div className="dealt">
        {g.tokens.map((t, i) => (
          <Deal key={i} i={i}>
            <div className="dealt-row">
              <Piece piece={t.piece} color={t.color} size={44} />
              <b>{t.name}</b>
              {dealt[i].map((s) => <span key={s} className="trade-deed" style={{ '--band': g.board[s].color ?? RIDE } as CSSProperties}>{g.board[s].name}</span>)}
            </div>
          </Deal>
        ))}
      </div>
      <p className="hint">Paid for out of everyone's $1,500</p>
    </div>
  )
}

