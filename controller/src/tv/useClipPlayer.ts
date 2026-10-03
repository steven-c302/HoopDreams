import { useEffect, useRef, type RefObject } from 'react'
import type { HostCommand } from '../protocol'
import { conduct, START, stopInMs, type ClipAction } from './clipConductor'
import type { SongDropTv } from './types'
import { chooseMaker, type ClipPlayer } from './ytClip'

type Clock = { deadline: number | null; frozen: number | null }

/**
 * Plays Song Drop's clips in the player inside [host] and keeps the engine in step: when a clip is really playing it sends
 * `ready:<seq>` (the engine then starts the guess clock), on any player error `bad:<seq>`, and for a stage it never started
 * (the page was reloaded mid-clip) `replay:<seq>`. [enabled] false leaves the player alone (the design gallery).
 */
export function useClipPlayer(host: RefObject<HTMLDivElement | null>, g: SongDropTv, paused: boolean, clock: Clock, cmd: (c: HostCommand) => void, enabled = true) {
  const player = useRef<ClipPlayer | null>(null)
  const conductor = useRef(START)
  const queue = useRef<ClipAction[]>([])
  const awaiting = useRef<number | null>(null)
  const stopTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const latest = useRef({ phase: g.phase, seq: g.clipSeq })
  latest.current = { phase: g.phase, seq: g.clipSeq }
  const send = useRef(cmd)
  send.current = cmd
  const act = (a: string) => send.current({ t: 'gameAction', action: a })

  const flush = () => {
    const p = player.current
    if (!p) return
    for (const a of queue.current.splice(0)) {
      if (a.t === 'load') { awaiting.current = a.seq; p.setVolume(100); p.load(a.videoId, a.startSec) }
      else if (a.t === 'seek') { awaiting.current = a.seq; p.seek(a.startSec) }
      else if (a.t === 'replay') act(`replay:${a.seq}`)
      else if (a.t === 'hook') { awaiting.current = null; p.setVolume(100); p.load(a.videoId, a.startSec) }
      else { awaiting.current = null; if (stopTimer.current) clearTimeout(stopTimer.current); p.pause() }
    }
  }

  useEffect(() => {
    const el = host.current
    if (!enabled || !el) return
    let dead = false
    const fail = () => {
      const l = latest.current
      if (l.phase === 'load' || l.phase === 'stage') { awaiting.current = null; act(`bad:${l.seq}`) }
    }
    chooseMaker()(el, {
      onPlaying: () => {
        const seq = awaiting.current
        if (seq === null) return
        awaiting.current = null
        act(`ready:${seq}`)
      },
      onError: fail,
      onBlocked: fail,
    }).then((p) => {
      if (dead) { p.destroy(); return }
      player.current = p
      flush()
    }).catch(fail)
    return () => { dead = true; if (stopTimer.current) clearTimeout(stopTimer.current); player.current?.destroy(); player.current = null }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled])

  useEffect(() => {
    if (!enabled) return
    const { next, actions } = conduct(conductor.current, g)
    conductor.current = next
    queue.current.push(...actions)
    flush()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, g.phase, g.clipSeq, g.videoId])

  // The clip ends when the engine's clock does; a pause freezes both.
  useEffect(() => {
    if (stopTimer.current) clearTimeout(stopTimer.current)
    if (!enabled || g.phase !== 'stage') return
    if (paused) { player.current?.pause(); return }
    player.current?.resume()
    stopTimer.current = setTimeout(() => player.current?.pause(), stopInMs(clock, g.clipMs, Date.now()))
    return () => { if (stopTimer.current) clearTimeout(stopTimer.current) }
  }, [enabled, g.phase, g.clipSeq, g.clipMs, paused, clock.deadline, clock.frozen])
}
