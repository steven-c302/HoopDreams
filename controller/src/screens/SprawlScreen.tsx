import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import type { ActionPayload, SprawlScreen as SprawlView } from '../protocol'
import { Die } from '../tv/TurfArt'
import { RES_FILL, ResourceIcon } from '../tv/SprawlArt'
import { SprawlMap, mapBox, spotPoint, type SpotKind } from '../tv/SprawlMap'
import './sprawl-phone.css'

const buzz = (ms: number | number[]) => { try { navigator.vibrate?.(ms) } catch { /* not supported */ } }
/** Prompts that need this phone to do something: switch to Now and buzz when one arrives. */
const ACTIONABLE = new Set(['setup', 'roll', 'main', 'discard', 'robber', 'steal', 'road2', 'pick', 'trade'])
const ANYONE = -2
const COSTS: Record<'road' | 'settlement' | 'city' | 'dev', number[]> = {
  road: [1, 1, 0, 0, 0], settlement: [1, 1, 1, 1, 0], city: [0, 0, 0, 2, 3], dev: [0, 0, 1, 1, 1],
}
const CARD_TEXT: Record<string, string> = {
  knight: 'Move The Landlord and rob someone. Play 3 for Most Bouncers.',
  road: 'Build 2 roads for free.',
  plenty: 'Take any 2 cards from the bank.',
  mono: 'Name a resource: everyone hands you all of theirs.',
  vp: 'A secret point. It counts on its own.',
}

type Tab = 'now' | 'cards' | 'trade'
type Build = 'road' | 'settlement' | 'city'
/** A trade being put together; survives other players' turns (Play keeps this screen mounted). */
interface Draft { to: number; give: number[]; get: number[]; counter?: number }

interface Props { screen: SprawlView; disabled: boolean; onAction(p: ActionPayload): void }

const zero = () => [0, 0, 0, 0, 0]
const sum = (xs: number[]) => xs.reduce((a, b) => a + b, 0)

/** A new game (a new island) starts with a fresh screen: no leftover tab, build mode or trade draft from the last one. */
export function SprawlScreen(props: Props) {
  const island = props.screen.map.hexes.map((h) => `${h.terrain}${h.number}${h.name}`).join()
  return <SprawlPhone key={island} {...props} />
}

function SprawlPhone({ screen, disabled, onAction }: Props) {
  const [tab, setTab] = useState<Tab>('now')
  const [draft, setDraft] = useState<Draft | null>(null)
  const [building, setBuilding] = useState<Build | null>(null)
  const p = screen.prompt
  const me = screen.me

  // A new decision pulls you back to Now with a buzz, and drops a half-picked build.
  const lastKind = useRef(p.kind)
  useEffect(() => {
    if (p.kind !== lastKind.current) {
      if (ACTIONABLE.has(p.kind)) { setTab('now'); buzz(p.kind === 'discard' ? [60, 60, 60] : [30, 40, 30]) }
      setBuilding(null)
    }
    lastKind.current = p.kind
  }, [p.kind])
  useEffect(() => { if (screen.drink) buzz([80, 60, 80, 60, 80]) }, [screen.drink])

  const act = (payload: ActionPayload) => { buzz(25); onAction(payload) }
  const counter = () => {
    const t = screen.trade
    if (!t) return
    setDraft({ to: t.from, give: [...t.get], get: [...t.give], counter: t.id })
    setTab('trade')
  }
  const playable = me?.dev.some((c) => c.playable) ?? false

  return (
    <div className="sp-phone" style={{ '--me': me?.color ?? 'var(--paper)' } as CSSProperties}>
      {me && <Band screen={screen} />}
      {screen.drink && <div className="sp-drink" key={screen.drink}><b>DRINK!</b><span>{screen.drink}</span></div>}
      <div className="sp-body">
        {tab === 'now' && <NowTab screen={screen} disabled={disabled} act={act} onAction={onAction} goTo={setTab} building={building} setBuilding={setBuilding} onCounter={counter} />}
        {tab === 'cards' && <CardsTab screen={screen} disabled={disabled} act={act} />}
        {tab === 'trade' && <TradeTab screen={screen} disabled={disabled} act={act} draft={draft} setDraft={setDraft} />}
      </div>
      {me && (
        <nav className="sp-tabs">
          <button className={tab === 'now' ? 'on' : ''} onClick={() => setTab('now')}>NOW{ACTIONABLE.has(p.kind) && tab !== 'now' && <i className="dot" />}</button>
          <button className={tab === 'cards' ? 'on' : ''} onClick={() => setTab('cards')}>CARDS{me.dev.length > 0 && <i className={`count ${playable ? 'hot' : ''}`}>{sum(me.dev.map((c) => c.count))}</i>}</button>
          <button className={tab === 'trade' ? 'on' : ''} onClick={() => setTab('trade')}>TRADE{screen.trade && <i className="dot" />}</button>
        </nav>
      )}
    </div>
  )
}

