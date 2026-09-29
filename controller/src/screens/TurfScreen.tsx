import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import type { ActionPayload, TurfDeed, TurfDeedRef, TurfScreen as TurfView } from '../protocol'
import { Led } from '../tv/Casino'
import { Die, Piece, PIECE_NAMES } from '../tv/TurfArt'
import './turf-phone.css'

const buzz = (ms: number | number[]) => { try { navigator.vibrate?.(ms) } catch { /* not supported */ } }
const money = (n: number) => `$${n.toLocaleString()}`
const RIDE = '#2B2B2B'
/** Prompts that need this phone to do something: switch to the Now tab and buzz when one arrives. */
const ACTIONABLE = new Set(['roll', 'jail', 'buy', 'bid', 'bus', 'triples', 'manage', 'debt', 'trade', 'pieces'])
const GROUP_NAMES = ['Brown', 'Light blue', 'Pink', 'Orange', 'Red', 'Yellow', 'Green', 'Dark blue', 'Rides home', 'Utilities']

type Tab = 'now' | 'places' | 'trade'
/** A trade being put together on this phone; survives other players' turns (Play keeps this screen mounted). */
interface Draft { to: number; give: number[]; get: number[]; giveCash: number; getCash: number; giveCards: number; getCards: number; counter?: number }

interface Props { screen: TurfView; disabled: boolean; onAction(p: ActionPayload): void }

/** Prompts where this phone is the one deciding (solo players always hold their own seat, so "mine" alone isn't a turn). */
const MY_MOVE = new Set(['roll', 'jail', 'buy', 'bid', 'bus', 'triples', 'manage', 'debt', 'trade', 'pieces'])

export function TurfScreen({ screen, disabled, onAction }: Props) {
  const [tab, setTab] = useState<Tab>('now')
  const [draft, setDraft] = useState<Draft | null>(null)
  const p = screen.prompt
  const me = screen.me

  // A new decision pulls you back to Now with a buzz.
  const lastKind = useRef(p.kind)
  useEffect(() => {
    if (p.kind !== lastKind.current && ACTIONABLE.has(p.kind)) {
      if (p.kind !== 'bid' || tab !== 'trade') setTab('now')
      buzz(p.kind === 'debt' ? [60, 60, 60] : [30, 40, 30])
    }
    lastKind.current = p.kind
  }, [p.kind]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (screen.drink) buzz([80, 60, 80, 60, 80]) }, [screen.drink])

  const act = (payload: ActionPayload) => { buzz(25); onAction(payload) }
  const incoming = screen.trade?.role === 'to' && p.kind === 'trade'
  const buildable = screen.deeds.filter((d) => d.build != null && (me?.cash ?? 0) >= d.build).length

  return (
    <div className="turf-phone" style={{ '--me': me?.color ?? 'var(--paper)' } as CSSProperties}>
      {me && <TokenBand me={me} kind={screen.prompt.kind} />}
      {screen.drink && <div className="turf-drink" key={screen.drink}><b>DRINK!</b><span>{screen.drink}</span></div>}
      <div className="turf-tab-body">
        {tab === 'now' && <NowTab screen={screen} disabled={disabled} act={act} goTo={setTab} onCounter={() => { setDraft(counterDraft(screen)); setTab('trade') }} />}
        {tab === 'places' && <PlacesTab screen={screen} disabled={disabled} act={act} />}
        {tab === 'trade' && <TradeTab screen={screen} disabled={disabled} act={act} draft={draft} setDraft={setDraft} />}
      </div>
      {me && !me.bankrupt && (
        <nav className="turf-tabs">
          <button className={tab === 'now' ? 'on' : ''} onClick={() => setTab('now')}>NOW{ACTIONABLE.has(p.kind) && tab !== 'now' && <i className="dot" />}</button>
          <button className={tab === 'places' ? 'on' : ''} onClick={() => setTab('places')}>MY PLACES{buildable > 0 && <i className="count">{buildable}</i>}</button>
          <button className={tab === 'trade' ? 'on' : ''} onClick={() => setTab('trade')}>TRADE{screen.trade && <i className="dot" />}</button>
        </nav>
      )}
      {incoming && tab !== 'now' && (
        <div className="sheet-scrim"><div className="sheet"><TradeOffer screen={screen} disabled={disabled} act={act} onCounter={() => { setDraft(counterDraft(screen)); setTab('trade') }} /></div></div>
      )}
    </div>
  )
}

