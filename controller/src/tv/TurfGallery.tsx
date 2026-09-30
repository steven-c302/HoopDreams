import { useEffect, useLayoutEffect, useState } from 'react'
import type { PlayerSummary, StageInfo } from '../protocol'
import { GameScene } from '../theme/GameScene'
import { TurfStage } from './TurfStage'
import type { TurfBeat, TurfSpace, TurfToken, TurfTv } from './types'

/**
 * Home Turf beats rendered from fixture data at 1920×1080 (no server): /tv?gallery=turf lists them,
 * /tv?gallery=turf&beat=<name> renders one. Some beats inject a move or a callout just after mount so the hop,
 * zoom and bursts show too. The board mirrors turf/board.json and TurfBoard.kt.
 */
const GC = ['#8B5A2B', '#9ED8F5', '#E0408C', '#FF8A2B', '#E8322B', '#FFD23F', '#1FA64A', '#1F4FB8']
const st = (name: string, label: string, group: number, price: number, house: number, rent: number[]): TurfSpace =>
  ({ name, label, kind: 'street', group, color: GC[group], price, rent, houseCost: house, tax: 0 })
const other = (name: string, label: string, kind: string, extra: Partial<TurfSpace> = {}): TurfSpace =>
  ({ name, label, kind, group: -1, price: 0, rent: [], houseCost: 0, tax: 0, ...extra })
const ride = (name: string, label: string) => other(name, label, 'railroad', { color: '#2B2B2B', price: 200 })
const util = (name: string, label: string) => other(name, label, 'utility', { color: '#8B4DFF', price: 150 })

const board: TurfSpace[] = [
  other('Payday', 'Payday', 'payday'),
  st('The Corner Store', 'Corner Store', 0, 60, 50, [2, 10, 30, 90, 160, 250]),
  other('Group Chat', 'Group Chat', 'chest'),
  st('The Laundromat', 'Laundromat', 0, 60, 50, [4, 20, 60, 180, 320, 450]),
  other('Split the Bill', 'Split the Bill', 'tax', { tax: 200 }),
  ride('The Night Bus', 'Night Bus'),
  st("Ethan's Dorm", "Ethan's Dorm", 1, 100, 50, [6, 30, 90, 270, 400, 550]),
  other('Plot Twist', 'Plot Twist', 'chance'),
  st("Anna's Porch", "Anna's Porch", 1, 100, 50, [6, 30, 90, 270, 400, 550]),
  st('The Taco Truck', 'Taco Truck', 1, 120, 50, [8, 40, 100, 300, 450, 600]),
  other('Timeout', 'Timeout', 'jail'),
  st("Kaishun's Basement", "Kaishun's Basement", 2, 140, 100, [10, 50, 150, 450, 625, 750]),
  util('The Wi-Fi', 'The Wi-Fi'),
  st("Sunhye's Studio", "Sunhye's Studio", 2, 140, 100, [10, 50, 150, 450, 625, 750]),
  st('The Karaoke Bar', 'Karaoke Bar', 2, 160, 100, [12, 60, 180, 500, 700, 900]),
  ride('Rideshare', 'Rideshare'),
  st("Steven's Garage", "Steven's Garage", 3, 180, 100, [14, 70, 200, 550, 750, 950]),
  other('Group Chat', 'Group Chat', 'chest'),
  st("Alex's Backyard", "Alex's Backyard", 3, 180, 100, [14, 70, 200, 550, 750, 950]),
  st('The Late-Night Diner', 'Late-Night Diner', 3, 200, 100, [16, 80, 220, 600, 800, 1000]),
  other('The Couch', 'The Couch', 'couch'),
  st("Junha's Kitchen", "Junha's Kitchen", 4, 220, 150, [18, 90, 250, 700, 875, 1050]),
  other('Plot Twist', 'Plot Twist', 'chance'),
  st("Amanda's Balcony", "Amanda's Balcony", 4, 220, 150, [18, 90, 250, 700, 875, 1050]),
  st('The Dive Bar', 'Dive Bar', 4, 240, 150, [20, 100, 300, 750, 925, 1100]),
  ride('Designated Driver', 'Designated Driver'),
  st("Daniel's Place", "Daniel's Place", 5, 260, 150, [22, 110, 330, 800, 975, 1150]),
  st("The Other Daniel's", "Other Daniel's", 5, 260, 150, [22, 110, 330, 800, 975, 1150]),
  util('The Aux Cord', 'Aux Cord'),
  st("Izzy's Rooftop", "Izzy's Rooftop", 5, 280, 150, [24, 120, 360, 850, 1025, 1200]),
  other('Go to Timeout', 'Go to Timeout', 'gotojail'),
  st("John's Living Room", "John's Living Room", 6, 300, 200, [26, 130, 390, 900, 1100, 1275]),
  st("Charlie's Hot Tub", "Charlie's Hot Tub", 6, 300, 200, [26, 130, 390, 900, 1100, 1275]),
  other('Group Chat', 'Group Chat', 'chest'),
  st('The Club', 'The Club', 6, 320, 200, [28, 150, 450, 1000, 1200, 1400]),
  ride('The Long Walk Home', 'Long Walk Home'),
  other('Plot Twist', 'Plot Twist', 'chance'),
  st('The Penthouse', 'Penthouse', 7, 350, 200, [35, 175, 500, 1100, 1300, 1500]),
  other('Bar Tab', 'Bar Tab', 'tax', { tax: 100 }),
  st('The Beach House', 'Beach House', 7, 400, 200, [50, 200, 600, 1400, 1700, 2000]),
]

