// Wire types for the PARTY OS server (tv/server/.../Protocol.kt). Kept in step by the shared fixtures
// in ./protocol/fixtures, which src/protocol.test.ts checks in both directions.

export const PROTOCOL_VERSION = 1

export type Role = 'PLAYER' | 'SPECTATOR'
export interface Avatar { emoji: string; color: string }
export interface PlayerSummary { id: string; name: string; avatar: Avatar; role: Role; connected: boolean }
export interface ScoreRow { id: string; name: string; avatar: Avatar; score: number }
export interface Choice { id: string; text: string }
export interface TutorialCard { title: string; body: string }

export type Screen =
  | { t: 'waiting'; title: string; detail?: string }
  | { t: 'text'; prompt: string; maxLen: number; value?: string; kind: string; hint?: string }
  | { t: 'choice'; prompt: string; options: Choice[]; selected?: string; kind: string }
  | { t: 'tutorial'; cards: TutorialCard[]; acknowledged: boolean }
  | { t: 'scores'; title: string; rows: ScoreRow[] }

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
  | { t: 'start'; gameId: string; rounds?: number }
  | { t: 'pause' }
  | { t: 'resume' }
  | { t: 'skip' }
  | { t: 'end' }
  | { t: 'kick'; playerId: string }
  | { t: 'setRounds'; rounds: number }

export type ActionPayload = { kind: string; [k: string]: string }

export type ClientMsg =
  | { t: 'hello'; protocol: number }
  | { t: 'action'; id: string; round: number; payload: ActionPayload }
  | { t: 'host'; id: string; cmd: HostCommand }
  | { t: 'ping' }

export interface GameListing { id: string; title: string; tagline: string; minPlayers: number; maxPlayers: number }

const SCREENS = new Set(['waiting', 'text', 'choice', 'tutorial', 'scores'])
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
    case 'NEXT_ROUND': return "You'll join in at the next round."
    case 'RATE_LIMIT': return 'Slow down a little!'
    case 'NOT_ENOUGH_PLAYERS': return 'Need more players connected.'
    case 'STALE': return ''
    default: return `Couldn't do that (${code}).`
  }
}
