# HoopDreams — Party Shot Tracker (v1) Design

**Date:** 2026-09-24
**Status:** Design approved in chat. Planning-time refinements are folded in. The plan is `docs/superpowers/plans/2026-09-24-shot-tracker.md`.
**Builds on:** `234b6d3` (Steven's hat-draw dashboard: FastAPI + SQLAlchemy + SQLite backend, React 19 + TS 6 + Vite 8 frontend)

## 1. Goal

A MacBook runs the HoopDreams server and drives a TV over HDMI (the "jumbotron").
Guests join from their phones by scanning a QR code, then log shots of alcohol for
themselves or anyone else. Every shot is reported on the TV immediately with
arcade-basketball hype.

The existing hat (which picks tonight's game: Trivia, Pictionary, …) stays as is.
The shot tracker adds a **party core** (players, teams, shot ledger, live updates,
TV stage) that later games plug into.

### Success criteria

- A logged shot appears on the TV in under 250 ms on the local Wi-Fi.
- No shot is lost or double-counted when phones sleep, drop Wi-Fi, or the server restarts.
- A new guest goes from scanning the QR code to logging a shot in under 30 seconds.
- A new game is added as one backend module plus one frontend folder, without editing the party core.
- Runs fully offline on the Mac (the new screens bundle their fonts and sounds). An optional public tunnel is available.
- Steven's hat page and its API keep working unchanged.

## 2. Decisions (from brainstorming)

| Topic | Decision |
|---|---|
| Connectivity | Same Wi-Fi first (LAN URL + QR). `--tunnel` adds a Cloudflare quick tunnel as a backup for guests on cellular. |
| Who logs | Anyone can log a shot for themselves or any other player, including several at once. |
| v1 features | TV hype moments, shot-cam photos **and videos**, TV-called challenges, teams, live leaderboard. |
| Look | Arcade hoops jumbotron for the new `/tv`, `/play` and `/host` screens. Steven's pastel hat page is untouched. |
| Backend | **Extend Steven's FastAPI app.** Add `python-socketio` (ASGI) for realtime and new SQLAlchemy tables in the same `hoopdreams.db`. Uploads via `python-multipart`; ffmpeg via asyncio subprocess. |
| Frontend | Stay in `frontend/` (React 19, TS 6, Vite 8, oxlint, plain CSS). Add `react-router`, `socket.io-client`, `motion`, `qrcode`, `zzfx`, and `@fontsource` fonts. |
| Types | Pydantic models are the single source of truth for the socket contract. They are exported to JSON Schema, and TS types are generated from that. A test fails if the committed schema is stale. |
| Rejected | Switching to Node/TS (would replace Steven's chosen stack). Colyseus. Hosted realtime (depends on venue internet). |

## 3. Architecture

### 3.1 Surfaces

In party mode, a single uvicorn process on port **8000** serves everything:

- **`/`** — Steven's hat page (unchanged).
- **`/tv`** — the jumbotron, opened full-screen in Chrome on the Mac and shown on the TV.
- **`/play`** — the phone controller (join → play). This is what the QR code opens.
- **`/host`** — the host panel, gated by a 4-digit PIN printed in the Mac terminal at startup (overridable with `HOOP_HOST_PIN`).
- **`/api/*`** — Steven's hat endpoints plus new party REST endpoints (media upload, health).
- **`/socket.io/*`** — realtime.
- **`/media/*`** — uploaded and processed photos and videos.

In development, Vite (`npm run dev -- --host`) proxies `/api`, `/socket.io` (ws) and `/media` to `:8000`.
The new code always uses relative URLs. Steven's `api.ts` and `.env` are left alone.

### 3.2 Layout (new files marked +)

```
backend/
  app/
    main.py                (edited) mounts Socket.IO, includes party router, serves frontend/dist in party mode
    models.py              (edited) new tables appended; Steven's Game/Draw untouched
    database.py            (edited) SQLite WAL + foreign keys pragma on connect
  + app/party/
      contract.py          pydantic models: every socket event payload, PublicState, Moment, Prompt
      config.py            default settings
      state.py             PartyService: in-memory authoritative state for the current night, write-through to SQLite
      ledger.py            pure functions: standings, team totals, streaks, milestones, lead change
      realtime.py          python-socketio AsyncServer, event handlers, auth, rate limits, broadcast
      media.py             upload route, ffmpeg job queue, file serving
      net.py               LAN IP discovery, join URLs
      games/
        base.py            GamePlugin protocol + GameCtx
        challenges.py      first plugin
        __init__.py        registry
  + scripts/
      party.py             build + serve + caffeinate + Chrome kiosk + watchdog + optional tunnel
      demo.py              8 simulated guests (python-socketio client)
      export_schema.py     writes frontend/src/party/contract.schema.json
  + tests/                 pytest (unit + integration)
  requirements.txt         (edited) + python-socketio, python-multipart, qrcode
  + requirements-dev.txt   pytest, pytest-asyncio, httpx, aiohttp
frontend/src/
  main.tsx                 (edited) wraps in a router: "/" → App (unchanged), /tv, /play, /host
  + party/                 socket client, outbox, state store, contract.schema.json, contract.gen.ts (generated)
  + arcade/                shared arcade UI: tokens.css, Scoreboard, Avatar, LedDigits (SVG 7-segment), sound.ts (zzfx), voice.ts
  + tv/                    TvPage, Leaderboard, Feed, Reel, JoinPanel, stage/ (queue reducer + overlays)
  + play/                  PlayPage, Join flow, ShotButton, RosterGrid, ShotCam, toasts
  + host/                  HostPage and panels
  + games/challenges/      Tv.tsx, Phone.tsx
  + games/index.ts         client registry
```

Naming note: Steven's `Game` model is a *hat entry* (a game to play in person). Our playable modules are **game plugins**,
kept in `party/games/`. Later, a hat entry can gain a `plugin_id` column so drawing "Trivia" launches the Trivia plugin (out of scope for v1).

### 3.3 Party core

- **Nights** — a night is one party. "New night" archives the current night (all rows keep `night_id`); nothing is deleted.
- **Players** — name, avatar (emoji or selfie), team.
  - On join the server issues a random 128-bit token, and the phone keeps it in `localStorage`.
  - `player:resume` with the token restores the identity after a refresh, screen lock or Wi-Fi drop.
  - The database stores only the SHA-256 of the token.
- **Teams** — 2–4 teams, host-editable. Defaults: HOME (#FF7A1A) and AWAY (#2D8CFF).
- **Presence** — live socket IDs per player. A player counts as connected if they have a socket now, or had one within the last 2 minutes.
- **Ledger** — append-only shot rows. Standings, team totals, streaks and milestones are derived by pure functions in `ledger.py`. Voiding sets `voided_at`.
- **Stage** — the server emits discrete *moments*. The TV queues and renders them.
- **Media** — upload, ffmpeg normalisation, posters, highlight reel.

`PartyService` loads the current night from SQLite at startup and holds it in memory.
Every mutation commits to SQLite first, then updates memory, then broadcasts.
Handlers run on the asyncio loop; the SQLite writes are sub-millisecond, and WAL mode is enabled.
**uvicorn must run with exactly one worker**, because state is in-process. `party.py` enforces this.

### 3.4 Game plugin interface (`party/games/base.py`)

```python
class GamePlugin(Protocol):
    id: str
    name: str
    actions: dict[str, type[BaseModel]]          # payload schema per action, validated by the core
    def __init__(self, ctx: GameCtx) -> None: ...
    def public_state(self) -> BaseModel: ...     # included in PublicState.games[id]
    async def on_action(self, caller: Caller, action: str, payload: BaseModel) -> None: ...
    async def tick(self, now_ms: int) -> None: ...   # called every second
```

`GameCtx` exposes:
- `players()` (ranked, each with a connected flag) and `teams()`
- `await award_shots(drinker_ids, reason=, logged_by_id=None)`
- `await push_moment(kind, data)`
- `load_settings()` and `save_settings(data)`
- `now_ms()`
- `rng` (a seedable `random.Random`)
- `record_event(type, data)` (writes to `game_events`)
- `await broadcast()`

Phones derive their prompts (the NOT ME offer, the challenge shot clock) from the public state, so there is no separate prompt channel. That keeps prompts correct across reconnects.

A `Caller` is either `PlayerCaller(player_id)` or `HostCaller`.

Client side, `frontend/src/games/<id>/Tv.tsx` and `Phone.tsx` export components that receive
`{ state, party, me?, act(action, payload) }` and render `null` while idle.
Every registered plugin is always mounted, and the host panel can enable or disable each one.

### 3.5 Realtime model

- Server-authoritative. Clients send *intents*; every intent carries a client-generated `requestId` (UUID) and is answered through a Socket.IO ack of the form `{ ok: true, ... } | { ok: false, error }`.
- After each state change the server broadcasts the full **`PublicState`**. That's a few KB for up to 60 players, so no diffs are needed.
- Discrete **moments** are emitted separately, to the TV room and to all phones.
- A connecting client receives `state` immediately.
- Payloads are validated with pydantic. Invalid payloads get an error ack and are logged.
- The contract is written in `contract.py`. `scripts/export_schema.py` writes `contract.schema.json`, and `npm run gen:types` (`json-schema-to-typescript`) turns it into `contract.gen.ts`, which also runs before `dev` and `build`. A pytest asserts the committed JSON schema matches the models.

### 3.6 Socket events

Client → server (all acked):

| Event | Payload | Who |
|---|---|---|
| `player:join` | `{ requestId, name (1–20 chars), avatar: {kind:'emoji', value} \| {kind:'photo', mediaId}, teamId }` → `{ playerId, token }` | phone |
| `player:resume` | `{ token }` → `{ playerId }` | phone |
| `player:update` | `{ name?, avatar?, teamId? }` | phone |
| `shot:log` | `{ requestId, drinkerIds: string[1..60] }` → `{ shotIds }` | phone |
| `shot:undo` | `{ requestId }` (logger only, within 10 s) | phone |
| `shot:reject` | `{ shotId }` ("NOT ME"; drinker only, within 60 s) | phone |
| `game:action` | `{ requestId, gameId, action, payload }` | phone / host |
| `host:auth` | `{ pin }` → `{ ok }` (5 attempts/min/socket) | host |
| `host:action` | `{ requestId, action, payload }`: team CRUD, player rename/move/remove/merge, shot add/void, settings, wifi, new night | host |

Server → client: `state` (`PublicState`, to everyone), `moment` (`Moment`, to everyone) and `host_state` (`HostState`, to the `host` room only).

### 3.7 Data model (new tables in `hoopdreams.db`)

All new timestamps are **integer epoch milliseconds (UTC)**, e.g. `1790000000000`. IDs are UUID4 strings.

| Table | Columns |
|---|---|
| `nights` | `id, name, started_at, ended_at NULL` |
| `teams` | `id, night_id, name, color, sort, removed_at NULL` |
| `players` | `id, night_id, name, avatar_kind ('emoji'\|'photo'), avatar_value, team_id, token_hash, created_at, removed_at NULL` |
| `shots` | `id, night_id, drinker_id, logged_by_id NULL, request_id, source ('manual'\|'host'\|'game:<id>'), reason, created_at, voided_at NULL, void_reason NULL ('undo'\|'not_me'\|'host')` — **unique (request_id, drinker_id)** |
| `media` | `id, night_id, player_id, shot_id NULL, purpose ('shot'\|'avatar'), kind ('photo'\|'video'), status ('processing'\|'ready'\|'failed'), path, poster_path NULL, duration_ms NULL, created_at` |
| `settings` | `night_id, key, value_json` — primary key (night_id, key) |
| `game_events` | `id, night_id, game_id, type, data_json, created_at` |

Tables are created by Steven's existing `Base.metadata.create_all`. Everything here is additive, so no migration tool is needed for v1.
Media files live in `backend/media/` (gitignored).

### 3.8 Scripts

- **Dev:** run the backend as in Steven's README, plus `npm run dev -- --host` in `frontend/`.
- **`backend/venv/bin/python backend/scripts/party.py [--tunnel] [--demo]`:**
  1. `npm run build` in `frontend/`.
  2. Starts `uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers 1` with `HOOP_PARTY=1` (serves `frontend/dist`) under a watchdog that restarts it on crash.
  3. Runs `caffeinate -dimsu -w <pid>`.
  4. Prints the LAN URL, host PIN and a terminal QR code.
  5. Opens `/tv` in Chrome using a dedicated profile with `--kiosk`.
  6. `--tunnel` spawns `cloudflared tunnel --url http://localhost:8000`, parses the `trycloudflare.com` URL, and posts it to the server so the TV shows it.
  7. `--demo` starts `demo.py`.

## 4. Behaviour

### 4.1 Logging a shot

1. **Phone screen:** a big **I TOOK ONE** button and an avatar grid of every player. Tapping faces selects them, and **LOG +1 (n)** logs one shot for each selected player.
2. **Optimistic update:** the phone shows +1 immediately, vibrates where supported, locks the button for 2 s, and puts the intent in a persistent outbox (`localStorage`).
3. **Server:** validates the intent, inserts one row per drinker in one transaction, acks, broadcasts `state`, and emits a `shot` moment. The moment contains:
   - the drinkers with their new counts and streak status
   - `loggedBy`
   - any milestones and lead change
4. **Outbox:** the entry is removed on ack. After a reconnect, the outbox resends with the same `requestId`. The unique index makes the server return the original shot IDs.
5. **NOT ME:** if the logger isn't the drinker, the drinker's phone shows a **NOT ME** button for 60 s. The phone works this out from the public feed. Rejecting voids the shot and emits a `waved-off` moment.
6. **UNDO:** the logger sees an **UNDO** toast for 10 s.
7. **Rate limit:** 20 `shot:log` per player per minute, then an error ack and a friendly toast.

### 4.2 Shot-cam (photo / video)

- The 📸 button is `<input type="file" accept="image/*,video/*" capture>`. It opens the native camera and works over plain HTTP. (In-page `getUserMedia` would need HTTPS.)
- The shot is logged **first**. The media then attaches through `POST /api/party/media` (multipart: token, shotId?, purpose `shot|avatar`), so the report is never delayed.
- **Photos** are downscaled on the phone to a 1600 px-long-edge JPEG (via canvas) before upload and stored as is.
- **Videos** have a 200 MB limit. An asyncio ffmpeg queue (concurrency 1, to keep the fanless M3 Air cool) does the following:
  - trims to the first 15 s
  - scales to 720p
  - encodes with `h264_videotoolbox` (falling back to `libx264 -preset veryfast`) plus AAC
  - adds `+faststart`
  - generates a poster JPEG
- When media is `ready`, the server emits a `replay` moment. The TV plays it as **INSTANT REPLAY** (a video with sound for up to 15 s, or a photo for 5 s) and adds it to the highlight reel.
- On failure the media is marked `failed`, and the TV shows the poster if one exists.
- The avatar selfie at join uses the same path (`purpose=avatar`) and is cropped to a circle on the phone.

### 4.3 TV stage and hype

Moments: `shot`, `milestone`, `lead-change`, `waved-off`, `replay`, plus plugin moments (`challenge-spin`, `challenge-result`, `challenge-violation`).

- **Queue:** a FIFO queue, implemented as a pure reducer in `tv/stage/` so it can be unit tested. A shot takeover lasts ~3 s.
- **Combos:** shot moments arriving while a shot takeover is showing merge into it. The combo window is 4 s, extended with each merge, up to 8 s. Headlines:
  - 2 → "DOUBLE!"
  - same team, 3 or more → "TEAM SHOT ×N!"
  - mixed teams, 3 or more → "COMBO ×N!"
- **Takeover content:** avatar, name, new count, team colour, a zzfx sound, and an announcer line spoken by macOS `speechSynthesis` (local voice; lines drawn from a pool with the name filled in).
- "logged by X" is shown when the logger isn't the drinker.
- **Streaks** are computed server-side at each shot and re-evaluated every 30 s. The thresholds are adjustable by the host.
  - HEATING UP: ≥ 2 shots within 20 min.
  - ON FIRE: ≥ 3 shots within 30 min. The player's leaderboard row gets animated flames. The fire goes out after 30 min with no shot.
- **Milestones:**
  - "FIRST BUCKET" for the night's first shot
  - Every 5th shot for a player
  - Party totals of 25 and 50, then every 100 (100 is "CENTURY CLUB")
  - `lead-change` when the team in sole first place changes
- **Highlight reel:** the side panel cycles through the night's ready photos and clips, muted, 6 s per item.

### 4.4 Challenges (first plugin)

- **Triggers:** automatically every `challenge_interval_min` (default 12; 0 = off), and on demand with **SPIN NOW** in the host panel. The TV shows a "NEXT CHALLENGE" countdown.
- **The wheel** (weighted, host-editable):
  - Random player
  - Random pair
  - Whole team
  - Everyone ("ALL-STAR SHOT")
  - The leader ("DEFEND THE CROWN")
  - Last-place team ("COMEBACK SHOT")
  - Dare or Drink: a random player plus a dare from the editable list
- **Eligibility:** only connected players can be picked. A slice with nobody eligible is skipped, and the wheel lands on the next one.
- **Flow:**
  1. The TV plays the spin (~4 s), then reveals the targets.
  2. Each target's phone shows a full-screen **24-second shot clock** with **DONE** (and **DID THE DARE** for dares), derived from the plugin's public state.
  3. DONE calls `award_shots(source='game:challenges', logged_by_id=self)`, and the TV shows "BUCKET!".
  4. At expiry, the TV shows **SHOT CLOCK VIOLATION** with the names of anyone who didn't respond, plus a buzzer. No shot is logged.
- One challenge runs at a time, and the auto timer pauses while it's active.
- Challenges are recorded in `game_events`.

### 4.5 Teams

- **Join flow:** name → avatar (emoji grid or selfie) → team (big coloured buttons showing team sizes).
- **Host:** creates, renames and recolours teams (2–4) and moves players.
- **TV:** the scoreboard bar shows team totals in LED digits.

## 5. Screens

### 5.1 TV (1920×1080 target; scales to any 16:9)

- **Scoreboard bar:** logo, team totals (LED digits), party clock, and sound/voice indicators.
- **Leaderboard (left):** rank, flame state, avatar, name, count and team colour. Top 10, auto-paging beyond that.
- **Right column:** highlight reel and live feed ("Sam → Jess +1 · 0:12").
- **Join panel:**
  - a Wi-Fi QR code (`WIFI:T:WPA;S:<ssid>;P:<pass>;;`), only if the host entered Wi-Fi info
  - a SCAN TO PLAY QR code for `http://<lan-ip>:8000/play`
  - with a tunnel running, a "not on Wi-Fi?" QR code
- **Next challenge** countdown in shot-clock style.
- **Overlays:** shot takeover, milestone, lead change, waved-off, instant replay, challenge wheel and shot clock.
- **Style:** dark arena gradient with hardwood accents, Bungee headings, Press Start 2P accents, SVG seven-segment digits, team colours and scanlines. Tokens are scoped under `[data-surface="arcade"]` so Steven's `:root` pastel tokens are unaffected.
- **TIP OFF:** browsers only allow speech after a click, so the TV shows a **TIP OFF** button once per page load. It unlocks sound and speech and plays a welcome line.

### 5.2 Phone (`/play`; portrait, one-handed)

- **Join:** name → avatar → team.
- **Play:**
  - my count, rank and streak badge
  - **I TOOK ONE**
  - the avatar grid (multi-select) with **LOG +1 (n)**
  - 📸
  - a mini leaderboard
- **Overlays:** the challenge shot clock, and NOT ME and UNDO toasts.
- **Connection:** a "Reconnecting…" banner while offline. Pending outbox items show "sending…".
- **Sizing:** touch targets ≥ 56 px. The primary button fills about 40% of the screen height.

### 5.3 Host (`/host`; PIN)

- **Teams**
- **Players:** rename, move, remove, merge duplicates (re-points shots)
- **Shot log:** newest first, void or add
- **Challenges:** enable, interval, weights, dares, SPIN NOW
- **Hype thresholds**
- **Sound and voice switches**
- **Wi-Fi info**
- **Tunnel status**
- **New night**

## 6. Error handling

| Failure | Behaviour |
|---|---|
| Phone disconnects or sleeps | Socket.IO auto-reconnect. "Reconnecting…" banner. The outbox resends with the original `requestId`, then `player:resume`, then full `state`. |
| Duplicate intent | Unique `(request_id, drinker_id)`. The server returns the original shot IDs. |
| Server crash | The watchdog restarts uvicorn within about 1 s. State reloads from SQLite, and clients reconnect and resync. |
| TV tab reloads | The fresh socket gets full `state` on connect. Click TIP OFF again for sound. |
| LAN IP changes | Re-checked every 30 s. Join URLs are in `PublicState`, so the QR codes update. |
| Tunnel dies | `party.py` respawns cloudflared and posts the new URL. |
| Upload fails | Retry button on the phone. The shot is unaffected. |
| Transcode fails | Media marked `failed`. Poster shown if available. |
| Invalid payload, bad token or bad PIN | Error ack, logged to the console. PIN attempts are rate-limited. |
| Spam | 20 `shot:log` per player per minute. |

## 7. Testing

- **Backend unit (pytest):**
  - `ledger.py`: standings, voids, streak windows, milestones, lead change and ties
  - challenge logic: weighted pick with a seeded RNG, eligibility skip, shot clock resolution
  - contract schema freshness
- **Backend integration (pytest + real uvicorn on a random port + `python-socketio` AsyncClient):**
  - join and resume
  - log → `state` + `moment`
  - retry idempotency
  - undo and NOT ME windows
  - rate limit
  - host auth and actions
  - challenge DONE awards shots
  - media upload → `replay` (fixture JPEG and a tiny MP4 generated by ffmpeg)
- **Frontend unit (Vitest):** stage reducer (FIFO, combo merge, headlines), outbox (persist, resend, ack removal), streak/rank display helpers.
- **E2E (Playwright):** `/tv` plus three iPhone-viewport `/play` contexts against the party build.
  - join
  - self log and other log (TV takeover visible)
  - NOT ME
  - host SPIN NOW → DONE
  - photo upload → instant replay
  - `/` hat page still loads and draws
- **Rehearsal:** `party.py --demo` runs 8 simulated guests, for visual QA and as a soak test.

## 8. Defaults (`party/config.py`)

```python
challenge_interval_min=12, shot_clock_sec=24,
heating_up=dict(count=2, window_min=20), on_fire=dict(count=3, window_min=30, cool_min=30),
combo_window_ms=4000, combo_max_ms=8000, undo_window_sec=10, not_me_window_sec=60,
reel_item_sec=6, shot_log_per_min=20, max_upload_mb=200, max_video_sec=15,
player_milestone_every=5, party_milestones=[25, 50], party_milestone_every=100,
voice=True, sound=True, port=8000
```

## 9. Out of scope for v1

- Accounts or cloud hosting
- In-page camera (needs HTTPS)
- A history screen for past nights (the data is kept)
- Native apps
- An end-of-night recap
- Linking hat entries to playable plugins
- Restyling Steven's hat page
- Additional plugins beyond challenges
