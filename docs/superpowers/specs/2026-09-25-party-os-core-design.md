# PARTY OS — Sub-project 1: Core Platform + Bluff Battle

**Date:** 2026-09-25
**Status:** Draft for review
**Target hardware:** TCL QM6K Google TV (assumed; verify model in Settings → System → About)

## 1. Goal

Turn the TV into a self-contained party console. The TV app hosts its own server; guests join from phone browsers with no install. This sub-project delivers the platform and one complete game (Bluff Battle), proving the engine is reusable before the other nine games are built.

**Success means:**
- CI produces an installable APK.
- A 16-bot simulated party completes Bluff Battle over real sockets on CI.
- Real phones join through the QR code: first against the Android TV emulator on the Mac, then on the real TV.
- The on-TV benchmark tour shows ≥95% of frames rendered within 16.7 ms on the QM6K.

**Non-goals for this sub-project:** Games 2–10, Party Cam, Party Rewind, pack editor/import/export, playlists, statistics, player profiles, drink counter. Their constraints are recorded in §13 so this design does not block them.

## 2. Context and decisions

HoopDreams today is a Mac-hosted FastAPI + Socket.IO + React party app (three team games, host PIN, reconnect/resync, shot-cam uploads). PARTY OS evolves this repo into a TV-hosted product, reusing HoopDreams' *designs and rules* but not porting its code line by line.

**Decisions made during brainstorming:**
| Decision | Choice |
|---|---|
| Where it lives | This repo (HoopDreams), new branch `party-os` |
| Stack | Native Kotlin: Compose for TV + embedded Ktor server; React phone controllers |
| TV rendering | All TV screens native Compose, with no WebView, for 60 fps |
| First game | Bluff Battle |
| Drink tracking | HoopDreams' competitive shot leaderboard (team totals, ranks, streaks) is **not** carried over; see §13 |
| Dev loop | Real Android TV emulator on the Mac + desktop JVM server mode |

