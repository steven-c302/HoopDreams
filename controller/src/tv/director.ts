import { useEffect, useRef } from 'react'
import type { TvState } from '../protocol'
import { setHurry, setMusic, sfx, type Mode } from './audio'
import type { BlackjackTv, BluffTv, TriviaTv } from './types'

type Game = BluffTv | BlackjackTv | TriviaTv | undefined

/** Seconds left on the stage clock, from the snapshot's remaining time anchored when it arrived. */
export function useDeadline(tv: TvState | null): { deadline: number | null; frozen: number | null } {
  const ref = useRef<{ seq: number; deadline: number | null }>({ seq: -1, deadline: null })
  const stage = tv?.stage
  if (!stage) return { deadline: null, frozen: null }
  if (stage.paused) return { deadline: null, frozen: stage.remainingMs ?? 0 }
  if (ref.current.seq !== stage.phaseSeq || ref.current.deadline == null) {
    ref.current = { seq: stage.phaseSeq, deadline: stage.remainingMs != null ? Date.now() + stage.remainingMs : stage.deadlineAt ?? null }
  }
  return { deadline: ref.current.deadline, frozen: null }
}

function musicFor(tv: TvState): Mode {
  const s = tv.stage
  if (!s) return 'lobby'
  if (s.tutorial) return s.gameId === 'blackjack' ? 'casino' : s.gameId === 'trivia' ? 'teamup' : 'lobby'
  const g = s.game as unknown as Game
  if (!g) return 'lobby'
  if (g.t === 'blackjack') return g.phase === 'dealer' ? 'reveal' : g.phase === 'podium' ? 'podium' : 'casino'
  if (g.t === 'trivia') {
    switch (g.phase) {
      case 'teamup': return 'teamup'
      case 'standings': return 'standings'
      case 'podium': return 'podium'
      case 'victim': case 'steal': return 'heist'
      case 'intro': return g.format === 'teamup' ? 'teamup' : (g.format as Mode)
      default: return g.format === 'teamup' ? 'teamup' : (g.format as Mode)
    }
  }
  switch (g.phase) {
    case 'write': case 'pick': return 'bluff'
    case 'reveal': return 'reveal'
    case 'podium': return 'podium'
    default: return 'scores'
  }
}

/**
 * Turns snapshot changes into sound: joins climb a scale, each bluff/bet in clinks higher, phases get
 * stingers, the music follows the phase and hurries in the last ten seconds. Per-card and per-reveal sounds
 * are played by the stage components themselves, in time with their animations.
 */
export function useCueDirector(tv: TvState | null, deadline: number | null) {
  const prev = useRef<TvState | null>(null)

  useEffect(() => {
    const before = prev.current
    prev.current = tv
    if (!tv) return
    setMusic(musicFor(tv))
    if (!before) return

    // Contestants.
    const players = (s: TvState) => s.players.filter((p) => p.role === 'PLAYER' && p.connected).map((p) => p.id)
    const was = new Set(players(before))
    const now = players(tv)
    const joined = now.filter((id) => !was.has(id))
    joined.forEach((_, i) => setTimeout(() => sfx.join(was.size + i), i * 120))
    if (!tv.stage && now.length < was.size) sfx.leave()

    const a = before.stage, b = tv.stage
    if (!a && b) { sfx.showOpen(); return }
    if (a && !b) { sfx.crash(); return }
    if (!a || !b) return

    if (a.paused !== b.paused) { if (b.paused) sfx.scratch(); else sfx.resume(); return }
    if (a.tutorial && b.tutorial && b.tutorial.acked.length > a.tutorial.acked.length) sfx.ding()

    const ga = a.game as unknown as Game, gb = b.game as unknown as Game
    if (!gb) return
    const newPhase = a.phaseSeq !== b.phaseSeq || !ga || ga.t !== gb.t || ga.phase !== gb.phase

    if (gb.t === 'bluff') {
      if (newPhase) {
        if (gb.phase === 'write') sfx.roundStart(gb.finalRound)
        if (gb.phase === 'pick' || gb.phase === 'reveal') {
          const all = ga && ga.t === 'bluff' && ga.submitted >= ga.expected
          if (!all) sfx.buzzer()
        }
        if (gb.phase === 'reveal') sfx.drumroll(0.8)
        if (gb.phase === 'scores') sfx.whoosh()
        if (gb.phase === 'podium') sfx.drumroll(2.4)
      } else if (ga && ga.t === 'bluff' && gb.submitted > ga.submitted) {
        sfx.chip(gb.submitted)
        if (gb.submitted >= gb.expected) setTimeout(() => sfx.allIn(), 180)
      }
    }

    if (gb.t === 'trivia') {
      const pa = ga && ga.t === 'trivia' ? ga : null
      if (newPhase) {
        if (gb.phase === 'question') sfx.whoosh()
        if ((gb.phase === 'reveal' || gb.phase === 'victim') && pa?.phase === 'question' && pa.answered < pa.expected) sfx.buzzer()
        if (gb.phase === 'reveal' && gb.format === 'ballpark') sfx.drumroll(1.1)
        if (gb.phase === 'standings') sfx.whoosh()
        if (gb.phase === 'podium') sfx.drumroll(2.4)
      } else if (pa) {
        if (gb.phase === 'question' && gb.answered > pa.answered) {
          sfx.vote(gb.answered)
          if (gb.answered >= gb.expected) setTimeout(() => sfx.allIn(), 160)
        }
        if (gb.phase === 'teamup') {
          const count = (t: TriviaTv) => t.teams.reduce((n, x) => n + x.members.length, 0)
          if (count(gb) > count(pa)) sfx.boing()
          if (gb.teams.some((t, i) => t.name !== pa.teams[i]?.name)) sfx.stamp()
        }
      }
    }

    if (gb.t === 'blackjack') {
      if (newPhase) {
        if (gb.phase === 'bet') sfx.roundStart(gb.finalRound)
        if (gb.phase === 'dealer') { sfx.cardFlip(); sfx.drumroll(0.9) }
        if (gb.phase === 'podium') sfx.drumroll(2.4)
      } else if (ga && ga.t === 'blackjack') {
        if (gb.phase === 'bet' && gb.submitted > ga.submitted) sfx.chipClack(gb.submitted)
        if (gb.phase === 'dealer' && gb.dealer.length > ga.dealer.length) { sfx.cardFlick(gb.dealer.length); if (gb.onTheLine >= 8) setTimeout(() => sfx.ooh(2), 300) }
        if (gb.phase === 'play') {
          const before = new Map(ga.seats.map((s) => [s.id, s]))
          for (const s of gb.seats) {
            const o = before.get(s.id)
            if (!o) continue
            if (s.cards.length > o.cards.length) sfx.cardFlick(s.cards.length)
            if (s.status !== o.status) {
              if (s.status === 'bust') setTimeout(() => sfx.bust(), 260)
              else if (s.status === 'stood') sfx.chipClack(1)
            }
            if (s.doubled && !o.doubled) sfx.chipClack(6)
          }
        }
      }
    }
  }, [tv])

  // Last ten seconds: the band hurries; last five: woodblock ticks that climb at 3-2-1.
  useEffect(() => {
    if (!deadline) { setHurry(false); return }
    let last = -1
    const id = setInterval(() => {
      const left = Math.ceil((deadline - Date.now()) / 1000)
      setHurry(left > 0 && left <= 10)
      if (left !== last && left > 0 && left <= 5) sfx.tick(left)
      last = left
    }, 100)
    return () => { clearInterval(id); setHurry(false) }
  }, [deadline])
}
