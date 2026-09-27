# Home Turf: design spec (Monopoly-style party game for Party OS)

## Context

The user wants Monopoly as a Party OS game, researched well and built "as best as possible". Party OS is played on a
TV by tipsy friends with phones as controllers. So this is Monopoly's real rules, sped up for a party, with drink
calls and the crew's own places on the board.

Decisions the user made (2026-09-27):

| Question | Answer |
| --- | --- |
| Players | **Both modes**: solo (one token each, up to 6) or teams sharing a token (2–6 teams) |
| Length | **Party speed, 45–60 min**: short-game rules, a timer on every decision, and a fixed end where the richest wins |
| Drinks | **Drink calls on by default**; the lobby can switch them off, and water counts |
| Board | **The crew's places**: a drafted board in one editable names file; the user renames spaces later |
| Trading | **Full trades on phone**: properties + cash + jail cards both ways; accept, reject or counter |
| Team calls | **Rotating team roller**: each team turn, a different teammate holds the dice and makes the calls |
| Auctions | **Live auction on every phone** when a property is passed on; each bid resets a short clock |
| TV layout | **Board centred**: the action plays in the board's middle, players sit on side rails, and the camera zooms while a token hops |
| Name | **Home Turf**, "Buy your friends' places. Charge them rent." No "Monopoly" or "-opoly" anywhere |
| Speed die | **On** (official Mega Edition rule), always used |
| Pieces | **Pick on your phone** in a 15 s "Grab your piece" beat; first tap wins; untaken ones are auto-assigned |
| Audio | **Generate new cues** with the user's ElevenLabs key (ask once more right before running it) |

Game id `turf`. The web TV only: the Android TV app stays on the 1.0 look and isn't registered (like Write It Down).

## Rules: Monopoly, party speed

Official US rules (Hasbro 2008+), except where marked **(party)**.

