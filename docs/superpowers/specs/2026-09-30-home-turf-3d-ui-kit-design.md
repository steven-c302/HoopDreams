# Home Turf 3D UI, sub-project 1: the 3D UI kit and the well host

**Status:** design approved in conversation 2026-09-30; written spec awaiting review.
**Follows:** `2026-09-29-home-turf-3d-design.md` (the 3D board), Plans 1 and 2 (merged into local `main`).

## Why

The Home Turf TV now shows a lit wooden tabletop in 3D, but every panel over it (the center well, the banner) is flat
DOM that looks pasted on. The user wants the panels to belong to the scene, and chose the most literal version:
every panel rebuilt in 3D, a paper-card look, locked to the camera.

The whole revamp is too big for one plan. It is split into four sub-projects, each with its own spec, plan and merge:

1. **This one:** the 3D UI kit, the camera-locked card host, and the simple phase panels.
2. Hero moments: buy deed, card, auction call, and the banner and callouts as 3D text.
3. Dense panels: trade, debt, tally, the auction bidding view, and team-up (which needs avatar faces).
4. Rails and table furniture: the player rails as trays or coasters at the table edge, the ticker, the game clock.

## Decisions made with the user

- Scope is the **Home Turf TV screen only** (not phones, not other games).
- The problem to fix is that the flat panels **do not fit the 3D board**.
- Panels are **rebuilt in 3D** (not DOM pinned onto a 3D card).
- Look: a **paper card** (cream face, thick ink outline, colour band, hard offset shadow), matching the board's paper
  and bands. It stays readable across the room and when drunk. No emoji, no bulbs, no synth flourishes.
- Placement: **locked to the camera**, always the same screen spot and size, slightly tilted toward the viewer, so text
  is readable in every shot.
- Text is SDF text (drei `<Text>`, troika), not canvas textures and not `<Html>`.

## Global constraints

- The engine (Kotlin) and the `TurfTv` data are unchanged. Panels read the same fields the DOM panels read.
- The existing DOM well stays: it is the 2D fallback (no WebGL2, context lost, error boundary) and it still shows every
  phase not yet ported. Nothing regresses while later sub-projects port the rest.
- Nothing loads from the network at runtime. Fonts are bundled. troika reads `.woff` and `.ttf` but not `.woff2`, so
  the kit uses fonts that ship `.woff`: Anton (headings and numbers) and Zilla Slab (body copy), both already
  installed. If Zilla Slab reads poorly in review, adding static `@fontsource/figtree` is the fallback.
- Labels stay generic: no brand names on any drink or card.
- No new audio.
- Performance: 50 fps or better at 1080p on an Apple-silicon Mac at the default `balanced` quality (Plan 2 measured 59).
- Colour, shape and text together: nothing is carried by colour alone.
- Commit messages carry no attribution lines.

## Units

All in `controller/src/tv/turf3d/ui/`. Each has one job and is understood without reading its internals.

