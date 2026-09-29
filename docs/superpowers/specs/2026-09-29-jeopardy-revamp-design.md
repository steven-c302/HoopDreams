# Answer & Question (Jeopardy) revamp: design spec

## Context

Answer & Question is Party OS's Jeopardy game (game id `jeopardy`, added by Steven Chen on `main`). Today it is a
fixed 5×5 board where the TV keyboard picks a clue, everyone types an answer at once for 30 s, and every right
answer earns the clue's value. Playing it for real (see "What was wrong", below) showed it works mechanically but is
missing most of what makes Jeopardy Jeopardy, has no way for a phone to pick a clue, and looks unlike the other games.

The user asked (2026-09-29): *"revamp jeopardy. make the admin/host on phone be able to select things on the board +
give the design a refresh from the other games. do some research around what really makes jeopardy jeopardy and
incorporate that into the game."*

Two earlier fixes are already on this branch's base (`jeopardy-fix`): answers phrased as a question ("What is
guacamole?") now count, and the TV's hint text is readable. This spec builds on them.

### What was wrong (observed by playing it)

- The only way to pick a clue is the TV keyboard (arrows + Enter). Phones say "Eyes on the board" and offer nothing.
- The pick step has no timer, so the game can sit on the board forever.
- Everyone answers at once and wrong answers cost nothing, so there is no buzzing, no risk, no wagering.
- It is one fixed board of 25 clues that ends only when all are used, with no Final Jeopardy and no second round.
- It uses no theme, so it is a plain dark screen next to the themed games.

### Research: what makes Jeopardy Jeopardy

