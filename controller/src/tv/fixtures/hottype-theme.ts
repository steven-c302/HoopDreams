import type { PlayerSummary, ScoreRow, Screen, StageInfo } from '../../protocol'

// Synthetic beats for the theme gallery (six fictional players). No tokens or real sessions.
const palette = ['#FF4B3E', '#FF8A2B', '#FFD23F', '#2FBF71', '#2F6BFF', '#9B5DE5']
const names = ['Ava', 'Ben', 'Cleo', 'Dev', 'Eli', 'Fay']
const players: PlayerSummary[] = names.map((name, i) => ({ id: `p${i}`, name, avatar: { face: `p:0${i}`, color: palette[i] }, role: 'PLAYER', connected: true }))
const scores: ScoreRow[] = players.map((p, i) => ({ id: p.id, name: p.name, avatar: p.avatar, score: 4000 - i * 500 }))
const me = players[0]
const TILES = 'STRPLONAHECIDWKU'.split('')
const pts = (len: number) => (len < 3 ? 0 : ([100, 400, 800, 1400, 1800][len - 3] ?? 2200))

const game = (over: Record<string, unknown>) => ({
  t: 'hottype', phase: 'hunt', round: 2, totalRounds: 3, finalRound: false, size: 4, tiles: TILES,
  rail: [], wordsFound: 0, longest: 0, page: [], deltas: [], drinks: [], ...over,
})
const rail = (rows: [string, string, number[]][]) => rows.map(([id, name, lengths]) => ({
  id, name, count: lengths.length, score: lengths.reduce((s, l) => s + pts(l), 0), lengths,
}))
const six = rail([['p0', 'Ava', [5, 3, 4]], ['p1', 'Ben', [3, 3]], ['p2', 'Cleo', [4, 6, 3]], ['p3', 'Dev', [3]], ['p4', 'Eli', []], ['p5', 'Fay', [3, 4]]])
const eight = rail([
  ['p0', 'Ava', [5, 3, 4]], ['p1', 'Ben', [3, 3]], ['p2', 'Cleo', [4, 6, 3]], ['p3', 'Dev', [3]],
  ['p4', 'Eli', []], ['p5', 'Fay', [3, 4]], ['p6', 'Gabriela Montgomery-Smythe', [3, 3, 3]], ['p7', 'Hal', [7]],
])
const stage = (g: object, remainingMs = 61_000): StageInfo => ({ gameId: 'hottype', title: 'Hot Type', phaseSeq: 3, remainingMs, paused: false, game: g as StageInfo['game'] })
const hunt = (over: Partial<Extract<Screen, { t: 'hunt' }>>): Screen => ({ t: 'hunt', phase: 'hunt', round: 2, totalRounds: 3, size: 4, tiles: TILES, found: [], score: 0, ...over })

const mine = [{ word: 'stone', points: 800, finders: 0, bonus: 0 }, { word: 'ton', points: 100, finders: 0, bonus: 0 }]
const page = [
  { word: 'one', points: 100, bonus: 0, finders: ['p0', 'p2'], longest: false },
  { word: 'ton', points: 100, bonus: 0, finders: ['p0', 'p1', 'p3'], longest: false },
  { word: 'toe', points: 100, bonus: 100, finders: ['p1'], longest: false },
  { word: 'stone', points: 800, bonus: 800, finders: ['p0'], longest: true },
]
const deltas = [
  { id: 'p0', name: 'Ava', base: 1000, unique: 800, longest: 500, total: 2300 },
  { id: 'p1', name: 'Ben', base: 200, unique: 100, longest: 0, total: 300 },
  { id: 'p2', name: 'Cleo', base: 100, unique: 0, longest: 0, total: 100 },
  { id: 'p3', name: 'Dev', base: 100, unique: 0, longest: 0, total: 100 },
  { id: 'p5', name: 'Fay', base: 0, unique: 0, longest: 0, total: 0 },
]

export default {
  players,
  beats: {
    ready: { stage: stage(game({ phase: 'ready', tiles: [] }), 3_000), scores, phone: { me, screen: hunt({ phase: 'ready', tiles: [], note: 'Get ready' }) } },
    hunt: {
      stage: stage(game({ rail: six, wordsFound: 11, longest: 6 })), scores,
      phone: { me, screen: hunt({ found: mine, score: 900 }) },
    },
    big: {
      stage: stage(game({ rail: six, wordsFound: 12, longest: 7, bigFind: { seq: 1, id: 'p2', name: 'Cleo', letters: 7 } }), 44_000), scores,
      phone: { me, screen: hunt({ found: mine, score: 900 }) },
    },
    last10: { stage: stage(game({ rail: six, wordsFound: 18, longest: 6 }), 7_000), scores, phone: { me, screen: hunt({ found: mine, score: 900 }) } },
    crowd: { stage: stage(game({ rail: eight, wordsFound: 20, longest: 7 }), 30_000), scores, phone: { me, screen: hunt({ found: mine, score: 900 }) } },
    press: { stage: stage(game({ phase: 'press', rail: six, wordsFound: 19, longest: 6 }), 1_500), scores, phone: { me, screen: hunt({ phase: 'press', found: mine, score: 900, note: 'Pencils down' }) } },
    reveal: {
      stage: stage(game({ phase: 'reveal', page, missed: { word: 'tones', points: 800 } }), 20_000), scores,
      phone: { me, screen: hunt({ phase: 'reveal', found: [{ word: 'stone', points: 800, finders: 1, bonus: 800 }, { word: 'ton', points: 100, finders: 3, bonus: 0 }, { word: 'one', points: 100, finders: 2, bonus: 0 }], score: 2300 }) },
    },
    empty: {
      stage: stage(game({ phase: 'reveal', page: [], missed: { word: 'stone', points: 800 } }), 20_000), scores,
      phone: { me, screen: hunt({ phase: 'reveal', found: [], score: 0 }) },
    },
    scores: {
      stage: stage(game({ phase: 'scores', page, deltas, drinks: [{ id: 'p5', name: 'Fay', sips: 2, text: 'Last place! Drink 2 sips' }], missed: { word: 'tones', points: 800 } }), 6_000), scores,
      phone: { me, screen: hunt({ phase: 'scores', found: [{ word: 'stone', points: 800, finders: 1, bonus: 800 }], score: 2300 }) },
    },
    podium: {
      stage: stage(game({ phase: 'podium', round: 3, finalRound: true }), 12_000), scores,
      phone: { me, screen: { t: 'scores', title: 'Final scores', rows: scores } as Screen },
    },
  },
}
