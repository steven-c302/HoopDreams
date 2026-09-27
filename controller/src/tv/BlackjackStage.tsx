import { motion } from 'motion/react'
import { useEffect, useMemo, type CSSProperties } from 'react'
import type { PlayerSummary, PlayingCard, ScoreRow, StageInfo } from '../protocol'
import { sfx } from './audio'
import { Card } from './Card'
import { CheersIcon, DrinkBet, Led, MugIcon } from './Casino'
import { GameHeader, Podium, Tutorial } from './Shared'
import { Face } from '../theme/Face'
import { AvatarFace, C, Panel, Pop, Slam, Stamp, coinShower, fireConfetti } from './toon'
import { sipLabel, type BjSeat, type BlackjackTv } from './types'

const BET_MS = 15_000, PLAY_MS = 25_000, DEALER_MS = 30_000, SETTLE_MS = 10_000, PODIUM_MS = 15_000
const SHOE = { x: 1620, y: 250 }
const DEALER_POS = { x: 960, y: 250 }

const value = (c: PlayingCard) => (c.rank === 1 ? 11 : c.rank >= 10 ? 10 : c.rank)
export function bjTotal(cards: PlayingCard[]) {
  let t = cards.filter((c) => c.rank > 0).reduce((s, c) => s + value(c), 0)
  let aces = cards.filter((c) => c.rank === 1).length
  while (t > 21 && aces > 0) { t -= 10; aces-- }
  return t
}

/** Seat centres along the table's smile; a second, inner arc when the table is crowded. */
function seatLayout(n: number) {
  const place = (count: number, rx: number, ry: number, cy: number, a0: number, a1: number) => // seats stay clear of the screen edges

    Array.from({ length: count }, (_, i) => {
      const a = ((count === 1 ? (a0 + a1) / 2 : a0 + ((a1 - a0) * i) / (count - 1)) * Math.PI) / 180
      return { x: 960 + rx * Math.cos(a), y: cy + ry * Math.sin(a) }
    })
  if (n <= 8) return { pos: place(n, 760, 520, 420, 150, 30), card: n <= 5 ? 104 : 88 }
  const outer = Math.ceil(n / 2)
  return { pos: [...place(outer, 840, 540, 430, 164, 16), ...place(n - outer, 470, 300, 430, 150, 30)], card: 66 }
}

export function BlackjackStage({ stage, players, scores, clock }: { stage: StageInfo; players: PlayerSummary[]; scores: ScoreRow[]; clock: { deadline: number | null; frozen: number | null } }) {
  if (stage.tutorial) {
    return (
      <div className="stage-pad">
        <GameHeader title="Drunk Blackjack" stage={stage} total={30_000} clock={clock} chips={[['HOW TO PLAY', C.paper]]} />
        <Tutorial cards={stage.tutorial.cards} acked={stage.tutorial.acked} players={players} />
      </div>
    )
  }
  const g = stage.game as unknown as BlackjackTv
  if (g.phase === 'podium') {
    return (
      <div className="stage-pad">
        <GameHeader title="Drunk Blackjack" stage={stage} total={PODIUM_MS} clock={clock} chips={[['MOST SIPS HANDED OUT', C.paper]]} />
        <Podium scores={scores} unit=" sips" />
      </div>
    )
  }
  return <Table g={g} stage={stage} clock={clock} />
}