/** Your colour, name, points, and your hand as five big tiles. */
function Band({ screen }: { screen: SprawlView }) {
  const me = screen.me!
  const total = sum(me.hand)
  return (
    <div className="sp-band">
      <div className="who">
        <b>{me.name}</b>
        <span className="vp">{me.vp} <small>PTS</small></span>
        <span className={`count ${total > 7 ? 'over' : ''}`}>{total} cards</span>
      </div>
      <Hand hand={me.hand} names={screen.map.resources} />
    </div>
  )
}

function Hand({ hand, names }: { hand: number[]; names: string[] }) {
  return (
    <div className="sp-hand">
      {hand.map((n, r) => (
        <span key={r} className={`sp-tile ${n === 0 ? 'none' : ''}`} style={{ '--res': RES_FILL[r] } as CSSProperties}>
          <ResourceIcon res={r} size={30} />
          <b>{n}</b>
          <small>{names[r]}</small>
        </span>
      ))}
    </div>
  )
}

function Card({ title, detail, tone, children }: { title: string; detail?: string; tone?: 'go' | 'win' | 'lose'; children?: ReactNode }) {
  return (
    <div className={`sp-card ${tone ?? ''}`}>
      <h1>{title}</h1>
      {detail && <p>{detail}</p>}
      {children}
    </div>
  )
}

/**
 * The island to tap on: pick a glowing spot, then confirm. The TV rings whatever you're eyeing. Zoomed in on the big
 * island or when the spots are bunched up (a road off a new settlement), scrolled so the spots are in view.
 */
function Placer({ screen, spots, kind, title, detail, confirm, onConfirm, onCancel, disabled, onAction }: {
  screen: SprawlView; spots: number[]; kind: SpotKind; title: string; detail?: string; confirm: string
  onConfirm(spot: number): void; onCancel?(): void; disabled: boolean; onAction(p: ActionPayload): void
}) {
  const [picked, setPicked] = useState<number | null>(null)
  const [zoom, setZoom] = useState(() => screen.map.size > 0 || spots.length <= 8)
  const box = useRef<HTMLDivElement>(null)
  const spotKey = spots.join()
  useEffect(() => { if (picked != null && !spots.includes(picked)) setPicked(null) }, [spots, picked])
  // Bring the spots into view: scroll the zoomed map to their middle.
  useEffect(() => {
    const el = box.current
    if (!el || !spots.length) return
    const b = mapBox(screen.map)
    const pts = spots.map((id) => spotPoint(screen.map, kind, id))
    const cx = pts.reduce((a, p) => a + p[0], 0) / pts.length, cy = pts.reduce((a, p) => a + p[1], 0) / pts.length
    const svg = el.querySelector('svg')
    if (!svg) return
    // getBoundingClientRect, not clientWidth: WebKit reports 0 for an SVG's clientWidth.
    const { width, height } = svg.getBoundingClientRect()
    el.scrollTo({ left: ((cx - b.x) / b.w) * width - el.clientWidth / 2, top: ((cy - b.y) / b.h) * height - el.clientHeight / 2 })
  }, [zoom, spotKey]) // eslint-disable-line react-hooks/exhaustive-deps
  const pick = (spot: number) => { buzz(15); setPicked(spot); onAction({ kind: 'peek', what: kind, target: spot }) }
  return (
    <div className="sp-placer">
      <div className="sp-placer-head"><b>{title}</b>{detail && <small>{detail}</small>}</div>
      <div ref={box} className={`sp-mapbox ${zoom ? 'zoom' : ''}`}>
        <SprawlMap map={screen.map} robber={screen.robber} vOwner={screen.vOwner} vLevel={screen.vLevel} eOwner={screen.eOwner} colors={screen.colors}
          spots={spots} spotKind={kind} selected={picked} onPick={pick} />
      </div>
      <div className="sp-placer-keys">
        <button className="sp-key small" onClick={() => setZoom(!zoom)} aria-label={zoom ? 'Zoom out' : 'Zoom in'}>{zoom ? '−' : '+'}</button>
        <button className="sp-key go" disabled={disabled || picked == null} onClick={() => { if (picked != null) { buzz(30); onConfirm(picked) } }}>
          {picked == null ? 'TAP A SPOT' : confirm}
        </button>
        {onCancel && <button className="sp-key small pass" onClick={() => { onAction({ kind: 'peek' }); onCancel() }}>BACK</button>}
      </div>
    </div>
  )
}

