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
| **Tooling** | |
| LAN proxy forwards HTTP and WebSocket | manual run against devserver (`/healthz`, `/j/ABCD`, WebSocket bye) |
| CI workflow syntax | `actionlint` |

## Built but not yet run on real hardware or an emulator

- **TV app not yet run:** home, lobby, game stages, host overlay and settings. The emulator could not boot because of disk space.
- **Networking and background running:** the foreground service keeping the server alive after Home, Wi-Fi and wake locks, and live network-change updates to the QR code.
- **Performance measurements:** the benchmark tour and FPS HUD have produced no numbers yet, on the emulator or the QM6K.
- **CI workflow:** it passes lint but hasn't run on GitHub yet. The branch isn't pushed, and the emulator-smoke job runs only on `main` and PRs.
- **Signed release APK:** needs `RELEASE_KEYSTORE_*` secrets.

## Known issues and limits

- **Mac disk space:** the Android TV emulator needs about 7.5 GB free, and the Mac has about 3.9 GB. See the final report for the options.
- **No Baseline Profile yet:** generating one needs a running device. `profileinstaller` is already included.
- **Release builds aren't minified:** R8 keep rules for Ktor and kotlinx.serialization haven't been written, so the APK is larger than it needs to be.
- **Co-host sessions don't survive a TV app restart:** they live in memory, so co-host phones re-enter the PIN.
- **Guest and isolated Wi-Fi:** the app can't work around networks that block phone-to-TV traffic. The lobby shows a hint.
