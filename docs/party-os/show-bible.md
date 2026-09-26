# Party OS show bible

The look, motion and sound of Party OS. Every screen and every sound should trace back to this page.
Research and reasoning are in the approved plan (late-night game show overhaul, 2026-09-25).

## The idea

Party OS is a **late-night game show broadcast from your living room**. The TV is the stage and the
host; phones are the contestants' buzzers. Two rules come straight from Jackbox and the Jack Principles:

1. **Talk to the room.** The show reacts to what just happened (three people fooled, everyone in early,
   a photo finish) instead of running on a fixed script. Pacing never stalls: one task at a time, and
   everyone always knows what happens next.
2. **Every beat lands twice.** Each visual beat has a paired sound, and both are sized to the moment:
   small for navigation, medium for a submission, big for a reveal.

## The Studio (shared shell)

| Token | Value | Use |
| --- | --- | --- |
| Ink | `#0B0716` | Stage floor, text on props |
| Velvet / VelvetHi | `#3B0A2A` / `#7A1446` | Curtain wall, marquee plates |
| Cream | `#FFF4D6` | Props: cards, name tags, spotlight light |
| Gold | `#FFD23F` | Bulbs, marquee lettering, leader |
| Pink | `#FF4D8D` | Neon accents, APPLAUSE, hot states |
| Red / Mint | `#FF5A5A` / `#3DDC97` | ON AIR, wrong / right, points gained |

- **Type:** Bungee Shade for the logo, room code and the biggest slams; Bungee for headings and numbers;
  Rubik (500–900) for everything people read. Nothing under 15sp, most text 18sp+ at 960×540dp.
- **Props, not panels:** cream cards with a 3–4dp ink outline and a hard offset shadow (no blur), set a
  degree or two askew. The stage is dark so props pop.
- **Set pieces** (`ui/components/Studio.kt`): velvet curtain wall, slow sunburst, two swaying spotlights,
  marquee bulb frame with a chase, ON AIR light, APPLAUSE sign, rubber stamps, confetti cannons.
- **Games nest inside the studio.** Bluff Battle brings felt green and brass: the question is printed on
  felt, answers are cream playing cards with a letter pip, submissions are poker chips.

## Motion vocabulary (`ui/components/Motion.kt`)

| Name | What it does | Used for |
| --- | --- | --- |
| Slam | 1.8× and tilted, lands with an overshoot | Headlines, reveal cards, podium names, stamps |
| Pop | 0.4× bounce to full size | Chips, avatars, name tags, points |
| Deal | flies up from below, tilt settles | Cards, rows, podium blocks |
| Stagger | Deal in sequence, 70ms apart | Lists |
| Wobble | idle ±1.5° sway | Anything waiting on players |
| CountUp | number ticks to its target in ≤24 steps | Score totals |

All of it animates in `graphicsLayer` or the draw phase only (spec §9). Nothing recomposes per frame.

## The host

A smooth late-night host: velvet baritone, cheeky asides, big "ladies and gentlemen" swells. He
whispers while people write their lies and shouts on the reveals. Name to be chosen.

- Lines never say player names (they are pre-rendered); names appear on screen with a Slam.
- 4–8 variants per cue in a shuffle bag. Chattiness setting: Off / Light / Full.
- Speaks at phase starts and big moments only; silent while people type; one line at a time; stale lines
  are dropped; at least 6s between lines.
- Voice: ElevenLabs Eleven v3 with a designed voice and audio tags (`[whispers]`, `[excited]`,
  `[shouts]`, `[laughs]`), stability ~30–45%. Local fallback: Chatterbox (exaggeration ~0.8–1.2,
  cfg_weight ~0.3). Rendered offline and shipped in the APK.

## Sound palette and mix

- **Buses:** Music, SFX, Voice under Master. VO −16 LUFS, music −22 LUFS, SFX peaks ≤ −3 dBTP. Music ducks
  to ~35% under the host (120ms attack, 400ms release).
- **Music:** a talk-show band (Hammond, brass stabs, walking bass, rimshots). Loops: `lobby_lounge`,
  `think_bluff` (+ hurry variant and a 1.08× push in the last 10s), `reveal_bed`, `scores_strut`,
  `podium_outro`. All in one key so stingers land.
- **SFX tiers:** navigate (focus tick) < select (thunk) < moment (stingers, crowd). Repeated sounds get
  3–5 variants and ±3–6% pitch. Progress rises in pitch: each join and each submission is a step higher.