| Unit | Job | Depends on |
| --- | --- | --- |
| `sizing.ts` (+ test) | Pure. Converts an on-screen pixel size at 1080p to world units at a given camera distance and fov; holds the minimum text sizes (body 28 px, label 22 px, hero 96 px); fits and wraps a string to a width | nothing |
| `theme.ts` | The paper palette and fonts (ink `#1a1a1a`, paper `#fbf3dc`, the board's band colours, Anton and Zilla Slab `.woff` URLs) | fonts |
| `Card.tsx` | Rounded paper card: ink outline, optional colour-band header, hard offset shadow; slight tilt | `theme` |
| `Label.tsx`, `Money.tsx`, `Pill.tsx` | Text styles that enforce the minimum sizes; `Money` counts up with an eased curve (`countUp` in `sizing.ts`, tested) | `sizing`, `theme` |
| `Button.tsx`, `Row.tsx` | Display-only chunky button (shows what players can pick on their phones; the TV has no pointer) and key/value or list rows | `Card` styles |
| `TimerRing.tsx` | The countdown ring, driven by the phase clock the DOM well already receives | `theme` |
| `Dais.tsx` | The camera-locked mount: parents its content to the camera, sets the tilt, scale and screen position, fades and slides for shots other than `wide`, and runs the lift-in and lift-out when the panel key changes | `sizing`, camera shot from `Craft` |
| `panels.ts` (+ test) | Pure. `panelFor(phase)` returns the name of the 3D panel for a phase, or `null` when that phase still uses the DOM well | nothing |
| `panels/*.tsx` | One small component per ported phase, built only from the kit | kit, `TurfTv` |

`TurfStage3D` renders `Dais` inside the canvas. It shows the 3D panel when `panelFor(g.phase)` is not null and the DOM
well otherwise (the DOM well is hidden when a 3D panel is showing).

## The card's frame

Every ported panel sits in the same frame, so the card looks like one object whose contents change:

- **Header:** the turn's piece (a small drink model from `Drinks.tsx`, mapped by `drinkFor`), the player name, the game
  clock (seven-segment style, as the DOM well shows it) and the `TimerRing` when the phase is timed.
- **Body:** the phase panel.
- **Footer strip:** the last three ticker lines, latest emphasised.

## Ported phases (this sub-project)

| Phase | 3D panel shows (same data the DOM panel shows) |
| --- | --- |
| `move` | The header and footer only; the real dice are already on the table |
| default (roll turn) | "NAME ROLLS" or "DOUBLES! ROLL AGAIN", the neighbourhood art replaced by a small stack of building blocks |
| `manage` | "BUILD, TRADE, OR END" and the bank's houses and hotels left |
| `jail` | "IN TIMEOUT" and "Pay $50, use a card, or roll doubles" |
| `choose` | "BUS! PICK A MOVE" or "TRIPLES! GO ANYWHERE" and who is choosing |
| `pieces` | "GRAB YOUR PIECE!" and the six drinks in a grid, free ones ghosted, taken ones tinted with the owner's name |
| `deal` | "STARTER PLACES": each player's drink, name and their starter places as small band-coloured tags; "Paid for out of everyone's $1,500" |

Not ported here (still the DOM well): `teamup` (needs avatar faces), `buy`, `auction`, `card`, `debt`, `trade`, `tally`.
The three-dice row that the DOM panels show is omitted in 3D because the physical dice are on the table (the speed die
still appears only in the DOM well, as Plan 2 noted).

## Behaviour

- **Transitions:** when the panel key changes, the old card drops away and the new one lifts in over about 0.3 s using
  the well's existing easing. Reduced-motion is not a concern on a TV.
- **Shots:** the card is hidden for every shot except `wide`, exactly as the DOM well dims today, so it never covers a
  walking piece. `Dais` reads `craft.shot`.
- **Readability rule:** no text on the card renders smaller than its kit minimum. `sizing.ts` enforces this and tests it.
- **Failure:** a text or font failure inside the canvas is caught by the existing error boundary, which drops to the
  DOM well; the banner still shows.

## Testing

- Unit tests (vitest): `sizing` (pixel to world conversion, minimums, fit and wrap, count-up curve), `panels`
  (phase to panel mapping, including every phase listed above and that unported phases return null).
- R3F components are checked by type-check and by 1080p screenshots. A gallery beat and a screenshot per ported phase
  are added to `TurfGallery.tsx` and `scripts/turf3d-shots.mjs`; each frame is read for legibility before the plan is
  called done.
- The frame-rate loop in the screenshot script runs again and must stay at 50 fps or better at `balanced`.
- The existing Playwright Home Turf spec must still pass (the phone flow is untouched).

## Out of scope

Phones, other games, the buy, card, auction, debt, trade and tally panels, team-up, the banner and callouts, the rails,
the ticker as its own object, any engine change, new audio.

## Risks

- **Text legibility in 3D** is the main risk. Mitigation: pixel-based minimums, SDF text, and a screenshot review per
  phase at 1080p.
- **Font support:** troika cannot read `.woff2`. Mitigation: `.woff` fonts already installed; a static Figtree is the
  fallback.
- **Camera-locked content** must not fight the postprocessing (depth-of-field or tilt-shift). Mitigation: the card is
  rendered so the effects treat it as in focus, and this is checked in the first screenshot before the rest is built.
