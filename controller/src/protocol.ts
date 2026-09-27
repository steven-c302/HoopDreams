// Wire types for the PARTY OS server (tv/server/.../Protocol.kt). Kept in step by the shared fixtures
// in ./protocol/fixtures, which src/protocol.test.ts checks in both directions.

export const PROTOCOL_VERSION = 1

export type Role = 'PLAYER' | 'SPECTATOR'
/** face: a preset `p:00`..`p:15` or a doodle `d:` + M/L points on a 0..99 grid. */
export interface Avatar { face: string; color: string }
export interface PlayerSummary { id: string; name: string; avatar: Avatar; role: Role; connected: boolean }
export interface ScoreRow { id: string; name: string; avatar: Avatar; score: number }
export interface Choice { id: string; text: string; color?: string; detail?: string }
export interface TeamTag { id: string; name: string; color: string }
export interface TutorialCard { title: string; body: string }
/** rank 1 (ace)..13 (king), suit 0..3 = spades, hearts, diamonds, clubs; rank 0 = face down. */
export interface PlayingCard { rank: number; suit: number }

export type Screen =
  | { t: 'waiting'; title: string; detail?: string; tone?: 'win' | 'lose' | 'neutral'; team?: TeamTag }
  | { t: 'text'; prompt: string; maxLen: number; value?: string; kind: string; hint?: string; team?: TeamTag }
  | { t: 'choice'; prompt: string; options: Choice[]; selected?: string; kind: string; style?: 'shapes' | 'sides' | 'teams'; votes?: Record<string, string[]>; team?: TeamTag }
  | { t: 'number'; prompt: string; unit?: string; value?: number; kind: string; guesses?: { id: string; value: number }[]; team?: TeamTag }
  | { t: 'multi'; prompt: string; options: Choice[]; selected: string[]; locked: boolean; kind: string; eliminated?: string[]; votes?: Record<string, string[]>; team?: TeamTag }
  | { t: 'tutorial'; cards: TutorialCard[]; acknowledged: boolean }
  | { t: 'scores'; title: string; rows: ScoreRow[] }
  | { t: 'cards'; title: string; hand: PlayingCard[]; total?: number; dealer: PlayingCard[]; actions: Choice[]; kind: string; note?: string; tone?: string; stack?: number }
  | TurfScreen

// ---- Home Turf (tv/engine/.../TurfViews.kt) -------------------------------------------------

/** Your token (or team). mine = you hold the dice right now. */
export interface TurfMe {
  index: number; name: string; color: string; piece?: string; cash: number; seat?: string; seatName?: string
  mine: boolean; jailed: boolean; jailCards: number; bankrupt: boolean; pos: number; spaceName: string; worth: number
}
/** kind: wait | watch | out | teamup | pieces | deal | roll | jail | buy | bid | bus | triples | manage | debt | trade | card | over */
export interface TurfPrompt {
  kind: string; title: string; detail?: string; actions: Choice[]; timed: boolean; tone?: 'win' | 'lose' | 'neutral'; space: number; amount: number
}
/** One of your places; build/sell/mortgage/unmortgage are the cost or refund when allowed right now. group: 0-7 streets, 8 rides, 9 utilities. */
export interface TurfDeed {
  space: number; name: string; color: string; group: number; level: number; mortgaged: boolean; rent: number
  build?: number; sell?: number; mortgage?: number; unmortgage?: number; tradable: boolean; set: boolean
}
export interface TurfDeedRef { space: number; name: string; color: string; group: number; mortgaged: boolean; tradable: boolean }
export interface TurfPartner { index: number; name: string; color: string; cash: number; jailCards: number; deeds: TurfDeedRef[] }
export interface TurfTradeView {
  id: number; from: number; to: number; fromName: string; toName: string; give: TurfDeedRef[]; get: TurfDeedRef[]
  giveCash: number; getCash: number; giveCards: number; getCards: number; role: 'from' | 'to' | 'watch'; canCounter: boolean
}
export interface TurfBidPad {
  auction: number; space: number; name: string; color: string; price: number; top: number; leaderName?: string
  leading: boolean; maxBid: number; canBid: boolean
}
export interface TurfScreen {
  t: 'turf'
  me?: TurfMe
  prompt: TurfPrompt
  deeds: TurfDeed[]
  partners: TurfPartner[]
  trade?: TurfTradeView
  canTrade: boolean
  auction?: TurfBidPad
  pieces: Choice[]
  drink?: string
  drinks: boolean
}