function TokenBand({ me, kind }: { me: NonNullable<TurfView['me']>; kind: string }) {
  return (
    <div className="turf-band">
      <Piece piece={me.piece} color={me.color} size={48} />
      <div className="who">
        <b>{me.name}</b>
        <small>{me.bankrupt ? 'Out of the game' : me.mine ? (MY_MOVE.has(kind) ? 'Your move!' : `On ${me.spaceName}`) : me.seatName ? `${me.seatName} has the dice` : `On ${me.spaceName}`}</small>
      </div>
      <div className="cash"><Led value={`$${me.cash}`} tone={me.cash < 100 ? 'red' : 'gold'} size={20} digits={5} /></div>
      {me.jailed && <span className="tag jail">TIMEOUT</span>}
      {me.jailCards > 0 && <span className="tag card">GET OUT ×{me.jailCards}</span>}
    </div>
  )
}

// ---- Now: the one thing to do ----------------------------------------------------------------------

function NowTab({ screen, disabled, act, goTo, onCounter }: { screen: TurfView; disabled: boolean; act(p: ActionPayload): void; goTo(t: Tab): void; onCounter(): void }) {
  const p = screen.prompt
  const me = screen.me
  switch (p.kind) {
    case 'roll': return (
      <Card title={p.title} detail={p.detail} tone="go">
        <button className="turf-roll" disabled={disabled} onClick={() => act({ kind: 'roll' })}>
          <span className="dice"><Die value={5} size={64} /><Die value={2} size={64} /></span>
          ROLL
        </button>
        <QuickLinks screen={screen} goTo={goTo} />
      </Card>
    )
    case 'jail': return (
      <Card title={p.title} detail={p.detail}>
        <div className="turf-keys">{p.actions.map((a) => (
          <button key={a.id} className={`turf-key ${a.id}`} disabled={disabled || (a.id === 'pay' && (me?.cash ?? 0) < 50)} onClick={() => act({ kind: 'jail', option: a.id })}>
            {a.text}{a.detail && <small>{a.detail}</small>}
          </button>
        ))}</div>
        <QuickLinks screen={screen} goTo={goTo} />
      </Card>
    )
    case 'buy': return (
      <Card title={p.title} detail={p.detail}>
        <div className="turf-keys">{p.actions.map((a) => (
          <button key={a.id} className={`turf-key ${a.id === 'buy' ? 'buy' : 'pass'}`} disabled={disabled} onClick={() => act({ kind: 'buy', option: a.id })}>{a.text}</button>
        ))}</div>
        <QuickLinks screen={screen} goTo={goTo} />
      </Card>
    )
    case 'bid': return <BidPad screen={screen} disabled={disabled} act={act} />
    case 'bus': return (
      <Card title={p.title} detail={p.detail} tone="go">
        <div className="turf-keys">{p.actions.map((a) => <button key={a.id} className="turf-key go" disabled={disabled} onClick={() => act({ kind: 'choose', option: a.id })}>{a.text}</button>)}</div>
      </Card>
    )
    case 'triples': return (
      <Card title={p.title} detail={p.detail} tone="go">
        <div className="turf-spaces">{p.actions.map((a) => (
          <button key={a.id} disabled={disabled} onClick={() => act({ kind: 'choose', option: a.id })}>
            <i style={{ background: a.color ?? 'var(--paper-2)' }} /><span>{a.text}</span>{a.detail && <small>{a.detail}</small>}
          </button>
        ))}</div>
      </Card>
    )
    case 'manage': return (
      <Card title={p.title} detail={p.detail}>
        <QuickLinks screen={screen} goTo={goTo} big />
        <button className="turf-key end" disabled={disabled} onClick={() => act({ kind: 'end' })}>END TURN</button>
      </Card>
    )
    case 'debt': return (
      <Card title={p.title} detail={p.detail} tone="lose">
        <button className="turf-key raise" onClick={() => goTo('places')}>SELL OR MORTGAGE</button>
        {p.actions.some((a) => a.id === 'pay') && <button className="turf-key buy" disabled={disabled} onClick={() => act({ kind: 'pay' })}>PAY {money(p.amount)}</button>}
        {screen.canTrade && <button className="turf-key pass" onClick={() => goTo('trade')}>SELL A PLACE TO SOMEONE</button>}
        <HoldButton disabled={disabled} onDone={() => act({ kind: 'bankrupt' })}>HOLD TO GO BANKRUPT</HoldButton>
      </Card>
    )
    case 'trade': return <Card title={p.title}><TradeOffer screen={screen} disabled={disabled} act={act} onCounter={onCounter} /></Card>
    case 'pieces': return (
      <Card title={p.title} detail={p.detail} tone="go">
        <div className="turf-pieces">{screen.pieces.map((c) => (
          <button key={c.id} disabled={disabled} onClick={() => act({ kind: 'piece', option: c.id })}>
            <Piece piece={c.id} color={screen.me?.color ?? 'var(--paper)'} size={76} /><span>{PIECE_NAMES[c.id] ?? c.text}</span>
          </button>
        ))}</div>
      </Card>
    )
    case 'card': return (
      <div className={`turf-gamecard ${p.title === 'Plot Twist' ? 'twist' : 'chat'}`}><b>{p.title.toUpperCase()}</b><p>{p.detail}</p></div>
    )
    default: {
      const cancel = p.actions.find((a) => a.id === 'cancel')
      return (
        <Card title={p.title} detail={p.detail} tone={p.tone === 'win' ? 'win' : p.tone === 'lose' || p.kind === 'out' ? 'lose' : undefined}>
          {cancel && screen.trade && <button className="turf-key pass" disabled={disabled} onClick={() => act({ kind: 'tradeCancel', trade: screen.trade!.id })}>{cancel.text}</button>}
          {p.kind === 'wait' && screen.canTrade && <button className="turf-key pass" onClick={() => goTo('trade')}>MAKE A TRADE</button>}
          {me && !me.bankrupt && p.kind !== 'over' && <p className="turf-worth">Worth {money(me.worth)}</p>}
        </Card>
      )
    }
  }
}

