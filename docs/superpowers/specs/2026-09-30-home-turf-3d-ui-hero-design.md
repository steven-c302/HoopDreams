# Home Turf 3D UI, sub-project 2: hero moments

**Status:** written from the approved sub-project split and the sub-project 1 kit; the user said "go ahead" for this stage.
**Follows:** `2026-09-30-home-turf-3d-ui-kit-design.md` (the kit, the card, the Dais). Same constraints, same look.

## Scope

Move three phase panels and two overlays from flat DOM into the 3D card system:

| Piece | 3D version (same data the DOM version shows) |
| --- | --- |
| `buy` panel | A deed header (place name on its colour band, kind), three fact pills (rent, whole set, hotel; rides or dice for rides and utilities), "BUY IT FOR $220?" and "or it goes to auction" |
| `auction` panel | The deed header, "AUCTION! TOP BID", the top bid in the seven-segment style on a dark plate that pops on each new bid, the leader (name, or "Nobody yet. Bid on your phone!") and "N bids · each bid resets the clock" |
| `card` panel | A deck-tinted plate (Plot Twist sky, Group Chat bubblegum) with the deck name, the card text and, when drinking is on, "DRINK 2 SIPS"; it flips in when a new card is drawn |
| The total banner ("4 + 4 = 8 DOUBLES!") | Big outlined Anton text hung near the top of the screen, camera-locked |
| The flash callouts (RENT, DRINK!, TIMEOUT!, BANKRUPT!, SOLD!, DEAL!, HOME TURF!, LAST LAP!, TRIPLES!, +$200) | A camera-locked star-burst sticker with the callout text and sub-line, one at a time in the order things happened (the existing queue) |

Not in scope: debt, trade, tally, team-up (sub-project 3); rails, ticker as an object, clock at the table edge
(sub-project 4); any engine change; new audio (the existing beat sounds keep playing).

## Decisions

- Callouts and the banner reuse `Dais` (drop, lift, camera lock) with a per-instance distance and vertical offset, so
  they can sit in front of the card and above it without depth fighting.
- Callout and banner text stays visible in every shot (unlike the card, which hides for close shots).
- Colours: the callouts' fills come from the game's CSS variables, which differ per game theme. The 3D side reads them
  once from the stage element (`getComputedStyle`) into a palette that a context provides inside the canvas, with
  hard-coded fallbacks if a variable is missing.
- The DOM banner and DOM flash keep working until the 3D text has synced (the same readiness signal as the card), so a
  font failure never loses a callout.
- The new panels use the standard card size; the deed on the board (`Deed.tsx`) stays as is.
- `Money`'s count-up is not built: the auction bid pops (scale bump) instead, which needs no new animation code.

## Global constraints

Engine and `TurfTv` unchanged. Nothing loads from the network. Labels generic. Minimum text sizes from `sizing.ts`.
50 fps or better at `balanced`. The DOM well, flash and banner remain the 2D fallback.

## Testing

Unit tests for the pure parts (`copy.ts`: deed facts, buy call, auction hint, sip line, colour-variable parsing and
palette resolution; `panels.ts` mapping). 1080p screenshots for buy, auction, card, a doubles banner, and rent, drink,
timeout and bankrupt callouts, read for legibility; the fps loop and the off-origin check re-run.

## Risks

- Star-burst text must stay inside the spikes for long sub-lines. Mitigation: wrap width and a size clamp, checked on
  the longest real callout ("Everyone but Amanda: 2 SIPS").
- A callout and the card can be on screen together (rent lands while a card is up). Mitigation: distinct distances and
  a check in the screenshots.
