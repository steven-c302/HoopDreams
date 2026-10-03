/** What the TV needs to know about the current clip, from the engine's SongDropTv. */
export interface ClipView { phase: string; clipSeq: number; videoId: string; startSec: number }

/** [loaded]: the video in the player; [seq]: the last clip number handled; [hooked]: the clip whose reveal hook has played. */
export interface ConductorState { loaded: string | null; seq: number; hooked: number }
export const START: ConductorState = { loaded: null, seq: 0, hooked: 0 }

export type ClipAction =
  | { t: 'load'; seq: number; videoId: string; startSec: number }
  | { t: 'seek'; seq: number; startSec: number }
  | { t: 'replay'; seq: number }
  | { t: 'hook'; videoId: string; startSec: number }
  | { t: 'stop' }

/**
 * Decides what the player has to do for a snapshot, remembering what it already did so the same snapshot (the TV gets
 * one on every change) never starts a clip twice. Pure: the player and the host socket are the caller's business.
 */
export function conduct(prev: ConductorState, g: ClipView | null): { next: ConductorState; actions: ClipAction[] } {
  const inSong = g != null && (g.phase === 'load' || g.phase === 'stage' || g.phase === 'reveal')
  if (!inSong) {
    return prev.loaded === null ? { next: prev, actions: [] } : { next: { ...prev, loaded: null }, actions: [{ t: 'stop' }] }
  }
  if (g.phase === 'load' && g.clipSeq !== prev.seq) {
    const sameVideo = prev.loaded === g.videoId
    return {
      next: { ...prev, loaded: g.videoId, seq: g.clipSeq },
      actions: [sameVideo ? { t: 'seek', seq: g.clipSeq, startSec: g.startSec } : { t: 'load', seq: g.clipSeq, videoId: g.videoId, startSec: g.startSec }],
    }
  }
  if (g.phase === 'stage' && g.clipSeq !== prev.seq) {
    return { next: { ...prev, seq: g.clipSeq }, actions: [{ t: 'replay', seq: g.clipSeq }] }
  }
  if (g.phase === 'reveal' && g.clipSeq !== prev.hooked) {
    return { next: { ...prev, loaded: g.videoId, seq: g.clipSeq, hooked: g.clipSeq }, actions: [{ t: 'hook', videoId: g.videoId, startSec: g.startSec }] }
  }
  return { next: prev, actions: [] }
}

/** How long until the player must stop the current clip: the engine's remaining time, frozen while paused. */
export function stopInMs(clock: { deadline: number | null; frozen: number | null }, clipMs: number, now: number): number {
  if (clock.frozen != null) return Math.max(0, clock.frozen)
  if (clock.deadline != null) return Math.max(0, clock.deadline - now)
  return clipMs
}
