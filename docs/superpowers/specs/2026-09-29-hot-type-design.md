# Hot Type: design spec (a Boggle-style word hunt for Party OS)

## Context

The user wants a better replica of Netflix's Boggle Party inside Party OS: phones are the controllers, the TV is the
shared board. This spec came out of a research and mockup pass (two mockup rounds, 2026-09-29). Its sources are at the
end.

What the research found, and what it means for the design:

| Finding | Decision |
| --- | --- |
| Netflix's Boggle Party is called "more individual than the others": the action is on the phones | The TV gets its own action: a live rail, big-find bursts, and a stamped **reveal** that is the main event |
| Its twist is that words only you found are worth more | Found words stay **hidden until the reveal**, so uniqueness is a real gamble. Nothing on the live TV may leak a word |
| Jackbox: one task at a time, and the game reacts to what specific players did | The phone only ever shows the swipe. A HOST bar comments on public facts (counts, lengths, time) |
| TV rules: about 28 px text at 1080p, 5% safe margins, cooler less-saturated colour, no fine detail | See "TV rules" below |
| Letterpress ink is translucent and thin strokes fail, so heavy slab type reads better | Heavy slab display type, misregistration only on big type |
| Fast diagonal swipes skip cells unless the cells in between are walked | Specified in "Phone input" |
| Juice: scale feedback to the event, hit-stop of 40 to 80 ms, never linear easing, and polish can't fix input lag | Specified in "Motion" |

Decisions the user made (2026-09-29):

| Question | Answer |
| --- | --- |
| Theme | **Hot Type**, a letterpress print shop (picked from three options, then approved after two mockup rounds) |
| Direction | Ink, blue, red and cream, with wood-type tiles, a lowering press as the timer, and a stamped newspaper reveal |

Assumed, not asked (flag if wrong): the game id `hottype`; 2 to 8 players, solo only; 5 rounds of 90 s (the lobby default) on a 4×4
board with a 5×5 lobby option; the scoring in "Scoring"; a drink call for last place; the dictionary source; a tutorial;
a per-game theme.

## Rules

### The board and a word

- **Board:** 4×4 by default, 5×5 with the `grid` lobby option. Tiles are dealt from Boggle-style dice (a fixed set of
  dice, each with six faces, shuffled and rolled). The tile `QU` is one tile that counts as two letters.
- **Adjacency:** a word is a path of tiles where each next tile touches the last one across an edge or a corner. Each tile
  is used at most once per word.
- **Minimum length:** 3 letters.
- **Dictionary:** an English word list held by the engine (see "Dictionary and solver"). Words are matched lower-case.
- **Good boards only:** a fresh board is rerolled until the solver finds enough words, so nobody gets a dead board.
  4×4 needs at least 60 words and at least one of 6 or more letters. 5×5 needs at least 120 words and at least one of 7
  or more.
- Everyone in a round plays the **same board**.

### Round flow

| Phase | Length | What happens |
| --- | --- | --- |
| `ready` | 5 s | Tiles are hidden on every screen. TV and phones count 3, 2, 1. The board is **not** in any view yet |
| `hunt` | 90 s (`ctx.timer`) | Tiles flip in. Players swipe words on their phones. The press lowers on the TV |
| `press` | 2.5 s | The press slams down and phones lock. Nothing new is accepted |
| `reveal` | 22 s (the host or captain can skip) | The TV stamps out the words. Phones show the player's own list, marked unique or shared |
| `scores` | 8 s | Each player's round points, split into parts, and the running leaderboard |
| `podium` | 15 s | After the last round |

The number of rounds comes from the existing `rounds` lobby setting (3 to 8). The default is **5**: the lobby shows 5 rounds until someone changes it, and ↓ shortens it. The
final round counts double, as in Bluff and Imposter.

### Submitting a word

The phone sends `{ kind: "word", path: [tileIndex, ...] }` when the player lifts their finger. The engine checks, in
order, and rejects with a code the phone shows:

