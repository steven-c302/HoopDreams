# PARTY OS Core Platform + Bluff Battle Implementation Plan (short form)

> Short-form plan by user request: tasks, interfaces and required tests; code written inline during native execution (superpowers:executing-plans). One whole-branch review at the end.

**Goal:** A sideloadable Android TV app that hosts its own server, where phones join by QR and play Bluff Battle.
**Architecture:**
- **Engine:** Pure-JVM `engine`, which holds the party, roster, tokens, runtime and games.
- **Server:** Pure-JVM `server`, which wraps the engine with Ktor HTTP + WebSocket and serialises access through a Mutex.
- **Android app:** `app` hosts the server in a foreground service and renders the TV with Compose for TV.
- **Phone controller:** `controller` is a React/Vite app served from the APK assets.

**Tech (pinned 2026-09-25):**
- **Kotlin and build:** Kotlin 2.4.20 · AGP 9.4.1 (built-in Kotlin; do NOT apply `org.jetbrains.kotlin.android`) · Gradle 9.8.0 · JDK 17
- **Android and Compose:** compileSdk/targetSdk 36, minSdk 31 · Compose BOM 2026.09.00 · tv-material 1.1.0 · activity-compose 1.13.0 · lifecycle 2.11.0
- **Networking and data:** Ktor 3.6.0 (CIO) · kotlinx-serialization 1.11.0 · coroutines 1.11.0
- **Storage, QR and profiling:** Room 2.8.5 + KSP 2.3.12 · DataStore 1.2.1 · ZXing core 3.5.4 · JankStats 1.0.0 · profileinstaller 1.4.1 · baselineprofile 1.5.0
- **Testing:** Robolectric 4.17 · Playwright 1.63 · Vite 8
- **Emulator images:** `system-images;android-34;google-tv;arm64-v8a` locally; `google-tv` x86_64 API 34 on CI via ReactiveCircus/android-emulator-runner v2.38.0

**Spec:** `docs/superpowers/specs/2026-09-25-party-os-core-design.md`

## Global Constraints
- No WebView anywhere in the TV app. All TV screens are Compose.
- `engine` and `server` must have zero Android imports.
- All user text is plain text. Server caps: name 16, fake answer 60, WebSocket frame 64 KB, 20 actions/s per socket.
- Only private/loopback/link-local source IPs are served.
- The drink counter is out of scope. Nothing about drinking appears in this sub-project.
- **Nothing is marked "tested" in CHECKLIST.md unless a command actually ran it.**

## Deliberate deviations from spec (flag in final report)
1. **§10 Room storage:** The roster lives inside the party snapshot JSON rather than a separate `player` table, which avoids storing players twice. Tables: `party(id, roomCode, createdAt, endedAt, snapshotJson, schemaVersion)` and `game_result(id, partyId, gameId, finishedAt, standingsJson, highlightsJson)`. Schema export is on; migration tests start when schema v2 exists.
2. **§4 protocol TS types:** Instead of code generation, these are hand-written in `controller/src/protocol.ts`. `ProtocolFixturesTest` (server) writes golden JSON fixtures, and a Vitest test parses every fixture with the app's own parser, so drift fails CI in both directions.

## Review Focus
1. **Duplicate display names across a refresh:** A player who rejoins by token keeps their name. A *new* join reusing a taken name gets 409 `NAME_TAKEN`, not a second identical player.
2. **The phone that submitted just as the phase ended:** A stale `round` gets `reject STALE`, and the phone re-renders from the fresh view without looping.
3. **All but one player disconnecting mid-Write:** The phase must end early for the remaining connected players, and the game auto-pauses under 2 connected players.
4. **A TV network change or emulator override:** The QR must show the override when it's set, and otherwise the live IP, never `0.0.0.0` or `10.0.2.15` on a real TV.
5. **An app kill during Reveal:** The restored state must show the same reveal, paused, with scores awarded exactly once (no double Award on replay).

---

### Task 1: Gradle scaffold + toolchain
**Files:** `tv/settings.gradle.kts`, `tv/build.gradle.kts`, `tv/gradle/libs.versions.toml`, `tv/gradle.properties`, the wrapper, and `tv/{engine,server,devserver,app}/build.gradle.kts`. Also `.gitignore` additions (`tv/**/build`, `tv/.gradle`, `tv/local.properties`, `controller/node_modules`, `controller/dist`).
**Tests:** `./gradlew :engine:test` runs a trivial test green, and `./gradlew :app:assembleDebug` builds an empty Compose activity.

