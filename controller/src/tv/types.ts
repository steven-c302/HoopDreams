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
export type TriviaFormat = 'teamup' | 'quick' | 'ballpark' | 'sides' | 'heist' | 'write' | 'gauntlet' | 'final'
export type TriviaPhase = 'teamup' | 'intro' | 'question' | 'bet' | 'reveal' | 'victim' | 'steal' | 'standings' | 'final_category' | 'final_wager' | 'final_question' | 'final_reveal' | 'podium' | 'awards'
export interface TriviaTeam { id: string; name: string; color: string; members: string[]; score: number; answered: number; position: number; headStart: number }
/** One end-of-show award. `line` never names the player; the TV shows their face and name. */
export interface TriviaAward { title: string; player: string; line: string; roast?: boolean }
/** Ballpark betting: one backable guess and what backing it pays (the multiplier on the stake). */
export interface BetOption { team: string; number: number; odds: number }
/** How a team's bet came out; `delta` is the real change to their score. */
export interface BetResult { on?: string; stake: number; odds: number; won: boolean; delta: number }
export interface TeamAnswer { team: string; choice?: string; number?: number; picks: string[]; correct: boolean; points: number; rank?: number; moved?: number; bullseye: boolean; seconds?: number; text?: string; bet?: BetResult }
/** One team's result in the Final Wager reveal; `before`/`after` are its score around the wager. */
export interface FinaleResult { team: string; text?: string; right: boolean; option: string; wager: number; delta: number; before: number; after: number }
/** The Final Wager: the category, which teams have wagered, and (reveal only) the answer and every result, last place first. */
export interface FinaleInfo { category: string; locked: string[]; results: FinaleResult[]; answerText?: string }
export interface TriviaTv {
  t: 'trivia'; phase: TriviaPhase; format: TriviaFormat; round: number; totalRounds: number; q: number; qTotal: number; durationMs?: number
  prompt: string; category?: string; options: { id: string; text: string }[]; unit?: string; teams: TriviaTeam[]; answered: number; expected: number
  reveal?: { correct: string[]; answerText: string; number?: number; answers: TeamAnswer[] }
  sides?: { left: string; right: string; item: number; items: number; history: { text: string; side: 'left' | 'right'; teamsRight: string[] }[] }
  heist?: { thief: string; victim?: string; amount: number }
  drink?: { teams: string[]; sips: number; reason: string }
  /** The bet phase only: every guess with its odds, and which teams have a bet in (not who backed what). */
  bet?: { line: BetOption[]; locked: string[] }
  finale?: FinaleInfo
  hostLine?: string; fact?: string; finishLine: number; podium: string[]
  /** Where a live question came from ("Open Trivia DB"); absent for the bundled packs. */
  credit?: string
  /** End-of-show shout-outs, on the awards screen only. */
  awards?: TriviaAward[]
}

export const ROUND_TITLES: Record<string, string> = {
  teamup: 'Team Up', quick: 'Quick Draw', ballpark: 'Ballpark', sides: 'Pick a Side', heist: 'The Heist', write: 'Write It Down', gauntlet: 'The Gauntlet', final: 'The Final Wager',
}
export const ROUND_RULES: Record<string, string> = {
  final: 'Bet your points before you see the question. Last place reveals first.',
  quick: "Four answers. Your team's top pick counts. Faster is worth more.",
  ballpark: "Guess the number. Your team's guess is the middle of everyone's. Closest wins. Then bet on whose guess is closest.",
  sides: 'Quick calls, five seconds each. Which side does it belong on?',
  heist: 'Right answers win 500. The fastest team robs somebody.',
  write: "No options this time. Type the answer; your team's most-written one counts. Close spelling is fine.",
  gauntlet: 'Pick every answer that fits. Right picks move you forward, wrong ones back. First to the finish wins.',
}

// ---- Jeopardy (tv/engine/.../JeopardyViews.kt) ----------------------------------------------