function Card({ title, detail, tone, children }: { title: string; detail?: string; tone?: 'go' | 'win' | 'lose'; children?: ReactNode }) {
  return (
    <div className={`turf-card-phone ${tone ?? ''}`}>
      <h1>{title}</h1>
      {detail && <p>{detail}</p>}
      {children}
    </div>
  )
}

function QuickLinks({ screen, goTo, big = false }: { screen: TurfView; goTo(t: Tab): void; big?: boolean }) {
  const cash = screen.me?.cash ?? 0
  const buildable = screen.deeds.filter((d) => d.build != null && cash >= d.build).length
  if (!buildable && !screen.canTrade && !big) return null
  return (
    <div className="turf-links">
      {buildable > 0 && <button onClick={() => goTo('places')}>BUILD ({buildable})</button>}
      {big && buildable === 0 && <button onClick={() => goTo('places')}>MY PLACES</button>}
      {screen.canTrade && <button onClick={() => goTo('trade')}>TRADE</button>}
    </div>
  )
}

/** Hold for a second to confirm something you can't undo. */
function HoldButton({ disabled, onDone, children }: { disabled: boolean; onDone(): void; children: ReactNode }) {
  const [held, setHeld] = useState(false)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const start = () => { if (disabled) return; setHeld(true); timer.current = setTimeout(() => { setHeld(false); onDone() }, 1200) }
  const stop = () => { setHeld(false); if (timer.current) clearTimeout(timer.current) }
  return (
    <button className={`turf-hold ${held ? 'held' : ''}`} disabled={disabled} onPointerDown={start} onPointerUp={stop} onPointerLeave={stop} onPointerCancel={stop}>
      <span className="fill" />{children}
    </button>
  )
}

