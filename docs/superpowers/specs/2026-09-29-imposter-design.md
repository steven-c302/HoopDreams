# Imposter: design spec (social deduction game for Party OS)

## Context

The user wants a social-deduction game in Party OS, picked from the game research in
`docs/party-os/improvement-research.md` ("who's the imposter, one secret word"). Phones are the controllers, the TV
shows the clue wall and the vote, and everyone's photo face is in the lineup.

Decisions the user made (2026-09-29):

| Question | Answer |
| --- | --- |
| Round style | **Typed one-word clues.** Everyone secretly sees the word (the imposter sees only the category), types one word, the TV shows all clues, the room argues out loud, everyone votes on their phone |
| Match shape | **5 rounds**, points per role; final round double; a fresh word and a random imposter each round |
| Phone approach | **B: a dedicated secret-card screen** with hold-to-peek, not a reuse of the existing waiting/text screens |
| Name | **Imposter**, game id `imposter` |

Assumed, not asked (flag if wrong): 4–16 players, solo only (no teams), drink calls on by default with the lobby
switch and water-tonight wording, a per-game theme, a tutorial, and a content pack in the Bluff pack format.

## Rules

### Roles

- **Imposters per round:** 1 for 4–8 players, 2 for 9–16.
- Roles are dealt at random each round from `GameContext.random`, so tests can seed them.
- The imposter sees the **category** only. Everyone else sees the **category and the secret word**.
- With 2 imposters they do not know each other. Both see the same imposter card.

### Round flow

| Phase | Length | What happens |
| --- | --- | --- |
| `role` | 8 s (advances early when everyone has pressed "Got it") | Phones show the face-down card. TV shows only the category and who has looked |
| `clue` | 45 s (`ctx.timer`) | Everyone types **one word**. Ends early once all clues are in. A missing clue at the deadline shows as "..." with no penalty |
| `discuss` | 60 s | TV shows every clue on the lineup board with each player's face. The room argues out loud. The host or captain can skip (`onHost("skip")`) |
| `vote` | 30 s (`ctx.timer`) | Each phone picks one suspect from a photo-face list. No self-votes. Clues stay on the TV. Ends early when all have voted |
| `result` | 6 s | TV reveals the imposter(s), the vote tally and who was accused |
| `guess` | 20 s (`ctx.timer`) | Only if an imposter was accused: that imposter types a guess at the word. Skipped otherwise |
| `scores` | 8 s | Round points and the running leaderboard |
| `podium` | 15 s | After the last round (or when the word pool is exhausted, as in Bluff) |

The number of rounds comes from the existing `rounds` lobby setting (3–8), default 5.

### Clue validation

A clue is one word: letters, digits and hyphens, 1–20 characters, after trimming. Rejected with a code the phone shows:

- `BAD_TEXT`: blank, more than one word, or other characters.
- `TOO_TRUE`: equal to the secret word after normalising (the same `normalise` Bluff uses). Only crew can hit this; an imposter cannot know the word.

Duplicate clues are allowed. Rejecting a duplicate would tell a player that someone else already said it.

### Voting and who is accused

Each voter names one other player. Let `k` be the imposter count and tally the votes per player. Let `c` be the
(k+1)-th highest tally (0 if there are fewer than k+1 players with votes). A player is **accused** when their tally is
strictly greater than `c`. So:

- With 1 imposter, the imposter is accused only if they alone have the most votes. A tie for first means nobody is
  accused and the imposter survives.
- With 2 imposters, the top two are accused unless the 2nd and 3rd are tied, in which case that tied player is not.

A missing vote at the deadline is a non-vote.

### Scoring (final round counts double, as in Bluff)

| Who | Points |
| --- | --- |
| Any crew member whose vote named an imposter | +1000 |
| An imposter who is not accused | +1500 |
| An accused imposter who then guesses the word | +1000 |

The guess is matched with the fuzzy matcher from trivia (`AnswerMatch`), so close spelling counts. The plan reads its
API. Points are awarded through `Effect.Award`, and each round's per-player deltas feed the scores screen.

### Drink calls

Drink calls are game text, not an engine effect (as in Blackjack), and the wording goes through `ofWater(water)`. The
lobby's `drinks` switch turns them off.

- An accused imposter: "Drink 2 sips".
- An accused crew member (wrongly ejected): "Drink 2 sips".
- A crew member who voted for an innocent: "Drink 1 sip".

### Players and presence

- `minPlayers = 4`, `maxPlayers = 16`, `lateJoin = LateJoin.NEXT_ROUND`.
- Players who join mid-round watch until the next round, and get the standard `NEXT_ROUND` rejection.
- A disconnected player (away) is skipped for roles from the next round on. Someone who drops mid-round keeps their
  role for that round, and a missing clue or vote is simply missing.
- If fewer than 4 connected players remain, the round ends through the normal runtime pause/skip controls. The game
  does not add its own rule.

## Content

