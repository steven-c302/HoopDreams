# Home Turf 3D UI, sub-project 4: the player rails

**Status:** written from the approved sub-project split; the user said "keep going" for this stage.
**Follows:** the kit, hero and dense-panel specs (all merged). Same look, same constraints.

## Scope

Replace the flat player cards on the left and right rails with 3D paper cards, locked to the camera at the same screen
spots, so nothing but the game logo and the podium is flat DOM any more.

Each card shows what the DOM `TokenCard` shows:

| Part | 3D version |
| --- | --- |
| Piece | The token's drink on a disc in its colour |
| Name and people | The token name in Anton; the seat's avatar face (solo) or the team's member faces, the seat holder larger, disconnected players dimmed |
| Cash | Big Anton dollars that count up or down when cash changes, with a green "+$200" or red "-$50" change note for about 1.5 s |
| Worth | "WORTH $1,540" |
| Places | A row (two if needed) of small squares in each owned place's colour band; a mortgaged place is drawn hollow; "No places yet" when none |
| Badges | "IN TIMEOUT", "N SET(S)", "GET OUT ×N" as pills, wrapping to a second line if needed |
| Turn | The card whose turn it is has a gold plate behind it |
| Out | A bankrupt token's card is dimmed with a tilted "OUT" stamp |

## Decisions

- Cards are 400 by 250 reference pixels, stacked per rail with the same vertical rhythm as the DOM rails, centred a
  little below the middle so the game logo (top left) stays clear.
- One `Dais` hosts each rail, so each side needs one point light for its drinks. The callout and banner Daises drop their
  light (they show no drinks), keeping the scene at three point lights.
- The DOM cards stay mounted and keep their layout but become invisible once the 3D text has synced (the same readiness
  signal as the panels), so a font failure leaves the DOM rails.
- The 3D rails only appear once the text is ready, so the two are never on screen together.
- The ticker and the game clock already live on the panel card (header clock, footer ticker); they are not moved to
  the table edge, which would add nothing. The game logo stays DOM.
- Cash count-up: 700 ms ease-out, one text update per changed whole dollar.
- "WORTH" sits beside the cash rather than as a badge so the badge row fits one line.

## Global constraints

Engine and `TurfTv` unchanged. Nothing loads from the network. Labels generic. Minimum text sizes from `sizing.ts`.
50 fps or better at `balanced` (three point lights at most). The DOM rails remain the 2D fallback.

## Testing

Unit tests for the pure helpers (count-up curve, pill widths and flow, place-square layout, change note). 1080p
screenshots with the six-token fixtures (worst case) read for legibility, including an active turn, a jailed token, a
bankrupt token and a team game; fps loop and off-origin check re-run.

## Risks

- Six cards, each with faces, squares and pills, must not cost frame rate. Mitigation: one shared square geometry,
  text only re-synced when a value changes, and the fps loop.
- A long token name or many badges must not overflow a 400 px card. Mitigation: max widths, wrapping pills, checks on
  the fixtures.
