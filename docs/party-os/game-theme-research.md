# Individual game identities — design research

Research date: 27 September 2026. Proposal only; no game UI or rules changed.

## Recommendation

Let Party OS be the recognizable entrance to a collection of games. Give each game its own setting, typography, materials, pieces, transitions and sound. Preserve the player faces, identity colors, connection status, captain controls and basic phone interaction conventions.

Start with Home Turf and Sprawl. Their different boards already offer an excellent foundation, but their surrounding presentation currently makes them look like variants of the same cartoon show.

## What exists today

Reviewed the checked-in Home Turf, Sprawl, Blackjack and Bluff screenshots in `docs/media`, the show bible, theme tokens, TV dispatch and game art components. These are repository screenshots, not a fresh live playthrough.

- `controller/src/theme/tokens.css` supplies a common Rammetto One headline face, Figtree body face, ink outlines, warm paper surfaces, hard shadows and bright palette.
- `controller/src/tv/TvPage.tsx` puts Home Turf on a lime Scene and both Sprawl and Blackjack on tangerine Scenes.
- The games share the alarm-clock motif, cream panels, halftone decoration and cartoon framing. Changing background color alone will leave that resemblance intact.
- Blackjack already has a stronger identity through felt, wood, playing cards and LED totals. It needs refinement more than reinvention.
- Home Turf already has original party-object pieces; Sprawl already separates terrain and resource colors. Build on those investments.
- The show bible explicitly makes the entire product a Saturday-morning cartoon. Adopting this proposal means revising that rule so it governs the shell and Brain Drain rather than every game.

## Reference findings

Source descriptions below are distinguished from the proposed adaptations. This is a visual/product reference review, not a claim that the competing apps were playtested.