### Task 2: Engine core: party, roster, tokens
**Produces:**
- `PartyEngine(clock: Clock, rng: SecureRandom-backed TokenSource, games: GameRegistry)`
  - `join(roomCode, name, avatar, role): JoinResult`
  - `resolve(token): PlayerId?`
  - `setPresence(pid, connected)`
  - `setPin(pin)`, `checkPin(pin): Boolean`
  - `snapshot(): PartySnapshot`
  - `companion restore(PartySnapshot, clock, games)`
  - `roomCode`
- Supporting types: `Player`, `Avatar`, `Role`, `PlayerId`, `Clock`, `FakeClock`, `JoinError {WRONG_ROOM, FULL, NAME_TAKEN, BAD_NAME}`.

**Tests:**
- The room code is 4 letters from an unambiguous alphabet.
- A wrong room gives `WRONG_ROOM`.
- The 17th player gives `FULL`, and a spectator still fits up to 16.
- Names are trimmed and case-insensitively unique (`NAME_TAKEN`), and 0 or >16 chars gives `BAD_NAME`.
- Only the token hash is stored (the snapshot JSON doesn't contain the raw token).
- `resolve` works after `restore`.
- A kicked player's token no longer resolves.

### Task 3: Game runtime
**Produces:**
- `GameModule<S>` exactly as in spec §6, but `onAction` takes `payload: JsonObject`. Plus `waitingOn(s): Set<PlayerId>?`, where null means "not an input phase".
- `Step`, and `Effect { Phase(durationMs: Long?), Award(pid, pts, reason), Highlight(text), Finish }`.
- `GameContext(now, random = Random(seed + phaseSeq), roster, scores, settings)`.
- `Reject(code)` exception.
- Engine APIs:
  - `action(pid, actionId, round, payload): ActionResult`
  - `host(cmd: HostCmd)`
  - `tick()`
  - `nextDeadline(): Long?`
  - `tvState(): TvState`
  - `phoneState(pid): PhoneState`
- `HostCmd { StartGame(gameId, settings), Pause, Resume, SkipPhase, EndGame, Kick(pid), SetRounds(n) }`.
- The runtime-owned tutorial stage runs before `start`.

**Tests** (use a tiny `CountdownGame` test double):
- A stale round gives `STALE`, and a duplicate actionId is ignored with `ack`.
- Pause freezes the remaining time, and resume restores it.
- Actions while paused give `PAUSED`.
- The phase ends early when all *connected* waiting players have acted.
- Start is refused below minPlayers (`NOT_ENOUGH_PLAYERS`).
- The game auto-pauses when fewer than 2 players are connected.
- The tutorial ends on all acks or after 30 s.
- A snapshot mid-phase, then restore, comes back paused with identical state and scores (**Review Focus 5**).
- The same seed and actions produce the same result.

### Task 4: Bluff Battle
**Files:** `engine/.../games/bluff/{BluffBattle.kt, BluffState.kt, Normalize.kt}`, `engine/src/main/resources/packs/bluff-core.json` (≥60 original questions).
**Tests:**
- `TOO_TRUE` against the answer and `alsoAccept`, with normalisation (case, punctuation, leading "the/a/an").
- Merged fakes credit both authors.
- A player's own option isn't in their choices.
- Decoys fill to at least 3 options.
- The truth pick earns 1000, each fooled player earns 500, and the final round doubles.
- Late joiners wait for the next round.
- No question repeats within a party.
- An away author's fake still counts.
- Reveal order is ascending by fooled count, truth last.
- Loading the pack validates it (≥2 decoys, and the answer isn't among the decoys).

### Task 5: Server
**Produces:**
- `PartyHost(engine, clock, scope, onCommit: suspend (PartySnapshot)->Unit)`, a Mutex-serialised wrapper that exposes `tv: StateFlow<TvState>` and schedules deadline ticks.
- `fun Application.partyModule(host, static: StaticFiles, cfg: ServerConfig)`.
- `PartyServer.start(host, static, ports = 8080..8089): Int` (the bound port).
- Routes as in spec §4.
- `Protocol.kt` holding the message types.

**Tests** (testApplication plus real WebSocket clients):
- Join 200/409/403.
- WebSocket `welcome` + `view` on connect.
- Token resume after close.
- A spectator action gives `reject SPECTATOR`.
- PIN lockout after 5 failures.
- A frame over 64 KB closes the socket.
- The TokenBucket limits to 20/s.
- `isPrivateAddress` table test.
- A kick sends `bye` and revokes the token.
- `ProtocolFixturesTest` writes the golden fixtures.
- **Review Focus 1 and 2** are covered by the name-taken and stale-round WebSocket tests.

### Task 6: Simulated 16-player party + devserver
**Files:** `server/src/test/.../SimulatedPartyTest.kt`, `devserver/src/main/kotlin/Main.kt` (flags `--port --pin --static <dir>`; prints URL + PIN).
**Tests:**
- 16 bots on a real CIO port play a full 3-round Bluff Battle, with a random 20% disconnect/reconnect each phase.
- All bots converge on an identical final scoreboard.
- The scoreboard equals a recomputation from the recorded picks and fakes.
- **Review Focus 3:** Drop 15 of 16 players mid-Write; the phase ends for the survivor and the game auto-pauses.

### Task 7: Controller (phone app)
**Files:**
- Setup: `controller/` (Vite React TS).
- `src/protocol.ts`
- `src/net/{connection.ts, outbox.ts, token.ts}`
- Pages: `src/pages/{Join.tsx, Play.tsx, Host.tsx}`
- Screens: `src/screens/{Waiting,TextEntry,ChoiceList,Tutorial,Scores}.tsx`
- Tests: `e2e/party.spec.ts`

**Tests:**
- **Vitest:**
  - Every server fixture parses.
  - The outbox resends unacked actions after reconnect and drops them on ack or reject.
  - Reconnect uses exponential backoff capped at 5 s.
  - The token is stored in localStorage with a cookie fallback.
  - A stale reject triggers no resend loop.
- **Playwright against devserver:** 3 phones and 1 host page join, start Bluff Battle, write fakes, pick and reach Scores.

### Task 8: Android app: service, network, persistence
**Files:**
- `app/.../PartyOsApp.kt`
- `service/PartyService.kt` (foreground `specialUse`, Wi-Fi low-latency lock, wake lock)
- `net/NetworkAddress.kt` (ConnectivityManager callback → `StateFlow<String?>`)
- `data/{PartyDb.kt, PartyStore.kt}` (Room)
- `settings/SettingsRepo.kt` (DataStore: pin, advertisedOverride, perfHud, rounds)
- `AssetStaticFiles.kt`
- The Gradle `SyncControllerAssets` task wired via `androidComponents` `addGeneratedSourceDirectory`

**Tests (Robolectric):**
- A PartyStore save/load round trip.
- Restore mid-round is paused.
- `advertisedUrl()` prefers the override and never returns `0.0.0.0` (**Review Focus 4**).

### Task 9: TV UI
**Files:**
- `ui/theme/*`, `ui/home/HomeScreen.kt`, `ui/lobby/{LobbyScreen.kt, Qr.kt}`, `ui/picker/GamePicker.kt`
- `ui/bluff/{WriteStage, PickStage, RevealStage, ScoresStage, Podium}.kt`
- `ui/host/HostOverlay.kt`, `ui/settings/SettingsScreen.kt`
- `MainActivity.kt` (FLAG_KEEP_SCREEN_ON, Back confirmation)

**Tests:**
- A Robolectric Compose test: Home shows exactly Play Games / Quick Play / Settings.
- A Robolectric Compose test: the lobby renders the QR and room code from a fake `TvState`.
- A manual emulator run, recorded in CHECKLIST.

### Task 10: Performance: JankStats HUD, benchmark tour, baseline profile
**Files:**
- `perf/{PerfMonitor.kt, PerfHud.kt, BenchmarkTour.kt}` (16 in-process bots through PartyHost; logs `PARTYOS_PERF frames= onTime=`)
- `tv/baselineprofile/` module
- `am start --ez tour true` handling

**Tests:**
- The tour on the emulator prints `PARTYOS_PERF`. The number is recorded as informational.
- `:app:generateBaselineProfile` runs on the emulator. If it fails, that goes in Known issues.

### Task 11: Emulator tooling, CI, docs
**Files:**
- `tv/tools/lan-proxy.mjs` (TCP pipe `<lan-ip>:8080`→`127.0.0.1:8080`)
- `scripts/emulator-party.sh`
- `.github/workflows/party-os.yml` (controller → JVM tests → assembleDebug artifact → emulator smoke on push to main/PR → signed release if secrets exist)
- `tv/app/debug.keystore`
- `docs/party-os/{SIDELOAD.md, EMULATOR.md, CHECKLIST.md}`

**Tests:**
- The emulator smoke check: launch, then `curl /healthz` via `adb forward`.
- A real phone on the Mac's Wi-Fi joins via the proxy (manual, recorded).
- Workflow YAML is validated with `actionlint` if available.