| Code | When |
| --- | --- |
| `NEXT_ROUND` | The player joined mid-round (standard) |
| `NOT_NOW` | Not in `hunt` |
| `BAD_PATH` | An index out of range, a repeated tile, or a step between tiles that don't touch |
| `TOO_SHORT` | Fewer than 3 letters |
| `NOT_A_WORD` | Not in the dictionary |
| `ALREADY` | This player already found it this round |

On success the word is added to that player's found list. Stale actions from an earlier round are refused by the
runtime as `STALE` (the phase counter). Rejected actions cannot change state in this engine, so invalid guesses are
not counted for anything.

### Scoring

Each word has a base value by its length, on the scale GamePigeon's Word Hunt uses (proposed here, easy to tune):

| Letters | 3 | 4 | 5 | 6 | 7 | 8+ |
| --- | --- | --- | --- | --- | --- | --- |
| Base points | 100 | 400 | 800 | 1400 | 1800 | 2200 |

- **Every finder** of a word earns its base points.
- **Unique bonus:** a word found by exactly one player earns an extra equal amount (the red **ONLY YOU** stamp shows
  the bonus, for example +800 on a 5-letter word).
- **Longest word:** the longest word(s) of the round earn **+500** for each finder (ties all earn it).
- The final round doubles every part.
- Points are awarded once, at the end of `press`, through `Effect.Award` (one per player per round with a reason of
  "hunted words"). The reveal and scores screens show the parts.
- During `hunt`, the live score on phone and TV is the **base total only**. The bonuses arrive at the reveal.

### Drink calls

Drink calls are game text, not an engine effect, and go through `ofWater(water)`. The lobby's `drinks` switch turns them
off.

- The player with the lowest round score: "Drink 2 sips". If several tie for lowest, nobody drinks.

### Players and presence

- `minPlayers = 2`, `maxPlayers = 8`, `lateJoin = LateJoin.NEXT_ROUND`, solo only.
- A player who drops mid-round keeps their found words for that round and scores them.
- If fewer than 2 connected players remain, the round ends through the normal runtime pause/skip controls.

## Theme and visual design

A new per-game theme, `hottype`, in the same system as the other games (`theme/gameTheme.ts`, `theme/games.css`,
`GameScene.tsx`). It is a print shop, not the Saturday Morning shell. No emoji, bulbs or synth music.

### Palette (tuned for TV, which over-saturates warm colour)

| Token | Value | Use |
| --- | --- | --- |
| `--ink` | `#1A1916` | Type, outlines, the press platen |
| `--paper` | `#EADFC2` | Scene background (not pure white, which blooms on TVs) |
| `--paper-2` | `#F4ECD6` | Cards, the preview chip, rail rows |
| `--type-blue` | `#2A4BA8` | Trail, points, finder names, misregistration offset |
| `--stamp-red` | `#C4432F` | Stamps, the last-10-seconds press, invalid words. Always with an outline, never a full-screen wash |
| `--proof-green` | `#3E7C4F` | A valid, stamped word |
| `--wood` | `#E8CE94` | Tile faces |
| `--forme` | `#2A2723` | The board bed behind the tiles |

Colour is never the only signal. Every state also has a shape (tick, strike-through, stamp, wobble) and text.

### Type

- **Display:** Alfa Slab One, for tiles, headlines, stamps, names and scores. Heavy on purpose: hairlines break under
  the press and on TVs.
- **Body:** Archivo 500 and 800, for host lines, labels and hints.
- Both are self-hosted through `@fontsource` like the other game fonts, so the TV works offline. The plan adds the two
  packages.

### TV rules

- Draw for 1920×1080. **No text under 28 px.** Keep a **5% safe margin** (96 px left and right, 54 px top and bottom).
- Nothing the room must read is smaller than the host line (34 px). Fine texture (paper grain) stays under 8% opacity.
- The blue **misregistration offset** (a 4 to 6 px offset shadow at about 50% opacity) is used only on the masthead,
  stamps and the boxed longest word. Tile letters and small text stay crisp.
- Everything the TV shows during `hunt` about other players is a **count or a length**, never a word or a path.

### Phone