- **TV speakers:** keep the character of every sound in 300Hz–5kHz; never rely on bass.
- **Phones stay quiet:** the TV is the speaker. Phones get haptics; personal sounds are opt-in.

## Bluff Battle beat sheet

| Beat | Visual | Sound / host |
| --- | --- | --- |
| Lobby idle | Bulbs chase, spotlights sway | `lobby_lounge`; rare quip |
| Player joins | Name tag pops on in their colour | Xylophone, one step higher per join |
| Game start | Curtain wipe, title slam | Band hit; "Welcome to BLUFF BATTLE!" `[booming]` |
| Tutorial | Cards dealt; ready avatars pop | Soft ding per ready |
| Write | Prompt slams onto the felt; ON AIR breathes; chips light up | `[whispers]` "Lie to your friends…"; `think_bluff`; chip clink rising per bluff |
| All in / time up | 150ms hit-stop, then ALL IN! slam or buzzer shake | Music cuts + ding-ding, or buzzer |
| Last 5 seconds | Clock turns red and throbs | Tick-tock rising at 3-2-1; hurry music |
| Pick | Answers dealt as playing cards | Card deal, pitch stepping per card |
| Reveal step | Card slams under the spotlight; FAKE! stamp; fooled avatars pop | Drumroll, womp + crowd "ooooh" scaled by fooled count; laugh on 3+ |
| Truth | THE TRUTH stamp, APPLAUSE lights, confetti | Fanfare + applause; `[excited]` |
| Scores | Rows dealt; +points pop; totals count up; leader gets the crown | Count ticks, rank whoosh, `scores_strut` |
| Final round | DOUBLE POINTS badge | Key-change stinger; `[shouts]` |
| Podium | Third, second, then first slam onto lit blocks; cannons fire | Drumroll, crash per rank, outro |
| Pause | Curtains close halfway; PAUSED marquee | Record scratch; music dips |

## Reviewing the look

`./gradlew :app:testDebugUnitTest --tests '*ShowcaseShots*'` renders every main screen at 1920×1080 into
`tv/app/build/showcase/`.

## Drunk Blackjack

Inspired by *Gamble With Your Friends* (Team GWYF / TENSTACK, 2026): a loud toon casino with patterned carpet,
neon crown and lightning signs, chunky LED readouts and keypads, slapstick consequences, and different
win and lose stingers (its soundtrack by Karl Flodin has "New Management" for a win and "I Gambled So Hard" for a loss).

- **The dealer rotates.** Each hand a different player is the House; a game is one hand per player (up to 10).
  Everyone else bets drinks against them: 1, 2 or 3 sips, or a SHOT (5 sips). Doubling down doubles the drinks.
- **Players go first, all at once** (hit / stand / double), then the dealer plays their own hand from their phone:
  they must hit under 17, and from 17 up they can stand or push their luck. The TV shows "ON THE LINE", the sips they
  drink if they bust.
- **Settle:** dealer busts, and they drink every bet still standing. Otherwise beat the dealer and they drink your bet;
  lose or bust and you drink it. Blackjack makes the dealer drink double. Score = sips you made other people drink.
- **House Rules** for hands after the first: Double Trouble (all drinks ×2), Lucky Sevens (each 7 in your hand costs the
  dealer a sip).
- **Cards:** Adrian Kennard's classic deck (Goodall & Son court figures, CC0) via `@letele/playing-cards`, with our
  velvet-and-gold back.
- **Look:** mahogany-railed felt under neon, the House as a visor-wearing mascot who blinks, sweats and Xes out when
  he busts, and sleek ivory cards with Bungee Shade face cards and a velvet-and-gold lattice back. Cards fly from
  the shoe in real deal order and land askew, and the hole card flips over in 3D.
- **Sound:** a lounge-bossa band with vibraphone, clay chip clacks that climb as bets come in, card flicks per
  card, a flip thump for the hole card, a drumroll into the dealer's turn, a bust crash, jackpot bells with a coin
  shower for blackjacks, a funky brass lick when the table wins, and a sad slide guitar when the House does.
- **Phone:** your hand dealt in big, the House's up-card on a felt strip, an LED total, casino-chip bet buttons and
  HIT / STAND / DOUBLE keypad keys with a haptic buzz.

> The original spec kept drink counts off the TV. Drunk Blackjack calls drinks on the TV on purpose, because it was
> asked for. Sober Hand exists, and "sips" (or water) are the house unit.
