# Home Turf overhaul: design spec (rule mods, the Shady Timeout add-on, and a UX/visual redo)

## Context

Home Turf (game id `turf`, see `2026-09-27-home-turf-design.md`) is a faithful, party-speed Monopoly-style game. The
user asked to "overhaul the entire monopoly game", research how to fit Monopoly into Party OS, and consider add-ons
like Super Jail and Free Parking extensions. Research showed that Super Jail is the official Hasbro **Go to Jail**
expansion and that Free Parking has an official **Free Parking Jackpot** expansion plus a very popular folk pot rule.

Decisions the user made (2026-09-28):

| Question | Answer |
| --- | --- |
| What is the overhaul for? | **Mods plus a full UX and visual redo** (not a rebuild; not a bug-fix pass) |
| Which mods? | **Pull all the rules from the Go to Jail expansion** and add it as an add-on to the base game |
| Engine approach | **A: mod seams.** Split only what the mods touch; base game stays byte-identical with mods off |
| Names | Use the renames below (no Hasbro marks), continuing the original spec's IP rule |
| Visual companion | Declined. Direction is agreed in text; static mockups are sent before UI polish |

Out of scope: the Android TV app (stays on the 1.0 look, unregistered), and **Free Parking Jackpot** (the mod bitmask
reserves a bit for it; it can be a follow-up).

## Research summary

