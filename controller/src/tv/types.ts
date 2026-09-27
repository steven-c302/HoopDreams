import type { Avatar, PlayingCard, SprawlMap } from '../protocol'

export interface BluffReveal { text: string; kind: 'fake' | 'decoy' | 'truth'; authors: string[]; fooled: string[] }
export interface BluffTv {
  t: 'bluff'; phase: 'write' | 'pick' | 'reveal' | 'scores' | 'podium'; round: number; totalRounds: number; finalRound: boolean
  prompt: string; submitted: number; expected: number; options: string[]; reveal: BluffReveal[]; deltas: { id: string; name: string; points: number }[]
}

export interface BjSeat {
  id: string; name: string; avatar: Avatar; cards: PlayingCard[]; total: number; bet: number; doubled: boolean
  status: 'betting' | 'ready' | 'playing' | 'stood' | 'bust' | 'blackjack'
  outcome?: 'blackjack' | 'win' | 'push' | 'lose' | 'bust'; drinks?: number
}
export interface BlackjackTv {
  t: 'blackjack'; phase: 'bet' | 'play' | 'dealer' | 'settle' | 'podium'; round: number; totalRounds: number; finalRound: boolean
  rule: string; ruleName: string; ruleText: string
  dealerId?: string; dealerName: string; dealerAvatar?: Avatar
  dealer: PlayingCard[]; dealerTotal?: number; onTheLine: number; dealerDrinks: number
  seats: BjSeat[]; submitted: number; expected: number
}

/** Sips as people say them: 5 sips is a shot. */
export function sipLabel(n: number): string {
  const shots = Math.floor(n / 5), sips = n % 5
  return [shots ? `${shots} SHOT${shots > 1 ? 'S' : ''}` : '', sips ? `${sips} SIP${sips > 1 ? 'S' : ''}` : ''].filter(Boolean).join(' + ') || '0 SIPS'
}

/** Games that run on the trivia engine (TriviaTv on the TV): Brain Drain, and Write It Down played on its own. */
export const isTrivia = (gameId?: string | null) => gameId === 'trivia' || gameId === 'writeitdown'

/** 'gauntlet' only appears in a show saved before it was retired. */
export type TriviaFormat = 'teamup' | 'quick' | 'ballpark' | 'sides' | 'heist' | 'write' | 'gauntlet'
export type TriviaPhase = 'teamup' | 'intro' | 'question' | 'reveal' | 'victim' | 'steal' | 'standings' | 'podium' | 'awards'
export interface TriviaTeam { id: string; name: string; color: string; members: string[]; score: number; answered: number; position: number; headStart: number }
/** One end-of-show award. `line` never names the player; the TV shows their face and name. */
export interface TriviaAward { title: string; player: string; line: string; roast?: boolean }
export interface TeamAnswer { team: string; choice?: string; number?: number; picks: string[]; correct: boolean; points: number; rank?: number; moved?: number; bullseye: boolean; seconds?: number; text?: string }
export interface TriviaTv {
  t: 'trivia'; phase: TriviaPhase; format: TriviaFormat; round: number; totalRounds: number; q: number; qTotal: number; durationMs?: number
  prompt: string; category?: string; options: { id: string; text: string }[]; unit?: string; teams: TriviaTeam[]; answered: number; expected: number
  reveal?: { correct: string[]; answerText: string; number?: number; answers: TeamAnswer[] }
  sides?: { left: string; right: string; item: number; items: number; history: { text: string; side: 'left' | 'right'; teamsRight: string[] }[] }
  heist?: { thief: string; victim?: string; amount: number }
  drink?: { teams: string[]; sips: number; reason: string }
  hostLine?: string; fact?: string; finishLine: number; podium: string[]
  /** Where a live question came from ("Open Trivia DB"); absent for the bundled packs. */
  credit?: string
  /** End-of-show shout-outs, on the awards screen only. */
  awards?: TriviaAward[]
}

export const ROUND_TITLES: Record<string, string> = {
  teamup: 'Team Up', quick: 'Quick Draw', ballpark: 'Ballpark', sides: 'Pick a Side', heist: 'The Heist', write: 'Write It Down', gauntlet: 'The Gauntlet',
}
export const ROUND_RULES: Record<string, string> = {
  quick: "Four answers. Your team's top pick counts. Faster is worth more.",
  ballpark: "Guess the number. Your team's guess is the middle of everyone's. Closest wins.",
  sides: 'Quick calls, five seconds each. Which side does it belong on?',
  heist: 'Right answers win 500. The fastest team robs somebody.',
  write: "No options this time. Type the answer; your team's most-written one counts. Close spelling is fine.",
  gauntlet: 'Pick every answer that fits. Right picks move you forward, wrong ones back. First to the finish wins.',
}

