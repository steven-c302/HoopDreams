import { InkStore } from '../../ink/store'
import type { InkOp } from '../../ink/types'
import type { PlayerSummary, ScoreRow, Screen, StageInfo } from '../../protocol'

// Synthetic beats for the theme gallery (six fictional players). No tokens or real sessions.
const palette = ['#FF4B3E', '#FF8A2B', '#FFD23F', '#2FBF71', '#2F6BFF', '#9B5DE5']
const names = ['Ava', 'Ben', 'Cleo', 'Dev', 'Eli', 'Fay']
const players: PlayerSummary[] = names.map((name, i) => ({ id: `p${i}`, name, avatar: { face: `p:0${i}`, color: palette[i] }, role: 'PLAYER', connected: true }))
const scores: ScoreRow[] = players.map((p, i) => ({ id: p.id, name: p.name, avatar: p.avatar, score: 4200 - i * 600 }))
const me = players[0]

// ---- a demo drawing: a house, a roof, a door, a sun and some grass, as real strokes ----------------------------------
type Pt = [number, number]
const line = (points: Pt[], per = 14): number[] => {
  const out: number[] = []
  for (let i = 1; i < points.length; i++) {
    const [x0, y0] = points[i - 1], [x1, y1] = points[i]
    for (let k = 1; k <= per; k++) out.push(Math.round(x0 + ((x1 - x0) * k) / per), Math.round(y0 + ((y1 - y0) * k) / per), 50)
  }
  return out
}
const circle = (cx: number, cy: number, r: number, n = 40): Pt[] => Array.from({ length: n + 1 }, (_, i) => [Math.round(cx + r * Math.cos((i / n) * Math.PI * 2)), Math.round(cy + r * Math.sin((i / n) * Math.PI * 2))])
const stroke = (s: number, c: number, w: number, points: Pt[], per = 14): InkOp[] => [
  { t: 'start', s, c, w, x: points[0][0], y: points[0][1], p: 50 }, { t: 'pts', s, pts: line(points, per) }, { t: 'end', s },
]
const house: InkOp[] = [
  ...stroke(1, 0, 1, [[300, 400], [300, 650], [620, 650], [620, 400], [300, 400]]),
  ...stroke(2, 1, 2, [[265, 410], [460, 225], [655, 410], [265, 410]]),
  ...stroke(3, 5, 1, [[430, 650], [430, 520], [500, 520], [500, 650]]),
  ...stroke(4, 3, 2, circle(790, 150, 70), 3),
  ...stroke(5, 4, 0, [[70, 700], [110, 660], [150, 700], [190, 660], [230, 700], [270, 660], [310, 700]]),
  ...stroke(6, 2, 0, [[680, 700], [760, 660], [860, 700], [940, 660]]),
]
export function demoInk(turns = [1, 2, 3, 4, 5]): InkStore {
  const store = new InkStore()
  turns.forEach((turn, i) => store.applyEvent({ turn, n: i + 1, ops: house }))
  return store
}

const game = (over: Record<string, unknown>) => ({
  t: 'doodle', phase: 'draw', turn: 2, totalTurns: 5, finalTurn: false, drawer: 'p1', drawerName: 'Ben', difficulty: 2, blanks: '',
  guessed: 0, expected: 5, drawMs: 75_000, tailMs: 22_500, solvers: [], wrong: [], missTotal: 0, drinks: [], deltas: [], gallery: [], ...over,
})
const stage = (g: object, remainingMs = 18_000): StageInfo => ({ gameId: 'doodle', title: 'Doodle Dash', phaseSeq: 4, remainingMs, paused: false, game: g as StageInfo['game'] })
const shots = [1, 2, 3, 4, 5].map((turn, i) => ({ turn, word: ['house', 'sun', 'cottage', 'roof', 'home'][i], drawer: `p${i % 6}`, drawerName: names[i % 6], first: `p${(i + 2) % 6}`, firstName: names[(i + 2) % 6] }))
const rows: Screen = { t: 'scores', title: 'Final scores', rows: scores }

