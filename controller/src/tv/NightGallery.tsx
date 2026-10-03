import '../theme/tokens.css'
import './tv.css'
import type { NightRecap, NightRow } from '../protocol'
import { NightReview } from './NightReview'
import { C, Scene } from './toon'

const NAMES = ['Amanda', 'Steven', 'John', 'Sunhye', 'Alex', 'Anna', 'Charlie', 'Daniel', 'Daniel K', 'Ethan', 'Junha', 'Kaishun', 'Izzy', 'Maximilian-Longname', 'Wes', 'Zoe']
const COLORS = ['#FF4B3E', '#FF8A2B', '#FFD23F', '#2FBF55', '#7FD3FF', '#2F6BFF', '#8B4DFF', '#FF6FB5']

/** /tv?gallery=night[&n=16][&awards=0][&moments=0]: the Night in Review card from fixture data at 1920×1080, no server needed. */
export function NightGallery() {
  const q = new URLSearchParams(location.search)
  const n = Math.min(16, Math.max(2, Number(q.get('n') ?? 12) || 12))
  const people = NAMES.slice(0, n).map((name, i) => ({ id: `p${i}`, name, avatar: { face: `p:${String(i).padStart(2, '0')}`, color: COLORS[i % COLORS.length] } }))
  const board: NightRow[] = people.map((p, i) => ({ ...p, points: Math.max(0, 14 - i), wins: Math.max(0, 3 - Math.floor(i / 2)) }))
  const night: NightRecap = {
    games: 4,
    board,
    awards: q.get('awards') === '0' ? [] : [
      { title: 'NIGHT CHAMP', players: [people[0]], note: '14 points' },
      { title: 'MOST WINS', players: [people[0], people[1]], note: '3 wins' },
      { title: 'HOT STREAK', players: [people[2]], note: '2 in a row' },
      { title: 'COMEBACK KID', players: [people[n - 1]], note: 'Last in Home Turf, first in Bluff Battle' },
      { title: 'WOODEN SPOON', players: people.slice(n - 3, n), note: 'Last 2 times' },
    ],
    moments: q.get('moments') === '0' ? [] : [
      { game: 'Bluff Battle', text: `${people[1].name} fooled 4 people with “wombat poop is shaped like stars”` },
      { game: 'Home Turf', text: `${people[0].name} won Home Turf with $4200` },
      { game: 'Brain Drain', text: `${people[2].name} won Brain Drain` },
    ],
  }
  return (
    <div className="tv-root">
      <div className="tv-stage" style={{ transform: `scale(${Math.min(innerWidth / 1920, innerHeight / 1080)}) translate(-50%, -50%)` }}>
        <Scene color={C.sun}><NightReview night={night} onClose={() => undefined} /></Scene>
      </div>
    </div>
  )
}