| Mechanic | What the show does | Source |
| --- | --- | --- |
| Ringing in | The clue is read, then contestants ring in. Ringing in before the host finishes locks you out briefly (about 0.25 s). | [Weekend](https://www.weekend.com/post/jeopardy-rules), [Trivia Bliss](https://triviabliss.com/jeopardy-rules/) |
| Risk | A wrong response subtracts the full clue value and scores can go negative; the others may then ring in. | [Playfactile](https://www.playfactile.com/blog/how-to-play-jeopardy-complete-game-rules-explained/) |
| Board control | The player who answers correctly picks next. In Double Jeopardy the lowest score picks first. | [Shapes](https://shapes.inc/fandom/jeopardy/gameplay-and-rules) |
| Daily Doubles | Hidden clues (1 in round one, 2 in round two). Only the picker answers, after a wager from $5 up to their score or the top clue value, whichever is higher. | [Weekend](https://www.weekend.com/post/jeopardy-rules) |
| Double Jeopardy | Round two doubles every clue value. | [Playfactile](https://www.playfactile.com/blog/how-to-play-jeopardy-complete-game-rules-explained/) |
| Final Jeopardy | Category revealed, secret wagers (players above $0), one clue, a written answer, and the wager is won or lost. | [Trivia Bliss](https://triviabliss.com/jeopardy-final-jeopardy-rules/) |
| Question form | Answers are phrased as a question. The show is strict about it only in Daily Doubles and Final. | [Weekend](https://www.weekend.com/post/jeopardy-rules) |

## Decisions the user made (2026-09-29)

| Question | Answer |
| --- | --- |
| Who picks the next clue | **The last player to answer right picks; the captain can always override.** If nobody got the last clue, the same picker stays. |
| How players answer | **Buzz in, then type.** A BUZZ button opens after the clue is read; first buzz gets the floor and types an answer. |
| Show length | **A lobby setting, Short or Full.** Short is one board then Final; Full is a board, Double Jeopardy, then Final. |
| Look | **The Studio set:** deep indigo stage with a soft spotlight, cream and gold tiles with hard shadows. |

Assumed, not asked (flag if wrong): 2–16 players scoring individually (no teams), the TV keyboard still picks as a
fallback, answers are typed on the phone, the answer-as-a-question rule stays lenient everywhere (a drunk room should
not lose a Daily Double over "What is" wording), there is no new music, and Water tonight and the drinks switch apply.

## Rules

### Show structure

- **Short** (`show = 0`, default): Board 1 (values $200–$1000, one Daily Double), then Final Jeopardy.
- **Full** (`show = 1`): Board 1, Board 2 (Double Jeopardy: values $400–$2000, two Daily Doubles), then Final.
- A board is 5 categories × 5 clues. Each show draws its categories at random from the pack's pool: 5 for board 1,
  and 5 different ones for board 2. A clue's value comes from its position in the category (index 0..4), times 200
  on board 1 and 400 on board 2. Position also means difficulty: the pack lists a category's clues easiest first.
- **Daily Doubles** sit at random on rows 2–5 (never the cheapest row), one per category at most, hidden from the board.
- **Final Jeopardy** uses one clue drawn from the pack's finals. The show ends there, or at the podium if nobody has a
  score above zero.

### Who picks

- Board 1 starts with the **captain** (or, with no captain, the earliest-joined player). Board 2 starts with the
  **lowest score** (ties: earliest joined).
- After a clue the **last player who answered right** picks next. If nobody answered right, the picker stays.
- The picker has **20 s** (scaled by the lobby timer setting). On timeout, or straight away if the picker has left, a
  random unused clue on the lowest row that still has one is picked.
- The **captain can pick at any time**, for anyone. The TV keyboard (arrows + Enter, `HostCmd.GameAction("pick:<id>")`)
  still works and needs no phone.

### A normal clue

1. **Read** (`clue` phase, no timer for players): the TV shows the clue for `readMs = clamp(2500 + 55 × characters,
   3000, 8000)`. Phones show "Wait for it" with the BUZZ button dimmed.
2. **Buzz** (`buzz` phase, 10 s, `ctx.timer`): phones light up. The engine is single-threaded, so the first `buzz`
   action to arrive wins the floor. A `buzz` sent during the read phase locks that player out for **1 s** (long enough
   to matter on a phone, longer than the show's 0.25 s to allow for network jitter); a buzz while locked out is
   rejected with `LOCKED_OUT`.
3. **Answer** (`answer` phase, 15 s, `ctx.timer`): the player with the floor types an answer. Right: **+value**,
   they take control of the board. Wrong (or a blank timeout): **−value**, and the clue goes back to `buzz` for 8 s
   for everyone who has not tried yet.
4. **Reveal** (5 s): the answer, who got it, the score changes. It comes as soon as someone is right, everyone has
   tried, or nobody buzzes in the buzz window.
5. Scores may go negative.

"Right" uses the shared `AnswerMatch` after dropping a leading question phrase ("what is", "who's"...), already in
`Jeopardy.asAnswer`.

### Daily Double

Only the picker plays it, so there is no buzzing. Phase `wager`: the picker types a wager (number keypad), minimum
**5**, maximum **max(their score, the round's top clue value)**, 20 s (`ctx.timer`); no wager in time counts as 5.
Then the clue shows and the picker has **30 s** to answer. Right: **+wager**, wrong: **−wager**. They keep control
either way (the picker picks again).

### Final Jeopardy

1. **Category** (6 s): the category name is shown, no clue yet.
2. **Wager** (30 s): each player with a score **above zero** wagers **0..score** in secret on their phone. Players at
   or below zero cannot wager (their wager is 0) but still see and answer the clue. No wager in time counts as 0.
3. **Answer** (30 s): the clue is shown; everyone types an answer.
4. **Reveal**: players are shown from the lowest score up, one every 5 s: their answer, the correct answer once, their
   wager, and the new total (right: +wager, wrong: −wager; answers by players who did not wager score nothing).
5. **Podium** (15 s): final standings.

### Drink calls

Drink calls are game text (as in Blackjack), worded through `ofWater(...)` and switched off by the lobby `drinks`
setting. A wrong answer after buzzing: **1 sip**. A wrong Daily Double or Final Jeopardy answer: **2 sips**. Correct
answers give none.

### Presence and late joiners

- Late joiners (`LateJoin.ANYTIME`) can buzz from the next clue; their score starts at 0, so they miss Final's wager
  unless they earn something.
- A disconnected player is skipped for control. The runtime's early-end rule covers the rest: `waitingOn` names who
  the phase is waiting for, and a phase ends the moment none of them is connected.

## Architecture

### Engine (`tv/engine`)

- `games/jeopardy/Jeopardy.kt`: the `GameModule<JeopardyState>`, rewritten. Phases: `intro`, `pick`, `wager`, `clue`,
  `buzz`, `answer`, `reveal`, `break`, `final_category`, `final_wager`, `final_answer`, `final_reveal`, `podium`.
- `games/jeopardy/JeopardyRules.kt` (new, pure): `dealBoard`, `valueOf(round, index)`, `readMs`, `wagerBounds`,
  `firstPicker`, `autoPick`, `finalOrder`, `clueDelta`. Tested without the module.
- `games/jeopardy/JeopardyPack.kt`: the pack format becomes **version 2** (below); `JeopardyPack.core()` loads it.
- `JeopardyViews.kt`: `JeopardyTv` grows (below); two new phone screens `Screen.Board` and `Screen.Buzzer` in `Views.kt`.
- `Game.kt`: `GameContext` gains `captain: PlayerId? = null` (additive), filled by `PartyEngine.ctx()`. This is how the
  module lets the captain pick for anyone.
- `PartyEngine.optionRange`: new setting `show` (0..1).

### State and timing

`JeopardyState` keeps: the phase, the round (1, 2, or 3 for Final), both boards' clues and used flags, the Daily
Double cell ids, the controller, the active clue, the floor holder, who has tried, lockouts (`until` in engine time),
the current wager, the round's deltas, and the Final data (category, wagers, answers, reveal cursor). All of it is
serialisable so a paused or restarted party restores. State from the old Jeopardy is not restorable and is dropped on
restore, as any unreadable game state already is.

Timers use `Effect.Phase(ctx.timer(...))` for decision phases and plain values for reveals, like the other games. The
5 s reveal, 8 s round intro/break and stepped Final reveal are fixed so a relaxed setting does not slow the show.

### Wire types

- `Screen.Board(prompt, categories, cells, canPick, pickFor, note)`; `cells` are `BoardCell(id, row, col, value, used)`.
  The phone picks a cell locally, then confirms, and sends `{ kind: "pick", cell: <id> }`.
- `Screen.Buzzer(state, category, value, detail, lockedMs)`; `state` is `reading | open | locked | up | beaten | tried
  | out`. The button sends `{ kind: "buzz" }`. `up` hands over to the existing `TextEntry` for the answer.
- Wagers use the existing `Screen.NumberEntry` (kind `wager`); answers use the existing `Screen.TextEntry`.
- `JeopardyTv` adds: `round`, `categories` (header order) with `shown` (how many are revealed during `intro`), the
  controller, `dailyDouble` and the hidden-wager state, `floor`, `locked`, `tried`, `buzzOpen`, and a `final` block
  (category, clue once shown, wager count, and the reveal steps).
- New lobby option key `show` in `protocol.ts` (`OptionKey`) and the shared fixtures.

### Controller (`controller/src`)

- `screens/BoardScreen.tsx`, `screens/BuzzerScreen.tsx` (+ `jeopardy-phone.css`): the mini-board and the BUZZ button.
  BUZZ is a full-width button over half the screen, with colour and text and shape for every state.
- `tv/JeopardyStage.tsx` is split by phase (`Board`, `ClueView`, `WagerView`, `FinalView`, `Scoreboard`) and dressed in
  the Studio theme; `tv/jeopardy.css` for styles.
- `theme/gameTheme.ts` adds `jeopardy` to `THEMED_GAMES`; `theme/games.css` gets its scene (indigo, spotlight, gold).
- TV lobby: key `S` toggles Short/Full when Jeopardy is focused; `D` (drinks) also applies to Jeopardy. The captain's
  phone lobby gets a "Show: Short / Full" stepper (`pages/Captain.tsx`).
- `tv/fixtures/jeopardy-theme.ts` + `ThemeGallery` cases give a stable review surface for every phase.

### Content

`jeopardy-core.json` version 2:

```json
{ "packId": "jeopardy-core", "title": "Answer & Question core", "game": "jeopardy", "version": 2,
  "categories": [ { "id": "food", "name": "Food & Drink", "clues": [ { "id": "food-1", "clue": "...", "answer": "..." } ] } ],
  "finals": [ { "id": "final-1", "category": "World Capitals", "clue": "...", "answer": "..." } ] }
```

- At least **14 categories × 5 clues** (the existing five kept, migrated to v2) and **8 finals**.
- The clue id is the played-content id (`UseContent`) so nights do not repeat until the pool is used up.
- Validation on load: game id, unique ids, exactly 5 clues per category, no blank fields, no duplicate answers in a
  category, at least 12 categories and 4 finals.

## Testing

- **Engine unit tests** (seeded RNG, fake clock): board dealing (5+5 distinct categories, values by position, Daily
  Double placement rules), first picker by round, control passing, auto-pick on timeout and for a departed picker,
  captain override, read-time formula, early-buzz lockout and the first-buzz-wins rule, wrong answer reopening for the
  rest, all-tried and no-buzz endings, negative scores, Daily Double wager bounds and default, Final eligibility,
  wager and answer handling, reveal order, an all-zero table skipping Final, drink lines and water wording, late join,
  and restoring mid-clue.
- **Wire fixtures:** golden samples for `board`, `buzzer`, the extended `JeopardyTv`, and the new client actions.
- **Simulation:** 16 flaky bots over real sockets play a Full show; scores must equal a recomputation from what the TV
  showed.
- **Browser (Playwright):** real phones buzz (one early, one on time), a wrong answer reopens the clue, the captain
  picks for someone else, a Daily Double wager, and Final Jeopardy; a live TV page watches for page errors.
- **Visual review:** screenshots of every TV and phone state at TV and phone sizes, checked for overlap and contrast
  (this project's recurring class of bug).

## Out of scope

- Reading clues aloud, picture and audio clues, and video.
- Teams, and a strict "answer must be a question" rule.
- New music or "Think!" audio.
- A pack editor for Jeopardy boards.
- The Android TV app (Jeopardy is registered in the devserver only, like the newer games).

## Open questions

- The exact clue and final text is authored during the plan and should be skimmed for facts you disagree with.
- The 1 s lockout, 3 s minimum read and 10 s buzz window are proposals; they are constants in one place so they can be
  tuned after a playtest.