- **Start:** $1,500 each, and up to 6 tokens.
  - **(party, Hasbro's official Time Limit game)** each token is dealt 2 random properties and pays for them.
  - **(party)** $1,500 rather than the speed-die rules' $2,500, so rent bites within 45 minutes. The simulation
    confirms or tunes this.
- **Turn:** roll 2d6, move, resolve the space. Doubles roll again; a third double sends you to Timeout (jail).
  Passing Payday (GO) pays $200.
- **Speed die (official Mega Edition rule):** used once you've passed Payday once. The faces are:
  - **1–3:** added to the move.
  - **Bus:** move by one die or by the total; you choose on your phone.
  - **Scout** (our name for Mr. Monopoly): after resolving the space, jump to the next unowned property, or else the
    next property where you owe rent.
  - **Triples:** move anywhere; you pick the space on your phone.
  - Only the white dice count for doubles and jail rolls. Utility rent uses all three dice, with Bus and Scout
    counting 0. There is no third-double jail when triples ends the turn.
- **Buying:** buy at the printed price, or pass and it goes to a **live auction** for every solvent token. There is
  no minimum bid, and the property stays with the bank if nobody bids.
- **Rent:**
  - Streets: base rent, doubled on an unimproved full colour set, then the 1–4 house and hotel rent tables.
  - Railroads: $25, $50, $100 or $200.
  - Utilities: 4× the dice, or 10× with both.
  - Mortgaged properties charge no rent.
- **Building:**
  - Even-build rule, and you can only build on your own turn.
  - **(party, official short-game rule)** a hotel needs **3 houses**, not 4.
  - The bank holds 32 houses and 12 hotels; the shortage rule applies.
  - Buildings sell back at half price.
- **Mortgage:** you get half the price; lifting a mortgage costs that plus 10%. A mortgaged property that changes
  hands charges its new owner the 10% at once.
- **Timeout (jail):** **(party, Hasbro's official Short Game rule)** you leave on your next turn.
  - Pay $50 or use a Get Out card, then roll normally.
  - Or try for doubles: doubles move you (no extra roll); a miss means you pay $50 and move by that roll.
  - No speed die in jail. You still collect rent and can build and trade while there.
- **Taxes:** Venmo Request (Income Tax) $200; Bar Tab (Luxury Tax) $100. The Couch (Free Parking) pays nothing,
  which is the official rule.
- **Debt:** if you owe more than your cash, you enter a debt screen. Mortgage, sell buildings or trade until you
  can pay, or declare bankruptcy:
  - Bankrupt to a player: all your assets go to them, and they pay the mortgage interest.
  - **(party)** Bankrupt to the bank: properties return unowned and buildings go back to the bank. The official
    rule auctions them, which is too slow for a party.
- **Trading:** anytime except during an auction, a move or the final tally. Properties with buildings in their
  colour group can't be traded until the buildings are sold (official rule).
  - One open trade at a time. **(party)** It freezes the turn clock, and the TV shows the deal in the middle.
- **End (party, official "time limit" rule):**
  - The game clock is 45 minutes by default (lobby: 30, 45, 60 or 90 minutes, or no limit).
  - When it runs out, **LAST LAP**: everyone finishes the current lap, then comes the final tally.
  - Net worth counts cash, unmortgaged properties at their printed price, mortgaged ones at half, and buildings at
    cost. The richest wins.
  - The game also ends early when one token is left.
- **Timers (party):**

  | Decision | Timer | If it runs out |
  | --- | --- | --- |
  | Roll | 20 s | auto-roll |
  | Buy | 15 s | goes to auction |
  | Auction | 10 s, then 6 s after each bid | highest bid wins |
  | Jail | 15 s | tries for doubles |
  | Debt | 60 s | auto-sell and mortgage, then bankrupt if still short |
  | End of turn | 20 s | ends the turn |
  | Trade reply | 30 s | rejected |
  | Bus / triples choice | 10 s | total / next property |

  An absent solo player plays on autopilot at 5 s. In teams, the seat skips to a teammate who is connected.

### Drink calls (on by default; `drinks` setting; a team drinks together)

| Event | Drink |
| --- | --- |
| Pay rent under $100 / $100–399 / $400+ or any hotel | 1 sip / 2 sips / SHOT |
| Sent to Timeout (jail) | 2 sips |
| Complete a colour set ("HOME TURF!") | everyone else drinks 1 |
| Land on The Couch | everyone else drinks 1 |
| Go bankrupt | finish your drink |
| Last place at the final tally | 2 sips |
| A few Plot Twist / Group Chat cards | flavour drinks (used only when drinks are on) |

### Teams

- **Lobby mode (`turfMode`):** Auto (solo up to 6 players, teams beyond), Solo or Teams. Teams use the existing
  `teams` count (Auto = ⌈players ÷ 3⌉, capped at 6).
- **Forming teams:**
  - Players are dealt evenly into teams, and remembered Brain Drain teams (`trivia.teams`) are reused when the
    count matches.
  - The captain can reshuffle during a 15 s Team Up beat.
  - Late joiners go to the smallest team; late joiners in solo mode watch.
- **The hot seat:** each team's seat rotates through its connected members after every one of that team's turns.
  The seat holder rolls, buys, bids, builds and trades. Teammates see the same screen read-only ("Alex has the
  dice. Yell at them.").

## The board (crew draft, editable)

**Names file:** `tv/engine/src/main/resources/turf/board.json` holds only names:
- each space's full name (≤ 24 chars) and TV label (≤ 12 chars, 2 lines);
- the two deck names;
- the card texts, which use `{space:id}` placeholders.

**Prices stay out of that file.** They're the official values, keyed by board position, in `TurfBoard.kt`, so a
rename can never break the economy. A test validates the file (40 spaces, lengths, placeholders). Rebuilding
applies edits (the launcher rebuilds every start), and the README gets a "Rename the board" section.

**Draft names:**
- **Groups, cheapest to priciest:**
  - Brown: Corner Store, Laundromat.
  - Light blue, pink, orange, red, yellow and green: 13 crew places ("Charlie's Couch", "Izzy's Rooftop", "Junha's
    Kitchen", "Daniel's Place" / "The Other Daniel's", …) mixed with party spots (Late-Night Diner, Karaoke Bar,
    Rooftop Bar, Taco Truck, …).
  - Dark blue: two headline spots.
- **Railroads:** The Night Bus, Rideshare, Designated Driver, The Long Walk Home.
- **Utilities:** The Wi-Fi, The Aux Cord.
- **Decks:** Plot Twist (Chance) and Group Chat (Community Chest), 16 cards each. Each mirrors an official card's
  effect with rewritten text.
- **Corners:** Payday (GO), Timeout / Just Visiting, The Couch (Free Parking), Go to Timeout.

## Research and reuse

No usable JVM Monopoly engine exists; the one Kotlin repo is MIT but has 0 stars. The engine is written in Kotlin
here, using these as references:

- **Rules:**
  - Hasbro rules with Speed Die: https://www.hasbro.com/common/instruct/00009.pdf
  - Short Game and Time Limit variants: https://www.hasbro.com/common/instruct/monins.pdf
  - 2008 taxes: Income $200 flat, Luxury $100.
  - Speed die details: https://en.wikibooks.org/wiki/Monopoly/The_Speed_Die
- **Board numbers:**
  https://github.com/Kau5hik46/monopoly-markov-chain/blob/main/data/board.us.json (MIT; all 22 streets' rent rows
  checked against the standard values).
  - Prices and rents are uncopyrightable facts, so they're typed into `TurfBoard.kt`.
  - A test pins every value.
  - Its `rules.us.json` house rules are ignored.
- **Card effects:**
  - The typed-effect schema follows https://github.com/kbennett2000/lan-games (MIT; a LAN party platform with timed
    auctions and trades, the closest match to our setup).
  - The effects match the 2008+ US decks: https://www.monopolyland.com/list-monopoly-chance-community-chest-cards/
  - All card text is rewritten; none of Hasbro's wording is copied.
- **Bot interface + heuristics:** https://github.com/intrepidcoder/monopoly (MIT) has `buyProperty` / `bid` /
  `acceptTrade` / `payDebt` / `postBail` hooks. The sim bots and `bots.mjs` use them:
  - **Buy:** always if it completes a set; otherwise buy if cash − price ≥ $150.
  - **Bid:** raise $10–$50 at random, stopping at 1.5× price, or 2× if it completes or blocks a set, or when cash
    would drop below $50.
  - **Build:** get to 3 houses fast (a hotel under our rule), prioritising orange, then red and light blue.
  - **Raise cash:** sell houses evenly, then mortgage utilities and singles before set members.
  - **Trade:** accept if it completes a set for us and not for them, or if the value gained minus given is > 0.
  - **Propose:** 1-for-1 swaps that complete a set, with cash making up the difference in printed prices.
  - **Jail:** leave fast early; late game, prefer rolling to stay put and collect.
- **UX lessons:**
  - Richup's turn clock: auto-roll and auto-end, but never auto-bid or auto-mortgage, except in our 60 s debt
    fallback.
  - Auctions with a countdown that resets on every raise.
  - One open trade at a time, and only the buttons that are valid right now.
  - The TV keeps a ticker so anyone who looked away can catch up.
  - The Switch version got panned for being too long and losing the table talk; our time limit and team seats are
    the answer to that.
- **IP:**
  - Mechanics and numbers aren't protectable, so we keep them.
  - We keep out Hasbro's protected marks: the name, "-opoly", Chance/Community Chest, corner names, Mr. Monopoly,
    the card wording and the board art.
  - Richup does the same: https://richup.io/info

## Architecture

### Engine: `tv/engine/src/main/kotlin/partyos/engine/games/turf/`

| File | Purpose |
| --- | --- |
| `TurfBoard.kt` | 40 `Space`s (kind, group, price, rent[6], houseCost, mortgage), official values; loads and validates `board.json` names |
| `TurfRules.kt` | Pure functions: rent, even-build and the house bank, mortgage costs, net worth, pass-Payday, nearest railroad/utility, trade validation, auto-liquidate |
| `TurfDecks.kt` | The two 16-card decks as effect data (advance to / nearest / back 3 / jail / jail card / pay / collect / each player / repairs / drink); shuffled into state |
| `TurfDice.kt` | Dice + speed die from the game's **own** RNG stream held in state |
| `HomeTurf.kt` | `GameModule<TurfState>`: the phase machine, actions, deadlines, views |
| `../../TurfViews.kt` | `TurfTv` payload (like `BlackjackViews.kt`) |

**RNG:** `PartyEngine` rebuilds `ctx.random` from `(seed, phaseSeq)` on every call, so two rolls in one phase would
match. `TurfState` instead holds `rngSeed` (from `ctx.random` at start) and `draws`. Each roll uses
`Random(PartyEngine.phaseSeed(rngSeed, draws++))`. This is the same SplitMix64 mix, deterministic and restorable.

**Phase machine (`TurfState.phase`):**
- `teamup` (teams only) → `pieces` (15 s; any teammate may grab the team's piece) → `deal` → `roll` → `move` → `buy` | `auction` | `card` | `choose` (bus/triples) → `manage`.
  `manage` then goes back to `roll` (doubles) or to `next` (the next token).
- **Side phases:**
  - `jail` replaces `roll` for a jailed token.
  - `debt` interrupts any payment; its queue handles cards that pay everyone.
  - `trade` freezes the phase it interrupted.
- `lastLap` is a flag; after it come `tally` and `podium`.
- **Phase vs deadline:**
  - A real step (roll → move → buy …) emits `Effect.Phase(ms)`. `move` lasts `hops × 260 ms + 1.4 s`, so the TV's
    zoom and hop animation finish before the phones prompt.
  - A timer reset inside a step emits the new `Effect.Deadline(ms)`. That covers auction bids, trade
    freeze/restore and autopilot shortening. It doesn't bump `phaseSeq`, so late bids after the hammer are
    correctly `STALE` and phones don't remount.
- **`waitingOn` returns `null` everywhere**, so timers and explicit actions drive progress. That way a sleeping
  phone never insta-skips a turn.
- **Trade freeze:** the first offer stores `frozenPhase` + `frozenMs`, taken from the new `ctx.remainingMs`, which
  survives host pause and snapshot restore.
  - Counter-offers don't re-capture it.
  - Once resolved, the turn resumes at max(`frozenMs`, 5 s). Main-flow actions reject with `TRADE_OPEN` while it's
    open.
  - Anti-stall: at most 2 counters per trade, and one offer per token per turn to the same partner.
  - SkipPhase during a trade ends just the trade.
- **Game clock:** stored as `clockLeftMs` (a duration), minus the time actually spent at each phase change, so host
  pauses and auto-pauses don't eat game time.

**State** (serialization-safe; the style matches `DrunkBlackjack`):
- **Plain types only:**
  - 40-slot `List<Int>` arrays: `owner` (token index, or −1), `houses` (0–3 houses, 4 = hotel), `mortgaged`.
  - Maps keyed by `PlayerId.v` strings.
  - A `kind: String` field instead of sealed types, and constant defaults only.
- **`tokens`:** id, name, colour, piece, `members`, `seat: PlayerId?`, cash, pos, jail, jailTries, jailCards,
  bankrupt, passedPayday, doubles.
- **Also:**
  - `bank` (houses/hotels left), `decks`, `turn`;
  - `auction` (with bid history), `trade`, `debts` (queue);
  - `clockLeftMs`, `rngSeed`/`draws`, `beat` + a 5-line ticker.
- `beat` is the last thing that happened, with a sequence number, so the TV can animate it. It covers roll, move,
  rent, buy, card, jail, build, trade, auction won, bankrupt and drink.

**Engine changes (small, opt-in, each unit-tested, including while paused):**
1. **`Effect.Deadline(durationMs)`:** resets the deadline without bumping `phaseSeq`. It's the same code path as
   `Phase` in `PartyEngine.apply()` (`PartyEngine.kt:366`), minus the `++`. While paused it sets
   `pausedRemaining`.
2. **`GameContext.remainingMs: Long?`:** filled from `g.remaining(now)` in `ctx()` (`PartyEngine.kt:321`).
3. **`GameModule.phaseFree(payload): Boolean = false`:** `PartyEngine.action` (`:186`) skips the `STALE` check
   when it returns true. Home Turf uses it only for `trade`, `tradeReply` and `tradeCancel`, which carry the trade
   id. Offers are drafted on the phone while other players' turns move on. Every other game is unchanged.
4. **`optionRange`:** add `turfMode` 0..2 and `minutes` 0..120 (`:171`). Widen the `setOption` key unions in
   `controller/src/protocol.ts` and `TvPage.tsx:72`.
5. **`Screen.Turf`** (`@SerialName("turf")`) in `Views.kt`. It's compact, with no avatars (ids only, since phones
   already have `people` from `scores`):
   - my token band: name, colour, cash, whether I hold the seat, and who does;
   - the prompt: kind, title, detail, actions, bid numbers, and whether it's timed;
   - my deeds, each with build/sell/mortgage flags and costs;
   - the open trade;
   - partners' tradable deed ids;
   - my latest drink call.

   `TurfTv` sends the static board once per payload (names, prices, rents) and per-space state as arrays. The
   simulation test asserts `PhoneState` ≤ 12 KB and `TvState` ≤ 24 KB with preset faces.

**Web runtime fixes these need:**
- **`pages/Play.tsx:74`:** the screen `<section>` is keyed on `${round}-${screen.t}`. For `turf`, key on
  `screen.t` only, and keep the trade-builder draft and tab outside the keyed subtree. Otherwise every
  roll/move/buy on anyone's turn wipes a half-built trade.
- **`tv/director.ts` `useDeadline`:** re-anchor on `deadlineAt`/`remainingMs` changes, not only on `phaseSeq`.
  This is needed for `Effect.Deadline`, and it also fixes an existing bug: after Resume the TV timer reads the
  pre-pause deadline.
- **Countdown gating:** the director's hurry bed and ticks (`director.ts:137-150`) and the phone's red timer
  (`Play.tsx:69`) fire only when `TurfTv`/the prompt marks a decision as `timed`. Otherwise every move phase and
  auction reset would tick.
- **`net/outbox.ts:15`:** add `payload.target` to the coalescing key. Several build/mortgage taps in one round
  otherwise collapse to the last after a reconnect. Older actions have no target, so nothing else changes. Bids
  carry the absolute amount + auction id, and the phone bids from max(server top bid, my last sent).

### Presence, kicks and edge cases

- **Autopilot:**
  - The seat holder is disconnected at phase start: the timer is 5 s.
  - They disconnect mid-phase: `Effect.Deadline(min(remaining, 5 s))`.
  - They reconnect: max(remaining, 10 s).
  - Debt keeps its full 60 s (never auto-bankrupt fast).
  - Detection can take up to the 10 s ping timeout.
  - In a 2-player game the engine auto-pauses below 2 connected, so autopilot never runs there. Document it.
- **Team seat:** stored as a `PlayerId`, and reassigned the moment the holder disconnects or is kicked. It rotates
  after each of the team's turns.
  - A player who joins mid-game goes to the smallest team; check membership first, because reconnects fire the same
    presence event.
- **Solo player (or a team's last member) kicked:** the token retires, bankrupt to the bank:
  - properties become unowned and unmortgaged;
  - buildings go back to the bank;
  - jail cards go back to their decks;
  - cash is retired in the ledger.
- **What the kick does to the game in progress:**

  | They were… | Then |
  | --- | --- |
  | Taking their turn | the turn ends and doubles clear |
  | Deciding a buy | it goes to auction |
  | Leading an auction | the previous bid wins, from the bid history |
  | In a trade | it's cancelled and the turn clock restored |
  | In debt | the debt is forgiven and logged |
- **Scores:** at the tally, every member of a token gets `Effect.Award(net worth)`, so Party OS standings and
  results work.

**Registration:** `tv/devserver/src/main/kotlin/partyos/devserver/Main.kt:43`, done **last** (step 8).
- The launcher rebuilds on every start, and phones silently drop an unknown `screen.t` (`protocol.ts:105`). A
  half-built Home Turf in the menu would freeze every phone.

**Shared team code:** move Brain Drain's `TEAM_KIT` + `RememberedTeam` decoding to `games/Teams.kt`.
- `BrainDrain.TEAM_KIT` and `BrainDrain.MEMORY_KEY` stay as aliases (`BrainDrainTest.kt:492` uses them), and
  `TTeam` stays put.
- Home Turf reuses remembered teams only when the count matches and team sizes differ by ≤ 1. It writes its teams
  back to `trivia.teams`, so trivia and Home Turf share crews.

### Phone: `controller/src/screens/TurfScreen.tsx` + `turf-phone.css`

`ScreenView.tsx` routes `t: 'turf'` to it. Add `turf` to `SCREENS` in `protocol.ts` and to the exact-set test in
`protocol.test.ts`.

- **Token band:** colour and piece, name, cash as a DSEG7 LED (`Casino.tsx` `Led`), jail and hot-seat badges.
- **Now tab** (one decision per screen):
  - ROLL: a giant dice button.
  - BUY $X / PASS.
  - Auction: the top bid and leader, then +$10, +$50, +$100 and "You're winning!".
  - Jail: PAY $50 / USE CARD / ROLL DOUBLES.
  - Debt: "Owe $X", with shortcuts into My Places, then PAY or GO BANKRUPT (hold to confirm).
  - END TURN.
- **My Places tab:** deeds grouped by colour, with + house / − house / mortgage toggles showing their costs.
- **Trade tab:**
  - Building an offer: pick a partner, then the You give / You get columns of deed chips, cash steppers and jail
    cards, then SEND.
  - Receiving an offer: a full-screen sheet with ACCEPT / REJECT / COUNTER. Counter reopens the builder prefilled.
- Teammates off the seat see everything read-only. Haptics fire on turn start, rent, auction outbid and drink calls.

### TV: `controller/src/tv/TurfStage.tsx`, `TurfBoard.tsx`, `turf.css`

`TvPage.tsx:161` gets a `turf` branch; `types.ts` gets `TurfTv`.

- **Board:** centred, about 1040 px square.
  - Each space shows its colour band, TV label, the owner's piece chip, house pips and a mortgage hatch.
  - Pieces are drawn toon SVGs in the token colour (red cup, pizza slice, sneaker, boombox, traffic cone, rubber
    duck), not emoji.
- **Centre well** (about 740 px):
  - dice (plus the speed die);
  - the big deed card with its rent table;
  - the auction (LED bid, leader face, `Timer` pie);
  - the trade (two columns, then an ACCEPTED/REJECTED `Stamp`);
  - deck card flips, the drink `Burst`, and the event ticker.
- **Rails:** 3 token cards on each side, showing the piece, name or team, and the seat holder's `AvatarFace`.
  - They also show `CountUp` cash, colour-set pips and jail/bankrupt stamps. The active token tilts forward.
- **Game clock:** top centre, becoming a LAST LAP burst.
- **Camera zoom:** while a token hops, the board scales about 1.8× around the token's segment (a `motion` spring),
  then eases back; each hop plays a `boing`. `prefers-reduced-motion` turns it off.
- **Tally and podium:** net-worth bars count up; reuse `Shared.tsx` `Podium`.
- **Also touched:**
  - Lobby `CoverArt` gets a drawn house and dice.
  - `.picker` gets a 5-game layout.
  - The TV keys ↑↓ set the minutes and T sets the mode or teams for Home Turf.
  - `director.ts`: the `Game` union, `musicFor`, and cues in `useCueDirector`.

### Lobby / captain / host

- **`Captain.tsx` `CaptainLobby`** gets a Home Turf block:
  - Mode: Auto, Solo or Teams.
  - Teams, shown only when the mode is Teams.
  - Game clock: 30, 45, 60 or 90 minutes, or no limit.
  - Drink calls: on or off.
  - Hide the Rounds stepper for Home Turf; it's shown for every non-blackjack game today (`Captain.tsx:36-39`).
- **`TvPage.tsx` lobby** mirrors those settings:
  - The arrow/T/D keys are gated on trivia and blackjack (`:224-227`) and need a Home Turf branch.
  - The settings text goes at `:259-267`, and `HostOverlay` is at `:403`.
- **`onHost("shuffle")`** reshuffles during Team Up, reusing the existing `gameAction` wiring and the TV's S key.
  - `Captain.tsx` `inTeamUp` (`:95`) must also detect Home Turf's Team Up for the captain's shuffle button.
- **Also touched:**
  - `Host.tsx` (the phase label).
  - `protocol.ts` `rejectMessage` gets friendly text for the new reject codes: `TRADE_OPEN`, `NOT_YOUR_TURN`,
    `CANT_AFFORD`, `BUILD_EVENLY`, `NO_HOUSES_LEFT`, `BID_TOO_LOW`, `MUST_SELL_BUILDINGS`.

### Audio

- **Reuse** `payout`, `jackpot`, `stamp`, `womp`, `drumroll`, `crash`, `applause`, `whoosh`, `boing`, `pop`,
  `chipClack`, `countTick` and `drinkCall`, plus the `hurry`, `standings` and `podium` beds.
- **New cues** in `controller/scripts/audio/cues.json`:
  - beds: `turf` (strolling big band) and `auction` (fast);
  - effects: `diceRoll`×3, `gavel`, `jailClang`, `hammer` (build), `register` (rent).
- The new cues are generated with the user's ElevenLabs key; that spends their credits, so ask first. Until then,
  the synth fallback covers them.

## Build order (TDD throughout)

0. Save this plan as `docs/superpowers/specs/2026-09-27-home-turf-design.md`, following the repo's convention, on
   branch `party-os-2`.
   - The working tree has uncommitted trivia work from last night. Leave it alone and ask before committing
     anything.
1. **Rules core:**
   - `TurfBoard` + `board.json` draft + validation test.
   - `TurfRules` + `TurfDecks` + `TurfDice`, each with exhaustive unit tests: every rent row, even-build, house
     shortage, mortgage interest, each card effect, speed-die faces, determinism.
2. **Engine module:**
   - Engine changes + tests: `Effect.Deadline`, `ctx.remainingMs`, `phaseFree`, the settings keys, and the
     `Teams.kt` extraction (all existing tests stay green).
   - `HomeTurf` phases + `TurfViews`.
   - `HomeTurfTest`, covering each flow: buy, auction, rent, jail ×3 exits, doubles/3 doubles, Payday, bus,
     triples, scout, debt → mortgage → pay, bankruptcy to player/bank, trade propose/accept/counter/reject/timeout,
     team seat rotation, disconnect autopilot, kicks mid-turn/mid-auction/mid-trade, trade freeze across host
     pause, last lap + tally, drinks on/off.
3. **Simulation test:**
   - Runs 500 seeded games of heuristic bots, checking these invariants:
     - money conserved against the bank ledger (retired cash included);
     - houses ≤ 32 and hotels ≤ 12;
     - no negative cash outside debt;
     - unique owners;
     - every game finishes within the clock + last lap;
     - snapshot → restore round-trips mid-game;
     - payload size limits hold.
   - It also reports the median game length and turns per lap, to tune the timers against the 45–60 min goal.
4. **Protocol:**
   - `Screen.Turf` + `TurfTv` fixtures in `ProtocolFixturesTest`, including client samples for Home Turf actions
     and the new `setOption` keys; regenerate with `-PupdateFixtures`.
   - `protocol.ts` `SCREENS` + exact-set test (which also adds the missing `cards`).
5. **Web runtime fixes:** `Play.tsx` key, `useDeadline` anchor, countdown gating, outbox target key. These come
   with tests where the code is testable (`outbox.test.ts`).
6. **Phone UI:** `TurfScreen`.
7. **TV UI:**
   - `TurfStage`/`TurfBoard`, and `/tv?gallery=turf` fixtures for every beat (a `Gallery.tsx` branch).
   - `shots.mjs` takes a gallery arg; today it hard-codes `trivia` at `:15`.
   - **Checkpoint:** send the user the screenshots before polishing (they asked to see visuals before building
     UI).
8. **Lobby, captain, director, audio wiring, cover art, and devserver registration.**
9. **Bots, e2e and docs:**
   - `scripts/bots.mjs` learns the `turf` screen (its act-once key at `:31` needs the same round-free keying), using
     the simulation's heuristics.
   - `e2e/turf.spec.ts`: 3 phones start, pick pieces, roll, buy, trade, accept, end turn.
   - README section ("Home Turf" + "Rename the board") and a show-bible beat sheet.
10. **Audio:** add the cues to `cues.json`, then generate them with ElevenLabs after the user confirms.

## Verification

- `cd tv && ./gradlew :engine:test :server:test` (the new unit, flow and simulation tests pass).
- `cd controller && npm test && npm run build && npm run e2e`.
- **Live run:** `preview_start` the devserver.
  1. Open `/tv` and run `node controller/scripts/bots.mjs 5`.
  2. Play a full game from one real browser phone, in solo and in team mode, with drinks on.
  3. Screenshot the board, zoom, auction, trade, debt, last lap and podium.
  4. Confirm the timing lands in the 45–60 min window. The simulation gives the estimate; one real run at a
     20-minute clock gives the smoke test.
- **Restore:** the devserver doesn't persist party snapshots (only the Android app does), so restore is covered by
  the engine snapshot round-trip test, not a server restart.