export interface JeopardyCellTv { id: string; col: number; row: number; value: number; used: boolean }
export interface JeopardyFinalStep { id: string; name: string; answer?: string; wager: number; right: boolean; delta: number; total: number }
export interface JeopardyFinalTv { category: string; clue?: string; answer?: string; wagers: number; expected: number; steps: JeopardyFinalStep[] }
export interface JeopardyTv {
  t: 'jeopardy'
  phase: 'intro' | 'pick' | 'wager' | 'clue' | 'buzz' | 'answer' | 'reveal' | 'break' | 'final_category' | 'final_wager' | 'final_answer' | 'final_reveal' | 'podium'
  round: number; boards: number
  categories: string[]; cells: JeopardyCellTv[]
  controller?: string
  category?: string; value?: number; clue?: string
  dailyDouble: boolean; wager?: number
  buzzOpen: boolean; floor?: string; locked: string[]; tried: string[]
  answer?: string; right?: boolean
  deltas: { id: string; name: string; points: number }[]
  drinks: { id: string; name: string; sips: number; text: string }[]
  final?: JeopardyFinalTv
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
  /** Quick pace: the short move with no dice theatre. Absent or false means Theatre. */
  quick?: boolean
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


// ---- Doodle Dash (tv/engine/.../DoodleViews.kt) ---------------------------------------------

export interface DoodleTv {
  t: 'doodle'; phase: 'pick' | 'draw' | 'reveal' | 'scores' | 'podium'
  turn: number; totalTurns: number; finalTurn: boolean
  drawer?: string; drawerName: string; difficulty: number
  /** Draw only: "_ _ _ _ _" with any revealed letters in capitals. */
  blanks: string
  guessed: number; expected: number
  /** The whole draw time, and the part of it after the current hint stage. */
  drawMs: number; tailMs: number
  solvers: { id: string; name: string; points?: number }[]
  wrong: { id: string; name: string; text: string }[]; missTotal: number
  word?: string
  drinks: { id: string; name: string; sips: number; text: string }[]
  deltas: { id: string; name: string; points: number }[]
  gallery: { turn: number; word: string; drawer: string; drawerName: string; first?: string; firstName?: string }[]
}

// ---- Imposter (tv/engine/.../ImposterViews.kt) ----------------------------------------------

export interface ImposterTv {
  t: 'imposter'; phase: 'role' | 'clue' | 'discuss' | 'vote' | 'result' | 'guess' | 'scores' | 'podium'
  round: number; totalRounds: number; finalRound: boolean; category: string
  submitted: number; expected: number; imposterCount: number
  clues: { id: string; name: string; text?: string }[]
  /** Held back until any caught imposter has guessed. */
  word?: string
  imposters: string[]; accused: string[]
  votes: { voter: string; suspect: string }[]
  guesses: { id: string; name: string; text: string; right: boolean }[]
  drinks: { id: string; name: string; sips: number; text: string }[]
  deltas: { id: string; name: string; points: number }[]
}

// ---- Hot Type (tv/engine/.../HotTypeViews.kt) ------------------------------------------------

export interface HotTypeTv {
  t: 'hottype'; phase: 'ready' | 'hunt' | 'press' | 'reveal' | 'scores' | 'podium'
  round: number; totalRounds: number; finalRound: boolean; size: number
  tiles: string[]
  /** Hunt and press only. Counts and word lengths, never words. */
  rail: { id: string; name: string; count: number; score: number; lengths: number[] }[]
  wordsFound: number; longest: number
  /** The latest word of 6 or more letters: a length, never the word. seq rises with each one. */
  bigFind?: { seq: number; id: string; name: string; letters: number }
  /** Reveal onward, in stamping order (rising points, longest last). */
  page: { word: string; points: number; bonus: number; finders: string[]; longest: boolean }[]
  missed?: { word: string; points: number }
  deltas: { id: string; name: string; base: number; unique: number; longest: number; total: number }[]
  drinks: { id: string; name: string; sips: number; text: string }[]
}
