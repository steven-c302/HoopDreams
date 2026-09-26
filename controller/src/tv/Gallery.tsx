import { useLayoutEffect, useState } from 'react'
import type { PlayerSummary, StageInfo } from '../protocol'
import { Card } from './Card'
import { Chip, Led } from './Casino'
import { SuitSprite } from './Suits'
import { TriviaStage } from './TriviaStage'
import type { TriviaTeam, TriviaTv } from './types'

/**
 * Design review sheets. /tv?gallery shows the deck, chips and readouts; /tv?gallery=trivia&beat=<name> renders one
 * BRAIN DRAIN beat from fixture data at 1920×1080 (no server needed); /tv?gallery=trivia lists the beats.
 */
export function Gallery() {
  const params = new URLSearchParams(location.search)
  if (params.get('gallery') === 'trivia') return <TriviaGallery beat={params.get('beat')} />
  return (
    <div style={{ position: 'fixed', inset: 0, overflow: 'auto', background: 'var(--felt)', padding: 24 }}>
      <SuitSprite />
      {[0, 1, 2, 3].map((suit) => (
        <div key={suit} style={{ display: 'flex', gap: 10, marginBottom: 14 }}>
          {Array.from({ length: 13 }, (_, i) => <Card key={i} card={{ rank: i + 1, suit }} width={110} animate={false} />)}
        </div>
      ))}
      <div style={{ display: 'flex', gap: 18, alignItems: 'center' }}>
        <Card card={{ rank: 0, suit: 0 }} width={110} animate={false} />
        {[100, 250, 500, 1000].map((v) => <Chip key={v} value={v} size={90} />)}
        <Led value={21} tone="gold" size={60} /><Led value={17} size={60} /><Led value={24} tone="red" size={60} />
      </div>
    </div>
  )
}

const NAMES = ['Ava', 'Ben', 'Cleo', 'Dev', 'Eli', 'Fin', 'Gus', 'Hana', 'Ivy', 'Jay', 'Kai', 'Lu']
const COLORS = ['#FF4B3E', '#FF8A2B', '#FFD23F', '#2FBF55', '#7FD3FF', '#2F6BFF', '#8B4DFF', '#FF6FB5']
const players: PlayerSummary[] = NAMES.map((name, i) => ({
  id: `p${i}`, name, role: 'PLAYER', connected: true, avatar: { face: `p:${String(i).padStart(2, '0')}`, color: COLORS[i % COLORS.length] },
}))
const team = (i: number, name: string, color: string, score: number, extra: Partial<TriviaTeam> = {}): TriviaTeam =>
  ({ id: `T${i + 1}`, name, color, members: players.slice(i * 4, i * 4 + 4).map((p) => p.id), score, answered: 0, position: 0, headStart: 0, ...extra })
const teams = [team(0, 'Smarty Pints', '#FF4B3E', 4200), team(1, 'Les Quizerables', '#2F6BFF', 5400), team(2, 'Sip Happens', '#2FBF55', 3100)]
const quickOptions = [{ id: 'a', text: 'USA' }, { id: 'b', text: 'Finland' }, { id: 'c', text: 'Colombia' }, { id: 'd', text: 'Italy' }]
const base: TriviaTv = {
  t: 'trivia', phase: 'question', format: 'quick', round: 1, totalRounds: 5, q: 2, qTotal: 5, durationMs: 25000, prompt: 'Which country drinks the most coffee per person?',
  category: 'Food & Drink', options: quickOptions, teams: teams.map((t, i) => ({ ...t, answered: [4, 2, 3][i] })), answered: 9, expected: 12, finishLine: 10, podium: [],
}