export interface PhoneState {
  me: PlayerSummary
  roomCode: string
  gameId?: string
  gameTitle?: string
  round: number
  paused: boolean
  pauseReason?: string
  remainingMs?: number
  screen: Screen
  scores: ScoreRow[]
  /** This phone holds the crown and can run the show. */
  captain: boolean
  captainName?: string
  settings: Record<string, number>
  /** Everyone at the party; sent to the captain only. */
  crew: PlayerSummary[]
}

export interface StageInfo {
  gameId: string
  title: string
  phaseSeq: number
  deadlineAt?: number
  remainingMs?: number
  paused: boolean
  pauseReason?: string
  tutorial?: { cards: TutorialCard[]; acked: string[] }
  game?: { t: string; [k: string]: unknown }
}

export interface GameResult { gameId: string; title: string; finishedAt: number; standings: ScoreRow[]; highlights: string[] }

export interface TvState {
  roomCode: string
  players: PlayerSummary[]
  stage?: StageInfo
  scores: ScoreRow[]
  lastResult?: GameResult
  gamesPlayed: number
  /** Player id holding the crown (absent when phone control is off). */
  captain?: string
  /** Shared lobby settings: rounds, teams, drinks, game (index into /api/games), captain (phones allowed). */
  settings: Record<string, number>
}

export type ServerMsg =
  | { t: 'welcome'; playerId?: string; role?: Role; host: boolean; protocol: number }
  | { t: 'view'; seq: number; view: PhoneState }
  | { t: 'tv'; seq: number; tv: TvState }
  | { t: 'ack'; id: string }
  | { t: 'reject'; id: string; code: string }
  | { t: 'pong' }
  | { t: 'bye'; reason: string }

export type HostCommand =
  | { t: 'start'; gameId: string; rounds?: number; options: Record<string, number> }
  | { t: 'pause' }
  | { t: 'resume' }
  | { t: 'skip' }
  | { t: 'end' }
  | { t: 'kick'; playerId: string }
  | { t: 'setRounds'; rounds: number }
  | { t: 'setOption'; key: OptionKey; value: number }
  | { t: 'makeCaptain'; playerId: string }
  /** A game's own show control, e.g. 'shuffle' during Brain Drain's Team Up. */
  | { t: 'gameAction'; action: string }

/** Shared lobby settings. turfMode: Home Turf 0 auto, 1 solo, 2 teams; minutes: Home Turf's game clock (0 = no limit). */
export type OptionKey = 'rounds' | 'teams' | 'drinks' | 'game' | 'captain' | 'turfMode' | 'minutes'

export type ActionPayload = { kind: string; [k: string]: string | number | boolean | string[] }

export type ClientMsg =
  | { t: 'hello'; protocol: number }
  | { t: 'action'; id: string; round: number; payload: ActionPayload }
  | { t: 'host'; id: string; cmd: HostCommand }
  | { t: 'ping' }

export interface GameListing { id: string; title: string; tagline: string; minPlayers: number; maxPlayers: number }

const SCREENS = new Set(['waiting', 'text', 'choice', 'number', 'multi', 'tutorial', 'scores', 'cards', 'turf'])
const isObj = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null

/** Parses one server frame; returns null for anything malformed or unknown instead of throwing. */
export function parseServerMsg(text: string): ServerMsg | null {
  let m: unknown
  try { m = JSON.parse(text) } catch { return null }
  if (!isObj(m) || typeof m.t !== 'string') return null
  switch (m.t) {
    case 'welcome': return typeof m.host === 'boolean' ? (m as ServerMsg) : null
    case 'view': {
      const v = m.view
      return typeof m.seq === 'number' && isObj(v) && isObj(v.me) && isObj(v.screen) && SCREENS.has(String(v.screen.t)) && typeof v.round === 'number'
        ? (m as ServerMsg) : null
    }
    case 'tv': return typeof m.seq === 'number' && isObj(m.tv) && Array.isArray(m.tv.players) ? (m as ServerMsg) : null
    case 'ack': return typeof m.id === 'string' ? (m as ServerMsg) : null
    case 'reject': return typeof m.id === 'string' && typeof m.code === 'string' ? (m as ServerMsg) : null
    case 'pong': return { t: 'pong' }
    case 'bye': return typeof m.reason === 'string' ? (m as ServerMsg) : null
    default: return null
  }
}