**Research (verified 2026-09-25):**
| Candidate | Finding | Verdict |
|---|---|---|
| react-native-tvos | MIT, v0.87.1 (2026-09-18), active | Healthy but UI-only; not chosen |
| React Native Couch Kit + Buzz starter | MIT, active, single maintainer, 4 stars; needs Expo + native modules; LAN mode HTTP 8080 / WS 8082; no media upload path | Good protocol ideas (shared reducer, session recovery); too thin a foundation |
| Ktor | Apache-2.0, 3.6.0 (2026-09-18) | **Chosen**: embedded HTTP + WebSocket + streaming uploads |
| AndroidX Media3 | Apache-2.0, 1.11.1 | Chosen later for Party Cam (ExoPlayer, Transformer) |
| ffmpeg-kit | Archived | Rejected |
| Chaquopy | MIT, active; index lacks `pydantic-core` | Rejected (FastAPI v2 won't run) |
| nodejs-mobile | Last release Node 18.20.4, 2024-10 | Rejected (stale) |
| AirConsole | Proprietary cloud service | Rejected (brief forbids commercial service dependency) |

## 3. Architecture

```
HoopDreams/  (branch: party-os)
├── tv/                       Android Gradle project (Kotlin 2.x, JDK 17, minSdk 31, targetSdk = latest stable, pinned in the plan)
│   ├── engine/               pure Kotlin/JVM, no Android imports
│   │                         lobby, identities, GameModule API, runtime (timers, pause, dedupe), Bluff Battle
│   ├── server/               pure Kotlin/JVM: Ktor (CIO) routes, WebSocket session layer, protocol (kotlinx.serialization)
│   ├── devserver/            JVM main() running engine + server on the Mac (no Android, no TV UI)
│   └── app/                  Android TV app: foreground service, Compose-for-TV UI, Room, QR (ZXing core)
├── controller/               React + TypeScript + Vite phone app; built output copied into tv/app assets
├── .github/workflows/        CI (tests, APK, optional signed release)
└── docs/party-os/            sideload guide, emulator guide, CHECKLIST.md
```

**Data flow:**
1. A phone sends an action over the WebSocket.
2. The server authenticates the socket token and hands the action to the engine runtime.
3. The runtime validates the action (phase, round ID, action ID) and calls the game's pure reducer.
4. The runtime commits the new state and derives three kinds of view:
   - the TV view
   - a private view per player
   - the host view
5. The server sends each phone its own view.
6. The Compose UI collects the TV view from an in-process `StateFlow`, with no network hop.

**Threading:**
- The engine runs on a single-threaded coroutine dispatcher, so every state change is serialised and races aren't possible.
- The server's I/O runs on Ktor's pool.
- The UI runs on the main thread and only reads immutable view objects.

The legacy `backend/` and `frontend/` folders stay untouched and working until PARTY OS reaches feature parity after sub-project 2. They're deleted after that.

## 4. Networking

**Server lifecycle:**
- A foreground service (type `specialUse`) owns the engine and the Ktor server.
- It holds `WifiManager` `WIFI_MODE_FULL_LOW_LATENCY` and a partial wake lock while a party is open.
- The activity sets `FLAG_KEEP_SCREEN_ON`.
- Pressing Home leaves the party running. Full TV standby drops the network; on return the active round is paused and phones reconnect automatically.

**Address and QR:**
- The app reads the active network's IPv4 address from `ConnectivityManager` / `LinkProperties` (Wi-Fi or Ethernet).
- It binds `0.0.0.0` on the first free port in 8080–8089.
- The QR encodes `http://<ip>:<port>/j/<ROOM>`, where ROOM is a 4-letter code (no ambiguous letters) regenerated per party.
- The URL and room code are shown in large text beneath the QR.
- The address updates live if the network changes.
- **Help card** shown on the lobby: same Wi-Fi required; guest networks often isolate clients.
- **Dev setting "Advertised address override"** replaces the QR host:port. It's used with the emulator (see §11).

**Endpoints:**
| Route | Purpose |
|---|---|
| `GET /`, `GET /j/{room}`, `GET /assets/*` | Controller SPA (from APK assets) |
| `POST /api/join` `{room, name, avatar, spectator}` | → `{playerId, token}`; 409 on wrong room, 403 when full |
| `POST /api/host/login` `{pin}` | → `{hostToken}`; 5 failures → 60 s lockout per client IP |
| `GET /ws?token=…` | WebSocket game channel |
| `GET /healthz` | Liveness, used by tests and the port probe |

**WebSocket protocol** (JSON, `t` discriminator; types generated for TS from Kotlin):
- **Client → server:**
  - `hello{protocol}`
  - `action{id, round, payload}` (`id` is a client-generated UUID)
  - `host{id, cmd}`
  - `ping`
- **Server → client:**
  - `welcome{playerId, role}`
  - `view{seq, view}` (a full view snapshot, not a diff)
  - `ack{id}`
  - `reject{id, code}`
  - `pong`
  - `bye{reason}` (kicked or party ended)
- **Why full snapshots:** Views stay small (target < 8 KB), and a full snapshot makes every reconnect a complete resync.

**Limits:**
- Frames are capped at 64 KB, with 20 actions/s per socket.
- Requests from non-private source IPs (outside RFC1918, link-local, loopback) are refused.
- Text fields are trimmed and length-capped server-side.
- All user text is rendered as plain text only.

## 5. Sessions and identity

**Party and room code:**
- One party is open at a time.
- Opening a new party issues a new room code and archives the old one to history.

**Joining:**
- Players pick a name (1–16 characters, unique case-insensitively within the party) and an emoji avatar with a colour.
- The server issues a 128-bit random token and stores only its SHA-256 hash.
- The phone keeps the token in `localStorage`, with a same-origin cookie as a fallback.

**Reconnect:**
- The phone opens `/ws?token=`, receives `welcome` plus the current `view`, and is back in place.
- A browser refresh follows the same path.
- The controller resends unacknowledged actions from an outbox, and the server deduplicates them by action ID.

**Presence:**
- Heartbeat every 3 s. A player with no traffic for 10 s is marked `away`.
- Away players stay in the party and never block a phase.

**Capacity:** 16 players + 16 spectators.
- Spectators receive a spectator view and cannot send actions.
- They can switch to player at a lobby boundary if a seat is free.

**Late join:** Each game declares a policy (`nextRound`, `nextGame`, `anytime`). Bluff Battle uses `nextRound`.

**Host:**
- The TV (remote) is always the authority.
- Phones become co-hosts with the PIN. The first PIN is random, and it can be changed in Settings.
- Any number of co-hosts is allowed.
- Host commands:
  - `startGame`, `pause`, `resume`, `skipPhase`, `endGame`
  - `kick`
  - `setRounds`
- Kicked players get `bye` and their token is revoked.

**Crash recovery:**
- Each committed step is snapshotted to Room (coalesced, off the main thread).
- On restart the app restores the party, players and game state, and the active round is paused. The host resumes it.

## 6. Engine

```kotlin
interface GameModule<S : Any> {
  val info: GameInfo                       // id, title, minPlayers, maxPlayers, tutorial cards, lateJoin policy, settings schema
  val stateSerializer: KSerializer<S>      // for snapshots
  fun start(ctx: GameContext): Step<S>
  fun onAction(s: S, who: PlayerId, a: GameAction, ctx: GameContext): Step<S>
  fun onDeadline(s: S, ctx: GameContext): Step<S>
  fun onPresence(s: S, who: PlayerId, present: Boolean, ctx: GameContext): Step<S> = Step(s)
  fun tvView(s: S, ctx: GameContext): TvView
  fun playerView(s: S, who: PlayerId, ctx: GameContext): PlayerView
}
data class Step<S>(val state: S, val effects: List<Effect> = emptyList())
// Effect: SetDeadline(ms), ClearDeadline, Award(playerId, points, reason), Highlight(text), Finish(results)
// GameContext: clock, seeded Random, roster (active/away/late), settings, content packs
```

**What the runtime owns, so games never re-implement it:**
- deadlines, driven by an injected `Clock`
- pause and resume (frozen remaining time)
- skip
- round IDs, rejecting actions tagged with a stale round
- action-ID deduplication
- ending a phase early when every connected, eligible player has acted (the game declares who is eligible through the player view's `awaitingInput` flag)
- tutorial gating: "got it" taps plus a timeout
- scoreboard accumulation
- snapshots
- the results hand-off to history

**Phone screens are built from shared primitives:**
- `TextEntry`, `ChoiceList`, `PlayerPicker`, `SecretCard`, `Canvas`, `Waiting`, `Tutorial`, `Scoreboard`
- A new game needs a custom React component only when it needs a new kind of input.

**Randomness:** Seeded per game, so a test can replay a whole game deterministically.

## 7. Bluff Battle

**Settings and content:**
- Rounds: 3–8, default 5.
- Content: an original question pack, about 60 questions.
- Question format: `{id, prompt, answer, alsoAccept[], decoys[≥2]}`, validated at load time.
- **Pack file format:** The same schema that sub-project 5's editor will import and export, wrapped as `{packId, title, game, version, items[]}`.

**Phases:**
1. **Tutorial**
   - 3 cards, ≤ 30 s.
   - It ends early once all active players tap "Got it".
2. **Write (60 s)**
   - Each player submits one fake answer, 1–60 characters. They can edit it until the phase ends.
   - A fake is rejected (`TOO_TRUE`) if, after normalisation, it matches `answer` or any `alsoAccept` entry. Normalisation lowercases, strips punctuation and whitespace, and drops leading articles.
   - Identical normalised fakes merge into one option credited to all their authors.
3. **Pick (30 s)**
   - The option list is the truth plus the distinct fakes, shuffled with the seeded RNG.
   - If there are fewer than 3 options, pack decoys (not submitted by anyone) fill up to 3.
   - Each player picks one option. Their own fake is excluded from their list.
   - Players who wrote nothing can still pick.
   - Picks can change until the phase ends.
4. **Reveal**
   - Fakes are revealed in ascending order of how many players they fooled, each showing its authors and the players it fooled.
   - The truth is revealed last.
   - The game auto-advances when the animation finishes. The host can skip ahead at any time.
5. **Scores**
   - Scoring:
     - +1000 for picking the truth.
     - +500 per player fooled by your fake, credited to every author of a merged fake.
     - The final round doubles all points.
   - The animated scoreboard then shows each player's round delta. It auto-advances after 8 s, and the host can skip.
6. **Podium** after the last round. Results are written to party history.

**Rules:**
- **Minimum players:** 3 connected players to start. If fewer than 2 remain connected mid-game, the game auto-pauses with a notice.
- **Questions:** None repeats within a party.

## 8. TV interface

**Screens in this sub-project:**
- **Home:** Hero area plus tiles: Play Games, Quick Play (random eligible game), Settings. Other destinations appear only when their sub-projects ship.
- **Lobby:** QR, room code and URL, a player grid with join animation and an away badge, a spectator count and the network help card.
- **Game picker:** Game cards with code-drawn art, player-count eligibility, and tutorial then start.
- **Bluff Battle stages:** Write with a submitted-count and countdown ring, Pick, Reveal sequence, Scores, Podium.
- **Host overlay:** Opened with Menu or a long-press on OK. Pause/resume, skip phase, end game, kick, rounds, PIN.
- **Settings:** Host PIN, advertised address override (dev), performance HUD toggle, benchmark tour, about/network info.

**Visual system:**
- Dark base with per-game gradient palettes and one bundled OFL display font plus a body font.
- Game art is drawn in code (shapes, gradients, motion). No bitmaps are copied from any product.
- Bluff Battle look: poker-table green and brass, with a mask motif.
- Original short UI sounds through `SoundPool`.

**Remote handling:**
- Compose for TV (`androidx.tv:tv-material`) handles focus, with scale plus glow on the focused element.
- D-pad only.
- Back never exits a running game without a confirmation dialog.

## 9. Performance (60 fps target)

**Rendering rules:**
- Render at the UI resolution Google TV provides (1080p), not 4K.
- Animate only transforms and alpha, inside `graphicsLayer`.
- No live `RenderEffect` blur.
- Cache brushes and gradients.
- Decode images at display size.
- Per-frame values (countdown rings, timers) are read in the draw or layout phase via lambdas, so they don't recompose the screen.
- Keep views immutable and `@Stable`.

**Build-time:**
- Bundle a Baseline Profile.
- R8/minify on release builds.

**Measurement:**
- **JankStats** records every frame, and a Settings toggle shows a live HUD with fps and jank %.
- The **benchmark tour**, started from Settings or with `adb shell am start … --ez tour true`:
  - spins up 16 in-process simulated players
  - runs lobby → tutorial → one full Bluff Battle round, including the reveal
  - logs `PARTYOS_PERF frames=… onTime=…%` to logcat
- **Pass mark:** ≥95% on the QM6K.
- The emulator figures are informational only; they don't count toward the pass mark.

## 10. Persistence

**Room tables:**
- `party`: id, room code, created/ended times, PIN hash
- `player`: id, party, name, avatar, token hash, role, kicked
- `game_session`: id, party, game id, status, snapshot JSON, schema version
- `game_result`: session, standings JSON, highlights JSON

**Other storage and write policy:**
- Settings live in DataStore.
- Snapshots are written after each committed step, coalesced to at most one write per 250 ms, on an IO dispatcher.
- Schema changes ship with Room migrations and a migration test.

## 11. Development setup

**Mac prerequisites:**
- JDK 17 (Temurin, Homebrew)
- Android command-line tools with `platform-tools`, `emulator`, a platform, build-tools, and an **Android TV arm64 system image** (Google TV image if available for the chosen API level; the exact image is chosen in the plan and verified with `sdkmanager --list`)
- Node 24 (already installed)

**Emulator and phone testing:**
1. `adb forward tcp:8080 tcp:8080` maps the emulator's server to `localhost:8080` on the Mac.
2. A small dev proxy (`tv/tools/lan-proxy`, a Node script) listens on `<mac-lan-ip>:8080` and forwards to `localhost:8080`, WebSocket upgrades included.
3. The TV app's "Advertised address override" is set to `<mac-lan-ip>:8080` so the QR points at the proxy.
4. `scripts/emulator-party.sh` automates steps 1–3 and prints the URL.

**Desktop dev mode:**
- `./gradlew :devserver:run` runs the engine and server on the Mac at `http://<mac-lan-ip>:8080`, with no TV UI.
- It's used for fast controller iteration with Vite's dev server proxying to it.

## 12. Testing

| Layer | Tooling | Covers |
|---|---|---|
| Engine | JUnit 5 + fake clock, seeded RNG | Bluff Battle scoring, final-round double, `TOO_TRUE`, merge credit, own-option exclusion, decoy fill, min players, late join, away players, pause/resume/skip, stale round, duplicate action, deterministic replay |
| Server | Ktor `testApplication`, real WS clients | join / wrong room / full, token resume, refresh recovery, spectator can't act, PIN lockout, 64 KB cap, rate limit, non-private IP refused, kick revokes token |
| Simulated party | JVM test, 16 coroutine bots over real sockets against a real server | Full game with random disconnect/reconnect. The invariants checked: all clients converge to the same scoreboard; scores equal a recomputation from the recorded actions |
| Persistence | Robolectric + Room in-memory | snapshot → kill → restore → paused round, scores intact; migration test |
| Controller | Vitest; Playwright against `devserver` | outbox and reconnect logic; headless phones join and play a round |
| Emulator smoke | CI Android TV emulator (x86_64, KVM) | App launches, lobby shows a QR that decodes to a URL answering `/healthz` |

## 13. Constraints for later sub-projects

**Sub-project 2: Games 2–10**
- Each game plugs in as a `GameModule` with the shared primitives.
- A new phone primitive is added only when needed: Drawing Disaster needs a canvas stroke stream; Secret Saboteur needs secret cards.

**Sub-project 3: Party Cam**
- Streaming multipart upload to app-private storage, with size caps.
- Validation by magic bytes. Server-generated filenames only.
- Media3 Transformer transcodes HEVC→H.264 on the TV. HEIC is decoded with `ImageDecoder`.
- Photos use `<input type=file accept capture>`, because `getUserMedia` needs a secure context that LAN HTTP lacks.
- There is a host approval queue, and nothing is reachable off the LAN.

**Sub-project 4: Party Rewind**
- Slideshow playback is built first. MP4 export is investigated after that.
- Only media whose uploader consented to appear in recaps is included.

**Sub-project 5: Host tooling**
- Pack editor and import/export using the §7 format.
- Playlists, statistics, player profiles.
- **Drink counter:**
  - opt-in and private to each player's phone view
  - editable and clearable per player
  - water-break reminders and a non-alcoholic option
  - never shown on the TV
  - no totals, ranks, streaks, milestones, or logging for someone else
  - no game ever requires drinking

## 14. Deliverables of this sub-project

**Code and workflows:**
- The `tv/` and `controller/` code, with the tests listed above.
- GitHub Actions workflow:
  1. controller build + tests
  2. JVM tests
  3. `assembleDebug` uploaded as an artifact
  4. the emulator smoke test
  5. a signed `assembleRelease` when the `RELEASE_KEYSTORE_*` secrets exist

**Signing:**
- A committed debug keystore (debug builds only) keeps updates installable over previous builds.
- The release key lives only in GitHub secrets.

**Docs:**
- `docs/party-os/SIDELOAD.md`: enable developer options on the QM6K, network debugging, `adb connect`, `adb install -r`, and an ADB-free fallback. Steps are verified against current Google TV documentation before writing.
- `docs/party-os/EMULATOR.md`: SDK install, AVD creation, `emulator-party.sh`.
- `docs/party-os/CHECKLIST.md`: Done / Built-but-untested-on-hardware / Known issues. Nothing is marked tested unless it was.

## 15. Risks

| Risk | Mitigation |
|---|---|
| QM6K GPU/CPU weaker than expected | Measured early via the benchmark tour; reduce effects per the rules in §9 |
| Router client isolation blocks phones | Lobby help card; documented; nothing the app can bypass |
| Emulator lacks a Google TV arm64 image at the chosen API | Fall back to the Android TV arm64 image; UI behaviour is equivalent |
| CI Actions minutes on the repo owner's account | JVM tests are fast; the emulator job runs only on `main` and PRs |
| Repo is owned by `steven-c302` | Work happens on the `party-os` branch; merging is the owner's call |