function Cost({ cost }: { cost: number[] }) {
  return <span className="sp-cost">{cost.flatMap((n, r) => Array.from({ length: n }, (_, k) => <ResourceIcon key={`${r}-${k}`} res={r} size={22} />))}</span>
}

// ---- Now: the one thing to do ----------------------------------------------------------------------

function NowTab({ screen, disabled, act, onAction, goTo, building, setBuilding, onCounter }: {
  screen: SprawlView; disabled: boolean; act(p: ActionPayload): void; onAction(p: ActionPayload): void; goTo(t: Tab): void
  building: Build | null; setBuilding(b: Build | null): void; onCounter(): void
}) {
  const p = screen.prompt
  const me = screen.me
  switch (p.kind) {
    case 'setup': case 'road2': case 'robber': {
      const kind = screen.spotKind ?? 'vertex'
      const verb = p.kind === 'robber' ? 'MOVE HERE' : kind === 'vertex' ? 'SETTLE HERE' : 'BUILD ROAD HERE'
      return (
        <Placer key={`${p.kind}-${p.title}`} screen={screen} spots={screen.spots} kind={kind} title={p.title} detail={p.detail} confirm={verb} disabled={disabled} onAction={onAction}
          onConfirm={(t) => act(p.kind === 'robber' ? { kind: 'robber', target: t } : { kind: 'place', target: t })} />
      )
    }
    case 'roll': return (
      <Card title={p.title} detail={p.detail} tone="go">
        <button className="sp-roll" disabled={disabled} onClick={() => act({ kind: 'roll' })}>
          <span className="dice"><Die value={3} size={64} /><Die value={4} size={64} /></span>
          ROLL
        </button>
        {me?.dev.some((c) => c.playable) && <button className="sp-key pass" onClick={() => goTo('cards')}>PLAY A CARD FIRST</button>}
      </Card>
    )
    case 'main': {
      if (building) {
        const spots = building === 'road' ? screen.build.roads : building === 'settlement' ? screen.build.settlements : screen.build.cities
        return (
          <Placer key={building} screen={screen} spots={spots} kind={building === 'road' ? 'edge' : 'vertex'} disabled={disabled} onAction={onAction}
            title={building === 'road' ? 'Build a road' : building === 'settlement' ? 'Build a settlement' : 'Upgrade to a city'}
            confirm={building === 'road' ? 'BUILD ROAD' : building === 'settlement' ? 'SETTLE HERE' : 'BUILD CITY'}
            onConfirm={(t) => { act({ kind: 'build', what: building, target: t }); setBuilding(null) }} onCancel={() => setBuilding(null)} />
        )
      }
      const opts: [Build | 'dev', string, boolean][] = [
        ['road', 'ROAD', screen.build.roads.length > 0], ['settlement', 'SETTLEMENT', screen.build.settlements.length > 0],
        ['city', 'CITY', screen.build.cities.length > 0], ['dev', 'CARD', screen.build.dev],
      ]
      return (
        <Card title={p.title} detail={p.detail}>
          <div className="sp-builds">{opts.map(([k, label, ok]) => (
            <button key={k} className={`sp-build ${ok ? 'ok' : ''}`} disabled={disabled || !ok}
              onClick={() => (k === 'dev' ? act({ kind: 'buyDev' }) : setBuilding(k))}>
              <b>{label}</b><Cost cost={COSTS[k]} />
            </button>
          ))}</div>
          <div className="sp-links">
            {screen.canTrade && <button onClick={() => goTo('trade')}>TRADE</button>}
            {me?.dev.some((c) => c.playable) && <button onClick={() => goTo('cards')}>PLAY A CARD</button>}
          </div>
          <button className="sp-key end" disabled={disabled} onClick={() => act({ kind: 'end' })}>END TURN</button>
        </Card>
      )
    }
    case 'discard': return <Discard screen={screen} disabled={disabled} act={act} />
    case 'steal': return (
      <Card title={p.title} detail={p.detail} tone="go">
        <div className="sp-keys">{screen.victims.map((v) => (
          <button key={v.id} className="sp-key victim" style={{ '--band': v.color } as CSSProperties} disabled={disabled} onClick={() => act({ kind: 'steal', victim: Number(v.id) })}>
            {v.text}<small>{v.detail}</small>
          </button>
        ))}</div>
      </Card>
    )
    case 'pick': return (
      <Card title={p.title} detail={p.detail} tone="go">
        <div className="sp-picks">{p.actions.map((a) => (
          <button key={a.id} disabled={disabled || !!a.detail} onClick={() => act({ kind: 'pick', res: Number(a.id) })} style={{ '--res': RES_FILL[Number(a.id)] } as CSSProperties}>
            <ResourceIcon res={Number(a.id)} size={48} /><b>{a.text}</b>{a.detail && <small>{a.detail}</small>}
          </button>
        ))}</div>
      </Card>
    )
    case 'trade': return <Card title={p.title}><Offer screen={screen} disabled={disabled} act={act} onCounter={onCounter} /></Card>
    default: {
      const cancel = p.actions.find((a) => a.id === 'cancel')
      return (
        <>
          <Card title={p.title} detail={p.detail} tone={p.tone === 'win' ? 'win' : p.tone === 'lose' ? 'lose' : undefined}>
            {cancel && screen.trade && <button className="sp-key pass" disabled={disabled} onClick={() => act({ kind: 'tradeCancel', trade: screen.trade!.id })}>{cancel.text}</button>}
          </Card>
          {p.kind !== 'over' && (
            <div className="sp-mapbox small">
              <SprawlMap map={screen.map} robber={screen.robber} vOwner={screen.vOwner} vLevel={screen.vLevel} eOwner={screen.eOwner} colors={screen.colors} />
            </div>
          )}
        </>
      )
    }
  }
}