- The board fills the width. Tiles are wood-type blocks with a heavy bottom edge that presses flat when touched.
- Above the board: the **press bar** (a black platen filling left to right with the time; it turns red in the last 10
  seconds, and only the bar and the phone frame turn red), the **live preview** of the word being spelled, and a
  **hint line** ("LIFT FINGER TO STAMP · 5 LETTERS = 800").
- Below: the last four found words as chips. The newest is highlighted for a moment.
- The fingertip shows as a visible dot at the head of the trail.

### TV during `hunt`

- The press bar across the top, with round and time.
- The shared board on the left.
- A player rail on the right: photo face, name, base score and one small **pill per found word, sized by length** (words
  stay hidden). With 6 to 8 players the rail switches to compact rows that show the count instead of pills.
- **Big-find burst:** a 6-plus-letter word fires a red comic burst with the player's name and the **length only**
  ("MAYA · 7 LETTERS"). Never the word.
- The **HOST** bar at the bottom comments on public facts only, for example "Joe found four words in ten seconds" or
  "Maya has the only 7-letter word so far". It never claims a word is unique.
- No trail echoes and no paths, because a coloured path would spell the word.

### TV reveal

A newspaper front page, "THE DAILY FORME":

- The page shows up to 8 words, picked in this order: the longest word, then unique words by points, then shared words
  by points. They are stamped one at a time in ascending points, so the longest word lands last.
- Unique words get a red **ONLY YOU** stamp with the finder's name. Shared words are struck through with the finders'
  faces stacked beside them.
- The longest word gets a boxed callout.
- **The one that got away:** the best word nobody found, from the solver, in a dashed box. It is chosen by points, and
  a small blocklist of slurs is never spotlighted (they still count if a player finds one).
- A scores column, and a HOST line built from the round's results.
- Each player's own phone shows their full list, marked unique or shared, so they can check every word.

### Motion

Feedback scales with the event. Nothing loops. Easing is never linear.

| Moment | Motion |
| --- | --- |
| Round start | Tiles flip in like a split-flap board over about 600 ms, then the press starts lowering |
| Touching a tile | The tile presses down (about 60 ms, ease-out) and its letter darkens |
| Valid word | The trail turns green, the tiles flash once, "+800" pops (scale to 1.12 and back) and the word slides into the chip strip |
| Invalid word | The preview shakes and the trail fades, with the reason in the hint line. This borrows the shake-and-vanish feedback described for Wordscapes |
| Last 10 s | The press bar turns red and pulses slowly (well under three flashes per second). No full-screen tint |
| Big find on TV | The burst scales in, holds about 1.5 s, and leaves |
| Reveal stamp | Raise (0 ms, scale 1.6, 35% opacity), impact (about 120 ms, scale 1, 60 ms hit-stop, the paper dips 4 px), rebound (scale 1.1), settle (about 450 ms, dust specks). A small screen shake only for the longest-word stamp and any word of 7 or more letters |
| Podium | The winner's face is ejected onto a front page |

`prefers-reduced-motion`: the shake becomes a static thunk, tile flips become fades, and the burst appears without
scaling. Nothing flashes more than three times a second.

### Audio

Sampled foley played by the TV only (phones have no audio engine), generated through the existing ElevenLabs sample
pipeline (`controller/scripts/audio/cues.json`): a split-flap flip at the start of the hunt, a stamp thud for each
stamp, a platen slam at `press`, and paper rustle at the reveal. There is no synthesized music; a synthesized fallback
exists for any cue without a sample, as the audio module already does. Phones use haptics only.

## Phone input

The phone draws the trail locally at once. The server only decides whether the word counts, so the swipe never waits on
the network.

- **Hit area:** a tile is hit when the pointer is inside a circle of about 60% of the tile size around its centre. The
  gaps between tiles and the corners are forgiving, so a sloppy diagonal still registers. Cells are found from the
  measured tile rectangles, not by dividing the board evenly, so gaps don't drift the hit position.
- **Fast drags:** on each pointer move, walk from the last tile toward the new one across the tiles in between (each
  step must touch the last), so a fast diagonal doesn't skip tiles.
