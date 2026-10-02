# Brain Drain: The Final Wager

Sub-project 2 of 3 (1: Ballpark betting, shipped; 3: the hot seat). Date: 2026-10-02.

## Why

Brain Drain ends when the last round ends: whoever led after five rounds wins, and the last minutes are a podium nobody can change. A Final Jeopardy-style finale gives every team one more big decision and a slow, dramatic reveal, so the room is still looking at the TV when the winner is decided. The research (Final Jeopardy's wager and lowest-first reveal, Trivia Murder Party's catch-up final, Jackbox's one-task-at-a-time phones) all points the same way: a secret wager before the question, then a reveal that builds to the leader last.

## Corrections to earlier notes

Points shows have no podium bonus today: `PODIUM_BONUS` (3000/1500/500) is only awarded for the retired Gauntlet race. So there is nothing to remove or shrink; the final simply decides the placings.

## Design check (simulation)

I simulated 6,000 shows (3 to 5 teams, varied skill, scores from the real scoring constants) with sensible wager strategies (leaders cover the runner-up, trailing teams swing big). Findings:

| Wager rules | Leader still wins | 2nd wins | Last wins | Finish within 10% |
| --- | --- | --- | --- | --- |
| 25 / 50 / 100% for everyone | 53% | 32% | 7% | 34% |
| 25 / 50 / 75% for everyone | 67% | 21% | 6% | 22% |
| Cap 50% for everyone | 68% | 21% | 5% | 19% |
| **Leader capped at 75%, upper-middle at 75%, bottom half may go ALL IN** | **65%** | **17%** | **8%** | **28%** |

Letting everyone go all in flips the winner about half the time, so the five rounds before it would barely matter. Capping everyone makes the finale feel timid. Giving ALL IN only to the bottom half keeps the leader a clear favourite (65%) while still giving last place a real shot and making nearly 3 in 10 finales a photo finish. The simulation is a model (independent answers, assumed strategies), so the numbers are a guide to the shape, not a promise; the wager levels are constants so they can be tuned after real play.

## Flow

The finale runs after the last round's standings (before the podium), in Brain Drain only (not Write It Down played on its own, and not in shows saved before this change).

1. **Category** (`FINAL_CATEGORY`, 7 s): the TV slams the category and the stakes; Brainy hypes. Phones: waiting screen.
2. **Wager** (`FINAL_WAGER`, 20 s): each phone shows the wager buttons (below). The TV shows each team's "wager locked" tick, never the amount. Ends early once every team has a wager in.
3. **Question** (`FINAL_QUESTION`, 40 s): the question appears on the TV; each player types the team's answer on their phone (the existing typed-answer screen). Ends early once every connected player has answered.
4. **Reveal** (`FINAL_REVEAL`): teams are revealed from last place to first. Each team, in turn: their typed answer appears, a check or cross lands, their wager slams in, their score bar moves. The leader goes last, with a drumroll. The podium order is live: it reshuffles as each result lands. Total about `5 s x teams + 4 s`.
5. Then the existing podium and awards, in the new order.

## Wager rules

- Base for percentages: `max(score, FINAL_FLOOR)` with `FINAL_FLOOR = 1000`, so a team on little still has a meaningful bet.
- Options: **0 (play it safe)**, **25%**, **50%**, **75%**, and **ALL IN (100%)**, each rounded to a multiple of 50.
- Who may pick what: the team in first place and teams in the upper half of the order (rank better than the median, ties share a rank) top out at **75%**. Teams in the bottom half (including last place; with two teams, the second team) may also pick **ALL IN**. Shown on the phone as "ALL IN: underdogs only".
- A team's wager is the amount most of its players chose, ties to the lower amount; no tap means 0.
- A right answer adds the wager; a wrong or missing answer subtracts it, but never more than the team's score (scores never go below zero).
- Answers are matched like Write It Down: typos accepted (existing `AnswerMatch`); a team's answer is its most-written one.

## Question choice

A typed question from the existing multiple-choice pool that works without options (existing `writable()` filter), has a short answer (at most 20 characters), and has not been played, preferring a category different from the last round's. Plain pool, no difficulty metadata exists, so "guessable" is approximated by short answers; this is the main risk to tune after playtests (see below).

## Drinks and host lines

- A wrong or missing answer is a drink call (1 sip); losing a 75% or ALL IN wager makes it 2.
- Host lines come from the engine: category slam, "X locked in", the leader's drumroll line, and a result line for the winner and for any lead change.

## Engine (`tv/engine/.../games/trivia/`)

- New file `Finale.kt` (pure): `Finale.options(score, rank, teams)`, `Finale.wagerAmount(...)`, `Finale.teamWager(members, wagers)`, `Finale.settle(...)`, and the constants. `BrainDrain.kt` only wires phases, state and views, as with `Betting.kt`.
- `TriviaState` gains `finale: FinaleState?` (category, question id, per-player wager and answer, the frozen reveal order); default null so old saves load.
- Phases `FINAL_CATEGORY`, `FINAL_WAGER`, `FINAL_QUESTION`, `FINAL_REVEAL`; `startRound` past the last round enters the finale instead of the podium (for Brain Drain with at least two active teams).
- Input: `"finalWager"` (option id `w0`, `w25`, `w50`, `w75`, `wall`) in `FINAL_WAGER`; `"finalAnswer"` (text) in `FINAL_QUESTION`. Rejects: `NOT_NOW` out of phase, `BAD_OPTION` for a wager not offered to that team (for example ALL IN for the leader).
- Scores change once, at the end of the reveal (the reveal order and amounts are computed when the question closes, held in state, and the TV animates from that).
- `waitingOn`: wager phase waits for players without a wager; question phase waits for players without an answer.
- Views: `TriviaTv.finale` (category; locked teams; later the reveal steps with answers, verdicts, wagers and score deltas, only in `FINAL_REVEAL`); phone screens are `ChoiceList` (wager) and `TextEntry` (answer), then `Waiting` with tone.

## Protocol and TV (`controller/`)

- `TriviaPhase` gains the four phases; `TriviaTv.finale`; fixtures for each phase in the Kotlin and TypeScript protocol tests.
- TV (`TriviaStage.tsx`, `trivia.css`): category slam; wager view with "locked" ticks and the team strips; question view reusing the Write It Down layout; the reveal sequence described above (animated by phase start time, deterministic so a reloaded TV shows the same step); gallery beats `final-category`, `final-wager`, `final-question`, `final-reveal`.
- Phone vibration: the phone vibrates on wager lock, on your team's reveal, and on the result, where the browser supports it (Android Chrome does; iPhone Safari does not, so it is a bonus, not a requirement).
- Sound: reuse existing cues (stamp on lock, drumroll before the leader, jackpot when a trailing team wins).

## Testing

Tests first. Engine: wager options by rank (leader never gets ALL IN; the bottom half does; a two-team show gives only the second team ALL IN), rounding, the floor, plurality and ties, settlement (right, wrong, missing, capped loss, scores never negative), the reveal order (last place first), scores applied once, skipped answers, a team on 0, saved-show compatibility, the finale skipped for Write It Down. Protocol fixtures round-trip. A browser test plays the finale on the real server with four phones (wager, answer, reveal on the TV). Screenshots of all four TV beats for overlap and readability at 1080p.

## Risks

- **Question difficulty:** with no difficulty data, a typed question can be too hard and fizzle the reveal. Mitigation: short-answer filter now; record how often each question is answered correctly (the played-content store already tracks usage) and prefer the ones that land, as a follow-up.
- **Run length:** about 2.5 to 3 minutes added to every show; the finale can be turned off from the same lobby settings as drink calls if a group wants it shorter (not in this sub-project).
- **A leader who is too far ahead** still gets the finale (it is the show's climax and the reveal is the point); the leader's bet just matters less.

## Not in scope

The hot seat; betting on other formats; a High Roller award; choosing the category; difficulty-aware questions.