const CREW = ['Charlie', 'Izzy', 'Junha', 'Amanda', 'Daniel', 'Sunhye', 'Alex', 'Anna', 'Ethan', 'Steven', 'Kaishun', 'John']
const COLORS = ['#FF4B3E', '#2F6BFF', '#2FBF55', '#8B4DFF', '#FF8A2B', '#FF6FB5']
const PIECES = ['cup', 'pizza', 'sneaker', 'boombox', 'cone', 'duck']
const players: PlayerSummary[] = CREW.map((name, i) => ({
  id: `p${i}`, name, role: 'PLAYER', connected: true, avatar: { face: `p:${String(i).padStart(2, '0')}`, color: COLORS[i % COLORS.length] },
}))

const owned: [number, number][] = [
  [1, 0], [3, 0], [6, 1], [8, 1], [9, 1], [16, 2], [18, 2], [5, 2], [15, 2], [21, 3], [24, 3], [12, 3],
  [26, 4], [27, 4], [29, 4], [31, 5], [37, 5], [39, 5], [35, 5], [28, 5],
]
const owner = Array.from({ length: 40 }, (_, i) => owned.find(([s]) => s === i)?.[1] ?? -1)
const level = Array.from({ length: 40 }, (_, i) => ({ 1: 2, 3: 2, 6: 3, 8: 3, 9: 4, 26: 1, 27: 1, 29: 1 } as Record<number, number>)[i] ?? 0)
const cash = [640, 1180, 905, 1420, 210, 760]
const pos = [3, 12, 18, 14, 9, 36]
const tokens: TurfToken[] = cash.map((c, i) => ({
  name: CREW[i], color: COLORS[i], piece: PIECES[i], members: [`p${i}`], seat: `p${i}`, cash: c, pos: pos[i],
  jailed: false, jailCards: i === 1 ? 1 : 0, bankrupt: false, worth: c + 900 + i * 140, sets: [1, 1, 0, 0, 1, 1][i],
}))

let seq = 100
const beat = (kind: string, b: Partial<TurfBeat> = {}): TurfBeat =>
  ({ seq: ++seq, kind, token: -1, other: -1, space: -1, amount: 0, dice: [], path: [], tokens: [], sips: 0, ...b })

