import { motion } from 'motion/react'
import { useEffect, useMemo, type CSSProperties } from 'react'
import type { PlayerSummary, PlayingCard, ScoreRow, StageInfo } from '../protocol'
import { sfx } from './audio'
import { Card } from './Card'
import { CheersIcon, DrinkBet, Led, MugIcon } from './Casino'
import { GameHeader, Podium, Tutorial } from './Shared'
import { AvatarDot, C, coinShower, fireConfetti, Neon, Pop, Slam, Stamp } from './Studio'
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
        <GameHeader title="DRUNK BLACKJACK" titleColor={C.gold} stage={stage} total={30_000} clock={clock} chips={[['HOW TO PLAY', C.cream], ['TAP “GOT IT” ON YOUR PHONE', C.pink]]} />
        <Tutorial cards={stage.tutorial.cards} acked={stage.tutorial.acked} players={players} accent={C.gold} card={'#5C0F2E'} />
      </div>
    )
  }
  const g = stage.game as unknown as BlackjackTv
  if (g.phase === 'podium') {
    return (
      <div className="stage-pad">
        <GameHeader title="DRUNK BLACKJACK" titleColor={C.gold} stage={stage} total={PODIUM_MS} clock={clock} chips={[['MOST SIPS HANDED OUT', C.cream]]} />
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
  const chips: [string, string][] = [[`HAND ${g.round} OF ${g.totalRounds}`, C.cream]]
  if (g.finalRound) chips.push(['LAST DEALER', C.pink])

  return (
    <>
      <div className="bj-table" />
      <div className="neon-sign" style={{ '--c': C.gold, left: 560, top: 190, fontSize: 110 } as CSSProperties}>♛</div>
      <div className="neon-sign" style={{ '--c': C.pink, left: 1330, top: 200, fontSize: 96, animationDelay: '2s' } as CSSProperties}>⚡</div>
      <FeltPrint seats={layout.pos} cardW={layout.card} showText={g.phase === 'play'} />
      <div className="bj-shoe" />
      <div className="stage-pad" style={{ pointerEvents: 'none' }}>
        <GameHeader title="DRUNK BLACKJACK" titleColor={C.gold} stage={stage} total={total} clock={clock} chips={chips} status={status} />
      </div>
      <RuleCard key={`${g.round}-${g.rule}`} g={g} />
      <div className="bj-dealer" style={{ top: 170 }}>
        <Dealer key={g.dealerId} mood={dealerBust ? 'bust' : g.phase === 'settle' ? 'smug' : 'idle'} name={who} emoji={g.dealerAvatar?.emoji} color={g.dealerAvatar?.color} />
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 10 }}>
          <div className="bj-dealer-cards">
            {g.dealer.map((c, i) => (
              <Card key={i} card={c} width={118} tilt={i % 2 ? 3 : -3} from={{ x: SHOE.x - DEALER_POS.x - i * 70, y: SHOE.y - DEALER_POS.y }} delay={g.phase === 'play' && i < 2 ? dealDelay(n, i) : 0} />
            ))}
          </div>
          {g.dealer.length > 0 && <Led value={g.dealerTotal ?? dealerTotal} tone={dealerBust ? 'red' : 'gold'} size={46} />}
        </div>
        {g.phase !== 'settle' && (
          <div className="on-the-line">
            <small>ON THE LINE</small>
            <div className="row" style={{ gap: 10 }}><MugIcon size={40} /><Led value={g.onTheLine} tone="red" size={40} /></div>
            <small style={{ color: '#fff4d6aa' }}>IF {who} BUSTS</small>
          </div>
        )}
      </div>
      {g.phase === 'bet' && <div className="bj-banner" style={{ top: 520 }}><Slam from={2}><Neon text={`BET AGAINST ${who}`} lit color={C.gold} className="breathe" /></Slam></div>}
      {g.phase === 'dealer' && <div className="bj-banner" style={{ top: 520 }}><Slam key="dealer" from={2}><Neon text={`${who} IS PLAYING`} lit color={C.pink} className="breathe" /></Slam></div>}
      {g.phase === 'settle' && (
        <div className="bj-banner" style={{ top: dealerBust ? 400 : 520 }}>
          {dealerBust ? (
            <Slam from={3} delay={0.1} tilt={-10}>
              <div className="dealer-bust">
                <span className="marquee-font">{who} BUSTS!</span>
                <b><MugIcon size={64} /> DRINK {sipLabel(g.dealerDrinks)}</b>
              </div>
            </Slam>
          ) : (
            <Slam from={2.4} delay={0.1}>
              <Neon text={g.dealerDrinks > 0 ? `${who} HAS ${dealerTotal} · DRINKS ${sipLabel(g.dealerDrinks)}` : `${who} HAS ${dealerTotal}`} lit color={g.dealerDrinks > 0 ? C.mint : C.red} />
            </Slam>
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
          <span className="bj-call" style={{ fontSize: callSize, background: drinks > 0 ? '#FF5A5A' : drinks < 0 ? '#FFD23F' : '#FFF4D6', color: '#0B0716' }}>
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
        <AvatarDot emoji={s.avatar.emoji} color={s.avatar.color} size={cardW * 0.44} dim={phase === 'bet' && s.status === 'betting'} />
        <span style={{ maxWidth: cardW * 1.7, overflow: 'hidden', textOverflow: 'ellipsis' }}>{s.name}</span>
        {(s.status !== 'betting' || phase !== 'bet') && s.bet > 0 && (
          <Pop key={s.bet}><span className="row" style={{ gap: 4 }}><DrinkBet sips={s.bet} size={cardW * 0.38} />{s.doubled && <span className="bet-amt" style={{ fontSize: cardW * 0.22 }}>×2</span>}</span></Pop>
        )}
      </div>
      {s.status === 'bust' && <div style={{ position: 'absolute', top: cardW * 0.35, zIndex: 4 }}><Stamp text="BUST" color="#FF5A5A" size={cardW * 0.4} delay={0.2} /></div>}
      {s.status === 'blackjack' && <div style={{ position: 'absolute', top: cardW * 0.35, zIndex: 4 }}><Stamp text="BLACKJACK" color="#FFD23F" size={cardW * 0.32} delay={1.1} tilt={8} /></div>}
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
        <g fontFamily="Bungee" textAnchor="middle">
          <text fontSize="30" fill="#FFD23F" fillOpacity="0.4" letterSpacing="8"><textPath href="#felt-arc" startOffset="50%">BLACKJACK PAYS 3 TO 2</textPath></text>
          <text fontSize="19" fill="#FFF4D6" fillOpacity="0.28" letterSpacing="5"><textPath href="#felt-arc2" startOffset="50%">DEALER STANDS ON ALL 17 · INSURANCE IS FOR COWARDS</textPath></text>
        </g>
      )}
      {seats.map((p, i) => (
        <g key={i}>
          <ellipse cx={p.x} cy={p.y - cardW * 0.1} rx={cardW * 1.15} ry={cardW * 0.95} fill="#0006" opacity="0.18" />
          <ellipse cx={p.x} cy={p.y - cardW * 0.1} rx={cardW * 1.15} ry={cardW * 0.95} fill="none" stroke="#FFD23F" strokeOpacity="0.28" strokeWidth="3" strokeDasharray="10 8" />
        </g>
      ))}
    </svg>
  )
}

function RuleCard({ g }: { g: BlackjackTv }) {
  return (
    <motion.div className="bj-rule" initial={{ x: -520, rotate: -12 }} animate={{ x: 0, rotate: -3 }} transition={{ type: 'spring', stiffness: 220, damping: 16, delay: 0.3 }}>
      <small>HOUSE RULE</small>
      <h3>{g.ruleName}</h3>
      <p>{g.ruleText}</p>
    </motion.div>
  )
}

/** This hand's dealer: the player's avatar wearing the green visor and bow tie. Sweats and Xes out on a bust. */
function Dealer({ mood, name, emoji = '🎩', color = '#FFE7B8' }: { mood: 'idle' | 'bust' | 'smug'; name: string; emoji?: string; color?: string }) {
  return (
    <motion.div className="dealer-badge" initial={{ y: -260, rotate: -20 }} animate={{ y: 0, rotate: 0 }} transition={{ type: 'spring', stiffness: 260, damping: 14 }}>
      <motion.svg width="200" height="210" viewBox="0 0 200 210" animate={{ y: [0, -6, 0] }} transition={{ duration: 2.2, repeat: Infinity, ease: 'easeInOut' }}>
        <path d="M34 210 Q34 150 100 150 Q166 150 166 210 Z" fill="#17121F" />
        <path d="M83 152 L100 176 L117 152 Z" fill="#FFF4D6" />
        <path d="M78 160 L100 171 L78 182 Z M122 160 L100 171 L122 182 Z" fill="#FF5A5A" stroke="#0B0716" strokeWidth="2" /><circle cx="100" cy="171" r="5" fill="#b8323f" />
        <circle cx="100" cy="90" r="66" fill={color} stroke="#0B0716" strokeWidth="5" />
        <text x="100" y="116" textAnchor="middle" fontSize="74" style={{ filter: mood === 'bust' ? 'grayscale(.4)' : undefined }}>{emoji}</text>
        <path d="M30 64 Q100 16 170 64 L156 78 Q100 50 44 78 Z" fill="#3DDC97" fillOpacity="0.88" stroke="#0B0716" strokeWidth="4" />
        <rect x="33" y="70" width="134" height="10" rx="5" fill="#0E5A3A" />
        {mood === 'bust' && <motion.path d="M168 76 Q177 92 168 100 Q159 92 168 76 Z" fill="#2EC4F1" stroke="#0B0716" strokeWidth="2" animate={{ y: [0, 34], opacity: [1, 0] }} transition={{ duration: 1.1, repeat: Infinity }} />}
        {mood === 'smug' && <text x="160" y="40" fontSize="34">😏</text>}
      </motion.svg>
      <span className="dealer-name">{name} DEALS</span>
    </motion.div>
  )
}

export { Pop }