function Table({ g, stage, clock }: { g: BlackjackTv; stage: StageInfo; clock: { deadline: number | null; frozen: number | null } }) {
  const layout = useMemo(() => seatLayout(g.seats.length), [g.seats.length])
  const n = g.seats.length
  const step = Math.min(0.13, 2.6 / (2 * n + 2))
  const dealDelay = (seatIndex: number, cardIndex: number) => (cardIndex < 2 ? (cardIndex * (n + 1) + seatIndex) * step + 0.25 : 0)

  // Deal sounds in the same order as the cards fly.
  useEffect(() => {
    if (g.phase !== 'play') return
    const ids: ReturnType<typeof setTimeout>[] = []
    for (let k = 0; k < 2; k++) for (let i = 0; i <= n; i++) ids.push(setTimeout(() => sfx.cardFlick(i + k), dealDelay(i, k) * 1000))
    return () => ids.forEach(clearTimeout)
  }, [stage.phaseSeq]) // eslint-disable-line react-hooks/exhaustive-deps

  const dealerTotal = bjTotal(g.dealer)
  const dealerBust = dealerTotal > 21 && g.phase !== 'play'
  useSettleSounds(g, stage.phaseSeq)

  const who = g.dealerName.toUpperCase()
  const status = g.phase === 'bet' ? `${g.submitted}/${g.expected} BETS IN` : g.phase === 'play' ? `${g.submitted}/${g.expected} DONE` : null
  const total = { bet: BET_MS, play: PLAY_MS, dealer: DEALER_MS, settle: SETTLE_MS, podium: PODIUM_MS }[g.phase]
  const chips: [string, string][] = [[`HAND ${g.round} OF ${g.totalRounds}`, C.paper]]
  if (g.finalRound) chips.push(['LAST DEALER', C.sun])

  return (
    <>
      <div className="bj-table" />
      <FeltPrint seats={layout.pos} cardW={layout.card} showText={g.phase === 'play'} />
      <div className="bj-shoe" />
      <div className="stage-pad" style={{ pointerEvents: 'none' }}>
        <GameHeader title="Drunk Blackjack" stage={stage} total={total} clock={clock} chips={chips} status={status} />
      </div>
      <RuleCard key={`${g.round}-${g.rule}`} g={g} />
      <div className="bj-dealer" style={{ top: 170 }}>
        <Dealer key={g.dealerId} mood={dealerBust ? 'bust' : g.phase === 'settle' ? 'smug' : 'idle'} name={who} face={g.dealerAvatar?.face} color={g.dealerAvatar?.color} />
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 10 }}>
          <div className="bj-dealer-cards">
            {g.dealer.map((c, i) => (
              <Card key={i} card={c} width={118} tilt={i % 2 ? 3 : -3} from={{ x: SHOE.x - DEALER_POS.x - i * 70, y: SHOE.y - DEALER_POS.y }} delay={g.phase === 'play' && i < 2 ? dealDelay(n, i) : 0} />
            ))}
          </div>
          {g.dealer.length > 0 && <Led value={g.dealerTotal ?? dealerTotal} tone={dealerBust ? 'red' : 'gold'} size={46} />}
        </div>
        {g.phase !== 'settle' && (
          <Panel className="on-the-line" fill={C.paper} tilt={2}>
            <small>ON THE LINE</small>
            <div className="row" style={{ gap: 10 }}><MugIcon size={40} /><Led value={g.onTheLine} tone="red" size={40} /></div>
            <small>IF {who} BUSTS</small>
          </Panel>
        )}
      </div>
      {g.phase === 'bet' && <div className="bj-banner" style={{ top: 520 }}><Banner text={`BET AGAINST ${who}`} tone="brass" /></div>}
      {g.phase === 'dealer' && <div className="bj-banner" style={{ top: 520 }}><Banner key="dealer" text={`${who} IS PLAYING`} tone="dark" /></div>}
      {g.phase === 'settle' && (
        <div className="bj-banner" style={{ top: dealerBust ? 400 : 520 }}>
          {dealerBust ? (
            <Slam from={1.6} tilt={-3} delay={0.1}><div className="bj-sign bust"><span>{who} BUSTS!</span><small>DRINK {sipLabel(g.dealerDrinks)}</small></div></Slam>
          ) : (
            <Banner text={g.dealerDrinks > 0 ? `${who} HAS ${dealerTotal}: DRINKS ${sipLabel(g.dealerDrinks)}` : `${who} HAS ${dealerTotal}`} tone={g.dealerDrinks > 0 ? 'brass' : 'ivory'} />
          )}
        </div>
      )}
      {g.seats.map((s, i) => (
        <Seat key={s.id} s={s} phase={g.phase} pos={layout.pos[i]} cardW={layout.card} delayFor={(k) => (g.phase === 'play' ? dealDelay(i, k) : 0)} />
      ))}
    </>
  )
}