const base: TurfTv = {
  t: 'turf', phase: 'roll', teams: false, board, chanceName: 'Plot Twist', chestName: 'Group Chat', owner, level, mortgaged: [28],
  tokens, turn: 3, dice: [4, 2, 3], doubles: 0, housesLeft: 18, hotelsLeft: 11, buy: -1, lastLap: false, timed: true, drinks: true,
  clockLeftMs: 1_925_000, phaseMs: 20_000, beats: [beat('turn', { token: 3 })], pieces: [], tally: [],
  ticker: ['Izzy built a HOTEL on The Taco Truck', 'Daniel paid Izzy $600 at The Taco Truck', "Amanda's turn"],
}

const withTokens = (f: (t: TurfToken, i: number) => Partial<TurfToken>) => tokens.map((t, i) => ({ ...t, ...f(t, i) }))

/** Fixture per beat, plus beats to inject after mount (for hops and callouts). */
const BEATS: Record<string, { g: TurfTv; later?: TurfBeat[]; after?: Partial<TurfTv> }> = {
  teamup: { g: { ...base, phase: 'teamup', teams: true, timed: true, notice: 'Too many for solo, so it\'s teams',
    tokens: [0, 1, 2].map((k) => ({ ...tokens[k], name: ['Team Tomato', 'Team Blueberry', 'Team Lime'][k], members: [`p${k * 4}`, `p${k * 4 + 1}`, `p${k * 4 + 2}`, `p${k * 4 + 3}`], seat: `p${k * 4}`, piece: undefined })) } },
  pieces: { g: { ...base, phase: 'pieces', tokens: withTokens((_, i) => ({ piece: i < 3 ? PIECES[i] : undefined })) } },
  deal: { g: { ...base, phase: 'deal', timed: false, beats: tokens.map((_, i) => beat('deal', { token: i, tokens: owned.filter(([, t]) => t === i).slice(0, 2).map(([s]) => s) })) } },
  roll: { g: base },
  move: { g: { ...base, phase: 'move', timed: false }, later: [beat('move', { token: 3, space: 23, path: [15, 16, 17, 18, 19, 20, 21, 22, 23] })], after: { tokens: withTokens((_, i) => (i === 3 ? { pos: 23 } : {})) } },
  lineup: { g: { ...base, phase: 'roll', tokens: withTokens(() => ({ pos: 0 })) } },
  'diced-move': { g: { ...base, phase: 'move', timed: false }, later: [beat('roll', { token: 3, dice: [4, 5, 1] }), beat('move', { token: 3, space: 23, path: [15, 16, 17, 18, 19, 20, 21, 22, 23] })], after: { tokens: withTokens((_, i) => (i === 3 ? { pos: 23 } : {})) } },
  'doubles-move': { g: { ...base, phase: 'move', timed: false }, later: [beat('roll', { token: 3, dice: [4, 4, 1] }), beat('move', { token: 3, space: 22, path: [15, 16, 17, 18, 19, 20, 21, 22] })], after: { tokens: withTokens((_, i) => (i === 3 ? { pos: 22 } : {})) } },
  buy: { g: { ...base, phase: 'buy', buy: 23, tokens: withTokens((_, i) => (i === 3 ? { pos: 23 } : {})) } },
  auction: { g: { ...base, phase: 'auction', auction: { id: 4, space: 23, top: 260, leader: 1, bids: 5 }, tokens: withTokens((_, i) => (i === 3 ? { pos: 23 } : {})) } },
  card: { g: { ...base, phase: 'card', timed: false, card: { deck: 'chance', deckName: 'Plot Twist', text: "Last call! Go to the nearest ride home. If it's owned, pay double. If not, you can buy it.", sips: 0 } } },
  trade: { g: { ...base, phase: 'trade', trade: { id: 7, from: 3, to: 5, give: [12, 21], get: [28], giveCash: 150, getCash: 0, giveCards: 0, getCards: 0, counters: 1 } } },
  debt: { g: { ...base, phase: 'debt', turn: 4, debt: { token: 4, amount: 600, to: 1, why: 'rent at The Taco Truck' } } },
  manage: { g: { ...base, phase: 'manage' } },
  jail: { g: { ...base, phase: 'jail', turn: 2, tokens: withTokens((_, i) => (i === 2 ? { pos: 10, jailed: true } : {})) } },
  rent: { g: { ...base, phase: 'manage' }, later: [beat('rent', { token: 4, other: 1, space: 9, amount: 600 }), beat('drink', { tokens: [4], sips: 5, text: 'paid rent at The Taco Truck' })] },
  'home-turf': { g: { ...base, phase: 'manage' }, later: [beat('set', { token: 3, space: 24 }), beat('drink', { tokens: [0, 1, 2, 4, 5], sips: 1, text: 'Amanda completed a set' })] },
  'last-lap': { g: { ...base, phase: 'roll', lastLap: true }, later: [beat('lastlap')] },
  bankrupt: { g: { ...base, phase: 'manage', tokens: withTokens((_, i) => (i === 4 ? { bankrupt: true, cash: 0 } : {})) }, later: [beat('bankrupt', { token: 4, other: 1 })] },
  tally: { g: { ...base, phase: 'tally', timed: false, tally: tokens.map((t, i) => ({ token: i, worth: [2140, 3380, 1905, 2890, 0, 3010][i], cash: t.cash, places: 3, buildings: 2, rank: [4, 1, 5, 3, 6, 2][i] })) } },
  podium: { g: { ...base, phase: 'podium', timed: false, tally: tokens.map((t, i) => ({ token: i, worth: [2140, 3380, 1905, 2890, 0, 3010][i], cash: t.cash, places: 3, buildings: 2, rank: [4, 1, 5, 3, 6, 2][i] })) } },
}

