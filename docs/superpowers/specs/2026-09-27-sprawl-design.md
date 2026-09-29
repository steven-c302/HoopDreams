# Sprawl: design spec (Catan-style party game for Party OS)

## Context

The user wants Catan as a Party OS game. As with Home Turf, the plan is the real rules sped up for a party of tipsy
friends: phones are the controllers, the TV shows the board, and the crew's own places are on the map.

Decisions the user made (2026-09-27):

| Question | Answer |
| --- | --- |
| Players | **Solo, 3–6.** 5–6 players use the official extension board (30 hexes). Extra players watch. |
| Length | **Party speed:** turn timers, a game clock (30/45/60/90 min or none), an **8 VP** target (lobby: 8 or 10), and a LAST ROUND when time runs out |
| Board | **The crew's places:** each hex carries a crew place name from an editable names file; resources stay the standard five |
| Trading | **Full phone trades:** the active player offers; the target (or anyone) accepts, rejects or counters; plus bank 4:1 and harbours |
| Placing | **Mini-map on the phone:** legal spots glow as big tap targets; tap, then confirm; the TV mirrors the pick live |
| Rules | **Full base game:** robber, discard on 7, the development deck, Longest Road, Largest Army, harbours |
| Layout | **Random and balanced** every game (no 6 or 8 on neighbouring hexes) |
| Drinks | **Drink calls on by default**; the lobby can switch them off |
| Name | **Sprawl**: "Build out from your friends' places. Block everyone else." No "Catan" anywhere |
| 5–6 rule | **No special build phase**: only the bigger board |
| Audio | **Generate new cues** with the user's ElevenLabs key |
| Approach | **Its own module, built the Home Turf way**; it reuses Home Turf's engine hooks unchanged |

Game id `sprawl`. The web TV only: the Android TV app stays on the 1.0 look and isn't registered.

## Rules: base game at party speed

Official base-game rules (5th edition) except where marked **(party)**.

- **Board:**
  - 3–4 players get 19 hexes in rows of 3-4-5-4-3.
  - 5–6 players get 30 hexes in rows of 3-4-5-6-5-4-3.
  - Terrain:
    - Base: 4 forest, 4 pasture, 4 fields, 3 hills, 3 mountains, 1 desert.
    - Extension: 6, 6, 6, 5, 5 and 2 deserts.
  - Number tokens:
    - Base: 2, 3–6 twice each, 8–11 twice each, 12.
    - Extension: 2 and 12 twice, 3–6 and 8–11 three times each.
  - Setup is **(party)** fully random. Terrain is shuffled and tokens are dealt onto the non-desert hexes. The deal
    is repeated until no two 6/8 tokens touch (the official variable-setup advice).
- **Harbours:**
  - Base: 9 (4 generic 3:1, plus one 2:1 for each resource). Extension: 11 (adds a generic and a sheep 2:1).
  - **(party)** They sit on evenly spaced coastal edges, shuffled.
  - A harbour belongs to both corners of its edge.
- **Bank:** 19 of each resource (24 for 5–6).
  - **Shortage rule:** if a resource can't be paid in full to everyone owed it, nobody gets any, unless only one
    player is owed it, in which case they get what's left.
- **Pieces** per player: 5 settlements, 4 cities, 15 roads.
- **Costs:**

  | Build | Cost |
  | --- | --- |
  | Road | brick + wood |
  | Settlement | brick + wood + sheep + wheat |
  | City | 2 wheat + 3 ore |
  | Dev card | sheep + wheat + ore |

- **Setup:** a snake draft (1→n, then n→1). Each pick is a settlement, then a road touching it.
  - Distance rule: no settlement on a corner next to another settlement.
  - Your second settlement pays one of each resource from its hexes.
  - **(party)** 30 s per piece. If time runs out, the phone auto-places: the settlement goes on the legal corner
    with the most pips, then the most resource variety, and the road on its first free edge.
- **Turn:**
  1. Roll (20 s, then auto-roll). You may play one dev card first.
  2. Production: every hex with the rolled number, except the robber's, pays 1 per settlement and 2 per city on
     its corners.
  3. The main phase **(party: 60 s)**: build, buy dev cards, play one dev card, and trade with the bank or players.
     The clock freezes while a trade is open, and each build tap tops the clock back up to at least 10 s.
  4. End turn (or the timer ends it).