function Stepper({ res, value, max, set, names }: { res: number; value: number; max: number; set(v: number): void; names: string[] }) {
  return (
    <div className="sp-step" style={{ '--res': RES_FILL[res] } as CSSProperties}>
      <ResourceIcon res={res} size={34} />
      <span className="name">{names[res]}</span>
      <button onClick={() => set(Math.max(0, value - 1))} disabled={value <= 0} aria-label={`fewer ${names[res]}`}>−</button>
      <b>{value}</b>
      <button onClick={() => set(Math.min(max, value + 1))} disabled={value >= max} aria-label={`more ${names[res]}`}>+</button>
    </div>
  )
}

function Discard({ screen, disabled, act }: { screen: SprawlView; disabled: boolean; act(p: ActionPayload): void }) {
  const [cards, setCards] = useState(zero)
  const me = screen.me!
  const owed = screen.discard
  const chosen = sum(cards)
  return (
    <Card title={screen.prompt.title} detail={screen.prompt.detail} tone="lose">
      <div className="sp-steps">{me.hand.map((n, r) => n > 0 && (
        <Stepper key={r} res={r} value={cards[r]} max={Math.min(n, cards[r] + owed - chosen)} names={screen.map.resources}
          set={(v) => setCards(cards.map((c, i) => (i === r ? v : c)))} />
      ))}</div>
      <button className="sp-key raise" disabled={disabled || chosen !== owed} onClick={() => act({ kind: 'discard', cards })}>
        {chosen === owed ? `DISCARD ${owed}` : `PICK ${owed - chosen} MORE`}
      </button>
    </Card>
  )
}