function BidPad({ screen, disabled, act }: { screen: TurfView; disabled: boolean; act(p: ActionPayload): void }) {
  const pad = screen.auction
  const sent = useRef<{ auction: number; amount: number }>({ auction: -1, amount: 0 })
  if (!pad) return <Card title={screen.prompt.title} />
  if (sent.current.auction !== pad.auction) sent.current = { auction: pad.auction, amount: 0 }
  const base = Math.max(pad.top, sent.current.amount)
  const bid = (inc: number) => {
    const amount = base + inc
    sent.current = { auction: pad.auction, amount }
    act({ kind: 'bid', auction: pad.auction, amount })
  }
  return (
    <div className={`turf-bid ${pad.leading ? 'leading' : ''}`}>
      <div className="lot"><i style={{ background: pad.color }} /><div><b>{pad.name}</b><small>Printed price {money(pad.price)}</small></div></div>
      <div className="top"><span>TOP BID</span><Led value={`$${pad.top}`} tone="gold" size={34} digits={5} /></div>
      <p className="status">{pad.leading ? "YOU'RE WINNING!" : pad.leaderName ? `${pad.leaderName} leads` : 'No bids yet'}</p>
      {pad.canBid ? (
        <div className="incs">{[10, 50, 100].map((inc) => (
          <button key={inc} disabled={disabled || base + inc > pad.maxBid} onClick={() => bid(inc)}>+{inc}<small>{money(base + inc)}</small></button>
        ))}</div>
      ) : <p className="status small">{screen.me?.mine === false ? 'Your teammate is bidding' : 'Not enough cash to bid higher'}</p>}
    </div>
  )
}

// ---- My places ---------------------------------------------------------------------------------------

function PlacesTab({ screen, disabled, act }: { screen: TurfView; disabled: boolean; act(p: ActionPayload): void }) {
  const cash = screen.me?.cash ?? 0
  if (!screen.deeds.length) return <Card title="No places yet" detail="Land on one nobody owns and buy it, or win it at auction." />
  const groups = new Map<number, TurfDeed[]>()
  for (const d of screen.deeds) groups.set(d.group, [...(groups.get(d.group) ?? []), d])
  const canManage = screen.deeds.some((d) => d.build != null || d.sell != null || d.mortgage != null || d.unmortgage != null)
  return (
    <div className="turf-places">
      {!canManage && <p className="turf-note">You can build and mortgage on your own turn.</p>}
      {[...groups.entries()].map(([g, deeds]) => (
        <section key={g}>
          <h2>{GROUP_NAMES[g] ?? 'Places'}{deeds[0].set && <span className="set">SET!</span>}</h2>
          {deeds.map((d) => (
            <div key={d.space} className={`turf-deed ${d.mortgaged ? 'mortgaged' : ''}`}>
              <i className="swatch" style={{ background: d.color }} />
              <div className="name">
                <b>{d.name}</b>
                <small>{d.mortgaged ? 'Mortgaged: no rent' : d.group === 9 ? `Rent ${d.rent}× dice` : `Rent ${money(d.rent)}`}{d.level > 0 && ` · ${d.level >= 4 ? 'HOTEL' : `${d.level} house${d.level > 1 ? 's' : ''}`}`}</small>
              </div>
              <div className="ops">
                {d.build != null && <button className="op build" disabled={disabled || cash < d.build} onClick={() => act({ kind: 'build', target: d.space })}>{d.level === 3 ? 'HOTEL' : '+HOUSE'}<small>{money(d.build)}</small></button>}
                {d.sell != null && <button className="op sell" disabled={disabled} onClick={() => act({ kind: 'sell', target: d.space })}>SELL<small>+{money(d.sell)}</small></button>}
                {d.mortgage != null && <button className="op mortgage" disabled={disabled} onClick={() => act({ kind: 'mortgage', target: d.space })}>MORTGAGE<small>+{money(d.mortgage)}</small></button>}
                {d.unmortgage != null && <button className="op unmortgage" disabled={disabled || cash < d.unmortgage} onClick={() => act({ kind: 'unmortgage', target: d.space })}>PAY OFF<small>{money(d.unmortgage)}</small></button>}
              </div>
            </div>
          ))}
        </section>
      ))}
    </div>
  )
}

