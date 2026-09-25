# PARTY OS: living checklist

This covers sub-project 1: the core platform and Bluff Battle. "Tested" means a command actually ran it; the command is named in each row.

## Done and tested

| Feature | Evidence |
|---|---|
| **Joining** | |
| Join with room code, name and avatar; errors WRONG_ROOM, NAME_TAKEN, BAD_NAME, FULL; 16 players + 16 spectators | `:engine:test` (PartyEngineTest), `:server:test` (ServerTest) |
| Tokens hashed at rest; resume after reconnect or refresh; kicked token revoked | PartyEngineTest, ServerTest, Playwright "refreshed phone" |
| **Game runtime** | |
| Tutorial gating, deadlines, pause/resume, skip, end, stale-round and duplicate-action handling | RuntimeTest (20 tests) |
| Early phase end on all connected players acting; auto-pause below 2 connected | RuntimeTest, SimulatedPartyTest (last survivor) |
| Snapshot and restore: resumes paused, scores awarded once | RuntimeTest, PartyStoreTest (Robolectric + Room) |
| **Bluff Battle** | |
| Too-true rejection, merged fakes, own answer hidden, decoy fill, 1000/500 scoring, final round ×2, late join, no repeats, reveal order | BluffBattleTest (15 tests) |
| 70-question original house pack, validated at load | `corePackLoadsWithAtLeastSixtyQuestions` |
| **Server and network** | |
| Ktor server: LAN-only guard, 64 KB frame cap, 20 actions/s, PIN lockout (5 tries / 60 s), host-only commands, BAD_MESSAGE handling | ServerTest, UnitsTest |
| 16 bots over real sockets with 20% random drop and rejoin: all converge, and scores match a recomputation from the reveals | SimulatedPartyTest (passed 4 consecutive runs) |
| Protocol fixtures shared by Kotlin and TypeScript, checked in both directions | ProtocolFixturesTest + `controller` vitest |
| **Phone controller** | |
| Outbox resend and drop-on-reject, backoff capped at 5 s, cookie fallback, heartbeat reconnect | `controller` vitest (16 tests) |
| Three phones and a host play a full Bluff Battle round in real browsers | `npx playwright test` |
| **TV app** | |
| TV home shows only working tiles; lobby shows QR, room code and players | ScreensTest (Robolectric Compose) |
| QR/join URL: override wins, never 0.0.0.0, first usable IPv4 | AdvertisedUrlTest |
| Frame tally math for the perf HUD and benchmark tour | FrameTallyTest |
| Debug APK builds, with the controller bundled at `assets/controller` | `./gradlew :app:assembleDebug` + `unzip -l` |
| **Android TV emulator** (Google TV API 34, release APK) | |
| Home, lobby with QR, tutorial, write, pick, reveal, scores and round 2, played by 3 browser phones through the Mac's LAN proxy | Screenshots from `adb exec-out screencap` (session log) |
| App killed mid-round, then restored in the same round, paused, with frozen time | Reinstall during round 2, then screenshot |
| Back at Home asks "Stop hosting?", and "Stop and exit" stops the service and server; relaunch restores them | `dumpsys activity services` + `curl /healthz` |
| Launch no longer crashes (JankStats before DecorView) | `MainActivityLaunchTest` + emulator |
| 16-player pick grid and reveal fit on screen | `BluffStageLayoutTest` |
| **Hardening from the whole-branch review** | |
| Host commands deduped; ids capped; bodies capped without Content-Length; atomic PIN lockout; PIN change signs out co-hosts | `HardeningTest` |
| Unreadable or orphaned saved game drops the game, not the party; quoted truths caught; emoji fakes stay distinct | `RestoreHardeningTest` |
| Spectator takes a free seat between games; host sees start errors | `RoleSwitchTest`, Playwright (4 tests) |
| **Tooling** | |
| LAN proxy forwards HTTP and WebSocket | manual run against devserver (`/healthz`, `/j/ABCD`, WebSocket bye) |
| CI workflow syntax | `actionlint` |

## Built but not yet run on the real TV (QM6K)

- **Everything on real hardware.** The whole app has run only on the emulator so far.
- **Real phones and remote:** actual phone cameras scanning the QR on your Wi-Fi (tested only with headless browsers through the proxy), and the TCL remote's own buttons.
- **Networking and background running:** Wi-Fi and wake locks under real standby, and live network-change updates to the QR code.
- **60 fps pass mark:** two benchmark tours on the emulator with the release APK. Both are informational only; the ≥95% pass mark must be measured on the QM6K.

  | Run | Frames | On time (display deadline) | Janky |
  |---|---|---|---|
  | Right after install, cold (ART not yet optimised, shaders being compiled) | 2,114 | 65.4% | 0.3% |
  | Warmed up | 3,873 | 98.9% | 0.0% |
- **CI workflow:** it passes lint but hasn't run on GitHub yet. The branch isn't pushed, and the emulator-smoke job runs only on `main` and PRs.
- **Signed release APK:** needs `RELEASE_KEYSTORE_*` secrets.

## Known issues and limits

- **First-launch stutter:** a cold run on the emulator was 65% on time, against 99% once warmed up. A Baseline Profile should close most of that gap.
- **Mac disk space:** only about 4.5 GB is free while the emulator is installed.
- **Reveal replays after restore:** the reveal animation keeps advancing under the pause overlay, and restarts after a restore. Scores are unaffected.
- **Auto-pause needs the host to resume:** "Waiting for players" doesn't resume by itself when players come back.
- **No Baseline Profile yet:** generating one needs a running device. `profileinstaller` is already included.
- **Release builds aren't minified:** R8 keep rules for Ktor and kotlinx.serialization haven't been written, so the APK is larger than it needs to be.
- **Co-host sessions don't survive a TV app restart:** they live in memory, so co-host phones re-enter the PIN.
- **Guest and isolated Wi-Fi:** the app can't work around networks that block phone-to-TV traffic. The lobby shows a hint.
