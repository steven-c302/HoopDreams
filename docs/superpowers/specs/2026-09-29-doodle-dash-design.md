# Doodle Dash: design spec (Pictionary-style drawing game for Party OS)

## Context

The user asked for a Pictionary game inside Party OS that is as fun as possible, with a distinct but lovely theme,
polished design and animation, and "technology to blow my friends' minds". Phones are the controllers and the TV is
the shared screen, so the TV is the big canvas and the phones are the pencils.

Research (2026-09-29): real Pictionary has one "Picturist" per turn drawing for 1 minute, no letters, numbers or
talking, with All Play races. skribbl.io adds 3 word choices with difficulty, speed-based scoring for guessers and
the drawer, and letter hints that reveal over time. Drawing party games are funny because bad art is the point, so
the game rewards silly results and shared reveal moments. `PointerEvent.getCoalescedEvents()` plus quadratic Bézier
smoothing gives good ink on a phone.

Decisions the user made (2026-09-29):

| Question | Answer |
| --- | --- |
| Game shape | **Free-for-all Pictionary.** One drawer per turn, everyone else races to guess on their phones. Not teams, not a Telephone mode |
| Design | Approved in chat as presented (sections below) |

Assumed, not asked (flag if wrong): 3-16 players, solo only, drink calls on by default with the lobby switch and
water wording, a tutorial, a word pack, and the name **Doodle Dash** (game id `doodle`; "Pictionary" is Mattel's
trademark, so only the tagline says "Pictionary-style").

## Rules

### Turns

- The number of turns is the existing `rounds` lobby setting (3-8), default 6. Each turn has one drawer.
- The drawer is the connected player with the fewest turns so far, ties broken at random from `GameContext.random`
  (seedable in tests). A player who is away is skipped.
- `minPlayers = 3`, `maxPlayers = 16`, `lateJoin = LateJoin.NEXT_ROUND`.

| Phase | Length | What happens |
| --- | --- | --- |
| `pick` | 12 s (`ctx.timer`) | The drawer's phone shows 3 words (easy, medium, hard) and taps one. The TV shows "X is choosing" and nothing else. Auto-picks the medium word on timeout |
| `draw` | 75 s (`ctx.timer`) | The drawer draws on their phone, the TV shows the ink live, everyone else types guesses. Ends early when every guesser has it right |
| `reveal` | 7 s | The word appears, the drawing replays as a time-lapse, correct guessers appear in order with their points |
| `scores` | 6 s | Turn points and the running leaderboard |
| `podium` | 15 s | Final ranking plus a gallery of every drawing from the game |

### Words and hints

- The word is shown as blanks on the TV and to guessers, e.g. `_ _ _ _ _   _ _ _`. Letters reveal on the TV and on
  guessers' phones at 40% and 70% of the draw time (one letter each time, never more than half the letters).
- The word is never in the TV payload or any guesser's view before `reveal`.
- The drawer's phone shows the chosen word for the whole `draw` phase.

### Guessing

- A guess is trimmed, 1-40 characters. It is matched with the existing fuzzy matcher (`AnswerMatch`, the plan reads its
  API), so small typos count and a guess phrased as a question already counts.
- A guess one edit away from the word (and not correct) makes the guesser's phone show a private "so close!". The TV
  is not told.
- A wrong guess is shown to the TV as a speech bubble from that player's face (last 8 kept). It is not shown to other
  phones. A correct guess never shows its text anywhere except the reveal.
- A player who guessed right cannot guess again this turn. The drawer cannot guess.
- Rejection codes: `BAD_TEXT` (blank or too long), `NOT_GUESSING` (drawer or already correct), `WRONG_PHASE`.

### Scoring (the last turn counts double)

Let `d` be the word's difficulty (1 easy, 2 medium, 3 hard) and `t` the fraction of draw time left when a guess lands.

| Who | Points |
| --- | --- |
| A correct guesser | `round((400 + 600 * t) * (0.6 + 0.2 * d))`, then times `1.0, 0.85, 0.7` for the 1st, 2nd and 3rd correct guess, and `0.55` for any later one |
| The drawer | 250 per correct guesser, +500 if every guesser got it, times `(0.6 + 0.2 * d)` |

Points are awarded through `Effect.Award`. The numbers are proposals and easy to tune after a playtest.

### Drink calls

Drink calls are game text as in Imposter, worded with `ofWater(water)`, and the lobby `drinks` switch turns them off.

- The drawer drinks "2 sips" if nobody guessed.
- Anyone who did not guess right when at least one other player did drinks "1 sip".

## Live ink