- **Rolling a 7:**
  - Everyone with more than 7 cards discards half, rounded down.
    - **(party)** 20 s, and they discard at the same time.
    - A timeout discards the most plentiful cards first.
  - Then the roller moves the robber, **The Landlord** (15 s, then auto-picks the best hex to block).
  - Then they steal one random card from a player on that hex (10 s to choose, or it's auto if there's only one).
  - No production happens on a 7.
- **Dev deck:**
  - Base: 25 cards (14 Knights, 5 VP, 2 Road Building, 2 Year of Plenty, 2 Monopoly).
  - Extension: 34 cards (20 Knights, 5 VP, 3 of each progress card).
  - You can play one per turn, before or after the roll, but not a card bought this turn. VP cards are never
    played: they count secretly.
  - **Knight** (Sprawl: **Bouncer**): move The Landlord and steal.
  - **Road Building** (**Road Trip**): place 2 free roads (15 s each, auto-placed on timeout).
  - **Year of Plenty** (**Windfall**): take any 2 from the bank.
  - **Monopoly** (**Shakedown**): name a resource; everyone gives you all of theirs.
- **Awards:**
  - Longest Road (**Longest Road**, 5 or more roads) and Largest Army (**Most Bouncers**, 3 or more) are worth
    2 VP each.
  - Ties don't take them away from the holder.
  - When a broken road leaves a tie among the leaders, or nobody is at 5, the award is set aside (the official rule).
- **Trading:**
  - With the bank: 4:1, 3:1 with a generic harbour, 2:1 with a matching harbour. Main phase only.
  - With players: the active player offers, in the main phase only, to one player or to **anyone**.
    - The target taps ACCEPT, REJECT or COUNTER.
    - An offer to anyone goes to whoever accepts first.
    - A counter becomes the counterer's offer to the active player, who accepts or rejects it.
  - **(party)** Limits:
    - One open trade at a time.
    - 30 s to reply.
    - At most 2 counters per trade and 4 offers per turn.
    - The turn clock freezes while a trade is open (Home Turf's freeze).
  - Neither side can give cards they don't hold (checked again at accept).
- **Winning:**
  - Reaching the VP target (8 by default, `vp` setting 8 or 10) **on your own turn** ends the game at once. That
    means VP cards too, and the TV reveals them.
  - **(party)** When the game clock runs out, **LAST ROUND**: turns continue until it would be the first player's
    turn again, then the most VP wins. Ties go to the Longest Road holder, then to whoever holds the most cards.
  - The game also ends if fewer than 2 players are left.

### Timers (party)

| Decision | Timer | If it runs out |
| --- | --- | --- |
| Setup settlement / road | 30 s | auto-placed (best pips) |
| Roll | 20 s | auto-roll |
| Main phase | 60 s (each build tap keeps ≥ 10 s) | end turn |
| Discard | 20 s | discards the most plentiful cards |
| Move The Landlord | 15 s | best hex to block (most opponent pips, none of yours) |
| Steal | 10 s | random victim |
| Road Trip road | 15 s | auto-placed |
| Windfall / Shakedown pick | 15 s | the rarest resource in your hand |
| Trade reply | 30 s | rejected |

An absent player's turn plays on autopilot at 5 s, the same as Home Turf, with the same
reconnect/disconnect clock adjustments.

### Drink calls (on by default; `drinks` setting)

| Event | Drink |
| --- | --- |
| The Landlord or a Bouncer steals from you | 1 sip |
| Discarding on a 7 | 2 sips |
| You lose Longest Road or Most Bouncers | 2 sips |
| You build a city | everyone else drinks 1 |
| A Shakedown takes your cards | 1 sip |
| You win | everyone else finishes their drink |

## The board names (crew draft, editable)

`tv/engine/src/main/resources/sprawl/board.json` holds only names:
- **`places`:** at least 28 crew place names (≤ 22 chars each). They reuse Home Turf's crew list (Charlie's Hot Tub,
  Izzy's Rooftop, Junha's Kitchen …) plus party spots. Each game deals them onto the non-desert hexes at random.
- **`desert`:** the desert's name, "The Couch".
- **`landlord`:** "The Landlord".
- **`resources`:** display names for brick, wood, sheep, wheat and ore.
- **`dev`:** names for knight, road, plenty, mono and vp.
- **`awards`:** names for road and army.