export const encodeClient = (m: ClientMsg): string => JSON.stringify(m)

/** Human text for server rejection codes shown on the phone. Empty string = silent. */
export function rejectMessage(code: string): string {
  switch (code) {
    case 'TOO_TRUE': return "That's too close to the real answer. Try another fake!"
    case 'BAD_TEXT': return 'Answers need 1 to 60 characters.'
    case 'OWN_ANSWER': return "You can't pick your own answer."
    case 'PAUSED': return 'The game is paused.'
    case 'NEXT_ROUND': return "You'll join in at the next question."
    case 'TAKEN': return 'A teammate already named your team.'
    case 'NAME_TAKEN': return 'Another team has that name.'
    case 'NO_TEAM': return 'Pick a team first.'
    case 'BAD_NUMBER': return 'Type a number.'
    case 'NOT_YOUR_HEIST': return "It's not your heist."
    case 'NOT_NOW': return ''
    case 'NOT_CAPTAIN': return 'Only the captain can do that.'
    case 'HOST_ONLY': return 'Only the TV can do that.'
    case 'NO_GAME': return 'No game is running.'
    case 'GAME_RUNNING': return 'Finish or end this game first.'
    case 'RATE_LIMIT': return 'Slow down a little!'
    case 'NOT_ENOUGH_PLAYERS': return 'Need more players connected.'
    case 'STALE': return ''
    // Home Turf
    case 'NOT_YOUR_TURN': return "It's not your turn."
    case 'NOT_YOUR_SEAT': return 'A teammate has the dice right now.'
    case 'NOT_PLAYING': return "You're not in this game."
    case 'PIECE_TAKEN': return 'Someone grabbed that piece. Pick another!'
    case 'ALREADY_PICKED': return 'You already have a piece.'
    case 'CANT_AFFORD': return "Not enough cash. Mortgage or sell something first."
    case 'BID_TOO_LOW': return 'Someone bid higher. Go again!'
    case 'NEED_SET': return 'You need the whole colour set to build.'
    case 'BUILD_EVENLY': return 'Build evenly: add to the places with fewer houses first.'
    case 'SELL_EVENLY': return 'Sell evenly: start with the places with the most houses.'
    case 'MORTGAGED_IN_SET': return 'Pay off the mortgages in this set before building.'
    case 'MAX_BUILT': return 'That place already has a hotel.'
    case 'NO_HOUSES_LEFT': return 'The bank is out of houses.'
    case 'NO_HOTELS_LEFT': return 'The bank is out of hotels.'
    case 'MUST_SELL_BUILDINGS': return 'Sell the buildings in that colour set first.'
    case 'ALREADY_MORTGAGED': return "It's already mortgaged."
    case 'NOT_MORTGAGED': return "It isn't mortgaged."
    case 'NOTHING_TO_SELL': return 'Nothing to sell there.'
    case 'NOT_YOURS': return "That's not yours."
    case 'NOT_A_STREET': case 'NOT_A_DEED': return "You can't do that there."
    case 'NO_CARD': return "You don't have a Get Out card."
    case 'PAY_FIRST': return 'Settle up first: sell or mortgage, then pay.'
    case 'TRADE_OPEN': return 'A trade is on the table. Wait for it to finish.'
    case 'TRADE_LATER': return 'Wait for the dice to settle, then send it.'
    case 'TRADE_GONE': return 'That offer is off the table.'
    case 'ONE_OFFER': return 'One offer per person per turn.'
    case 'NO_MORE_COUNTERS': return 'No more counters: accept or reject.'
    case 'EMPTY_TRADE': return 'Add something to the deal first.'
    case 'BAD_TRADE': return "That deal doesn't work."
    default: return `Couldn't do that (${code}).`
  }
}