### Why a separate channel

Every engine commit pushes a fresh view to every phone and the TV. Streaming strokes through game state would push
16 phones and the TV per point. Ink therefore travels over its own lightweight path and the engine only validates it.

### Wire

New client message and new server messages (`Protocol.kt`, `protocol.ts`).

- Client to server: `{ t: "ink", round, ops: InkOp[] }`. Counts against a dedicated token bucket (20 messages/s), not
  the action bucket.
- Server to the host (TV) sockets only: `{ t: "ink", turn, ops }`, plus on TV connect (or reconnect) one
  `{ t: "inkSync", turn, strokes }` with everything buffered for the current turn.
- Phones never receive ink.

`InkOp` is one of:

| Op | Fields |
| --- | --- |
| `start` | `s` stroke id (int), `c` colour index 0-7, `w` brush 0-2, `x`, `y`, `p` pressure 0-100 |
| `pts` | `s`, `pts` flat list `[x, y, p, x, y, p, ...]` |
| `end` | `s` |
| `undo` | none: removes the drawer's last stroke |
| `clear` | none: removes all strokes |

Coordinates are integers on a 0-1000 by 0-750 grid (4:3), so the TV and the phone agree on any screen size.

### Validation (server + engine)

`GameModule` gains an optional capability, `InkAware`, implemented by Doodle only:

- `inkAllowed(state, who, round): Boolean` is true only for the current drawer, in the `draw` phase, for the
  current round.
- The server drops a batch silently (no ack, so it is cheap) if not allowed, if a batch has more than 400 numbers,
  if a coordinate is out of range, if the turn has more than 200 strokes or 40 000 points, or if a colour or brush
  index is unknown.
- The server keeps an `InkBoard` per turn: the ordered strokes after undo/clear. It is cleared when the turn changes
  and archived (as compact strokes) when the turn ends, for the time-lapse and the gallery.
- The board and the archive live outside the game snapshot. If a party is resumed mid-drawing the turn restarts with a
  fresh pick. The gallery of finished turns is kept until the game ends.

### Phone canvas

- `pointerdown/move/up` with `setPointerCapture`, `touch-action: none`, all coalesced points, pressure-aware width and
  quadratic-Bézier smoothing, drawn locally in real time so the drawer sees zero latency.
- Points are batched about every 50 ms and sent as `pts` ops.
- Tools: 8 crayon colours, 3 brush sizes, undo, clear. No eraser and no fill.
- The canvas locks (no input) as soon as the phase leaves `draw`, on `visibilitychange` and on blur.
- Light haptics (`navigator.vibrate`) on picking a word and on a correct guess where the browser allows it.

### TV canvas

- Renders strokes from `ink` and `inkSync`, with the same smoothing and a crayon texture, interpolating points across
  batches so motion is continuous rather than steppy.
- Time-lapse in `reveal`: replays the archived strokes over about 3 s (proportional pacing, capped).
- Gallery in `podium`: every drawing as a framed picture with the word, the drawer's face and who guessed it first.

## Content

`tv/engine/src/main/resources/packs/doodle-core.json`, in the same shape as the other packs:

```json
{ "packId": "doodle-core", "title": "Doodle core", "game": "doodle", "version": 1,
  "items": [ { "id": "animals-01", "category": "Animal", "word": "elephant", "difficulty": 1 } ] }
```

- Categories: Person or Job, Place or Animal, Object, Action, Difficult. At least 40 words per category and at least
  60 easy, 60 medium and 40 hard overall. Words are family-friendly, one or two words, no proper names.
- Validation on load, like the other packs: game is `doodle`, unique ids, difficulty 1-3, no blank or duplicate words,
  each word 3-24 characters, letters and spaces only.
- A turn offers one fresh word per difficulty through `ctx.fresh`. The picked word is passed to `Effect.UseContent`
  (id = the item id) so it feeds the "played" memory across nights.

## Architecture

### Engine (Kotlin, `tv/engine`)

- `games/doodle/Doodle.kt`: the `GameModule<DoodleState>`. State: phase, turn, total turns, drawer, turn counts, the 3
  options, the chosen word and difficulty, the draw start and hint schedule, correct guessers in order, wrong-guess
  ring (for the TV), per-turn deltas. All serialisable.
- `games/doodle/DoodleRules.kt`: pure functions for drawer choice, hint reveal, near-miss and scoring (tested without
  the module).
- `games/doodle/DoodlePack.kt`: loader and validation.
- `DoodleViews.kt`: the `DoodleTv` payload (phase, turn, drawer, blanks, wrong-guess bubbles, correct guessers, the
  word from `reveal` on, the deltas, and gallery entries at `podium`).