export function TurfGallery({ beat: name }: { beat: string | null }) {
  const [scale, setScale] = useState(1)
  useLayoutEffect(() => {
    const fit = () => setScale(Math.min(window.innerWidth / 1920, window.innerHeight / 1080))
    fit(); window.addEventListener('resize', fit)
    return () => window.removeEventListener('resize', fit)
  }, [])
  const fixture = name ? BEATS[name] : null
  const [g, setG] = useState<TurfTv | null>(fixture?.g ?? null)
  useEffect(() => {
    if (!fixture?.later) return
    // Numbered straight after the beats already on the board: a gap in seq reads as a reconnect and snaps instead of playing.
    const id = setTimeout(() => setG((cur) => {
      if (!cur) return cur
      const top = cur.beats.reduce((m, b) => Math.max(m, b.seq), 0)
      return { ...cur, ...fixture.after, beats: [...cur.beats, ...fixture.later!.map((b, i) => ({ ...b, seq: top + 1 + i }))] }
    }), 700)
    return () => clearTimeout(id)
  }, [fixture])
  if (!fixture || !g) {
    return (
      <div style={{ padding: 40, fontSize: 24, fontFamily: 'var(--font-body)', background: 'var(--lime)', minHeight: '100vh' }}>
        {Object.keys(BEATS).map((b) => <p key={b}><a href={`/tv?gallery=turf&beat=${b}`}>{b}</a></p>)}
      </div>
    )
  }
  const stage: StageInfo = { gameId: 'turf', title: 'Home Turf', phaseSeq: 1, paused: false, game: g as unknown as StageInfo['game'] }
  const clock = { deadline: g.timed ? Date.now() + 12_000 : null, frozen: null }
  return (
    <div className="tv-root">
      <div className="tv-stage" style={{ transform: `scale(${scale}) translate(-50%, -50%)` }}>
        <GameScene game="turf"><TurfStage stage={stage} players={players} scores={[]} clock={clock} /></GameScene>
      </div>
    </div>
  )
}
