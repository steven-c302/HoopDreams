# Home Turf 3D UI, sub-project 3: the dense panels

**Status:** written from the approved sub-project split; the user said "keep going" for this stage.
**Follows:** the kit and hero-moments specs (`2026-09-30-home-turf-3d-ui-kit-design.md`, `2026-09-30-home-turf-3d-ui-hero-design.md`), both merged. Same look, same constraints.

## Scope

Move the last four phase panels from flat DOM into the 3D paper cards, so only the podium keeps the flat stage:

| Panel | 3D version (same data the DOM panel shows) | Card |
| --- | --- | --- |
| `debt` | The debtor's drink, "NAME OWES $1,200", "to X for why", the debtor's cash in seven-segment digits (green when it covers the debt, red when not), and "Sell or mortgage to cover it, or go bankrupt" | standard |
| `trade` | "TRADE OFFER!" or "COUNTER-OFFER #n"; two columns, each with the giver's drink and name, up to five place tags on their colour band (plus "+N more"), a cash pill and a Get Out card pill, or "nothing"; two arrows between; "NAME decides on their phone. Heckle freely." | setup (big) |
| `tally` | "FINAL TALLY"; one row per token, best first: rank, drink, name, a bar that grows in proportion to worth, and the worth; "Cash + places (half if mortgaged) + buildings at cost" | setup (big) |
| `teamup` | "TEAMS!", the notice if any, one row per team: a colour plate with the team name, then up to four members as their avatar face and first name (plus "+N"); "Shuffle: S on the TV or the captain's phone" | setup (big) |

Avatar faces: a face is a preset cartoon, a doodle, or a photo. The 3D side reuses the existing SVG `Face`: it is
rendered to markup, its CSS variables are replaced by palette colours so it can be drawn as a standalone image, drawn
onto a canvas texture, and a photo (same-origin) is drawn on top, clipped to the face circle. If the picture cannot
load, the preset shows through.

## Decisions

- The setup-size card has no ticker, so the frame drops its footer for that size (trade shows no ticker; the header
  still shows whose turn it is).
- The DOM tally keeps firing its confetti: the DOM panel stays mounted (hidden) behind the 3D card, so nothing to move.
- Copy tweaks for fit: "Selling and mortgaging to cover it… or going bankrupt" becomes "Sell or mortgage to cover it,
  or go bankrupt" (shorter, no ellipsis character). The trade swap arrows are drawn from shapes, not text glyphs
  (a glyph missing from the font would make the text library fetch a fallback font from a CDN).
- Tally numbers appear with their bars; a count-up is not built (the bars carry the drama).
- Members beyond the fourth on a team, and places beyond the fifth in a trade, collapse to "+N".

## Global constraints

Engine and `TurfTv` unchanged. Nothing loads from the network (only the app's own photo endpoint for face photos).
Labels generic. Minimum text sizes from `sizing.ts`. 50 fps or better at `balanced`. The DOM well remains the 2D fallback.

## Testing

Unit tests for the pure parts (debt, trade, tally and team-up helpers; SVG variable inlining; photo circle geometry;
the phase-to-panel map, where only the podium stays flat). 1080p screenshots of each panel read for legibility, with the
six-player fixtures as the worst case; fps loop and off-origin check re-run.

## Risks

- Worst-case rows (six tokens on the tally, eight rows in a trade, four members on a team) must fit the big card.
  Mitigation: caps and "+N", checked on the existing six-token fixtures.
- Face textures load asynchronously (dynamic import of the server-side renderer plus image decode). Mitigation: the
  face simply appears when ready; nothing depends on it.