function useSettleSounds(g: BlackjackTv, seq: number) {
  useEffect(() => {
    if (g.phase !== 'settle') return
    const ids: ReturnType<typeof setTimeout>[] = []
    const bust = bjTotal(g.dealer) > 21
    if (bust) ids.push(setTimeout(() => { sfx.bust(); sfx.ooh(Math.min(6, g.seats.length)) }, 100), setTimeout(() => { sfx.laugh(); sfx.applause(2.4); fireConfetti(true) }, 900))
    else ids.push(setTimeout(() => (g.dealerDrinks > 0 ? sfx.winSting() : sfx.loseSting()), 150))
    if (g.seats.some((s) => s.outcome === 'blackjack')) ids.push(setTimeout(() => { sfx.jackpot(); coinShower() }, 500))
    g.seats.forEach((_, i) => ids.push(setTimeout(() => sfx.pop(), 900 + i * 60)))
    return () => ids.forEach(clearTimeout)
  }, [seq]) // eslint-disable-line react-hooks/exhaustive-deps
}

function Seat({ s, phase, pos, cardW, delayFor }: { s: BjSeat; phase: BlackjackTv['phase']; pos: { x: number; y: number }; cardW: number; delayFor: (k: number) => number }) {
  const deciding = phase === 'play' && s.status === 'playing'
  const settled = phase === 'settle' && s.outcome
  const drinks = s.drinks ?? 0
  const callSize = Math.round(cardW * 0.25)
  return (
    <div className="bj-seat" style={{ left: pos.x, top: pos.y }}>
      {settled && (
        <Pop delay={0.9} style={{ position: 'absolute', top: -cardW * 0.58, zIndex: 6 }}>
          <span className="bj-call" style={{ fontSize: callSize, background: drinks > 0 ? C.tomato : drinks < 0 ? C.sun : C.paper, color: drinks > 0 ? C.white : C.ink }}>
            {drinks > 0 ? <MugIcon size={callSize * 1.25} /> : drinks < 0 ? <CheersIcon size={callSize * 1.25} /> : null}
            <b>{drinks > 0 ? `DRINK ${sipLabel(drinks)}` : drinks < 0 ? `DEALER +${-drinks}` : 'SAFE'}</b>
          </span>
        </Pop>
      )}
      <div className="bj-hand" style={{ '--overlap': `${-cardW * 0.6}px`, height: cardW * 1.4 } as CSSProperties}>
        {s.cards.map((c, k) => (
          <Card key={k} card={c} width={cardW} tilt={(k - (s.cards.length - 1) / 2) * 5} delay={delayFor(k)}
            from={{ x: SHOE.x - pos.x - k * cardW * 0.4, y: SHOE.y - pos.y }} />
        ))}
      </div>
      {s.cards.length > 0 && <Led value={s.total} tone={s.total > 21 ? 'red' : s.total === 21 ? 'gold' : 'green'} size={cardW * 0.3} />}
      <div className={`bj-plate ${deciding ? 'turn' : ''}`} style={{ fontSize: cardW * 0.28 }}>
        <AvatarFace avatar={s.avatar} size={cardW * 0.44} dim={phase === 'bet' && s.status === 'betting'} />
        <span style={{ maxWidth: cardW * 1.7, overflow: 'hidden', textOverflow: 'ellipsis' }}>{s.name}</span>
        {(s.status !== 'betting' || phase !== 'bet') && s.bet > 0 && (
          <Pop key={s.bet}><span className="row" style={{ gap: 4 }}><DrinkBet sips={s.bet} size={cardW * 0.38} />{s.doubled && <span className="bet-amt" style={{ fontSize: cardW * 0.22 }}>×2</span>}</span></Pop>
        )}
      </div>
      {s.status === 'bust' && <div style={{ position: 'absolute', top: cardW * 0.35, zIndex: 4 }}><Stamp text="BUST" color={C.tomato} size={cardW * 0.4} delay={0.2} /></div>}
      {s.status === 'blackjack' && <div style={{ position: 'absolute', top: cardW * 0.35, zIndex: 4 }}><Stamp text="BLACKJACK" color={C.grape} size={cardW * 0.32} delay={1.1} tilt={8} /></div>}
    </div>
  )
}

