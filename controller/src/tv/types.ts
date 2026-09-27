import type { Avatar, PlayingCard } from '../protocol'

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

export type TriviaFormat = 'teamup' | 'quick' | 'ballpark' | 'sides' | 'heist' | 'gauntlet'
export type TriviaPhase = 'teamup' | 'intro' | 'question' | 'reveal' | 'victim' | 'steal' | 'standings' | 'podium' | 'awards'
export interface TriviaTeam { id: string; name: string; color: string; members: string[]; score: number; answered: number; position: number; headStart: number }
/** One end-of-show award. `line` never names the player; the TV shows their face and name. */
export interface TriviaAward { title: string; player: string; line: string; roast?: boolean }
export interface TeamAnswer { team: string; choice?: string; number?: number; picks: string[]; correct: boolean; points: number; rank?: number; moved?: number; bullseye: boolean; seconds?: number }
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

export const ROUND_TITLES: Record<string, string> = { teamup: 'Team Up', quick: 'Quick Draw', ballpark: 'Ballpark', sides: 'Pick a Side', heist: 'The Heist', gauntlet: 'The Gauntlet' }
export const ROUND_RULES: Record<string, string> = {
  quick: "Four answers. Your team's top pick counts. Faster is worth more.",
  ballpark: "Guess the number. Your team's guess is the middle of everyone's. Closest wins.",
  sides: 'Quick calls, five seconds each. Which side does it belong on?',
  heist: 'Right answers win 500. The fastest team robs somebody.',
  gauntlet: 'Pick every answer that fits. Right picks move you forward, wrong ones back. First to the finish wins.',
}
