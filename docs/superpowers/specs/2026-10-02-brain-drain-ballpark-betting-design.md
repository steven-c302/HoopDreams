# Brain Drain: Ballpark betting

Sub-project 1 of 3 (the others: a final wager round, and the hot seat). Date: 2026-10-02.

## Why

Ballpark scores only the closest team, so every other team is idle at the reveal and a team that guessed badly has nothing to decide. This adds a Wits & Wagers step: after all guesses are shown, each team bets on whose guess is closest. A team can score by reading the room, and trailing teams get a real comeback lever. It also gives the show some gambling tension, which the crew asked for.

Research basis: Wits & Wagers (betting on others' answers lets non-experts score), pub-quiz and Final Jeopardy wagers (late swings give trailing teams a chance). Two later sub-projects (final wager round, hot seat) reuse the bet plumbing built here.

## Flow

Ballpark becomes **question, then bet, then reveal**.

1. **Question** (unchanged, `BALLPARK_MS` 35 s): players type numbers; a team's guess is the median of its members' guesses.
2. **Bet** (new phase `bet`, `BET_MS` 15 s): the TV shows every team's guess on the number line, each with its odds badge, and the true answer hidden. Phones show the bet screens below.
3. **Reveal** (unchanged length): the closest team wins the existing 1000 / 500 / bullseye points. Bets settle on top, in the same step, so scores change once.

The bet phase is skipped when fewer than two teams have a guess. It ends early once every active team has locked a bet.

## Betting rules

- **One bet per team**, made by its players on their phones. The team's bet is the most-tapped guess among its players; ties go to the guess tapped first. The team's stake is the most-chosen stake among the players backing that guess; ties go to the lower stake.
- **Backable guesses:** every team that has a guess, including the team's own.
- **Stake options:** Skip, 250, 500, 1000. A stake above the team's score is disabled, except 250, which is always allowed (house money), so a team with little or no score can still bet.
- **Odds** are by distance of the guess from the median of all teams' guesses (the "middle of the pack"): the nearest guess pays **1x**, the next **2x**, every other guess **3x**. Guesses at the same distance share a tier. With two guesses both are the median; both pay 1x.
- **Settlement:** a bet wins if the backed guess is the closest to the true answer (ties for closest all win). A win adds `stake x odds`; a loss subtracts the stake, but never more than the team has (its score plus this round's points), so house money can't push a score below zero, and the reported loss is the real change.
- **No bet** (skipped, not finished by the timer, or no players tapped): nothing is won or lost.
- A team with no guess cannot be backed but can bet.

## Engine (`tv/engine/.../games/trivia/BrainDrain.kt`, `TriviaViews.kt`)

- `BET = "bet"` phase constant on `BrainDrain`; the constants (`BET_MS`, `STAKES`, `HOUSE_STAKE`) and the pure rules live in a `Betting` object (`Betting.kt`).
- `TriviaState` gains:
  - `line: List<BetOption>`: frozen on entering `bet` (team id, guess, odds), so a restored show shows the same odds.
  - `bets: Map<String, TBet>`: player id to `TBet(on: String?, stake: Int?, at: Long)`.
  Both default empty, so saved shows from before this change still load and never enter the phase.
- Input: a `"bet"` message in the `BET` phase carrying one `option` field (what `ChoiceList` already sends): a team id backs that guess; `s250` / `s500` / `s1000` sets the stake; `skip` skips; `back` clears the guess. Rejects: `NOT_NOW` outside the phase or for a stake before a guess; `BAD_OPTION` for an unknown team; `BAD_STAKE` for a stake not in `STAKES` or above the allowance.
- Transition: Ballpark `QUESTION` end goes to `BET` (if two or more guesses) else to `REVEAL`; `BET` end (timer or all locked) goes to `REVEAL`.
- `score()` for Ballpark: compute round points as today, then settle bets and add the result to `points`; clamp the final team score at zero.
- `TeamAnswer` gains `bet: BetResult?` (`on`, `stake`, `odds`, `won`, `delta`); `TriviaReveal` is unchanged.
- The phone `Screen` for `BET` is `ChoiceList` style `"teams"` (as the Heist victim vote), two stages by whether the player has picked a guess: stage 1 lists guess cards (team colour, sublabel `"guess 206 · pays 2x"`) plus Skip; stage 2 lists stakes plus "Change guess". After locking, a `Waiting` screen says what the team currently backs. Players not on a team see the usual waiting screen.
- Host lines: new reactions for a big win (stake 1000 or odds 3x won), a bust, and "nobody bet".
- `tally()` is unchanged; no new award in this sub-project.

## Protocol and TV (`controller/src/protocol.ts`, `src/tv/types.ts`, `TriviaStage.tsx`, `trivia.css`)

- `TriviaPhase` gains `'bet'`. `TriviaTv` gains `bet?: { line: { team: string; number: number; odds: number }[]; locked: string[] }` (locked = teams whose bet is in; who backed what is not sent until the reveal). `TeamAnswer` gains `bet?`.
- Client message fixture `"bet"`; server fixtures for a `bet`-phase view and a ballpark reveal with bets; both the Kotlin `ProtocolFixturesTest` and `protocol.test.ts` cover them.
- TV bet view: the existing number line with guesses placed and odds badges, truth hidden, a "bet in" tick per locked team, and a short rules line. Reveal: chips fly to the backed guesses and pay or burn; team strips show "+1,000" / "-500".
- Sound: reuse existing effects (`stamp` on lock-in, `jackpot` on a 3x win); no new audio files.

## Testing

Tests are written first.

- **Engine (`BrainDrainTest`):** odds tiers and ties; plurality of bet and of stake; settlement for a win, a loss and a tie for closest; stake cap and the 250 house-money floor; score floored at zero; skip and timeout paths; the bet phase skipped with fewer than two guesses; early end when all teams lock; a saved show without the new fields still loads; scores change once, at the reveal.
- **Protocol:** the new fixtures round-trip in both test suites.
- **TV:** gallery beats `ballpark-bet` and `ballpark-bet-reveal`, screenshotted at 1080p for overlap and readability, as in the polish pass.
- **Phone:** the bet screens render correctly at 320 px width in the existing browser tests.

## Not in scope

Betting on other formats; a High Roller award; pari-mutuel odds; splitting a stake across several guesses.
