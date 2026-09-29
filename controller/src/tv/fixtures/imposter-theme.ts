import type { PlayerSummary, ScoreRow, Screen, StageInfo } from '../../protocol'

// Synthetic beats for the theme gallery (six fictional players). No tokens or real sessions.
const palette = ['#FF4B3E', '#FF8A2B', '#FFD23F', '#2FBF71', '#2F6BFF', '#9B5DE5']
const names = ['Ava', 'Ben', 'Cleo', 'Dev', 'Eli', 'Fay']
const players: PlayerSummary[] = names.map((name, i) => ({ id: `p${i}`, name, avatar: { face: `p:0${i}`, color: palette[i] }, role: 'PLAYER', connected: true }))
const scores: ScoreRow[] = players.map((p, i) => ({ id: p.id, name: p.name, avatar: p.avatar, score: 4000 - i * 500 }))
const me = players[0]

const game = (over: Record<string, unknown>) => ({
  t: 'imposter', phase: 'clue', round: 2, totalRounds: 5, finalRound: false, category: 'Food', submitted: 4, expected: 6, imposterCount: 1,
  clues: [], imposters: [], accused: [], votes: [], guesses: [], drinks: [], deltas: [], ...over,
})
const clues = ['cheesy', 'slice', 'crust', 'round', 'oven', 'delivery'].map((text, i) => ({ id: `p${i}`, name: names[i], text }))
const stage = (g: object, remainingMs = 18_000): StageInfo => ({ gameId: 'imposter', title: 'Imposter', phaseSeq: 3, remainingMs, paused: false, game: g as StageInfo['game'] })
const card = (over: Partial<Extract<Screen, { t: 'secret' }>>): Screen => ({ t: 'secret', title: 'Round 2', face: 'PIZZA', category: 'Food', role: 'crew', acknowledged: false, ...over })

export default {
  players,
  beats: {
    role: { stage: stage(game({ phase: 'role', submitted: 3 })), scores, phone: { me, screen: card({ note: "Don't let the imposter find out the word.", kind: 'seen' }) } },
    imposter: { stage: stage(game({ phase: 'role', submitted: 3 })), scores, phone: { me, screen: card({ face: 'IMPOSTER', role: 'imposter', note: 'Nobody knows who you are. Blend in.', kind: 'seen' }) } },
    clue: {
      stage: stage(game({ phase: 'clue', submitted: 4 })), scores,
      phone: { me, screen: card({ input: { prompt: 'One word about the secret word', maxLen: 20, kind: 'clue', hint: 'One word only' } }) },
    },
    discuss: { stage: stage(game({ phase: 'discuss', clues, submitted: 0 }), 42_000), scores, phone: { me, screen: card({ note: "Watch the TV. Who's faking it?" }) } },
    vote: {
      stage: stage(game({ phase: 'vote', clues, submitted: 2 })), scores,
      phone: { me, screen: { t: 'choice', prompt: 'Who is the imposter?', options: players.slice(1).map((p) => ({ id: p.id, text: p.name })), kind: 'vote', style: 'faces' } as Screen },
    },
    result: {
      stage: stage(game({
        phase: 'result', clues, imposters: ['p3'], accused: ['p3'], submitted: 6,
        votes: [{ voter: 'p0', suspect: 'p3' }, { voter: 'p1', suspect: 'p3' }, { voter: 'p2', suspect: 'p3' }, { voter: 'p4', suspect: 'p3' }, { voter: 'p5', suspect: 'p1' }, { voter: 'p3', suspect: 'p0' }],
        drinks: [{ id: 'p3', name: 'Dev', sips: 2, text: 'Caught! Drink 2 sips' }, { id: 'p5', name: 'Fay', sips: 1, text: 'Voted for an innocent. Drink 1 sip' }],
      }), 4_000),
      scores,
      phone: { me, screen: { t: 'waiting', title: 'You found the imposter!', detail: 'Eyes on the TV', tone: 'win' } as Screen },
    },
  },
}