// ---- Trades ---------------------------------------------------------------------------------------------

function counterDraft(screen: TurfView): Draft | null {
  const t = screen.trade
  if (!t) return null
  return {
    to: t.from, give: t.get.map((d) => d.space), get: t.give.map((d) => d.space),
    giveCash: t.getCash, getCash: t.giveCash, giveCards: t.getCards, getCards: t.giveCards, counter: t.id,
  }
}

function Chip({ d, on, onClick, disabled }: { d: TurfDeedRef; on: boolean; onClick(): void; disabled?: boolean }) {
  return (
    <button className={`turf-chip ${on ? 'on' : ''}`} disabled={disabled || !d.tradable} onClick={onClick} style={{ '--band': d.color || RIDE } as CSSProperties}>
      {d.name}{d.mortgaged ? ' (M)' : ''}{!d.tradable && <small>sell buildings first</small>}
    </button>
  )
}

function CashStepper({ label, value, max, set }: { label: string; value: number; max: number; set(v: number): void }) {
  return (
    <div className="turf-cash-step">
      <span>{label}</span>
      <button onClick={() => set(Math.max(0, value - 50))} disabled={value <= 0}>−50</button>
      <b>{money(value)}</b>
      <button onClick={() => set(Math.min(max, value + 10))} disabled={value + 10 > max}>+10</button>
      <button onClick={() => set(Math.min(max, value + 50))} disabled={value + 50 > max}>+50</button>
    </div>
  )
}

