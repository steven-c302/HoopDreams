import type { PlayerSummary, ScoreRow, Screen, StageInfo } from '../../protocol'

// Synthetic beats for the theme gallery (six fictional players, invented clues). No tokens or real sessions.
const palette = ['#FF4B3E', '#FF8A2B', '#FFD23F', '#2FBF71', '#2F6BFF', '#9B5DE5']
const names = ['Ana', 'Ben', 'Cleo', 'Dev', 'Eli', 'Fay']
const players: PlayerSummary[] = names.map((name, i) => ({ id: `p${i}`, name, avatar: { face: `p:0${i}`, color: palette[i] }, role: 'PLAYER', connected: true }))
const totals = [1200, 800, 600, 200, -200, -400]
const scores: ScoreRow[] = players.map((p, i) => ({ id: p.id, name: p.name, avatar: p.avatar, score: totals[i] }))
const me = players[0]

const categories = ['Food & Drink', 'Sports', 'Music', 'Movies', 'Space']
const cells = (usedIds: string[] = [], round = 1) =>
  categories.flatMap((_, col) => [0, 1, 2, 3, 4].map((row) => {
    const id = `c${col}-${row}`
    return { id, col, row, value: (round === 1 ? 200 : 400) * (row + 1), used: usedIds.includes(id) }
  }))
const used = ['c0-0', 'c1-0']

const game = (over: Record<string, unknown>) => ({
  t: 'jeopardy', phase: 'pick', round: 1, boards: 2, categories, cells: cells(used), controller: 'p1',
  dailyDouble: false, buzzOpen: false, locked: [], tried: [], deltas: [], drinks: [], ...over,
})
const stage = (g: object, remainingMs = 18_000): StageInfo => ({ gameId: 'jeopardy', title: 'Answer & Question', phaseSeq: 5, remainingMs, paused: false, game: g as StageInfo['game'] })
const clueText = 'This spread is made by mashing avocados with lime and salt.'
const board = (over: Partial<Extract<Screen, { t: 'board' }>> = {}): Screen => ({
  t: 'board', prompt: 'Pick a clue', categories, cells: cells(used), canPick: true, ...over,
})
const buzzer = (over: Partial<Extract<Screen, { t: 'buzzer' }>>): Screen => ({
  t: 'buzzer', state: 'reading', category: 'Food & Drink', value: 200, lockedMs: 0, live: false, ...over,
})
const beat = (g: object, screen: Screen, remainingMs?: number) => ({ stage: stage(g, remainingMs), scores, phone: { me, screen } })

export default {
  players,
  beats: {
    board: beat(game({ controller: 'p0' }), board()),
    boardwait: beat(game({}), board({ prompt: 'Ben is picking', canPick: false, note: 'Watch the TV' })),
    boardcaptain: beat(game({}), board({ prompt: 'Pick for Ben', pickFor: 'Ben' })),
    intro: beat(game({ phase: 'intro', cells: cells() }), { t: 'waiting', title: 'Here we go', detail: 'Watch the categories come up on the TV' }),
    reading: beat(game({ phase: 'clue', category: 'Food & Drink', value: 200, clue: clueText, active: 'c0-0' }), buzzer({ state: 'reading', detail: 'Wait for the clue to finish' })),
    open: beat(game({ phase: 'buzz', buzzOpen: true, category: 'Food & Drink', value: 200, clue: clueText }), buzzer({ state: 'open', detail: 'Ring in!', live: true })),
    lockedlive: beat(game({ phase: 'buzz', buzzOpen: true, locked: ['p0'], category: 'Food & Drink', value: 200, clue: clueText }), buzzer({ state: 'locked', detail: 'Too early! Hold on...', lockedMs: 700, live: true })),
    beaten: beat(game({ phase: 'answer', floor: 'p2', category: 'Food & Drink', value: 200, clue: clueText }), buzzer({ state: 'beaten', detail: 'Cleo rang in first' })),
    tried: beat(game({ phase: 'buzz', buzzOpen: true, tried: ['p0'], category: 'Food & Drink', value: 200, clue: clueText }), buzzer({ state: 'tried', detail: 'You missed this one', live: true })),
    answer: beat(game({ phase: 'answer', floor: 'p0', category: 'Food & Drink', value: 200, clue: clueText }), { t: 'text', prompt: clueText, maxLen: 60, kind: 'answer', hint: 'Answer as a question, or just say it' }),
    dailydouble: beat(game({ phase: 'wager', controller: 'p0', dailyDouble: true, category: 'Music', value: 600 }), { t: 'number', prompt: 'Daily Double! How much do you wager?', unit: '$', kind: 'wager' }),
    wager: beat(game({ phase: 'wager', controller: 'p0', dailyDouble: true, category: 'Music', value: 600 }), { t: 'number', prompt: 'Daily Double! How much do you wager?', unit: '$', kind: 'wager' }),
    reveal: beat(
      game({
        phase: 'reveal', category: 'Food & Drink', value: 200, clue: clueText, floor: 'p2', tried: ['p1'], answer: 'Guacamole', right: true,
        deltas: [{ id: 'p2', name: 'Cleo', points: 200 }, { id: 'p1', name: 'Ben', points: -200 }],
        drinks: [{ id: 'p1', name: 'Ben', sips: 1, text: 'Drink 1 sip' }],
      }),
      { t: 'waiting', title: 'You got it! +200', detail: 'Guacamole', tone: 'win' }, 4_000,
    ),
    finalwager: beat(
      game({ phase: 'final_wager', round: 3, categories: [], cells: [], final: { category: 'World Capitals', wagers: 2, expected: 4, steps: [] } }),
      { t: 'number', prompt: 'Final Jeopardy: how much do you wager?', unit: '$', kind: 'wager' },
    ),
    finalreveal: beat(
      game({
        phase: 'final_reveal', round: 3, categories: [], cells: [],
        final: {
          category: 'World Capitals', clue: 'This capital on the Danube was formed in 1873 by merging Buda, Obuda and Pest.', answer: 'Budapest', wagers: 3, expected: 3,
          steps: [
            { id: 'p4', name: 'Eli', answer: 'Vienna', wager: 0, right: false, delta: 0, total: -200 },
            { id: 'p1', name: 'Ben', answer: 'What is Budapest?', wager: 500, right: true, delta: 500, total: 1300 },
          ],
        },
      }),
      { t: 'waiting', title: 'Eyes on the TV', detail: 'Final answers are coming up, lowest score first' },
      3_000,
    ),
  },
}