- **Erase:** moving back onto the second-to-last tile removes the last one.
- **Submit:** lifting the finger sends the word if it has at least 3 letters. Fewer just clears, with a small wobble.
  `pointercancel` or a tap outside the board clears the selection.
- **Pending:** after sending, the preview shows "checking" until the next screen update contains the word (stamped) or a
  `reject` arrives. A pending word with no answer after 1.5 s is dropped quietly.
- **Haptics:** a short vibration per tile and a longer one on a stamp, where the browser supports it (Android only; iOS
  Safari has no vibration API).
- The board uses `touch-action: none` and pointer capture, so the page never scrolls or zooms during a swipe.

## Architecture

### Engine (Kotlin, `tv/engine`)

- `games/hottype/HotType.kt`: the `GameModule<HotTypeState>`. State holds the phase, round, total rounds, grid size,
  the tiles, the participants, each player's found words (in order), and per-round deltas. All serialisable so a party
  can pause, resume and restore like the other games. The tiles are stored so a restored game keeps its board.
- `games/hottype/HotTypeRules.kt`: pure functions for dealing a board, path validation, word points, the unique and
  longest bonuses, and the per-round deltas. Tested without the module.
- `games/hottype/HotTypeDictionary.kt` and `HotTypeSolver.kt`: see below.
- `HotTypeViews.kt`: the `HotTypeTv` `TvGame` payload (phase, round, grid, tiles from `hunt`, the rail rows with counts
  and word lengths, the latest big find as length only with a sequence number, and the reveal data from `reveal`). The
  TV payload never contains a found word before `reveal`.
- `playerView` returns a new `Screen.Hunt` (serial name `hunt`, next to `Cards`, `Turf`, `Sprawl` and `Secret`): the
  phase, grid size, tiles (empty before `hunt`), the player's found words with base points, their base score, and, in
  `reveal`, each word's finder count and bonus.
- Registration: `tv/devserver/.../Main.kt` adds `HotType()` to the `GameRegistry` list. Android TV app registration is out
  of scope, as with Sprawl and Imposter.
- Lobby option: `grid` (0 = 4×4, 1 = 5×5) is added to `optionRange`. The plan wires its key on the TV lobby.

### Dictionary and solver

- The engine loads one word list from `tv/engine/src/main/resources/`. The proposed source is **ENABLE** (a public-domain
  word list of about 173,000 words); the plan confirms the licence before adding it. Words are filtered to letters
  only, length 3 to 25.
- `HotTypeDictionary` holds the words compactly (a sorted array with prefix search, not a large object trie) so it is
  small on an Android TV. The plan measures memory on the emulator.
- `HotTypeSolver` finds every word on a board with a depth-first search that stops when no word starts with the current
  letters. It is used to **gate board quality** at deal time and to find **the one that got away** at the reveal.
- The dictionary check runs on the engine only. It is not shipped to phones, which keeps the phone light and removes
  any temptation to bundle a solver into the client.

### Controller (React, `controller/src`)

- `protocol.ts`: the matching `hunt` Screen type, the `HotTypeTv` payload type, and `rejectMessage` text for `TOO_SHORT`
  ("3 letters minimum"), `NOT_A_WORD` ("Not a word"), `ALREADY` ("Already found") and a silent `BAD_PATH`.
- `screens/HuntScreen.tsx` and `screens/hunt-phone.css`: the phone board, the swipe handling in a small hook
  (`useSwipePath`, unit-tested for hit-testing, fast diagonals and erase), the press bar, preview, hint and chip strip.
- `tv/HotTypeStage.tsx` and `tv/hottype.css`: the TV stage (`ready`, `hunt`, `press`, `reveal`, `scores`, `podium`),
  wired into `TvPage.tsx` beside the other stages. The stage keeps its 1920×1080 layout and scales to the screen.
- `theme/gameTheme.ts`: add `'hottype'` to `THEMED_GAMES`, with its tokens in `theme/games.css` and the scene in
  `GameScene.tsx` (paper background with grain under 8% opacity).