- New `Screen.Draw` in `Views.kt` (serial name `draw`) for the drawer's pad: `word`, `colors`, `brushes`, `canvas`
  size, `note`. The pick, guess and idle phases use the existing `choice`, `text` and `waiting` screens.
- `InkAware` interface added to `Game.kt`; `PartyEngine` exposes `inkAllowed(pid, round)`.
- Registration in `tv/devserver/.../Main.kt`.

### Server (`tv/server`)

- `Protocol.kt`: `ClientMsg.Ink`, `ServerMsg.Ink`, `ServerMsg.InkSync`.
- `InkBoard.kt`: validation limits, per-turn buffer, archive.
- `PartyModule.kt`: route `ink` for players, fan out to host sockets only, replay `inkSync` when a host socket
  connects.

### Controller (React, `controller/src`)

- `protocol.ts`: the `draw` screen type, `DoodleTv`, the ink messages and the stroke codec.
- `ink/` module: the stroke model, smoothing and renderer shared by the phone and the TV, with unit tests.
- `screens/DrawPad.tsx` and `screens/draw.css`: the phone pad, wired into `ScreenView.tsx`.
- `tv/DoodleStage.tsx` and `tv/doodle.css`: the TV stage, wired into `TvPage.tsx`.
- `theme/gameTheme.ts`, `theme/games.css`, `theme/GameScene.tsx`: theme `doodle`.

## Theme: "The Crayon Studio"

- A cozy art-room in the same cartoon style as the other games: warm paper, a big framed easel on the TV, a crayon
  tray, washi tape and paper-cut shapes, big readable text (readable when drunk).
- Crayon-textured ink; strokes draw in smoothly with a small sparkle at the pen tip.
- Correct guess: confetti and a sticker pop. Word reveal: paint-splat. Reveal and gallery frames hang with a small
  swing. Phone haptics as above.
- No emoji, no bulb frames, no synthesised music (per the user's design notes).

## Testing

- **Engine unit tests** (seeded RNG, fake clock): drawer rotation and fairness, away players skipped, the pick and
  its auto-pick, hint schedule, scoring including order factors and the last-turn double, guess matching and near
  miss, self-guess and repeat-guess rejection, early end, stale round, late join, pause/skip, restore mid-turn.
- **View tests:** the word is absent from the TV payload and every guesser's view before `reveal`; the drawer sees it.
- **Pack test:** the core pack parses and meets the counts; validation rejects bad packs.
- **Server tests:** `ink` accepted only from the drawer in `draw` for the current round, dropped otherwise;
  limits and rate cap; fan-out to hosts only; `inkSync` on host connect; undo and clear.
- **Controller:** vitest for the stroke codec, smoothing and board reducer.
- **End to end:** a Playwright run with one drawer and several guessers on a live TV page (like the Imposter spec),
  plus a 16-bot simulation over real sockets with random disconnects. Invariant: scores equal a recomputation from
  the recorded guesses.
- **Visual check:** the TV stage and a phone viewed in the browser at real sizes.

## Out of scope

- Teams, a Telephone/chain mode and All Play steal rounds.
- Android TV app registration.
- Custom word packs and a pack editor.
- An eraser, fill tool, stickers or text on the canvas.

## Open questions

- Scoring numbers are proposals; tune after a playtest.
- The TV renders ink with plain canvas 2D. If crayon texture costs too much on the TV hardware, the plan falls back
  to plain strokes with a grain overlay.
- The plan verifies how the runtime ends a phase early before relying on the "everyone guessed" end.

## Refinements decided during planning (2026-09-29)

- Default number of turns is 5 (the lobby shows 5 when `rounds` is unset), not 6.
- Guessers get a dedicated `guess` phone screen and the drawer a `draw` screen (text-entry's lock-in flow does not fit rapid guessing).
- "So close" means the normalised edit distance is at most `len / 3 + 1`, because the fuzzy matcher already accepts small typos.
- Hint letters reveal at draw-stage deadlines (`Effect.Deadline`); the phone and TV clocks add `tailMs`. Host skip during a draw advances one stage.
- The server ink archive is keyed by turn number; each accepted batch carries a sequence `n` and `inkSync` carries `upTo` so a late TV never doubles strokes.
- The word pack lists `easy` / `medium` / `hard` words inside each category.
- The podium phase lasts 24 s: podium blocks first, then the gallery.
- A guess or pick in the wrong phase is rejected with `NOT_NOW` (the code every other game uses), not `WRONG_PHASE`.