/** Printed on the felt: the rules along an arc, and a betting circle at every seat. */
function FeltPrint({ seats, cardW, showText }: { seats: { x: number; y: number }[]; cardW: number; showText: boolean }) {
  return (
    <svg className="felt-print" viewBox="0 0 1920 1080">
      <defs>
        <path id="felt-arc" d="M 520 520 Q 960 760 1400 520" />
        <path id="felt-arc2" d="M 560 575 Q 960 800 1360 575" />
      </defs>
      {showText && (
        <g fontFamily="Rammetto One" textAnchor="middle">
          <text fontSize="30" fill="var(--sun)" fillOpacity="0.6" letterSpacing="6"><textPath href="#felt-arc" startOffset="50%">BLACKJACK PAYS 3 TO 2</textPath></text>
          <text fontSize="19" fill="var(--paper)" fillOpacity="0.45" letterSpacing="4"><textPath href="#felt-arc2" startOffset="50%">DEALER STANDS ON ALL 17. INSURANCE IS FOR COWARDS</textPath></text>
        </g>
      )}
      {seats.map((p, i) => (
        <g key={i}>
          <ellipse cx={p.x} cy={p.y - cardW * 0.1} rx={cardW * 1.15} ry={cardW * 0.95} fill="none" stroke="var(--paper)" strokeOpacity="0.45" strokeWidth="5" strokeDasharray="14 10" />
        </g>
      ))}
    </svg>
  )
}

function RuleCard({ g }: { g: BlackjackTv }) {
  return (
    <motion.div className="bj-rule panel" style={{ background: C.paper, boxShadow: 'var(--shadow-tv) var(--shadow-tv) 0 var(--ink)' }} initial={{ x: -520, rotate: -12 }} animate={{ x: 0, rotate: -3 }} transition={{ type: 'spring', stiffness: 220, damping: 16, delay: 0.3 }}>
      <small>HOUSE RULE</small>
      <h3>{g.ruleName}</h3>
      <p>{g.ruleText}</p>
    </motion.div>
  )
}

/** A brass-framed table sign that drops onto the felt. */
function Banner({ text, tone }: { text: string; tone: 'brass' | 'dark' | 'ivory' }) {
  return (
    <Slam from={1.4} tilt={-2}>
      <div className={`bj-sign ${tone}`}>{text}</div>
    </Slam>
  )
}

/** This hand's dealer: the player's own face under a green visor, with a bow tie. Sweats on a bust. */
function Dealer({ mood, name, face = 'p:03', color = 'var(--paper)' }: { mood: 'idle' | 'bust' | 'smug'; name: string; face?: string; color?: string }) {
  return (
    <motion.div className="dealer-badge" initial={{ y: -260, rotate: -20 }} animate={{ y: 0, rotate: mood === 'bust' ? -8 : 0 }} transition={{ type: 'spring', stiffness: 260, damping: 14 }}>
      <motion.div style={{ position: 'relative', width: 200, height: 210 }} animate={{ y: [0, -6, 0] }} transition={{ duration: 2.2, repeat: Infinity, ease: 'easeInOut' }}>
        <svg width="200" height="210" viewBox="0 0 200 210" style={{ position: 'absolute', inset: 0 }} aria-hidden="true">
          <path d="M34 210 Q34 150 100 150 Q166 150 166 210 Z" fill={C.ink} />
          <path d="M83 152 L100 176 L117 152 Z" fill={C.paper} />
          <path d="M76 160 L100 171 L76 182 Z M124 160 L100 171 L124 182 Z" fill={C.tomato} stroke={C.ink} strokeWidth="4" strokeLinejoin="round" />
        </svg>
        <Face face={face} color={color} size={136} style={{ position: 'absolute', left: 32, top: 18 }} />
        <svg width="200" height="210" viewBox="0 0 200 210" style={{ position: 'absolute', inset: 0 }} aria-hidden="true">
          <path d="M30 58 Q100 8 170 58 L158 74 Q100 46 42 74 Z" fill={C.lime} stroke={C.ink} strokeWidth="5" strokeLinejoin="round" />
          {mood === 'bust' && <motion.path d="M170 80 Q180 96 170 104 Q160 96 170 80 Z" fill={C.sky} stroke={C.ink} strokeWidth="3" animate={{ y: [0, 34], opacity: [1, 0] }} transition={{ duration: 1.1, repeat: Infinity }} />}
        </svg>
        {mood === 'smug' && <Pop style={{ position: 'absolute', right: -70, top: 6 }}><span className="bj-call" style={{ position: 'static', fontSize: 30, background: C.paper }}>HA!</span></Pop>}
      </motion.div>
      <span className="dealer-name">{name} DEALS</span>
    </motion.div>
  )
}

export { Pop }