`tv/engine/src/main/resources/packs/imposter-core.json`, same shape as the Bluff pack:

```json
{ "packId": "imposter-core", "title": "Imposter core", "game": "imposter", "version": 1,
  "items": [ { "id": "food", "category": "Food", "words": ["pizza", "taco", "..."] } ] }
```

- About 30 categories with at least 10 words each (validation requires 8).
- The played-content id of a word is `"<category id>:<word>"`, passed to `Effect.UseContent`, so it feeds
  `ctx.fresh` and the "played questions" memory across nights.
- Validation on load, like `BluffPack.validate`: game is `imposter`, no empty pack, unique category ids, at least 8
  distinct words per category, no blank entries.
- A round picks a category, then a fresh word from it. Words are family-friendly by default.

## Architecture

### Engine (Kotlin, `tv/engine`)

- `games/imposter/Imposter.kt`: the `GameModule<ImposterState>`. State holds the phase, round, total rounds, category
  and word, the imposter ids, clues, votes, the accused list, guesses, and per-round deltas. All serialisable so a
  party can pause, resume and restore like the other games.
- `games/imposter/ImposterPack.kt`: loader and validation.
- `games/imposter/ImposterRules.kt`: pure functions for role dealing, the accused rule and scoring, so they are tested
  without the module.
- `ImposterViews.kt`: the `ImposterTv` `TvGame` payload (phase, round, category, clue wall with authors, vote tally,
  reveal, deltas). The TV never receives the secret word before the `result` phase.
- `playerView` returns the new `Screen.Secret` on `role`, plus the standard text-entry, choice-list and waiting
  screens for clue, vote, guess and idle phases. During `clue` the phone keeps a small peek chip so a player can
  re-check their word while typing.
- A new `Screen.Secret` variant in `Views.kt` (serial name `secret`), next to `Cards`, `Turf` and `Sprawl`:
  - `title`, `face` (the word, or `IMPOSTER`), `category`, `role` (`crew` | `imposter`), `note` (instruction line),
    `kind` (the action name for "Got it"), `acknowledged`.
  - Both roles use identical dimensions and back design, so a neighbour cannot tell them apart face-down.
- Registration: `tv/devserver/.../Main.kt` adds `Imposter()` to the `GameRegistry` list, as for Home Turf, Sprawl and
  Jeopardy. Android TV app registration is out of scope, as with Sprawl.

### Controller (React, `controller/src`)

- `protocol.ts`: the matching `secret` Screen type and the `ImposterTv` payload type.
- `screens/ScreenView.tsx`: a `SecretCard` component.
  - Face-down until the player **holds** it: pointer down shows, pointer up or leave hides. It also hides on
    `visibilitychange` and blur so a locked or backgrounded phone never keeps the word up.
  - A "Got it" button sends the acknowledgement. Buttons are thumb-sized and role is shown by colour, shape and text,
    like the rest of the phone UI.
  - In the `clue` phase the same component renders as a small peek chip above the text field.
- `tv/ImposterStage.tsx` and `tv/imposter.css`: the TV stage (role wait, clue wall lineup board, vote, reveal, scores,
  podium), wired into `TvPage.tsx` beside the other stages.
- `theme/gameTheme.ts`: add `'imposter'` to `THEMED_GAMES`, with its scene in `theme/games.css` and `GameScene.tsx`.
  Look: an interrogation-room lineup in the same cartoon style as the other games, with big readable text. No emoji,
  bulbs or synth music.

## Testing

- **Engine unit tests** (`ImposterTest`, seeded RNG and fake clock): role dealing counts (1 and 2 imposters);
  imposters never see the word in any view; the accused rule (clear winner, tie for first, 2-imposter cutoff tie,
  no votes); scoring including the final-round double; clue validation (`BAD_TEXT`, `TOO_TRUE`, imposter clue never
  `TOO_TRUE`); self-vote rejection; guess matching; late join; away players; pause/skip; stale round; duplicate
  action; state restore mid-round.
- **View tests:** the TV payload does not contain the word before `result`; the imposter's phone view has no word.
- **Pack test:** the core pack parses, and validation rejects a bad pack.
- **Simulation:** a 16-bot game over real sockets, like the other games, with random disconnect and reconnect. The
  invariant checked is that scores equal a recomputation from the recorded actions.
- **Controller:** vitest for the protocol type, a fixture, and `SecretCard` (hold shows, release hides, blur hides,
  Got it sends once). A browser check of the TV stage and a phone.

## Out of scope

- A spoken-clues mode and an elimination mode (both considered and rejected).
- Teams.
- Android TV app registration.
- A pack editor for Imposter words (the Bluff pack format is reused so the later editor can cover it).
- Categories chosen by the host.

## Open questions

- The scoring numbers (1000 / 1500 / 1000) are proposals. They are easy to tune after a playtest.
- Whether early-advance on all-clues-in needs a `waitingOn` change. The plan verifies how the runtime ends a phase
  early before relying on it.