export default {
  players,
  beats: {
    pick: { stage: stage(game({ phase: 'pick', difficulty: 0 }), 9_000), scores, phone: { me, screen: { t: 'waiting', title: 'Ben is picking a word', detail: 'Get your guessing fingers ready' } as Screen } },
    draw: {
      stage: stage(game({
        blanks: 'H _ _ _ E', guessed: 2, missTotal: 9,
        solvers: [{ id: 'p2', name: 'Cleo' }, { id: 'p3', name: 'Dev' }],
        wrong: ['cabin', 'castle', 'barn', 'igloo', 'shed'].map((text, i) => ({ id: `p${(i + 3) % 6}`, name: names[(i + 3) % 6], text })),
      })),
      scores,
      phone: { me, screen: { t: 'guess', drawer: 'Ben', blanks: 'H _ _ _ E', kind: 'guess', solved: false, close: false, last: 'shed', guessed: 2, expected: 5, tailMs: 22_500 } as Screen },
    },
    drawer: { stage: stage(game({ blanks: '_ _ _ _ _' })), scores, phone: { me, screen: { t: 'draw', word: 'house', difficulty: 2, guessed: 2, expected: 5, tailMs: 22_500, note: 'Draw it! No letters or numbers.' } as Screen } },
    pickme: {
      stage: stage(game({ phase: 'pick', difficulty: 0 }), 9_000), scores,
      phone: { me, screen: { t: 'choice', prompt: 'Pick a word to draw', options: [{ id: 'a', text: 'sun', detail: 'Easy' }, { id: 'b', text: 'house', detail: 'Medium' }, { id: 'c', text: 'lighthouse', detail: 'Hard' }], kind: 'pick' } as Screen },
    },
    solved: {
      stage: stage(game({ blanks: 'H _ _ _ E', guessed: 3, solvers: [{ id: 'p0', name: 'Ava' }, { id: 'p2', name: 'Cleo' }, { id: 'p3', name: 'Dev' }] })), scores,
      phone: { me, screen: { t: 'guess', drawer: 'Ben', blanks: 'H _ _ _ E', kind: 'guess', solved: true, points: 850, close: false, guessed: 3, expected: 5, tailMs: 22_500 } as Screen },
    },
    reveal: {
      stage: stage(game({
        phase: 'reveal', word: 'house', guessed: 3, tailMs: 0,
        solvers: [{ id: 'p2', name: 'Cleo', points: 1000 }, { id: 'p3', name: 'Dev', points: 850 }, { id: 'p0', name: 'Ava', points: 700 }],
        drinks: [{ id: 'p4', name: 'Eli', sips: 1, text: 'Missed it. Drink 1 sip' }, { id: 'p5', name: 'Fay', sips: 1, text: 'Missed it. Drink 1 sip' }],
      }), 5_000),
      scores, phone: { me, screen: { t: 'waiting', title: 'It was HOUSE', detail: '+700', tone: 'win' } as Screen },
    },
    scores: {
      stage: stage(game({ phase: 'scores', word: 'house', deltas: [{ id: 'p1', name: 'Ben', points: 1500 }, { id: 'p2', name: 'Cleo', points: 1000 }, { id: 'p3', name: 'Dev', points: 850 }, { id: 'p0', name: 'Ava', points: 700 }] }), 4_000),
      scores, phone: { me, screen: { t: 'scores', title: 'Turn 2 scores', rows: scores } as Screen },
    },
    podium: { stage: stage(game({ phase: 'podium', turn: 5, finalTurn: true, gallery: shots }), 24_000), scores, phone: { me, screen: rows } },
    gallery: { stage: stage(game({ phase: 'podium', turn: 5, finalTurn: true, gallery: shots }), 8_000), scores, phone: { me, screen: rows } },
  },
}
