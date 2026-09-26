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