// ---- cards ------------------------------------------------------------------------------------------

function CardsTab({ screen, disabled, act }: { screen: SprawlView; disabled: boolean; act(p: ActionPayload): void }) {
  const me = screen.me
  if (!me?.dev.length) return <Card title="No cards yet" detail="Buy one on your turn: sheep + wheat + ore." />
  return (
    <div className="sp-devs">
      {me.dev.map((c) => (
        <div key={c.kind} className={`sp-dev ${c.kind}`}>
          <div className="top"><b>{c.name}</b>{c.count > 1 && <span className="n">×{c.count}</span>}</div>
          <p>{CARD_TEXT[c.kind]}</p>
          {c.kind !== 'vp' && (
            c.playable
              ? <button className="sp-key go" disabled={disabled} onClick={() => act({ kind: 'play', card: c.kind })}>PLAY {c.name.toUpperCase()}</button>
              : <small>{c.fresh >= c.count ? 'New: play it next turn' : 'Play on your turn (one card a turn)'}</small>
          )}
        </div>
      ))}
    </div>
  )
}

// ---- trades -----------------------------------------------------------------------------------------

function Offer({ screen, disabled, act, onCounter }: { screen: SprawlView; disabled: boolean; act(p: ActionPayload): void; onCounter(): void }) {
  const t = screen.trade
  if (!t) return null
  const names = screen.map.resources
  return (
    <div className="sp-offer">
      <h2>{t.fromName} gives you</h2>
      <Hand hand={t.give} names={names} />
      <h2>For your</h2>
      <Hand hand={t.get} names={names} />
      <div className="sp-keys two">
        <button className="sp-key buy" disabled={disabled || !t.canAccept} onClick={() => act({ kind: 'tradeReply', trade: t.id, option: 'accept' })}>ACCEPT</button>
        <button className="sp-key pass" disabled={disabled} onClick={() => act({ kind: 'tradeReply', trade: t.id, option: 'reject' })}>REJECT</button>
      </div>
      {!t.canAccept && <p className="sp-note">You don't have those cards.</p>}
      {t.canCounter && <button className="sp-key go" disabled={disabled} onClick={onCounter}>COUNTER</button>}
    </div>
  )
}

function TradeTab({ screen, disabled, act, draft, setDraft }: { screen: SprawlView; disabled: boolean; act(p: ActionPayload): void; draft: Draft | null; setDraft(d: Draft | null): void }) {
  const me = screen.me
  const open = screen.trade
  const counter = draft?.counter != null && open?.id === draft.counter && open.canCounter
  if (!me) return <Card title="Trades" detail="You're watching this one." />
  if (open && !counter) {
    if (open.role === 'to') return <Card title={`${open.fromName}'s offer`}><Offer screen={screen} disabled={disabled} act={act} onCounter={() => setDraft({ to: open.from, give: [...open.get], get: [...open.give], counter: open.id })} /></Card>
    return <Card title={open.role === 'from' ? `Your offer to ${open.toName}` : `${open.fromName} is dealing`} detail="It's on the TV. One trade at a time." />
  }
  if (!screen.canTrade && !counter) {
    return <Card title="Trades" detail="On your turn, after you roll: trade with the bank or offer a deal. On other turns, answer their offers." />
  }
  return (
    <div className="sp-trade">
      {!counter && <BankTrade screen={screen} disabled={disabled} act={act} />}
      <PlayerTrade screen={screen} disabled={disabled} act={act} draft={draft ?? { to: ANYONE, give: zero(), get: zero() }} setDraft={setDraft} counter={counter} />
    </div>
  )
}

function BankTrade({ screen, disabled, act }: { screen: SprawlView; disabled: boolean; act(p: ActionPayload): void }) {
  const me = screen.me!
  const [give, setGive] = useState<number | null>(null)
  const [get, setGet] = useState<number | null>(null)
  const names = screen.map.resources
  const can = (r: number) => me.hand[r] >= me.ratios[r]
  return (
    <section>
      <h3>THE BANK</h3>
      <p className="sp-note left">Give {give == null ? 'some' : `${me.ratios[give]} ${names[give]}`}, get 1 of anything.</p>
      <div className="sp-chips">{[0, 1, 2, 3, 4].map((r) => (
        <button key={r} className={`sp-chip ${give === r ? 'on' : ''}`} disabled={!can(r)} onClick={() => setGive(r)} style={{ '--res': RES_FILL[r] } as CSSProperties}>
          <ResourceIcon res={r} size={26} /><span>{me.ratios[r]}:1</span>
        </button>
      ))}</div>
      <div className="sp-chips">{[0, 1, 2, 3, 4].map((r) => (
        <button key={r} className={`sp-chip ${get === r ? 'on' : ''}`} disabled={r === give || screen.bank[r] <= 0} onClick={() => setGet(r)} style={{ '--res': RES_FILL[r] } as CSSProperties}>
          <ResourceIcon res={r} size={26} /><span>GET</span>
        </button>
      ))}</div>
      <button className="sp-key buy" disabled={disabled || give == null || get == null || !can(give)} onClick={() => { if (give != null && get != null) act({ kind: 'bank', give, get }) }}>
        {give != null && get != null ? `TRADE ${me.ratios[give]} ${names[give]} FOR 1 ${names[get]}` : 'PICK WHAT TO GIVE AND GET'}
      </button>
    </section>
  )
}

function PlayerTrade({ screen, disabled, act, draft, setDraft, counter }: {
  screen: SprawlView; disabled: boolean; act(p: ActionPayload): void; draft: Draft; setDraft(d: Draft | null): void; counter: boolean
}) {
  const me = screen.me!
  const names = screen.map.resources
  const giving = sum(draft.give), getting = sum(draft.get)
  const send = () => {
    act({ kind: 'trade', to: draft.to, give: draft.give, get: draft.get, ...(draft.counter != null ? { counter: String(draft.counter) } : {}) })
    setDraft(null)
  }
  const partner = screen.partners.find((x) => x.index === draft.to)
  return (
    <section>
      <div className="sp-row"><h3>{counter ? `COUNTER ${partner?.name?.toUpperCase() ?? ''}` : 'OFFER A DEAL'}</h3>{counter && <button className="small ghost" onClick={() => setDraft(null)}>CANCEL</button>}</div>
      {!counter && (
        <div className="sp-chips">
          <button className={`sp-chip who ${draft.to === ANYONE ? 'on' : ''}`} onClick={() => setDraft({ ...draft, to: ANYONE })}>ANYONE</button>
          {screen.partners.map((x) => (
            <button key={x.index} className={`sp-chip who ${draft.to === x.index ? 'on' : ''}`} style={{ '--band': x.color } as CSSProperties} onClick={() => setDraft({ ...draft, to: x.index })}>
              {x.name}<small>{x.cards} cards</small>
            </button>
          ))}
        </div>
      )}
      <h4>YOU GIVE</h4>
      <div className="sp-steps">{[0, 1, 2, 3, 4].map((r) => (
        <Stepper key={r} res={r} value={draft.give[r]} max={draft.get[r] > 0 ? 0 : me.hand[r]} names={names}
          set={(v) => setDraft({ ...draft, give: draft.give.map((c, i) => (i === r ? v : c)) })} />
      ))}</div>
      <h4>YOU GET</h4>
      <div className="sp-steps">{[0, 1, 2, 3, 4].map((r) => (
        <Stepper key={r} res={r} value={draft.get[r]} max={draft.give[r] > 0 ? 0 : 9} names={names}
          set={(v) => setDraft({ ...draft, get: draft.get.map((c, i) => (i === r ? v : c)) })} />
      ))}</div>
      <button className="sp-key buy" disabled={disabled || giving === 0 || getting === 0} onClick={send}>
        {giving === 0 || getting === 0 ? 'ADD CARDS TO BOTH SIDES' : counter ? 'SEND COUNTER' : draft.to === ANYONE ? 'OFFER TO ANYONE' : `OFFER TO ${partner?.name.toUpperCase() ?? ''}`}
      </button>
    </section>
  )
}
