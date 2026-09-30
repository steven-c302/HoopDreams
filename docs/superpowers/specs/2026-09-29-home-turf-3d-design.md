# Home Turf 3D: a tabletop board, drink pieces and choreographed turns (spec)

Replaces "Plan 3, visual redo" from `2026-09-28-home-turf-overhaul-design.md` (its Milestone 2 UX ideas, such as the
Ledger and the Shady reveals, still apply and are laid out on top of this scene). Rules and engine mods stay in that
spec's Plans 1 and 2.

## Context

The TV board is flat and spreadsheet-like (`docs/media/turf-*.png`, `/tv?gallery=themes&game=turf`). The user asked for
3D pieces that visibly walk to their space, anticipation moments that make people watch the screen ("come on, make it
fun"), and a full look overhaul because the current one "looks too basic".

A throwaway spike on branch `turf-3d-spike` (`controller/src/tv/spike3d/`) tested react-three-fiber, Rapier physics
dice, postprocessing and four looks. Decisions the user made:

| Question | Answer |
| --- | --- |
| Which direction? | **Classic**: a wooden table, a spotlight, real materials (chosen over toon, vinyl and luxe) |
| Brand across the app | **Not uniform.** Each game may have its own theme, like Jackbox packs. Home Turf is the classic tabletop. |
| Fonts | The spike's `system-ui` bold was "really ugly". Use fontsource faces; keep letters distinct at TV size. |
| Title sticker | "HOME TURF" must not touch its red border |
| Pieces | **Drinks**: soju, vodka, beer bottle, beer can, shot glass, red cup. Generic labels only (no Smirnoff or Absolut marks). |
| Online assets | Looked at and declined. Build the drinks from scratch; no downloaded models and no credits needed. |

Out of scope: the Android TV app (stays on 1.0, compile-only), the phone UI beyond the piece art and lobby switch, new
rules, and any other game's look.

## What the spike proved (and did not)

- Three.js, Rapier dice, N8AO, tilt-shift and real glass render correctly at 1920x1080 with no console errors.
- **Speed:** with the Mac calm, no screenshots in the run, classic with drinks held 50 fps (real glass) and 53 fps
  (fast glass), p95 frame 22 ms. There was one hitch of about 200 ms per roll, probably shader compilation. It was not
  measured on the real TV.
- Not proven: the choreography against live engine beats, dice that land on the engine's number, long games (memory),
  and 13 to 16 players on a lobby screen.

## Direction: the classic tabletop

| Element | Decision |
| --- | --- |
| Scene | A wooden table, a slab board with a cream, printed top, the camera tilted about 40 degrees from the couch side |
| Light | One warm spotlight with soft shadows, local Lightformer reflections (no downloaded HDRI, so it works offline), N8AO ambient occlusion, a light vignette, ACES tone mapping. Tilt-shift stays very light (0.03) because heavier blur made side tiles unreadable. |
| Board art | Drawn to a canvas texture: cream tiles, ink lines, a colour band on the inner edge of each street, `Anton` names auto-fitted to the tile |
| Title | `Rammetto One` on a red sticker with an inset border, fitted to 80% of the width, plus the tagline "GOOD NEIGHBORS. BAD LANDLORDS." (Alfa Slab One was rejected: its F reads as E at TV size.) |
| Text direction | **Every name reads upright from the couch**, unlike a real board where the far side is upside down. Side tiles are wider than tall, so upright text fits them. |
| Houses and hotels | Small 3D houses and a wider hotel, popping in with a bounce when built |
| Readability rule | Colour plus shape plus text is unchanged. Owner is shown by a flag on the tile and a tint band, never colour alone. |
| Per-game brand | The theme lives in `theme/games.css` scoped by `data-game-theme` as today. The 3D scene reads its palette from one `TurfLook` object so another game can get a different look later. |

## The pieces: six drinks

| Piece id (new) | Was | Shape and label |
| --- | --- | --- |
| `soju` | `pizza` | Short green glass bottle, white label, green cap |
| `vodka` | `sneaker` | Tall clear bottle, red label "VODKA / PREMIUM", silver cap |
| `beer` | `boombox` | Brown long-neck, cream label "BEER / COLD LAGER", crown cap |
| `can` | `cone` | Aluminum can, red band "LAGER / COLD & CRISP", pull tab |
| `shot` | `duck` | Thick-base shot glass with amber liquid |
| `cup` | `cup` | Red party cup with a foam-topped amber liquid |

- **Coasters.** Each piece stands on a coaster in the token's colour with a white printed ring. The coaster is how a
  player is identified (colour plus the drink's silhouette), so a piece is never distinguished by colour alone.
- **IP rule.** Labels are generic words in the game's own style. No brand names, logos or trade dress. A test scans the
  label strings and 2D art text for a small deny-list (Smirnoff, Absolut, Jinro, Chamisul, Budweiser, Heineken, Solo).
- **Builds from scratch** with lathe geometry and canvas label textures, as in the spike (`Drinks.tsx`).
- **Engine.** `PIECES_ALL` and `PIECE_NAMES` change to the six ids. `PIECE_NAMES` becomes Soju Bottle, Vodka Bottle,
  Beer Bottle, Beer Can, Shot Glass, Red Cup. `autoPieces` and its tests follow.
- **Resume compatibility.** A saved party (`--party`, 6 hour window) may contain old ids. Loading maps
  `pizza->soju, sneaker->vodka, boombox->beer, cone->can, duck->shot, cup->cup`.
- **2D fallbacks.** The phone piece picker, token cards and the 2D fallback board need matching flat drawings for the six
  drinks in `TurfArt.tsx` (the current `PieceDrawing`).

## Architecture

New folder `controller/src/tv/turf3d/`, loaded with `React.lazy` so nothing 3D loads for other games (the spike chunk was
3.2 MB raw and 1.1 MB gzipped, mostly the physics engine). Pure logic is kept separate from React so vitest can test it
without WebGL.

| Unit | Kind | Job |
| --- | --- | --- |
| `layout.ts` | pure | `spacePos(i)`, tile sizes, corner and edge geometry, the inward direction per side |
| `boardTexture.ts` | canvas | Draws the board texture from `TurfSpace[]` and a `TurfLook`; fits names; called after fonts are ready |
| `Drinks.tsx` | R3F | The six drink models and the coaster |
| `Dice.tsx`, `diceFaces.ts` | R3F / pure | Physics dice and the engine-result reconciliation (see Dice) |
| `timeline.ts` | pure | Turns new beats into a timed list of steps: `dice`, `walk`, `land`, `rent`, `payday`, `timeout`, `bankrupt`, `build`. It also owns the hop cadence formula |
| `Choreographer.tsx` | R3F | Plays the timeline: moves pieces, drives the camera rig and effects, and reports "settled" so the DOM overlays can follow |
| `CameraRig.tsx` | R3F | Named shots: `wide`, `dice`, `follow`, `close`, `owner`; eased with shake and dolly |
| `Post.tsx` | R3F | The effect chain and a quality switch |
| `TurfScene.tsx` | R3F | The canvas, lights, table, board slab, houses, pieces |
| `TurfStage3D.tsx` | DOM | Places the canvas full-bleed and lays the existing rails, well panels and flashes over it |

`TurfStage.tsx` keeps its rails, well panels, flashes and audio. It swaps `TurfBoard` for `TurfStage3D` when WebGL2 is
available and the lobby switch is on, and keeps `TurfBoard` as the fallback. `useHops` and the `display` positions move
into the choreographer. No change to the phone protocol is needed except the piece ids.

**Data flow.** The engine is authoritative and instant: a roll immediately produces a `roll` beat (with `dice`) and a
`move` beat (with `path`), and `TurfTv.tokens[].pos` is already final. The TV runs the beats as a queue: the choreographer
holds a "shown" position per token and animates it forward, exactly as `useHops` does today, so a reconnecting or late TV
snaps to the true state.

**Pacing lives in one place.** Today the engine holds the MOVE phase for `path.size * HOP_MS(260) + MOVE_PAD_MS(1400)`.
The new choreography is longer (dice theatre plus a slowed final approach), so both sides use one formula:
`moveDwellMs = DICE_MS + sum(hopMs(k)) + LAND_PAD_MS`, defined in Kotlin (`HomeTurf.kt`) and mirrored in `timeline.ts`,
with a shared fixture test that fails if they differ. Roughly 2.6 s of dice, 0.23 s per early hop, slowing to 0.82 s on
the last, and about 0.9 s to land, so a 7-space move takes about 6.5 s.

**Turn length and the game clock.** Longer turns mean fewer turns inside the 30 to 90 minute clock. A lobby option
`Show: Theatre | Quick` (TV key S) sets the pacing. Quick keeps today's 260 ms hops and skips the dice theatre. The
captain can also tap to skip the current animation. With more than 4 tokens the dice theatre shortens to 1.6 s.

## Dice

The engine picks the numbers, so the dice must land on them. Method: before each throw, run the same throw in a hidden
Rapier world stepped at a fixed rate, read which face ends up, then assign the pip textures to the faces so the engine's
value is the face that ends up on top. The visible dice then run the identical throw. If the two ever disagree (a
dropped frame, a divergence), the choreographer detects the settled top face after the fact and swaps the two die
textures during the last bounce; a die never visibly snaps. Doubles get a flourish. A die that comes to rest tilted
against the other is nudged flat (the spike showed this once).

## Choreography

Beats become the steps below. Timings are starting values to tune on the real TV.

| Moment | What the viewer sees | Camera |
| --- | --- | --- |
| Turn start | The active drink lifts slightly with a soft glow; the rail says "X rolls" | `wide` |
| Roll | Dice enter, tumble, settle; the total banner shows the sum; doubles flash | `dice` |
| Walk | The drink hops space to space, tilts, squashes on landing, pauses a beat at corners | `follow` |
| Anticipation | The last three hops slow and rise; a pulsing target ring marks the destination; a drumroll builds (existing `sfx.drumroll`) | `close` |
| Land | Shock ring, camera shake, the drink wobbles as it settles, the tile lifts | `close` |
| Unowned street | The deed rises off the tile for a buy decision | `close` |
| Rent | Coins stream from the drink to the owner's rail; "RENT $X"; the tile flag pulses | `owner` |
| Tax and Plot Twist | A coin burst, or a card flips over the tile | `close` |
| Timeout | Bars drop over the drink, which slides into the corner | `wide` |
| Payday | A confetti and coin rain when passing or landing on Payday | `follow` |
| Bankrupt | The drink tips over, rolls off the board and shatters or spills | `wide` |
| Build | A house pops up with a bounce | `close` |

Auctions, trades and the debt prompt stay as the current DOM well panels for this spec. They open over the board while
the camera pulls back to `wide`.

Sounds reuse the existing cues (`diceRoll`, `hop`, `drumroll`). New cues (glass clink, bottle tip, coin stream) are a
stretch item. Generating them spends the user's ElevenLabs credits, so ask first; the synth fallback covers them.

## Performance and fallbacks

- **Budget:** 50 fps or better at 1080p on an Apple-silicon Mac, measured with no other GPU work. The dpr is capped at 2,
  and a `Quality` setting (High, Balanced, Low) drops N8AO and real glass in that order. Balanced is the default until
  the real TV is measured.
- **Shader pre-warm:** compile every material off-screen behind the lobby or the piece-picking phase to remove the roll
  hitch.
- **No network:** fonts come from fontsource, reflections from local Lightformers, the drinks and board are procedural.
  Nothing loads from a CDN, so an offline party works.
- **WebGL2 missing or context lost:** fall back to the current 2D `TurfBoard` without error.
- **Memory:** dispose textures and geometries on unmount; a soak test plays 3 seeded bot games back to back and checks
  the renderer's memory counters stay flat.
- **One page only:** the TV screen is the only place the scene renders. The gallery route is dev-only.

## Testing and verification

- **Unit (vitest, no WebGL):** `layout` (every space maps to a distinct tile centre; corners and edges tile without
  gaps), `timeline` (beats to steps, cadence, skip and Quick rules, and the Kotlin/TypeScript dwell formula against a
  shared fixture), `diceFaces` (a value-to-face permutation always places the requested value on top), piece-id mapping
  including old saved ids, and the brand deny-list scan.
- **Engine (Kotlin):** piece ids and names, `autoPieces`, resume mapping, and the new `moveDwellMs` formula; the golden
  master from Plan 1 is unaffected by piece ids and stays green.
- **Visual:** `/tv?gallery=turf3d&beat=<name>` renders fixture beats (lineup, dice, walk close, landed, rent, timeout,
  bankrupt) at 1920x1080. A checked-in Playwright script captures them and a measured-fps run, replacing the ad hoc
  scripts from the spike. Big-party fixtures (13 to 16 people in the lobby, 6 tokens stacked on Payday) are included.
- **E2E:** `e2e/turf.spec.ts` gets a run with the 3D stage on and one with Quick and the 2D fallback.
- **Manual:** one full game on the real TV, watching frame rate and turn length, before merging.

## Milestones and build order

1. **Scene and pieces in the gallery.** `layout`, `boardTexture` (upright text), `Drinks`, lights, post; the six-piece
   lineup and the wide board. No live data. Ends with a measured frame rate.
2. **Live walk.** `timeline`, `Choreographer`, `CameraRig` wired to real beats in `TurfStage3D`; pacing formula on both
   sides; Quick and the skip tap; the 2D fallback.
3. **Dice.** Physics dice with engine-result reconciliation and the settle nudge.
4. **Landing moments.** Land, rent, tax and card, timeout, payday, bankrupt and build effects.
5. **Piece rename.** Engine ids, phone picker and token-card drawings, resume mapping, tests.
6. **Polish and docs.** Pre-warm, quality settings, the soak test, README and show-bible, the optional new audio cues.

Steps 1 to 4 can ship without step 5 by mapping the six old ids to drink art in the scene; step 5 makes the names and
phone art match.

## Risks and open interpretations

- **Turn length:** the biggest risk to game feel. Mitigated by Quick, skip, and tuning; measure a real game.
- **Real TV hardware is unknown.** The 50 fps figure is one Mac. The Quality setting is the safety valve.
- **Dice determinism** depends on Rapier being deterministic for identical inputs on the same machine; the after-the-fact
  swap covers the exception.
- **Upright board text** departs from a real board; it is chosen for couch readability and is easy to revert.
- **Old saved parties** with old piece ids are handled by the mapping; a corrupted id falls back to `cup`.
- **Overhaul overlap:** Plan 2 (functional UI) touches `TurfStage.tsx` too. Build this on top of Plan 2's merge, or
  merge Plan 2 first, to avoid conflicts.
- **Two GPU-heavy tabs** (a dev gallery plus the party) halve the frame rate; the README should say to close spare tabs.

## Credits and licenses

Three.js, react-three-fiber, drei, react-three-rapier and react-postprocessing are MIT. Anton and Rammetto One are
SIL Open Font License (via fontsource). There are no third-party models or textures, so no attributions are needed.