A test validates it (counts, lengths, no duplicates). The README gets a "Rename Sprawl's places" line next to Home
Turf's.

## Research and reuse

- **Rules:**
  - Catan 5th-edition base rules and the 5–6 player extension (piece counts, deck makeup, terrain and token counts).
  - The official "variable setup" advice (no adjacent 6/8).
- **Hex geometry:** Red Blob Games' hex grid guide (axial coordinates, pointy-top corners). Vertices and edges are
  deduplicated by rounding corner positions, then numbered by position (top-to-bottom, left-to-right), so ids are
  stable.
- **Bots:** Catanatron's rule-based bot (as a reference only; no code copied), which:
  - opens on the most pips and resource variety;
  - builds city > settlement > dev card > road when it can afford them;
  - trades with the bank only to complete a build;
  - accepts an offer if it completes a build now and gives away nothing it needs.
- **IP:**
  - Mechanics and numbers aren't protected, so we keep them.
  - We keep out the name "Catan", the art, the card wording and the "Robber" figure.
  - The robber is The Landlord, the Knight is the Bouncer, and every name is editable.

## Architecture

### Engine: `tv/engine/src/main/kotlin/partyos/engine/games/sprawl/`

| File | Purpose |
| --- | --- |
| `SprawlGeometry.kt` | `Layout(rows)`: hexes (axial q, r, centre x/y), vertices (x/y), edges (vertex pairs) and adjacency: hex→vertices, vertex→hexes/edges/vertices, edge→vertices, and coast edges in order round the island. Built once per size and cached. |
| `SprawlSetup.kt` | Deals terrain, numbers (the 6/8 rule) and harbours from a `Random`. |
| `SprawlRules.kt` | Pure functions: costs; legal settlement, city and road spots (setup and normal); production with the shortage rule; trade ratios; Longest Road (DFS; other players' buildings cut it); award holders; VP; discard size and auto-discard; the robber's auto-hex; the opening spot score. |
| `SprawlDeck.kt` | The dev deck lists (base / extension). |
| `SprawlState.kt` | `SprawlState`, `SSeat`, `STrade`, `SBeat`, all with plain serializable types. |
| `Sprawl.kt` | `GameModule<SprawlState>`: phases, actions, deadlines, presence, views. |
| `SprawlPhone.kt` | `Screen.Sprawl` builder. |
| `../../SprawlViews.kt` | `SprawlTv`, `SprawlMapTv` and the phone data classes. |
| `SprawlNames` (in `SprawlSetup.kt`) | Loads and validates `sprawl/board.json`. |

**Resources** are indexed 0..4 (brick, wood, sheep, wheat, ore), and a hand is a 5-int `List<Int>`. The terrain
kinds are hills, forest, pasture, fields, mountains and desert (resource index = terrain index, desert = 5).

**RNG:** the same approach as Home Turf: `rngSeed` + `draws` with `Random(PartyEngine.phaseSeed(seed, draws++))`
for dice, steals and deals.

**Phase machine (`SprawlState.phase`):**
- **Setup:** `setup` (settlement, then road, snake order; `setupStep` counts 0 until 2n×2).
- **Turn:** `roll` → (7: `discard` → `robber` → `steal`) → `main` → next seat.
- **Side phases:**
  - `road2` for Road Trip (up to 2 roads);
  - `pick` for Windfall and Shakedown (the resource choice);
  - `robber` and `steal` can also come from a Bouncer before or after the roll, then return to `roll` or `main`
    (`resume` field);
  - `trade` freezes whatever phase it interrupted (`frozenPhase` + `frozenMs`, like Home Turf).
- **End:** `lastRound` is a flag. `tally` (14 s) → `podium` (15 s) → Finish. An instant win goes straight to
  `tally`.
- `waitingOn` returns null everywhere except `discard`, which returns the players who still owe cards, so the
  phase ends early once everyone has discarded.

**State:**
- The board:
  - `size` (0 base, 1 extension);
  - `terrain` and `numbers` (lists by hex), `names` (hex place names);
  - `harbours` (list of `SHarbour(edge, kind)`, where kind −1 = generic 3:1 and 0..4 = 2:1).
- The pieces: `robber` (hex), `vOwner`, `vLevel` (0 none, 1 settlement, 2 city) and `eOwner` (by edge).
- Each seat (`SSeat`): id, name, colour, hand, dev cards (a list of kinds), `newDev` (bought this turn), knights
  played, `gone`.
- The table:
  - `bank`, `deck` (the draw order);
  - `turn`, `order`, `setupStep`, `dice`;
  - `devPlayed` (this turn);
  - `roadHolder` and `armyHolder` (−1 when nobody);
  - `discards` (owed per seat);
  - `trade`, `offers` (this turn), `peek` (the phone's current pick, for the TV);
  - `clockLeftMs`, `phaseMs`, `lastRound`;
  - `vpTarget`, `drinks`;
  - `beats` + `beatSeq` + `ticker`;
  - `winner` and `tally`.

**Engine changes:** none to the runtime.
- `optionRange` gets `"vp" -> 8..10` (only 8 or 10 are offered).
- `Screen.Sprawl` joins `Views.kt`.
- The devserver registration comes last.

### Views

- **`SprawlMapTv`** is static per game and sent in both payloads, about 4 KB for the big board:
  - hexes: `x`, `y`, terrain, number and name;
  - vertices: `[x, y]` in board units;
  - edges: `[a, b]`;
  - harbours.
- **`SprawlTv`:**
  - `phase`, the map, `robber`, `vOwner`/`vLevel`/`eOwner`, `dice`;
  - seats: name, colour, members, card count, dev count, VP shown (hidden VP cards excluded until the tally or a
    win), knights, road length, award flags, `gone`;
  - `turn`, trade, `peek`, discards owed, clock fields, `lastRound`, `timed`, `vpTarget`, drinks, beats, ticker,
    tally.
- **`Screen.Sprawl`** (the phone):
  - `me`: seat, colour, hand, dev cards with a playable flag, VP including hidden ones, pieces left, harbour ratios.
  - `prompt`: kind, title, detail, actions, timed.
  - `spots` + `spotKind` (vertex, edge or hex) for the current placing mode, and `victims`.
  - Also: the map, the live board arrays, the robber, `trade` (role, counts), `partners` (index, name, colour,
    card count), `canTrade`, `canBank`, the costs the phone can afford (`affords`), `discard` (how many), `drink`.
  - The simulation test asserts `PhoneState` ≤ 12 KB and `TvState` ≤ 24 KB on the 6-player board.

**Actions (`kind`):**
- `roll`, `end`;
- `place {target}` in setup and Road Trip;
- `build {what: road|settlement|city, target}`, `buyDev`;
- `play {card: knight|road|plenty|mono}`, then `pick {res}` (twice for plenty);
- `discard {cards: [5 ints]}`, `robber {target}`, `steal {victim}`;
- `bank {give, get}`;
- `trade {to (−1 = anyone), give: [5], get: [5], counter?}`, `tradeReply {trade, option: accept|reject}`,
  `tradeCancel {trade}` (all three are phase-free);
- `peek {target}`: sets the TV's highlight and nothing else.

**Presence:**
- Autopilot as in Home Turf.
- A kicked player's seat becomes `gone`: skipped in the turn order, with their pieces left on the board as blockers
  (they produce nothing).
- Late joiners watch.
- Fewer than 2 seats left ends the game in a tally.

**Scores:** at the tally, each seat's player gets `Effect.Award(vp)`, and the winner's line becomes a Highlight.

### Phone: `controller/src/screens/SprawlScreen.tsx` + `sprawl-phone.css`

- **Top band:** colour, name, VP and the hand as five resource tiles (drawn icons, count, and the colour and
  initial of the resource name).
- **Tabs:**
  - **NOW:** the one decision, with a big ROLL, the mini-map for placing, discard steppers, victim buttons, resource
    pickers, trade replies and END TURN. In the main phase NOW shows BUILD shortcuts: ROAD, SETTLEMENT and CITY
    open the map in that mode, and DEV CARD buys one.
  - **CARDS:** your dev cards, with PLAY buttons.
  - **TRADE:** bank/harbour trades (give × ratio → get) and the offer builder (partner or ANYONE; give/get steppers
    capped by your hand).
- **Mini-map** (`SprawlMap.tsx`, shared with the TV):
  - SVG of the hexes, numbers and pieces.
  - Legal spots are big pulsing tap targets (at least 44 px at 2×). The 30-hex board opens zoomed with scroll.
  - Tap selects and sends `peek`; CONFIRM places.
- **Buzz:** on a new decision, on a steal and on drink calls.

### TV: `controller/src/tv/SprawlStage.tsx` + `sprawl.css`

- **Board:** the centred hex map, about 1000 px tall.
  - Hexes are flat cartoon terrain with ink outlines, a drawn terrain glyph, the place name and a number token
    (red 6/8, pips).
  - Harbours are drawn as a jetty and a ratio tag. Settlements are little houses, cities are two-storey blocks,
    and roads are fat bars in the seat colour with ink outline.
  - The Landlord is a drawn top-hat figure.
  - The phone's `peek` shows as a pulsing ring.
- **Rails:** 3 seat cards per side, showing the face, name, VP (big), card count (red when over 7), dev count,
  bouncers, road length, and award ribbons. The active seat tilts forward.
- **Top bar:** the turn banner (face + name + phase call), dice, game clock or LAST ROUND, and the decision `Timer`.
- **Overlays:** the trade (two columns of resource tiles, then an ACCEPTED/REJECTED stamp), the discard board (who
  still owes), the Windfall and Shakedown picks, and the tally bars. Flashes (Burst) for 7, STOLEN, LONGEST ROAD,
  MOST BOUNCERS, CITY, SHAKEDOWN, DRINK, LAST ROUND and WINNER.
- **Production:** resource chips pop from each producing hex to its owner's rail card.
- **Lobby:**
  - `CoverArt` gets a drawn hex cluster.
  - The TV keys ↑↓ set the minutes and V toggles 8/10 VP; D toggles drinks.
  - The captain's lobby gets Game clock, VP target and Drink calls.
- **`director.ts`:** the `sprawl` music, with standings and podium beds for the tally and podium.

### Audio

- **Reuse** `diceRoll`, `hammer`, `offerPing`, `dealDone`, `womp`, `drinkCall`, `pop`, `whoosh`, `drumroll`,
  `lastLap`, the `hurry`, `standings` and `podium` beds, and `lastlap`.
- **New:**
  - bed `sprawl`;
  - effects `landlord` (sneaky villain sting), `harvest` (a bright pluck for production), `settle` (a wooden thunk
    for a new settlement), `cityUp` (a brass rise), `longRoad` and `bigCrew` (award fanfares), and `sprawlWin`.
- Each has a synth/existing-sample fallback until it's generated.

## Testing

- **`SprawlGeometryTest`:**
  - Base: 19 hexes, 54 vertices and 72 edges. Extension: 30 hexes, 80 vertices and 109 edges (pinned from the
    computed layout).
  - Each vertex touches 2–3 edges and 1–3 hexes. The coast is a closed loop.
- **`SprawlRulesTest`:** the distance rule, road connection (blocked by an opponent's building), production with
  the shortage rule, Longest Road (branches, loops, cut by a settlement, tie handling), discard auto-pick, trade
  ratios, and the 6/8 rule over 200 deals.
- **`SprawlTest` (flow, through the real engine):**
  - Setup: the snake order and the second settlement's resources.
  - Rolls: production and a 7 (discard → robber → steal).
  - Building and its costs.
  - Dev cards: not the turn they're bought, one per turn, a Knight before the roll.
  - Awards moving, a trade and its counter, the bank trade.
  - Winning: the instant win, the last round, and a timeout on every phase.
  - A kick mid-turn, and a snapshot round trip.
- **`SprawlSimulationTest`:**
  - Bots play seeded 3-, 4-, 5- and 6-player games to the end, checking invariants every second: the bank plus
    hands conserve each resource, piece counts stay within limits, the distance rule holds, and roads connect.
  - It also checks payload sizes and prints the median game length at a 45-minute clock.
- **Controller:**
  - `protocol.test.ts` gets `sprawl` in `SCREENS`, with fixtures regenerated.
  - `e2e/sprawl.spec.ts` covers three phones placing the setup via the mini-map (settlement → confirm → road → confirm),
    a roll, and END TURN.

## Build order

1. Spec (this file).
2. Geometry, setup, rules and deck (test-first).
3. The `Sprawl` module, state, views and phone view (test-first flow tests).
4. The bot simulation.
5. Protocol types, fixtures, the phone screen and the shared map.
6. The TV stage, lobby, captain, director and bots.mjs.
7. Devserver registration, README and e2e.
8. Audio generation, then commit.