| Reference | Evidence and lesson | Application here |
| --- | --- | --- |
| [Marmalade MONOPOLY](https://www.marmaladegamestudio.com/games/monopoly) | The official digital board game offers themed boards with coordinated tokens and properties. Its Atlantis section makes the scope of a theme particularly clear: the world changes across several asset families. | Home Turf needs a coordinated neighborhood, token and property-card treatment. Use this as the closer reference for property-game presentation. |
| [MONOPOLY GO art case study](https://www.damasobenitez.es/work/monopolygo) | An artist describes using simple volumes, a consistent glass pedestal and a staged opening moment to make tokens recognizable and desirable. | Make Home Turf's cup, duck, sneaker and other pieces feel like little objects. Give purchases and upgrades a brief, readable reveal. |
| [MONOPOLY GO board explanation](https://monopolygo.helpshift.com/hc/en/3-monopoly-go/faq/66-board/) | Board movement, tile rewards and landmark upgrades form its particular play loop. | Treat it as a reference for objects and presentation. Home Turf's turn, ownership and auction information still needs to suit its own rules. |
| [Colonist press kit](https://colonist.io/press-kit) | Supplies standard and expansion gameplay screenshots and board-building references for its browser strategy game. Useful for studying how hexes, numbers, roads and ownership share limited space. | Make Sprawl's decisions easy to read: quiet terrain, clear numbers, distinct player pieces and unmistakable legal placements. |
| [CATAN Universe](https://www.catan.com/catan-universe), [official media gallery](https://catanuniverse.com/en/media/) | CATAN explicitly positions its graphics around the feeling of a real gaming table; the gallery supplies the physical-board reference. | Give Sprawl tactile terrain and pieces while retaining a stable overhead board suitable for the TV. |
| [Jackbox's Party Pack 4 retrospective](https://www.jackboxgames.com/blog/making-the-jackbox-party-pack-4) | Describes Survive the Internet's shift toward a 1990s desktop/AOL theme, alongside separate character and music development for other games. [Fibbage 3's pack page](https://www.jackboxgames.com/games/packs/the-jackbox-party-pack-4) describes its 1970s lounge direction. | A common party platform can support very different fictional settings. Give Bluff Battle its own editorial/comedy world. |

Marmalade MONOPOLY and MONOPOLY GO are separate references with different purposes. Combining the former's board-game legibility with selected presentation ideas from the latter is the useful direction for Home Turf.

## Proposed game directions

These are design recommendations, not descriptions of the reference products.

### Home Turf: a miniature neighborhood

Warm ivory, deep navy, brick red and restrained brass, with ownership colors reserved for players and property groups. Use compact architectural lettering for signs and a clear body face for prices.

Keep the whole board visible. Add small illustrated/isometric landmarks for places such as the taco truck, rooftop and corner store. Use the board center for an enlarged view of the current property, owner, rent and pending decision. On the phone, present that property as a deed with clear Buy, Auction or Upgrade actions.

Retain the original party-object tokens but give them a consistent material, lighting and silhouette. Motion should express a particular event: token hop, ownership sign planting, construction rise, auction hammer. Sound suggestions: dice on a tabletop, paper deeds, a small till bell.

Avoid permanent camera rotation or dense buildings that hide spaces. Perspective can live inside illustrations without turning the entire board into a 3D scene.

### Sprawl: a crafted island strategy board

Deep ocean blue, sand, pine, clay and stone. Softer terrain boundaries, light terrain illustration and beveled wooden pieces should separate it from Home Turf's urban setting. Keep resource symbols consistent across terrain, cards, prices and trade proposals.

Use Colonist as the clarity reference and CATAN Universe as the tabletop-material reference. Numbers, probability pips, ownership and legal placement markers must remain stronger than decorative terrain. Keep the camera steady.

The TV shows the public island and highlights the active event; the phone holds the private hand, construction costs and a readable offer/counteroffer tray. The existing friends' place names stay, but should not compete equally with every production number. Show the selected location's full name prominently in the action area.

Suggested event treatments: a road placed with a wooden click, resources traveling from producing tiles, a short shadow when the Landlord arrives. Ocean ambience should remain subtle beneath conversation.

### Drunk Blackjack: an after-hours casino

Develop the existing felt and mahogany into a darker emerald, oxblood, ivory and brass setting. Give the game its own sign lettering, chip treatment and table markings. Keep classic cards and readable totals.

Replace the orange cartoon surround and generic alarm clock with casino-specific furniture and a restrained countdown. Phones become personal betting mats. Card slides, chip clacks and lounge music reinforce the setting. The player's drawn dealer face can remain a familiar Party OS element.

### Bluff Battle: a dubious tabloid newsroom

Newsprint, charcoal, warning red and a little acidic yellow. Condensed headline lettering, clipped photos/illustrations, correction marks and editorial stamps create a world around inventing plausible lies.

TV prompts become headlines; reveal cards disclose the author and who believed them. Phones are reporters' submission slips. Keep all candidate answers visually equal until reveal so styling never hints at the truth. Reserve typewriter flourishes for transitions rather than slowing answer entry.

Fibbage is the genre reference; the tabloid identity is an original proposed direction.

### Brain Drain and Write It Down

Brain Drain can own the current loud cartoon game-show identity: Brainy, bursts, answer shapes and bright round colors. Improving distinction elsewhere makes this treatment feel deliberate again.

Write It Down can become the pub-quiz sibling: dark green, warm paper, score sheets, pencils and restrained chalk accents. Body text remains clean and readable rather than handwritten. If keeping the trivia variants visually related matters more than differentiation, share Brain Drain's layout while changing the props and sound.

## Scope of a theme

| Keep consistent | Let each game own |
| --- | --- |
| Player faces and identity colors | Background/environment and materials |
| Join/reconnect/captain behavior | Headline font and game logo |
| Action placement and confirmation conventions | Board, pieces, cards and illustrations |
| Color + symbol + label for meaning | Timer appearance and transition vocabulary |
| Large touch targets and reduced-motion support | Sound palette and musical setting |

Avoid changing a player's identity color when changing games. Decorative palettes should accommodate those colors. Private information must stay on the phone even when a dramatic TV reveal would look appealing.

## Feasible implementation approach

The existing separate game stages, stylesheets and shared TV/phone SVG art make this feasible without changing game rules.

1. Add a game theme scope to the TV and phone roots. Separate semantic theme tokens—background, surface, text, display font, border, shadow—from player colors and answer meanings.
2. Let each stage select its environment and timer treatment. Shared countdown behavior can render as an auction clock, island turn marker or casino display.
3. Start with existing CSS/SVG for layouts, markers, icons and pieces. Commission or generate decorative backgrounds and landmark art only after the visual direction is chosen. Full 3D is unnecessary for the first pass.
4. Update the show bible to describe the shared shell plus individual game directions, including motion and sound.
5. Validate on a couch-distance TV and actual phone sizes: long names, six-player boards, large Sprawl map, trades, auctions, reconnect/pause and reduced motion.

The largest risk is making attractive tabletop art less readable from across the room. Keep the public board steady and use enlarged contextual details rather than shrinking labels to fit additional decoration.

## Suggested first design study

Produce four matching mockups: Home Turf TV + phone, Sprawl TV + phone. Use real mid-game states rather than title screens. Each should include player standings, a timer, a selected board target and an actionable phone decision.

This tests the central proposal cheaply: can someone tell which game is running before reading its title, and can they still identify whose turn it is, what happened, and what to do next?
