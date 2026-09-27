<div align="center">

<a href="brag-output-2026-09-27-015402/brag.mp4"><img src="docs/media/party-os-crew-poster.jpg" alt="Watch PARTY OS: Amanda, Steven, John, Sunhye, Alex, Anna, Charlie, Daniel, Daniel, Ethan, Junha, Kaishun and Izzy — 13 friends, two Daniels, one game show" width="100%"></a>

# PARTY OS

**Your TV hosts game night. Phones are the controllers.**

A party game show for the living room, built for trivia night. The TV runs the show; up to 16 friends play in
teams from their phone's browser. No app, no account, one double-click.

[Watch the video](#13-friends-two-daniels-one-game-show) · [What’s new](#whats-new) · [Quick start](#quick-start) · [Brain Drain](#brain-drain-team-trivia-night) · [The other games](#the-other-games) · [How it's built](#how-its-built)

</div>

---

## 13 friends. Two Daniels. One game show.

**[▶ Watch the 24-second launch video](brag-output-2026-09-27-015402/brag.mp4)** — 1080p, with Party OS's own music,
an animated roll call, real Quick Draw reveals, a Heist and the new Write It Down round.

Starring **Amanda, Steven, John, Sunhye, Alex, Anna, Charlie, Daniel, Daniel, Ethan, Junha, Kaishun and Izzy**.
Both Daniels get their own face and player ID. The video uses staged demo data rendered through the current game
components; the names and results illustrate a party, rather than document a real match.

Made with [brag](https://github.com/latent-spaces/brag)'s lightweight `brag-slim` workflow.
[Poster](docs/media/party-os-crew-poster.jpg) · [Share caption](brag-output-2026-09-27-015402/share-copy.txt) ·
[Storyboard](brag-output-2026-09-27-015402/brag-plan.md)

## What's new

| Change | What it means on game night |
| --- | --- |
| **Home Turf** | The property game on the crew's own places. Buy, auction, build and trade from your phone. A game clock ends it in 30 to 90 minutes, and the richest wins. |
| **Write It Down** | Type your team's answer with no multiple-choice hints. Close spelling counts. Play it inside Brain Drain or as its own pub quiz. |
| **A bigger question bank** | 302 multiple-choice questions, 62 Ballpark numbers and 23 Pick a Side sets. |
| **Questions remembered between nights** | The Mac launcher saves played questions, so the next party starts with fresh material. Exhausted packs recycle older questions on a later night. |
| **Live trivia when the pack runs low** | Optional Open Trivia DB questions extend Quick Draw and The Heist; the bundled game works offline. |
| **Shuffle and shout-outs** | Balance teams from the TV or captain's phone, then hand out awards based on how people played. |
| **The crew cut** | A new launch video featuring all 13 requested players, using the current game screens and soundtrack. |

## Quick start

You need a Mac plugged into a TV (HDMI or AirPlay), **Java 17**, **Node.js** and **Chrome**.

```bash
brew install openjdk@17 node          # once
```

Then double-click **`Start Party OS.command`** in this folder.

1. It builds everything, starts the party server and keeps the Mac awake.
2. The TV screen opens full screen in Chrome on the Mac's main display (so mirror the TV). If it shows **GO LIVE**,
   press **Enter** for sound.
3. Friends scan the QR code on the TV (same Wi-Fi), type a name and **draw their own face**.
4. The first person to join gets the **crown**: they pick the game and start it from their phone, so nobody has to
   get up. The TV keyboard works too.
5. To stop: press **Ctrl+C** in the Terminal window (or close it), and **Cmd+Q** closes the TV window.

If something's off:

- **Phones can't join:** they must be on the same Wi-Fi as the Mac, and guest networks usually block this. If macOS
  asks whether `java` may accept incoming connections, click **Allow**.
- **TV window on the wrong screen:** System Settings → Displays → mirror the TV. **No sound on the TV:** System
  Settings → Sound → Output → the TV, and check PARTY OS isn't muted (**M** toggles it).
- **"Port 8080 is already in use":** PARTY OS is already running in another Terminal window. Close that one first.
- **Host PIN:** printed in the Terminal window. Open `/host` on your phone and enter it to pause, skip or remove
  players without the Mac's keyboard.

No friends yet? Rehearse with bots that join, team up, argue and vote like people do:

```bash
node controller/scripts/bots.mjs 12
```

| On the TV | Does |
| --- | --- |
| ← → · Enter | pick a game · start it |
| ↑ ↓ | questions per round (Brain Drain) or rounds (3–8) |
| T · D | Brain Drain teams (auto, 2–6) · drink calls on/off |
| Esc | host controls: pause, skip ahead, end, remove players, give the crown, phone control on/off, volume, Spotify |
| N | next song (when Spotify is playing) |
| P · M · F | pause · mute · full screen |

<p align="center"><img src="docs/media/lobby.png" alt="The lobby: QR code, every player's hand-drawn face, and the games as comic-book covers" width="88%"></p>

## Brain Drain: team trivia night

Teams vote on their phones. **The team's answer is whatever most of them pick**, so the fun is arguing out loud
before the buzzer. Five rounds, five different formats, dealt in a new order every show, one host: Brainy, a pink brain sipping through a bendy straw.

| Round | How it works |
| --- | --- |
| **Quick Draw** | Four answers with a colour *and* a shape (Kahoot-style, readable from the couch). Right answers score 1,000, plus up to 500 for speed. |
| **Ballpark** | Guess a number on a keypad. Your team's guess is the median of everyone's. Closest team wins; within 1% is a **bullseye**. |
| **Pick a Side** | Seven rapid calls, five seconds each: *Pokémon or medication? Font or cheese? IKEA or Middle-earth?* |
| **The Heist** | Right answers win 500. The fastest correct team votes on which team to rob. |
| **Write It Down** | No options on screen: type the answer. Your team's most-written answer counts, and close spelling is fine ("Seatle", "da Vinci", "the river Seine"), but numbers must be exact and "red or blue" never counts. |

<p align="center">
  <img src="docs/media/trivia-teamup.png" alt="Team Up: players tap a team colour and the first teammate to type a name names it" width="49%">
  <img src="docs/media/trivia-ballpark-reveal.png" alt="Ballpark reveal: team flags on a number line and the answer dropping like an anvil, with a BULLSEYE burst" width="49%">
  <img src="docs/media/trivia-sides-reveal.png" alt="Pick a Side: a split screen, each item snapping to its side" width="49%">
  <img src="docs/media/trivia-steal.png" alt="The Heist: a loot sack flies between teams, ROBBED! and a drink call for the victim" width="49%">
  <img src="docs/media/trivia-podium.png" alt="The podium: the winning team's faces hop under a crown while confetti falls" width="49%">
</p>

- **Drink calls** (on by default, **D** turns them off): last place after each round drinks a sip, a Heist victim
  drinks a sip, and the last-place team at the end drinks two. Water counts. The most points wins; The Heist never opens a show.
- Teams carry over to the next show. Late arrivals join the smallest team at the next question.
- **Shuffle:** friends always pile onto one team. During Team Up, press **S** on the TV (or tap "Shuffle evenly"
  on the captain's phone) to deal everyone evenly across the teams; names stay, everyone checks their new team.
- **Awards:** after the podium, up to four shout-outs from how each person actually played: Big Brain, Fastest
  Thumb, Lone Wolf (went against their team and was right), Human Calculator, and roasts like Contrarian, Dead
  Weight and Ghost. Winners see theirs on their phone.
- 302 multiple-choice questions, 62 Ballpark numbers, 23 Pick a Side sets, all original,
  every one with a fun fact or a checkable answer. That's enough for about 20 full shows without a repeat, and
  Pick a Side deals its calls in a new order every show.
- **No repeats:** every question played is remembered in `~/Library/Application Support/PartyOS/played-questions.json`,
  so later shows and later nights skip it. Nothing played tonight comes back tonight; once a pack is used up on a
  later night, the questions played longest ago return. Delete that file to replay everything.
- **Live fallback:** once the bundled multiple-choice questions run low, the server quietly fetches more from
  [Open Trivia DB](https://opentdb.com) (easy and medium only, anything too long for the TV dropped) and uses them
  for Quick Draw and The Heist after the bundled ones are gone. Live questions have no fun fact and show a small
  "from Open Trivia DB · CC BY-SA 4.0" credit. With no internet the round just ends early; `--live-trivia off` on
  the devserver keeps a show fully offline.
- A show runs about 15–20 minutes at the default five questions per round.

## Your phone is the controller

<p align="center">
  <img src="docs/media/phone-join.png" alt="Joining: type a name and draw your own face with a finger" width="19%">
  <img src="docs/media/phone-shapes.png" alt="Quick Draw on the phone: four big colour-and-shape buttons under your team's colour band" width="19%">
  <img src="docs/media/phone-number.png" alt="Ballpark on the phone: a big number keypad" width="19%">
  <img src="docs/media/phone-sides.png" alt="Pick a Side on the phone: two giant buttons" width="19%">
  <img src="docs/media/phone-waiting-tone-win.png" alt="Your team's result: Correct! +200" width="19%">
</p>

**The captain.** Whoever joins first holds the crown (Jackbox calls this the VIP). Their phone picks the game, sets
questions per round, teams and drink calls, and starts the show; during a game a crown button opens pause, skip
ahead and end. The TV mirrors every choice. If their phone drops, the crown moves to the next person who joined and
comes back when they do; they can pass it on, and the TV's host controls can hand it to anyone or switch phone
control off. Removing players stays on the TV.

Scan, type a name, draw a face, play. Buttons are thumb-sized, every choice is colour **and** shape **and** text,
teammates' faces show up on the answer they picked, and phones buzz on every tap and on your team's result. A
refreshed or dropped phone rejoins as the same player.

## The other games

**Write It Down.** Brain Drain's typed-answer round as a game of its own: a pub quiz in three rounds. No options on
screen, everyone types the answer, and the team's most-written answer counts (close spelling is fine). Same teams,
settings, standings, drink calls and awards as Brain Drain.

**Bluff Battle.** Everyone gets a weird-but-true question and writes a fake answer. Then everyone hunts for the
truth among the fakes. Fool a friend for points; find the truth for more.

**Drunk Blackjack.** The dealer rotates round the room. Everyone else bets sips or a shot against this hand's
dealer, and the dealer plays their own hand from their phone. If the dealer busts, they drink every bet on the table.

**Home Turf.** Buy your friends' places and charge them rent, with the real property-game rules sped up for a party:

- **Turns:** roll on your phone. Buy what you land on, or pass it to a live auction on every phone, where each bid
  resets the clock. The speed die kicks in once you've passed Payday.
- **Sets, hotels and trades:** a full colour set doubles the rent, three houses make a hotel, and trades (places,
  cash and Get Out cards) are built, countered and accepted on the phones while the TV shows the deal.
- **Solo or teams:** play solo up to 6, or in teams with a rotating dice-holder.
- **The clock:** when it runs out, everyone finishes the lap and the richest wins (cash, plus places at their price,
  plus buildings at cost).
- **Drinks:** rent, Timeout and bankruptcy come with drink calls; the lobby can switch them off.
- **Controls:** on the TV lobby, ↑/↓ sets the game clock and T switches between Auto, Solo and Teams.

<p align="center">
  <img src="docs/media/turf-board.png" alt="Home Turf: the crew's places around the board, players' cash on the side rails, dice in the middle" width="49%">
  <img src="docs/media/turf-auction.png" alt="Home Turf auction: the place on offer, the top bid on an LED readout and who's leading" width="49%">
</p>

### Rename the board

The spaces are named after the crew's places. To rename them, edit
[`tv/engine/src/main/resources/turf/board.json`](tv/engine/src/main/resources/turf/board.json):

- **What you can change:** each space has a `name` (≤ 24 characters) and a TV `label` (≤ 22). The deck names and the
  card text are in the same file, and `{space:N}` in a card is replaced by that space's name.
- **What stays fixed:** prices and rents are the standard values, keyed by board position in `TurfBoard.kt`, so a
  rename can't break the economy.
- **Applying edits:** the launcher rebuilds on every start. A test checks the file (40 spaces, lengths,
  placeholders) if you run `./gradlew :engine:test`.

<p align="center">
  <img src="docs/media/bluff-reveal.png" alt="Bluff Battle: a fake answer stamped FAKE! with who wrote it and who fell for it" width="49%">
  <img src="docs/media/blackjack-table.png" alt="Drunk Blackjack: the dealer's own face under a green visor, cards at every seat" width="49%">
</p>

## The look and the sound

- **Saturday Morning:** thick ink outlines, flat loud colour, halftone dots, comic bursts, hand-drawn faces instead
  of emoji. One token file ([`controller/src/theme/tokens.css`](controller/src/theme/tokens.css)) drives the TV
  and the phones. Fonts: Rammetto One for display, Figtree for reading.
- **Spotify instead:** with the Spotify app on the Mac, press **Esc** on the TV → **Music** → **Spotify**. The score
  goes quiet, Spotify starts playing (pick a playlist in Spotify first), the effects still play over it, and **N**
  skips a song. The first time, macOS asks whether `java` may control Spotify: click **OK**.
- **Music:** a composed cartoon big band score (120 BPM, F major) with a loop per round, a hurry-up bed for the last
  ten seconds and stingers that duck the music. Generate it once with ElevenLabs:

  ```bash
  export ELEVENLABS_API_KEY=...                    # ElevenLabs → Developers → API keys
  node controller/scripts/audio/generate.mjs       # writes controller/public/assets/audio
  ```

  The cue list lives in [`controller/scripts/audio/cues.json`](controller/scripts/audio/cues.json); re-roll any cue
  with `--only <id> --force`. Until the score exists, music stays silent and effects fall back to small synthesized
  versions.

## How it's built

```
tv/engine      Kotlin: the games and the party rules (one engine, used by every screen)
tv/server      Ktor: join, host PIN, WebSocket protocol
tv/devserver   runs engine + server on a Mac: this is what Start Party OS.command launches
tv/app         Android TV app (Jetpack Compose) running the same engine on the TV itself
controller/    React + Vite: the phone controller and the web TV screen (/tv)
```

- Brain Drain is [`tv/engine/.../games/trivia/BrainDrain.kt`](tv/engine/src/main/kotlin/partyos/engine/games/trivia/BrainDrain.kt);
  its questions are the `trivia-*.json` packs next to it in `src/main/resources/packs`.
- The web TV draws on a fixed 1920×1080 stage scaled to any screen, with [Motion](https://motion.dev) for animation.
  Phones and the TV both render snapshots from the engine; the server owns every timer, vote and score.
- **Android TV:** the same engine runs natively in `tv/app`; its screens still use the 1.0 look.

### Develop

```bash
cd tv && ./gradlew :devserver:installDist && devserver/build/install/devserver/bin/devserver --pin 1234 --static ../controller/dist
cd controller && npm run dev              # live reload; proxies /api and /ws to the devserver
```

Open `http://localhost:5173/tv` for the TV and `/j/ROOM` for a phone. `/tv?gallery=trivia` renders every Brain Drain
beat from fixtures, and `node controller/scripts/shots.mjs` screenshots them all at 1920×1080.

```bash
cd tv && ./gradlew :engine:test :server:test :app:testDebugUnitTest   # engine, server and TV layout tests
cd controller && npm test && npm run e2e                                # protocol tests + phone flows in Playwright
```

Design notes and every game's beat sheet are in [`docs/party-os/show-bible.md`](docs/party-os/show-bible.md).

## Credits

Fonts: [Rammetto One](https://fonts.google.com/specimen/Rammetto+One), [Figtree](https://github.com/erikdkennedy/figtree),
[DSEG7](https://github.com/keshikan/DSEG) (all OFL). Cards: [Adrian Kennard](https://www.me.uk/cards/) via
[@letele/playing-cards](https://github.com/letele/playing-cards) (CC0). Round formats nod to *Buzz!*, *You Don't Know
Jack*, *Wits & Wagers* and *Trivia Murder Party*; the names, art and questions are original.

Please drink responsibly. Sips of water count.

---

<details>
<summary><b>The original HoopDreams</b>: game night, the shot tracker and the random game hat</summary>

A TV party arcade for four teams of four: live team trivia, phone-to-TV drawing, funny-answer voting, and the shot tracker.

**Quick start on Mac:** double-click `Start Game Night.command`. On the host page, choose **Set up four teams**, invite everyone with the TV QR code, and start a game. No API account required. See [the game-night guide](docs/game-night.md). The original random game hat is at `/hat`.

### Stack

- **Frontend**: React + TypeScript (Vite)
- **Backend**: Python (FastAPI)
- **Database**: SQLite (via SQLAlchemy)

### Running locally

#### Backend

```bash
cd backend
python3 -m venv venv
venv/bin/pip install -r requirements.txt
venv/bin/uvicorn app.main:app --reload --port 8000
```

This seeds the game list on first run and creates `backend/hoopdreams.db`.

#### Frontend

```bash
cd frontend
npm install
npm run dev -- --port 5180
```

Open the printed URL (defaults to `http://localhost:5180`). Live party traffic uses the Vite proxy to port 8000. The original `/hat` page expects the API at `http://localhost:8000` (see `frontend/.env`).

### How it works

- The hat holds every active game from the `games` table.
- Tapping the hat draws a random game that hasn't come up yet this round (`POST /api/draw`), logs it to the `draws` table, and shows it as a card.
- Once every game has been drawn, the next tap reshuffles the hat automatically.
- "Recently drawn" pulls the last few draws from `GET /api/history`.
- Add or edit games directly in `backend/app/seed_data.py` (only used to seed an empty database) or the `games` table.

### Party mode (shot tracker)

Run it on the MacBook that is plugged into the TV:

```bash
backend/venv/bin/python backend/scripts/party.py            # add --tunnel for guests on cell data
```

This builds the frontend, serves everything on port 8000 and keeps the Mac awake. It prints a QR code and a host PIN, then opens the TV page in Chrome.

- **TV**: `http://localhost:8000/tv`. Mirror the Mac onto the TV and choose **Full screen**. The original arcade shot board is at `/tracker`.
- **Phones**: scan the QR code on the TV (same Wi-Fi), or the "not on Wi-Fi?" code when `--tunnel` is on.
- **Host panel**: `http://localhost:8000/host` with the printed PIN (set `HOOP_HOST_PIN` to choose one).
- **Rehearsal**: add `--demo` for 16 simulated guests in a separate temporary night.

Development: run the backend as above, then `npm run dev -- --host` in `frontend/`. Open `/tv`, `/play` or `/host` on the Vite port, and set `HOOP_PUBLIC_PORT=5173` for the backend so the TV's QR code points at Vite.
Tests: `cd backend && venv/bin/python -m pytest` and `cd frontend && npm test`.

</details>