// ---- Home Turf (tv/engine/.../TurfViews.kt) -------------------------------------------------

/** kind: payday | street | railroad | utility | chance | chest | tax | jail | couch | gotojail */
export interface TurfSpace { name: string; label: string; kind: string; group: number; color?: string; price: number; rent: number[]; houseCost: number; tax: number }
export interface TurfToken {
  name: string; color: string; piece?: string; members: string[]; seat?: string; cash: number; pos: number
  jailed: boolean; jailCards: number; bankrupt: boolean; worth: number; sets: number
}
/** Something that just happened, oldest first; seq only goes up. Unused fields keep their defaults. */
export interface TurfBeat {
  seq: number; kind: string; token: number; other: number; space: number; amount: number
  dice: number[]; path: number[]; tokens: number[]; sips: number; text?: string
}
export type TurfPhase = 'teamup' | 'pieces' | 'deal' | 'roll' | 'jail' | 'move' | 'buy' | 'auction' | 'card' | 'choose' | 'manage' | 'debt' | 'trade' | 'tally' | 'podium'
export interface TurfTv {
  t: 'turf'
  phase: TurfPhase
  teams: boolean
  board: TurfSpace[]
  chanceName: string
  chestName: string
  owner: number[]
  level: number[]
  mortgaged: number[]
  tokens: TurfToken[]
  turn: number
  dice: number[]
  doubles: number
  housesLeft: number
  hotelsLeft: number
  buy: number
  choose?: 'bus' | 'triples'
  card?: { deck: string; deckName: string; text: string; sips: number }
  auction?: { id: number; space: number; top: number; leader: number; bids: number }
  trade?: { id: number; from: number; to: number; give: number[]; get: number[]; giveCash: number; getCash: number; giveCards: number; getCards: number; counters: number }
  debt?: { token: number; amount: number; to: number; why: string }
  clockLeftMs?: number
  phaseMs?: number
  lastLap: boolean
  timed: boolean
  drinks: boolean
  beats: TurfBeat[]
  ticker: string[]
  pieces: string[]
  tally: { token: number; worth: number; cash: number; places: number; buildings: number; rank: number }[]
  notice?: string
}

/** Speed die faces beyond the pips. */
export const SPEED_BUS = 4
export const SPEED_SCOUT = 5
export const TURF_HOTEL = 4

// ---- Sprawl (tv/engine/.../SprawlViews.kt) -------------------------------------------------

export interface SprawlBeat {
  seq: number; kind: string; seat: number; other: number; target: number; amount: number
  dice: number[]; seats: number[]; targets: number[]; gains: number[][]; sips: number; text?: string
}
/** vp leaves out hidden VP cards until the tally; discard = cards still owed on a 7. */
export interface SprawlSeat {
  name: string; color: string; player: string; cards: number; dev: number; vp: number; knights: number; road: number
  longest: boolean; army: boolean; gone: boolean; discard: number
}
export type SprawlPhase = 'setup' | 'roll' | 'discard' | 'robber' | 'steal' | 'main' | 'road2' | 'pick' | 'trade' | 'tally' | 'podium'
export interface SprawlTv {
  t: 'sprawl'
  phase: SprawlPhase
  map: SprawlMap
  robber: number
  vOwner: number[]
  vLevel: number[]
  eOwner: number[]
  seats: SprawlSeat[]
  turn: number
  setupPiece?: 'settlement' | 'road'
  setupRound: number
  dice: number[]
  trade?: { id: number; from: number; to: number; give: number[]; get: number[]; counters: number; passed: number[] }
  peek: number
  peekKind?: 'vertex' | 'edge' | 'hex'
  pick?: string
  bank: number[]
  deckLeft: number
  clockLeftMs?: number
  phaseMs?: number
  lastRound: boolean
  timed: boolean
  vpTarget: number
  drinks: boolean
  beats: SprawlBeat[]
  ticker: string[]
  winner: number
  tally: { seat: number; vp: number; vpCards: number; rank: number }[]
}