- **Go to Jail expansion (Hasbro, 2025).** Rules read from the official two-page instruction sheet
  (https://assets-us-01.kc-usercontent.com/500e0a65-283d-00ef-33b2-7f1f20488fe2/0cbcae4f-ed08-48dd-ba76-c950a02e7422/G0719IN13_INST_MONOPOLY_GO_TO_JAIL_FAR.pdf),
  plus https://instructions.hasbro.com/en-us/instruction/monopoly-go-to-jail and
  https://www.geekyhobbies.com/monopoly-go-to-jail-expansion-rules/. The sheet does not list individual cards, so
  all 44 cards here are our own.
- **Free Parking Jackpot expansion** (https://www.geekyhobbies.com/monopoly-free-parking-jackpot-rules/): the pot
  is funded by bank purchases, house purchases and jail bail, and is won by landing exactly on Free Parking, with a
  spinner, Spin chips and a "Deal Mobile" token. Deferred.
- **Folk rules** (https://en.wikibooks.org/wiki/Monopoly/House_Rules): Free Parking cash pools are used by about 75%
  of tables and prolong games because cash never leaves circulation. Mandatory auctions, 3-house hotels, time limits,
  dealt starting properties and flat taxes are the accepted speed-ups; Home Turf already has all of them.
- **Tension.** Home Turf's Short Game jail ("leave next turn") exists to speed things up; Super Jail exists to add
  drama. The game therefore needs switchable rule sets, not hard-coded rules.

## Naming (Hasbro marks stay out)

| Official | Ours |
| --- | --- |
| Jail | Timeout (already) |
| Super Jail | **Super Timeout** |
| Corruption cards | **Shady cards** |
| Super Corruption cards | **Super Shady cards** |
| Chance / Community Chest spaces | Plot Twist / Group Chat spaces (already) |
| Escape die / Heist die | **Escape die / Heist die** (generic words) |
| Corruption space (Just Visiting corner) | The Timeout corner's "Just Visiting" half |

A test (`TurfNamesTest`) fails if any of "Corruption", "Super Jail", "Monopoly", "Chance", "Community Chest" appears in
`board.json`, the card text or any view payload.

## The mod seam (engine architecture, approach A)

`TurfState` gains `mods: List<String>` (the active mod ids; empty = Classic Party) and `shady: ShadyState? = null`.
A small resolver, `TurfMods`, exposes hooks; each returns `null` (or the default) to mean "use the base rule".

| Hook | Base behaviour (today) | Shady Timeout |
| --- | --- | --- |
| `onChanceSpace` | draw a Plot Twist / Group Chat card | roll the Escape die (Plot Twist) or Heist die (Group Chat) |
| `onSendToJail` | jail on the corner | choose regular or Super Timeout and set the jail record |
| `onJailTurn` | leave next turn; pay $50, card or doubles | the jail flow below |
| `onPassCorner` | nothing | draw a Shady card on landing on or passing Timeout |
| `onTally` | net worth | Final Payout, jailed tokens can't win |
| `extraActions` | none | `shady`, `shadyTarget`, `shadyReact` |
| `lastLapTriggers` | the clock, or one token left | plus all deeds owned, or the first bankruptcy |

**File split, only where the mods touch.** `HomeTurf.kt` (1,046 lines) stays the phase dispatcher. These move out:
jail flow (`TurfJail.kt`), card/dice resolution (`TurfChance.kt`), the tally (`TurfTally.kt`), trade (`TurfTrade.kt`;
trades must carry Shady cards). Auction, debt, teams and everything else stay. New files: `TurfMods.kt`,
`mods/ShadyTimeout.kt`, `mods/ShadyCards.kt`.

**Golden master (first thing built).** Before any refactor, record the final results of 20 seeded bot games
(`TurfSimulationTest` harness). After the split, with `turfMods = 0`, the same seeds must produce identical results.
Only then do the mod hooks get implemented. Classic Party cannot silently change.

**Freeze generalised.** The trade freeze (`TTrade.frozenPhase` / `frozenMs`) moves to a shared `Freeze` on the state so
the reaction window can interrupt a phase the same way a trade does and resume it afterwards.

**State (plain types only, serialization-safe):**

- `ShadyState`: `deck`, `superDeck` (draw order of catalogue ids from the game's own RNG stream), `discard`,
  `superDiscard` (reshuffled into the deck when it runs out), `hands: List<List<THeld>>` per token
  (`THeld(card: Int, gotTurn: Int)`), `jails: List<TJail>` per token
  (`TJail(kind: "none|jail|super", turns: Int, by: Int)`), `turnNo` (increments each token turn, for "not the turn you
  got it"), active effects (`gouge`: token flags, `squat`: space → until-turn, `blackout`: until-turn + owner,
  `encore`: token flag), and `reaction: TReaction?`.
- `TToken.jailed` stays the source of truth for "is in a Timeout"; `jails[t]` adds the kind and turns.

**Lobby option:** `turfMods` (bitmask, range 0..3 for now): bit 0 = Shady Timeout, bit 1 reserved for Free Parking
Jackpot. Presets: **Classic Party** (0, today's game) and **Shady Timeout** (1). Wired in `PartyEngine.optionRange`,
`protocol.ts` and the TV lobby.

## Shady Timeout: the rules as they run

Official rules from the Hasbro sheet, adapted for Party OS where marked **(party)**.

### Setup and spaces

- The Plot Twist and Group Chat card decks are **not used** (they stay in `board.json`, unused, when the mod is on).
- **Escape die** on the three Plot Twist spaces (7, 22, 36). **Heist die** on the three Group Chat spaces (2, 17, 33).
- **Go to Timeout spaces:** besides the corner (30), Venmo Request (4) and Bar Tab (38) become Go to Timeout spaces.
  The attachments cover the tax on those two spaces, so **no tax is charged while the mod is on** (an interpretation
  of the physical attachment; the simulation checks the economy).
- **Timeout corner** ("Just Visiting"): landing on it or passing it draws one Shady card.
- Each token starts with **2 Shady cards**. Hands are secret from other tokens (a team's hand is visible to its own
  members).
- Money and the rest of the base rules are unchanged (speed die, doubles, rent, building, auctions, debt).

### Dice

- **Escape die** (starting faces, tuned by simulation): 1, 1, 2, 2, 3, cop. A number means "you got away": collect
  that many Shady cards (they can't be used until your next turn). A cop sends you to regular Timeout.
- **Heist die** (starting faces): $50, $100, $100, $150, $200, cop. Money is collected from the bank. A cop sends you
  to regular Timeout.
- Both are rolled from the game's own RNG stream (`draws++`), shown as a beat on the TV, no phone decision needed.

### Timeout (regular)

- You can still collect rent, bid, build, mortgage, trade and play Shady cards while in.
- At the start of each of your turns in Timeout you choose:
  - **PAY $100** to the bank, then roll and move as normal (with the speed die); or
  - **STAY**: draw one Shady card, and your turn ends.
- Turn 3 is forced: draw the card, pay $100, place the token on Just Visiting, and the turn ends without a roll.
- Doubles do **not** get you out (rolling three doubles in a row still sends you to regular Timeout). Timer: 15 s; on
  timeout the token stays (draws a card) on turns 1 and 2, and pays on turn 3.
- A payment you can't cover (a forced turn-3 fee or a chosen PAY) goes through the normal debt screen: mortgage, sell
  or trade, or go bankrupt to the bank.
- This replaces Home Turf's Short Game jail rule while the mod is on (the base game is unchanged with mods off).

### Super Timeout

- **Only another token can send you there**, by playing a Shady card (Lockdown).
- Each turn you stay, draw one **Super Shady** card. Up to 3 turns.
- At the start of your turn you may leave by paying **$300 to the token that sent you**, or by handing that token the
  Super Shady cards you drew, then roll and move as normal. Turn 3 is forced the same way: draw, then pay or hand
  over, then stand on the Go to Timeout corner and ignore its effect.
- Doubles do not get you out. If the sender went bankrupt or was kicked, the $300 goes to the bank and the cards are
  discarded.
- Sending a token that is already in a Timeout to Super Timeout is allowed; sending one already in Super Timeout is
  rejected.
- **(party)** Timer 15 s; on timeout the token stays on turns 1 and 2; on turn 3 it pays $300 if affordable, else
  hands over cards, else goes into debt to the sender.

### Shady hands

- Cap of **6 Shady and 6 Super Shady** cards; an overflow card is discarded automatically (the TV calls it out).
- A card can't be played the turn you got it. Otherwise, cards are played on **your own turn**, before you roll (phase
  `roll`), while managing (phase `manage`) or while in Timeout (phase `jail`), unless marked **reaction (R)**.
- **Reaction window (party).** When a card targets a token that holds a matching reaction card, or a token owes rent
  and holds Ghost, an **8 s window** opens on that token's phone only. It interrupts the current phase via the shared
  `Freeze` and resumes afterwards. The window only opens if the target holds a reaction, so nothing waits for nothing;
  the TV shows a plain "Something's brewing..." beat for the pause. (Accepted trade-off: a long pause tells the table
  the target holds a reaction.)
- Cards are **tradeable by specific card** in the trade builder (the receiver sees the card faces while the offer is
  open), and stealable by cards. Team seat holders play the team's hand.
- Targets are chosen on the phone (a friend's face, a deed, or a space, per the card). A target timeout (15 s)
  cancels the play and the card stays in hand.

### The 32 Shady cards

All text ≤110 chars, our own wording. `R` = reaction.

| Card | × | Effect |
| --- | --- | --- |
| Sticky Fingers | 3 | Take a random Shady card from a friend's hand |
| Pickpocket | 2 | Take $75 from a friend |
| Swap Shop | 2 | Swap one of your unbuilt deeds for one of a friend's (neither in a colour group with buildings; neither mortgaged) |
| Ghost (R) | 2 | Cancel one rent you owe |
| Counter (R) | 3 | Cancel a Shady card played on you (does not stop Super Shady) |
| Price Gouge | 2 | The next rent you collect is doubled (one active at a time) |
| Squatter | 2 | A friend's deed charges no rent until your next turn |
| Free Build | 2 | Build one house or hotel free on a set you own (even-build rule, bank permitting) |
| Shortcut | 2 | Move forward to any space and resolve it (Payday if you pass it) |
| Encore | 2 | After this turn, take another turn |
| Snitch | 2 | Send a friend to regular Timeout |
| Lockdown | 3 | Send a friend to Super Timeout |
| Cash Out | 2 | Collect $150 from the bank |
| Whip Round | 2 | Every other token pays you $30 |
| Last Call | 1 | A friend finishes their drink (or takes 3 sips; water counts). Drinks off: they pay you $50 |

### The 12 Super Shady cards (only drawn in Super Timeout)

| Card | × | Effect |
| --- | --- | --- |
| Hostile Takeover | 2 | Take any unbuilt, unmortgaged deed from a friend |
| Wire Transfer | 2 | Take $300 from a friend (or all they have) |
| Wrecking Ball | 2 | Remove one building from a friend's deed (it returns to the bank) |
| Blackout | 1 | Until your next turn, nobody but you collects rent |
| Protection Money | 1 | Every other token pays you $75 |
| Double Cross (R) | 1 | When any card is played on you, it lands on whoever played it (also stops Super Shady) |
| Jailbreak | 1 | Every token in a Timeout or Super Timeout walks out free |
| Deed of Trust | 1 | Move to any unowned deed and take it free |
| Jackpot | 1 | Collect $500 from the bank |

### Drink calls (only when drinks are on; water counts)

| Event | Drink |
| --- | --- |
| Caught by a cop die | 2 sips (same as any Timeout) |
| Sent to Super Timeout | 3 sips |
| A Shady card played on you | 1 sip |
| Last Call | the named friend finishes their drink |
| Last place at the Final Payout | 2 sips (unchanged) |

### The end

- The game clock still rules. **(party)** The mod adds two triggers that start **Last Lap** early: **every deed is
  owned**, or **the first bankruptcy**. Nobody sits out a long endgame after being eliminated. The base "one token
  left" end is unchanged.
- **Final Payout** replaces the net-worth tally. Each token **not in a Timeout** collects from the bank the rent each
  of its unmortgaged deeds would charge as it stands (set doubling and buildings included; railroads by count;
  utilities pay as if the dice showed 7). Mortgaged deeds pay nothing. Ranking is by cash after the payout.
- **A token in a Timeout or Super Timeout at the tally can't win.** It collects no payout and ranks below every free
  token, ordered by its cash. Bankrupt tokens rank last. If every token is jailed, they rank by cash.
- Scores: each member of a token gets `Effect.Award(cash after the payout)`; the last-place sips are unchanged.

### Timers (party)

| Decision | Timer | If it runs out |
| --- | --- | --- |
| Timeout choice | 15 s | stay (turns 1-2), pay (turn 3) |
| Card target pick | 15 s | play is cancelled, card stays |
| Reaction window | 8 s | the card resolves |
| Dice beat | about 2.5 s | (TV animation, no decision) |

## Protocol changes

- `Screen.Turf` gains: `hand` (per card: id, name, text, kind own|react, super, state playable|fresh|locked, needs
  none|token|deed|deedPair|space), `jail` (kind, turns, the sender, the choices and their costs), `reaction`
  (who is acting, on what, ms left), and the target lists a card needs.
- `TurfTv` gains: per token `hand` (a count only) and `jail` (kind, turns, sender); beats `escape`, `heist`, `shady`
  (card, from, target, result), `payout` (deed by deed); `mods`; and the space names for the Escape and Heist spaces.
- `board.json` gains a `mods.shadyTimeout` block: names for the two dice spaces, the 44 cards' names and texts
  (≤110 chars, `{space:id}` placeholders), validated by test. Prices stay out of it, as before.
- New `setOption` key `turfMods`; fixtures regenerated with `-PupdateFixtures`; `protocol.ts` `SCREENS` and the
  exact-set test updated. Reject codes get friendly text: `NOT_PLAYABLE_NOW`, `NEEDS_TARGET`, `BAD_TARGET`,
  `HAND_FULL`, `NOT_IN_TIMEOUT`, `ALREADY_SUPER`.
- Payload budgets hold: `PhoneState` ≤ 12 KB and `TvState` ≤ 24 KB (asserted by the simulation with hands full).

## UX and visual redo (milestone 2)

**Baseline.** The screenshots in `docs/media/turf-*.png` predate the "miniature neighborhood" theme (navy, ivory,
brick, brass; `games.css`, commit `d25a29f`). The redo **keeps that theme** and changes layout and moments. The first
build step renders `/tv?gallery=turf` and screenshots the current themed state as the baseline.

**TV board: split the jobs.** The board is height-limited to about 1000 px, so 40 spaces can't carry couch-readable
names. The board answers "where is everyone and who owns what": a bigger colour band, a whole-tile owner tint (with a
shape mark, so it is never colour alone), larger house and hotel pips and a stronger mortgage hatch; names become
labels. The **centre** answers "what's happening" with large type: when a token lands, the space's full name and
deed come up as a spotlight.

**The Ledger.** When nothing is happening the centre well shows live net-worth bars for every token and the last
three events in large type. Beats (dice, spotlight deed, auction, trade, Shady reveals) sit on top of it.

**Rails.** Token cards gain a Shady hand count, a Timeout or Super Timeout badge with turn pips, and the seat holder.
Classic Party looks as it does today.

**New TV moments:**

1. **Dice theatre:** the Escape and Heist dice roll big in the centre; a cop gets a siren-red flash and a jail clang,
   loot gets the till bell.
2. **Shady play reveal:** a card slides from the player's rail to the target's, flips, shows a one-line result and the
   sip call.
3. **Super Timeout cell:** the corner opens into a cell view: the token behind bars, turn pips 1/3, a "sent by" chip
   and the Super cards stacking up.
4. **Final Payout ceremony:** deeds fly one by one from the board to their owner's rail with a till bell and a
   count-up; jailed tokens get a padlock and "can't win"; then the podium.

**Card look.** Shady cards are manila-envelope, stamped-paper contraband inside the neighborhood theme; Super Shady are
red-stamped with a seal edge. All art is drawn SVG glyphs in the style of `TurfArt.tsx`; no emoji as ornament.
Everything stays colour + shape + text (drunk-readable).

**Phone.** A fourth **Hand** tab appears only when the mod is on: fanned cards with a one-line effect and a state chip
(Playable now / Next turn / Reaction). Playing is tap, pick a friend by face, and hold-to-confirm for destructive
cards. The reaction window is a full-screen sheet ("Izzy is stealing your Balcony: COUNTER or let it go"). Timeout is
a plain Now-tab decision (PAY $100 / STAY; Super: PAY $300 to Izzy / GIVE 2 CARDS). Off-turn phones always have the
Hand.

**Checkpoint.** Before any M2 polish, send the user 2-3 static mockup options for: the board tile plus Ledger, the
Shady card and reveal, and the Super Timeout cell (per their standing rule to see directions before UI is built).

**New UI files (controller):** `tv/Ledger.tsx`, `tv/DiceTheatre.tsx`, `tv/JailCell.tsx`, `tv/Payout.tsx`,
`tv/ShadyReveal.tsx`, `tv/ShadyCardArt.tsx`, `screens/HandTab.tsx`; changes in `TurfBoard.tsx`, `TurfStage.tsx`,
`TurfScreen.tsx`, `turf.css`, `turf-phone.css`, `Captain.tsx` and the TV lobby (a "Rule mods" chip row; TV key M
cycles the preset).

## Milestones and build order (TDD throughout)

- **M1: engine, mod and functional UI.** Steps 0-6 below. Ends with a playable Shady Timeout on the current theme.
- **M2: the visual and UX redo.** Steps 7-8.

0. **Baseline:** themed gallery screenshots; the golden-master recording of 20 seeded games.
1. **Seam:** the file split and `TurfMods`; with `turfMods = 0` the golden master is identical.
2. **Shady core:** catalogue, decks, effects and target validation as pure functions, with tests per card.
3. **Mod hooks:** dice, jail and Super Timeout, hand actions, reactions, hand cap, card trade and steal, Final Payout,
   Last Lap triggers; flow tests for each.
4. **Simulation:** 500 seeded games with the mod on; tune die faces and check the economy without the two taxes.
5. **Protocol and web runtime:** `Screen.Turf` / `TurfTv` additions, option key, fixtures, `protocol.ts`; the phone
   keeps the Hand tab state outside the keyed screen subtree (as the trade draft does).
6. **M1 UI:** functional Hand tab, jail decisions, dice beat, payout on the current theme; Captain and TV lobby preset
   chips; bots learn to play cards; `e2e/turf.spec.ts` gets a Shady Timeout case; README section.
7. **Checkpoint + M2:** send the mockups, then build the board, Ledger, the four TV moments and the phone polish.
8. **Docs and audio:** README and show-bible; new cues in `cues.json` (`siren`, `jailClang`, `till`, `envelope`,
   `stamp` variants). Generating them spends the user's ElevenLabs credits, so ask first; the synth fallback covers
   them until then.

## Testing and verification

- **Golden master:** `turfMods = 0` reproduces the pre-refactor results for 20 seeds.
- **Rule tests:**
  - every card effect and each rejection;
  - the jail matrix: regular and super, both exits, the forced third turn, no doubles, the sender bankrupt or kicked,
    an already-jailed token targeted;
  - hand cap 6 and "not playable the turn you got it";
  - the reaction window: Counter, Ghost, Double Cross, timeout, and a target with no reaction (no window);
  - card trades and steals;
  - the Final Payout: set and building rent, utilities at 7, mortgaged pays 0, jailed tokens can't win, all-jailed;
  - both early Last Lap triggers; drinks on and off; teams (seat play, teammates read-only);
  - a kick mid-Super Timeout; a snapshot round-trip mid-jail and mid-reaction.
- **Simulation (500 games, mod on):**
  - invariants: money conserved including the payout; all 44 cards conserved across hands, decks and discards;
    nobody jailed past 3 turns; every game finishes within clock + Last Lap; payload size limits hold with full hands;
  - reports: chance-space jail rate (target roughly 1 in 6), median game length, and how often jail disqualification
    decides the winner.
- **`TurfNamesTest`:** the banned-string guard above.
- **e2e:** 3 phones, mod on: hit a chance space, see the die, play a card on a friend, react, leave Timeout, see the
  payout.
- **Live run:** `preview_start` the devserver, run `node controller/scripts/bots.mjs 5` with the mod on, then play a
  full game from one real phone in solo and teams with drinks on; screenshot dice, reveal, Super Timeout, payout.
- **Commands:** `cd tv && ./gradlew :engine:test :server:test` (with `JAVA_HOME` set to Java 17);
  `cd controller && npm test && npm run build && npm run e2e`.

## Risks and open interpretations

- **Tax spaces covered by the attachments:** assumed the Go to Jail attachments replace the tax. The simulation checks
  that the missing bank sinks don't inflate the economy; if they do, restore the tax on spaces 4 and 38 and drop
  them as Go to Timeout spaces (the corner stays the only one).
- **Card list:** the official card texts aren't in the sheet; all 44 cards are original. If the user owns the box, the
  effect mix can be aligned to the physical cards later without any engine change (cards are data).
- **Reaction-window tell:** a long pause reveals that the target holds a reaction (accepted).
- **Size:** two large pieces of work; milestones keep M1 shippable if M2 slips.
- **Refactor risk:** contained by the golden master and by splitting only the files the mods touch.