function TradeTab({ screen, disabled, act, draft, setDraft }: { screen: TurfView; disabled: boolean; act(p: ActionPayload): void; draft: Draft | null; setDraft(d: Draft | null): void }) {
  const me = screen.me
  const open = screen.trade
  const counter = draft?.counter != null && open?.id === draft.counter && open.canCounter
  if (open && !counter && open.role !== 'watch') {
    return <Card title={open.role === 'from' ? `Your offer to ${open.toName}` : `${open.fromName}'s offer`} detail="It's on the TV. One trade at a time." />
  }
  if (!screen.canTrade && !counter) {
    return <Card title="Trades" detail={screen.partners.length ? "Offer a deal on anyone's turn while they're rolling or building, when you have the dice." : 'Nobody left to trade with.'} />
  }
  if (!draft) {
    return (
      <div className="turf-trade">
        <h2>Trade with…</h2>
        <div className="turf-partners">{screen.partners.map((p) => (
          <button key={p.index} onClick={() => setDraft({ to: p.index, give: [], get: [], giveCash: 0, getCash: 0, giveCards: 0, getCards: 0 })} style={{ '--band': p.color } as CSSProperties}>
            <b>{p.name}</b><small>{money(p.cash)} · {p.deeds.length} place{p.deeds.length === 1 ? '' : 's'}</small>
          </button>
        ))}</div>
      </div>
    )
  }
  const partner = screen.partners.find((p) => p.index === draft.to)
  if (!partner || !me) {
    return <Card title="That deal's off" detail="They're out of the game."><button className="turf-key pass" onClick={() => setDraft(null)}>BACK</button></Card>
  }
  const mine: TurfDeedRef[] = screen.deeds.map((d) => ({ space: d.space, name: d.name, color: d.color, group: d.group, mortgaged: d.mortgaged, tradable: d.tradable }))
  const toggle = (list: number[], s: number) => (list.includes(s) ? list.filter((x) => x !== s) : [...list, s])
  const empty = !draft.give.length && !draft.get.length && !draft.giveCash && !draft.getCash && !draft.giveCards && !draft.getCards
  const send = () => {
    act({
      kind: 'trade', to: draft.to, give: draft.give.map(String), get: draft.get.map(String),
      giveCash: draft.giveCash, getCash: draft.getCash, giveCards: draft.giveCards, getCards: draft.getCards,
      ...(draft.counter != null ? { counter: String(draft.counter) } : {}),
    })
    setDraft(null)
  }
  return (
    <div className="turf-trade">
      <div className="row between"><h2>{counter ? `Counter ${partner.name}` : `Deal with ${partner.name}`}</h2><button className="small ghost" onClick={() => setDraft(null)}>CANCEL</button></div>
      <section>
        <h3>YOU GIVE</h3>
        <div className="chips">{mine.map((d) => <Chip key={d.space} d={d} on={draft.give.includes(d.space)} onClick={() => setDraft({ ...draft, give: toggle(draft.give, d.space) })} />)}</div>
        <CashStepper label="Cash" value={draft.giveCash} max={me.cash} set={(v) => setDraft({ ...draft, giveCash: v })} />
        {me.jailCards > 0 && <button className={`turf-chip ${draft.giveCards ? 'on' : ''}`} onClick={() => setDraft({ ...draft, giveCards: draft.giveCards ? 0 : 1 })}>Get Out card</button>}
      </section>
      <section>
        <h3>YOU GET</h3>
        <div className="chips">{partner.deeds.map((d) => <Chip key={d.space} d={d} on={draft.get.includes(d.space)} onClick={() => setDraft({ ...draft, get: toggle(draft.get, d.space) })} />)}</div>
        {partner.deeds.length === 0 && <p className="turf-note">{partner.name} has no places.</p>}
        <CashStepper label="Cash" value={draft.getCash} max={partner.cash} set={(v) => setDraft({ ...draft, getCash: v })} />
        {partner.jailCards > 0 && <button className={`turf-chip ${draft.getCards ? 'on' : ''}`} onClick={() => setDraft({ ...draft, getCards: draft.getCards ? 0 : 1 })}>Their Get Out card</button>}
      </section>
      <button className="turf-key buy" disabled={disabled || empty} onClick={send}>{counter ? 'SEND COUNTER' : 'SEND OFFER'}</button>
    </div>
  )
}

function Lot({ deeds, cash, cards }: { deeds: TurfDeedRef[]; cash: number; cards: number }) {
  if (!deeds.length && !cash && !cards) return <p className="turf-note">Nothing</p>
  return (
    <div className="chips">
      {deeds.map((d) => <span key={d.space} className="turf-chip on static" style={{ '--band': d.color || RIDE } as CSSProperties}>{d.name}{d.mortgaged ? ' (M)' : ''}</span>)}
      {cash > 0 && <span className="turf-chip cash static">{money(cash)}</span>}
      {cards > 0 && <span className="turf-chip static">Get Out card ×{cards}</span>}
    </div>
  )
}

function TradeOffer({ screen, disabled, act, onCounter }: { screen: TurfView; disabled: boolean; act(p: ActionPayload): void; onCounter(): void }) {
  const t = screen.trade
  if (!t) return null
  return (
    <div className="turf-offer">
      <h2>{t.fromName} offers you</h2>
      <Lot deeds={t.give} cash={t.giveCash} cards={t.giveCards} />
      <h2>For your</h2>
      <Lot deeds={t.get} cash={t.getCash} cards={t.getCards} />
      <div className="turf-keys two">
        <button className="turf-key buy" disabled={disabled} onClick={() => act({ kind: 'tradeReply', trade: t.id, option: 'accept' })}>ACCEPT</button>
        <button className="turf-key pass" disabled={disabled} onClick={() => act({ kind: 'tradeReply', trade: t.id, option: 'reject' })}>REJECT</button>
      </div>
      {t.canCounter && <button className="turf-key go" disabled={disabled} onClick={onCounter}>COUNTER</button>}
    </div>
  )
}