const BEATS: Record<string, TriviaTv> = {
  teamup: { ...base, phase: 'teamup', format: 'teamup', q: 0, qTotal: 0, prompt: 'Team up!', category: undefined, options: [], durationMs: 45000, hostLine: 'Pick your people wisely.',
    teams: [team(0, 'Team Tomato', '#FF4B3E', 0, { members: ['p0', 'p1', 'p2'] }), team(1, 'Brain Freeze', '#2F6BFF', 0, { members: ['p4', 'p5'] }), team(2, 'Team Lime', '#2FBF55', 0, { members: ['p8', 'p9', 'p10'] })] },
  intro: { ...base, phase: 'intro', format: 'ballpark', q: 0, prompt: '', options: [] },
  'quick-question': base,
  'quick-reveal': { ...base, phase: 'reveal', reveal: { correct: ['b'], answerText: 'Finland', answers: [
    { team: 'T1', choice: 'a', picks: [], correct: false, points: 0, bullseye: false }, { team: 'T2', choice: 'b', picks: [], correct: true, points: 1380, bullseye: false, seconds: 3.1 },
    { team: 'T3', choice: 'b', picks: [], correct: true, points: 1120, bullseye: false, seconds: 9.4 }] },
    hostLine: '2 of 3 teams got it.', fact: 'Finns get through about 12 kg of coffee each per year.' },
  'ballpark-question': { ...base, format: 'ballpark', prompt: 'How many bones are in the adult human body?', category: 'Body', unit: 'bones', options: [], durationMs: 35000 },
  'ballpark-reveal': { ...base, format: 'ballpark', phase: 'reveal', prompt: 'How many bones are in the adult human body?', category: 'Body', unit: 'bones', options: [],
    reveal: { correct: [], answerText: '206 bones', number: 206, answers: [
      { team: 'T1', number: 180, picks: [], correct: false, points: 0, rank: 2, bullseye: false }, { team: 'T2', number: 206, picks: [], correct: true, points: 1500, rank: 1, bullseye: true },
      { team: 'T3', number: 320, picks: [], correct: false, points: 0, rank: 3, bullseye: false }] }, hostLine: 'Les Quizerables nailed it. Who\'s googling?', fact: 'Babies are born with around 300.' },
  'sides-question': { ...base, format: 'sides', prompt: 'Platypus', category: 'Mammal or not?', q: 3, qTotal: 7, durationMs: 6000,
    options: [{ id: 'left', text: 'Mammal' }, { id: 'right', text: 'Not a mammal' }],
    sides: { left: 'Mammal', right: 'Not a mammal', item: 3, items: 7, history: [{ text: 'Dolphin', side: 'left', teamsRight: ['T1', 'T2'] }, { text: 'Seahorse', side: 'right', teamsRight: ['T3'] }] } },
  'sides-reveal': { ...base, format: 'sides', phase: 'reveal', prompt: 'Platypus', category: 'Mammal or not?', q: 3, qTotal: 7, options: [{ id: 'left', text: 'Mammal' }, { id: 'right', text: 'Not a mammal' }],
    reveal: { correct: ['left'], answerText: 'Mammal', answers: [] },
    sides: { left: 'Mammal', right: 'Not a mammal', item: 3, items: 7, history: [{ text: 'Dolphin', side: 'left', teamsRight: ['T1', 'T2'] }, { text: 'Seahorse', side: 'right', teamsRight: ['T3'] }, { text: 'Platypus', side: 'left', teamsRight: ['T2'] }] } },
  victim: { ...base, format: 'heist', phase: 'victim', durationMs: 12000, heist: { thief: 'T2', amount: 0 }, hostLine: 'Les Quizerables were fastest. Who are they robbing?' },
  steal: { ...base, format: 'heist', phase: 'steal', heist: { thief: 'T2', victim: 'T1', amount: 500 }, drink: { teams: ['T1'], sips: 1, reason: 'robbed' }, hostLine: 'Les Quizerables robbed Smarty Pints for 500.' },
  standings: { ...base, phase: 'standings', round: 2, drink: { teams: ['T3'], sips: 1, reason: 'last place' }, hostLine: 'Les Quizerables on top. For now.' },
  'gauntlet-question': { ...base, format: 'gauntlet', q: 3, qTotal: 8, prompt: 'Which of these are Mario Kart items?', category: undefined, durationMs: 30000,
    options: [{ id: 'a', text: 'Blue Shell' }, { id: 'b', text: 'Poké Ball' }, { id: 'c', text: 'Banana' }],
    teams: [team(0, 'Smarty Pints', '#FF4B3E', 4200, { position: 5 }), team(1, 'Les Quizerables', '#2F6BFF', 5400, { position: 7 }), team(2, 'Sip Happens', '#2FBF55', 3100, { position: 3 })] },
  'gauntlet-reveal': { ...base, format: 'gauntlet', phase: 'reveal', q: 3, qTotal: 8, prompt: 'Which of these are Mario Kart items?', category: undefined,
    options: [{ id: 'a', text: 'Blue Shell' }, { id: 'b', text: 'Poké Ball' }, { id: 'c', text: 'Banana' }],
    reveal: { correct: ['a', 'c'], answerText: 'Blue Shell, Banana', answers: [{ team: 'T1', picks: ['a', 'c'], correct: true, points: 0, moved: 2, bullseye: false },
      { team: 'T2', picks: ['a', 'b'], correct: false, points: 0, moved: 0, bullseye: false }, { team: 'T3', picks: ['c'], correct: true, points: 0, moved: 1, bullseye: false }] },
    teams: [team(0, 'Smarty Pints', '#FF4B3E', 4200, { position: 7 }), team(1, 'Les Quizerables', '#2F6BFF', 5400, { position: 7 }), team(2, 'Sip Happens', '#2FBF55', 3100, { position: 4 })],
    hostLine: 'Smarty Pints can smell the finish.' },
  podium: { ...base, phase: 'podium', podium: ['T2', 'T1', 'T3'], drink: { teams: ['T3'], sips: 2, reason: "didn't escape" }, hostLine: 'Les Quizerables win Brain Drain!' },
}

function TriviaGallery({ beat }: { beat: string | null }) {
  const [scale, setScale] = useState(1)
  useLayoutEffect(() => {
    const fit = () => setScale(Math.min(window.innerWidth / 1920, window.innerHeight / 1080))
    fit(); window.addEventListener('resize', fit)
    return () => window.removeEventListener('resize', fit)
  }, [])
  const g = beat ? BEATS[beat] : null
  if (!g) {
    return (
      <div style={{ padding: 40, fontSize: 24, fontFamily: 'var(--font-body)', background: 'var(--sun)', minHeight: '100vh' }}>
        {Object.keys(BEATS).map((b) => <p key={b}><a href={`/tv?gallery=trivia&beat=${b}`}>{b}</a></p>)}
      </div>
    )
  }
  const stage: StageInfo = { gameId: 'trivia', title: 'Brain Drain', phaseSeq: 1, paused: false, game: g as unknown as StageInfo['game'] }
  const clock = { deadline: g.durationMs ? Date.now() + g.durationMs * 0.6 : null, frozen: null }
  return (
    <div className="tv-root">
      <div className="tv-stage" style={{ transform: `scale(${scale}) translate(-50%, -50%)` }}>
        <TriviaStage stage={stage} players={players} scores={[]} clock={clock} />
      </div>
    </div>
  )
}