- `scripts/bots.mjs`: bots play by reading the same dictionary file and walking the board, so a full rehearsal works
  without friends.

## Corrections to the mockup

The approved mockup had four problems, fixed in this design:

1. The live TV burst showed the word ("PLANETS"). It now shows the length only.
2. The live TV drew coloured trail echoes across the board. A path spells the word, so they are removed.
3. The HOST line said "nobody else has touched them", which reveals uniqueness. Host lines use only counts and lengths.
4. The mockup rail fits 5 players. With 6 to 8 it switches to compact rows. The shared words in the reveal show full base
   points (only unique words earn the extra bonus).

## Testing

- **Engine unit tests** (`HotTypeTest`, seeded RNG and a fake clock): dealing gives the right tile count and passes the
  quality gate; path validation (out of range, repeated tile, non-touching step, `QU`); the reject order; word points by
  length; the unique bonus (one finder versus several); the longest bonus with ties; the final-round double; late join;
  away players keep their words; pause and skip; a stale action from an earlier round; state restore mid-round.
- **View tests:** the TV payload has no tiles before `hunt` and no found word before `reveal`; a player's phone view has
  only their own words; the big-find payload has a length and no word.
- **Solver and dictionary tests:** a known board returns a known word set; the solver stops on dead prefixes; the
  "one that got away" is never a word a player found.
- **Simulation:** an 8-bot game over real sockets, like the other games, with random disconnect and reconnect. The
  invariant is that scores equal a recomputation from the recorded found words.
- **Controller:** vitest for the protocol types and fixtures, and for `useSwipePath` (a fast diagonal walks the tiles in
  between, backing up erases, lifting under 3 letters clears, cancel clears). A browser check of the TV stage at
  1920×1080 and a phone width, including the reduced-motion path.

## Out of scope

- Netflix's Pranks, Daily and Survival modes (a possible v2).
- Teams, and languages other than English.
- Android TV app registration.
- A dictionary editor or per-party word bans.
- Word challenges (disputing a word).

## Open questions

- The scoring numbers (the length scale, the equal unique bonus and the +500 longest bonus) are proposals. They are easy
  to tune after a playtest.
- ENABLE's licence and its memory cost on Android TV are checked in the plan. If it is too heavy, a smaller list
  (common words only) is the fallback, at the cost of some valid words being refused.
- Whether the audio samples are recorded by the user or sourced as CC0. The plan lists the cues needed either way.
- Whether the rail at 6 to 8 players needs a different layout than compact rows once it is seen on a real TV.

## Sources

- Netflix Boggle Party: [overview](https://puzzle-boggle.com/netflix-boggle/), [The National review](https://www.thenationalnews.com/arts-culture/pop-culture/2025/11/14/review-can-netflixs-new-tv-party-games-compete-with-jackbox/), [AV Club](https://www.avclub.com/netflix-party-games-how-is-it).
- Word Hunt scoring: [PuzzlePage](https://www.puzzlepage.app/word-hunt-solver).
- Jackbox principles: [Built In Chicago](https://www.builtinchicago.org/articles/jackbox-games-design-party-pack).
- TV UI rules: [UX Studio](https://www.uxstudioteam.com/ux-blog/best-practices-for-designing-tv-interfaces).
- Letterpress: [Rise and Shine](https://riseandshinepaper.com/learn/designing-for-letterpress/); misregistration: [Studio 2AM](https://studio2am.co/blogs/news/the-risograph-effect-ink-misregistration-for-zines-gig-posters-and-editorial-work).
- Hit-testing: [dxl-word-grid pull request](https://github.com/UBCDigitalExperienceLab/dxl-word-grid/pull/2).
- Game feel: [Egmatic](https://egmatic.com/blog/how-to-make-your-game-feel-good). Flashing limits: [WCAG 2.3.1](https://wcag.dock.codes/documentation/wcag231/).
- Not found in research, so these are proposals rather than measurements: Netflix's screens and animation, Wordle's
  motion design, the stamp timings, and the tile-flip duration.
