# HoopDreams Shot Tracker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a live party shot tracker to HoopDreams. Guests' phones log shots; a TV "jumbotron" driven by the MacBook reacts instantly with arcade-basketball hype, shot-cam replays, team scores and TV-called challenges.

**Architecture:**
- Steven's FastAPI app gains a party core in `backend/app/party/`: an in-memory authoritative `PartyService`, written through to SQLite, with Socket.IO (`python-socketio`) mounted inside the same FastAPI `app`.
- The React app in `frontend/` gains three routes (`/tv`, `/play`, `/host`) with an arcade look, a socket client with an offline outbox, and a TV "stage" that queues hype moments.
- Game plugins (the first is Challenges) implement a small interface on both sides.

**Tech Stack:**
- Python 3.14: FastAPI 0.141, Starlette 1.7, SQLAlchemy 2.1, pydantic 2.13, python-socketio 5.17, python-multipart, qrcode, pytest 9 + pytest-asyncio 1.4, httpx, aiohttp.
- Frontend: React 19.2, TypeScript 6, Vite 8, react-router 8, socket.io-client 4.8, motion 13, qrcode 1.5, @fontsource (Bungee, Press Start 2P), json-schema-to-typescript 16, Vitest 5, Playwright 1.63.
- Tools: ffmpeg 8 (`h264_videotoolbox`), cloudflared.

**Spec:** `docs/superpowers/specs/2026-09-24-shot-tracker-design.md`

## Global Constraints

- Steven's hat feature must keep working unchanged: `/`, `GET /api/games`, `POST /api/draw`, `GET /api/history`, `GET /api/health`, and `frontend/src/{App.tsx,api.ts,components/*}`. Do not edit those files except where a task says so.
- **Port.** One uvicorn process on port **8000** with **exactly one worker**; the party state lives in-process.
- **Wire format.** Socket payloads are camelCase JSON. The Python models are snake_case `CamelModel`s (`backend/app/party/camel.py`), serialised with `.wire()`.
- **Socket acks.** Every socket intent is acked `{ "ok": true, ...data }` or `{ "ok": false, "error": "<human message>" }`.
- **Timestamps and IDs.** All new timestamps are integer epoch milliseconds (UTC). All new IDs are UUID4 strings.
- **Phone IDs.** Phones generate IDs with `crypto.getRandomValues` (`frontend/src/party/ids.ts`). `crypto.randomUUID` is unavailable over plain-HTTP LAN.
- **Environment variables:**
  - `HOOP_DB`: SQLite path (default `backend/hoopdreams.db`).
  - `HOOP_MEDIA_DIR`: default `backend/media`.
  - `HOOP_HOST_PIN`: default is a random 4-digit PIN, printed at startup.
  - `HOOP_PARTY=1`: serve `frontend/dist`.
  - `HOOP_PUBLIC_PORT`: the port shown in the join URL; default `8000`.
  - `HOOP_DIST`: default `frontend/dist`.
- **Frontend conventions:** new files use double quotes and plain CSS. The arcade tokens are scoped under `body[data-surface="arcade"]`. `tsconfig.app.json` has `erasableSyntaxOnly`, so there are **no constructor parameter properties, enums or namespaces**. `verbatimModuleSyntax` means type-only imports use `import type`. Code must pass `noUnusedLocals` and `noUnusedParameters`.
- **Offline.** The new screens load no CDN assets: fonts come from `@fontsource`, and sounds are synthesised with Web Audio.
- **Defaults** (`backend/app/party/config.py`):
  - Streaks: heating up at 2 shots within 20 min; on fire at 3 within 30 min, cooling after 30 min.
  - TV timings: combo 4000 ms (max 8000); reel item 6 s.
  - Phone windows: undo 10 s; NOT ME 60 s.
  - Limits: 20 `shot:log` per player per minute; uploads up to 200 MB; videos trimmed to 15 s.
  - Milestones: every 5th shot per player; party totals 25 and 50, then every 100.
  - Challenges: every 12 min, 24-second shot clock.
- **Commands.** Backend commands run from `backend/` with `venv/bin/python -m pytest`. Frontend commands run from `frontend/`.
- **Git.** Work on branch `shot-tracker`. Commit after every task. Never push unless the user asks.

## File Structure

```
backend/
  requirements.txt                 + python-socketio, python-multipart, qrcode
  requirements-dev.txt             NEW pytest, pytest-asyncio, httpx, aiohttp
  pytest.ini                       NEW
  app/
    database.py                    HOOP_DB env, WAL + foreign keys
    models.py                      + Night, Team, Player, Shot, Media, Setting, GameEvent
    main.py                        lifespan=party_lifespan; mount_party(app) as the last line
    party/
      camel.py                     CamelModel base (snake_case <-> camelCase)
      config.py                    PartySettings + constants
      errors.py                    PartyError
      ledger.py                    pure functions: counts, ranks, team totals, streaks, milestones
      contract.py                  every wire model + WIRE_MODELS
      schema.py                    contract_schema() -> JSON Schema (for TS codegen)
      service.py                   PartyService (players, shots, presence, state, games)
      admin.py                     PartyAdmin (host actions)
      realtime.py                  Socket.IO handlers + SioEmitter
      media.py                     upload route + ffmpeg MediaProcessor
      net.py                       LAN IP + join URL
      web.py                       wiring: sio, service, routes, SPA, lifespan
      games/
        __init__.py                GAME_TYPES registry
        base.py                    GamePlugin protocol, Callers, GameCtx protocol
        challenges.py              Challenges plugin
  scripts/
    export_schema.py               writes frontend/src/party/contract.schema.json
    party.py                       party mode launcher
    demo.py                        8 simulated guests
  tests/                           pytest (unit + integration)
frontend/
  package.json                     deps + scripts (gen:types, test, e2e)
  vite.config.ts                   dev proxy for /api/party, /socket.io, /media
  vitest.config.ts                 NEW
  playwright.config.ts             NEW
  e2e/party.spec.ts                NEW
  src/
    main.tsx                       router: / (hat), /tv, /play, /host
    party/                         contract.schema.json, contract.gen.ts, ids, socket, store, outbox, identity, time
    arcade/                        arcade.css, Surface, Avatar, LedDigits, sound, voice, lines
    play/                          PlayPage, usePlayer, JoinFlow, RosterGrid, Toasts, ShotCam, image, upload, play.css
    tv/                            TvPage, Scoreboard, Leaderboard, Feed, Reel, JoinPanel, tv.css, stage/*
    host/                          HostPage, panels, host.css
    games/                         types.ts, index.ts, challenges/*
```

---

### Task 1: Backend foundation: dependencies, test harness, party tables

**Files:**
- Modify: `backend/requirements.txt`
- Create: `backend/requirements-dev.txt`, `backend/pytest.ini`
- Modify: `backend/app/database.py`
- Modify: `backend/app/models.py` (append only)
- Modify: `.gitignore`
- Create: `backend/app/party/__init__.py`, `backend/app/party/camel.py`, `backend/app/party/config.py`, `backend/app/party/errors.py`
- Test: `backend/tests/conftest.py`, `backend/tests/test_models.py`

**Interfaces:**
- Produces:
  - `app.party.camel.CamelModel`: a pydantic base with a `.wire() -> dict` method (camelCase JSON dict).
  - `app.party.config`:
    - Models `PartySettings`, `StreakRule`, `FireRule`.
    - Constants `DEFAULT_TEAMS`, `MIN_TEAMS`, `MAX_TEAMS`, `PRESENCE_GRACE_MS`, `FEED_SIZE`, `REEL_SIZE`, `MAX_UPLOAD_BYTES`, `MAX_VIDEO_SEC`.
  - `app.party.errors.PartyError`.
  - ORM classes `models.Night`, `Team`, `Player`, `Shot`, `Media`, `Setting`, `GameEvent`.
  - The pytest fixture `db_reset`.

- [ ] **Step 1: Add dependencies and pytest config**

`backend/requirements.txt` (full file):
```
fastapi
uvicorn[standard]
sqlalchemy
pydantic
python-socketio
python-multipart
qrcode
```

`backend/requirements-dev.txt`:
```
-r requirements.txt
pytest
pytest-asyncio
httpx
aiohttp
```

`backend/pytest.ini`:
```ini
[pytest]
testpaths = tests
pythonpath = .
asyncio_mode = auto
asyncio_default_fixture_loop_scope = function
```

Run: `cd backend && python3 -m venv venv && venv/bin/pip install -q -r requirements-dev.txt`
Expected: installs without errors.

- [ ] **Step 2: Extend `.gitignore`**

Append to the repo-root `.gitignore`:
```
# party
__pycache__/
backend/*.db-*
backend/media/
frontend/e2e/.tmp/
frontend/test-results/
frontend/playwright-report/
```

- [ ] **Step 3: Write the failing tests**

`backend/tests/conftest.py`:
```python
"""Test setup. Point the app at throwaway storage *before* anything imports app.database."""
import os
import tempfile

_TMP = tempfile.mkdtemp(prefix="hoop-tests-")
os.environ["HOOP_DB"] = os.path.join(_TMP, "unit.db")
os.environ["HOOP_MEDIA_DIR"] = os.path.join(_TMP, "media")
os.environ["HOOP_HOST_PIN"] = "4242"

import pytest  # noqa: E402

from app import models  # noqa: E402,F401  (registers every table on Base)
from app.database import Base, engine  # noqa: E402


@pytest.fixture
def db_reset():
    Base.metadata.drop_all(engine)
    Base.metadata.create_all(engine)
    yield
```

`backend/tests/test_models.py`:
```python
import pytest
from sqlalchemy import inspect, text
from sqlalchemy.exc import IntegrityError

from app import models
from app.database import SessionLocal, engine


def test_party_tables_are_created_next_to_the_hat_tables(db_reset):
    names = set(inspect(engine).get_table_names())
    assert {"games", "draws", "nights", "teams", "players", "shots", "media", "settings", "game_events"} <= names


def test_sqlite_runs_in_wal_mode(db_reset):
    with engine.connect() as conn:
        assert conn.execute(text("PRAGMA journal_mode")).scalar() == "wal"


def test_a_request_can_only_log_one_shot_per_drinker(db_reset):
    with SessionLocal() as db:
        db.add(models.Night(id="n1", name="Game Night", started_at=1))
        db.flush()
        db.add(models.Team(id="t1", night_id="n1", name="HOME", color="#FF7A1A", sort=0))
        db.flush()
        db.add(models.Player(id="p1", night_id="n1", name="Jess", avatar_kind="emoji", avatar_value="🔥",
                             team_id="t1", token_hash="h1", created_at=1))
        db.flush()
        db.add(models.Shot(id="s1", night_id="n1", drinker_id="p1", request_id="r1", source="manual", created_at=2))
        db.commit()
        db.add(models.Shot(id="s2", night_id="n1", drinker_id="p1", request_id="r1", source="manual", created_at=3))
        with pytest.raises(IntegrityError):
            db.commit()
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `cd backend && venv/bin/python -m pytest tests/test_models.py -q`
Expected: FAIL with `AttributeError: module 'app.models' has no attribute 'Night'` (the first test fails on the table set).

- [ ] **Step 5: Implement database settings, tables and party base modules**

`backend/app/database.py` (full file):
```python
import os
from pathlib import Path

from sqlalchemy import create_engine, event
from sqlalchemy.orm import DeclarativeBase, sessionmaker

DEFAULT_DB_PATH = Path(__file__).resolve().parent.parent / "hoopdreams.db"
DB_PATH = Path(os.environ.get("HOOP_DB", DEFAULT_DB_PATH))
DATABASE_URL = f"sqlite:///{DB_PATH}"

engine = create_engine(DATABASE_URL, connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


@event.listens_for(engine, "connect")
def _sqlite_pragmas(dbapi_connection, _record):
    # WAL keeps TV/phone reads from blocking shot writes; foreign keys are off by default in SQLite.
    cursor = dbapi_connection.cursor()
    cursor.execute("PRAGMA journal_mode=WAL")
    cursor.execute("PRAGMA foreign_keys=ON")
    cursor.close()


class Base(DeclarativeBase):
    pass


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
```

In `backend/app/models.py`:
- Replace the import lines with the following. They add `BigInteger`, `Text` and `UniqueConstraint`.
```python
from datetime import datetime, timezone

from sqlalchemy import BigInteger, DateTime, ForeignKey, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .database import Base
```
- Then append at the end of the file:
```python


# --- Party shot tracker (timestamps are epoch milliseconds, ids are UUID4 strings) ---


class Night(Base):
    __tablename__ = "nights"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    name: Mapped[str] = mapped_column(String(80))
    started_at: Mapped[int] = mapped_column(BigInteger)
    ended_at: Mapped[int | None] = mapped_column(BigInteger, nullable=True)


class Team(Base):
    __tablename__ = "teams"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    night_id: Mapped[str] = mapped_column(ForeignKey("nights.id"), index=True)
    name: Mapped[str] = mapped_column(String(24))
    color: Mapped[str] = mapped_column(String(7))
    sort: Mapped[int] = mapped_column(Integer, default=0)
    removed_at: Mapped[int | None] = mapped_column(BigInteger, nullable=True)


class Player(Base):
    __tablename__ = "players"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    night_id: Mapped[str] = mapped_column(ForeignKey("nights.id"), index=True)
    name: Mapped[str] = mapped_column(String(20))
    avatar_kind: Mapped[str] = mapped_column(String(8))
    avatar_value: Mapped[str] = mapped_column(String(64))
    team_id: Mapped[str] = mapped_column(ForeignKey("teams.id"))
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    created_at: Mapped[int] = mapped_column(BigInteger)
    removed_at: Mapped[int | None] = mapped_column(BigInteger, nullable=True)


class Shot(Base):
    __tablename__ = "shots"
    __table_args__ = (UniqueConstraint("request_id", "drinker_id"),)

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    night_id: Mapped[str] = mapped_column(ForeignKey("nights.id"), index=True)
    drinker_id: Mapped[str] = mapped_column(ForeignKey("players.id"), index=True)
    logged_by_id: Mapped[str | None] = mapped_column(ForeignKey("players.id"), nullable=True)
    request_id: Mapped[str] = mapped_column(String(36))
    source: Mapped[str] = mapped_column(String(32))
    reason: Mapped[str] = mapped_column(String(80), default="")
    created_at: Mapped[int] = mapped_column(BigInteger)
    voided_at: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    void_reason: Mapped[str | None] = mapped_column(String(8), nullable=True)


class Media(Base):
    __tablename__ = "media"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    night_id: Mapped[str] = mapped_column(ForeignKey("nights.id"), index=True)
    player_id: Mapped[str] = mapped_column(ForeignKey("players.id"))
    shot_id: Mapped[str | None] = mapped_column(ForeignKey("shots.id"), nullable=True)
    purpose: Mapped[str] = mapped_column(String(8))
    kind: Mapped[str] = mapped_column(String(8))
    status: Mapped[str] = mapped_column(String(12))
    path: Mapped[str] = mapped_column(String(255), default="")
    poster_path: Mapped[str | None] = mapped_column(String(255), nullable=True)
    duration_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    created_at: Mapped[int] = mapped_column(BigInteger)


class Setting(Base):
    __tablename__ = "settings"

    night_id: Mapped[str] = mapped_column(ForeignKey("nights.id"), primary_key=True)
    key: Mapped[str] = mapped_column(String(40), primary_key=True)
    value_json: Mapped[str] = mapped_column(Text)


class GameEvent(Base):
    __tablename__ = "game_events"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    night_id: Mapped[str] = mapped_column(ForeignKey("nights.id"), index=True)
    game_id: Mapped[str] = mapped_column(String(32))
    type: Mapped[str] = mapped_column(String(32))
    data_json: Mapped[str] = mapped_column(Text)
    created_at: Mapped[int] = mapped_column(BigInteger)
```

`backend/app/party/__init__.py`:
```python
"""Party shot tracker: live players, shots, TV moments and game plugins."""
```

`backend/app/party/camel.py`:
```python
from typing import Any

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    """Wire model: snake_case attributes in Python, camelCase keys on the wire."""

    model_config = ConfigDict(
        alias_generator=to_camel,
        validate_by_name=True,
        validate_by_alias=True,
        serialize_by_alias=True,
        json_schema_serialization_defaults_required=True,
    )

    def wire(self) -> dict[str, Any]:
        return self.model_dump(mode="json", by_alias=True)
```

`backend/app/party/config.py`:
```python
from pydantic import Field

from .camel import CamelModel

DEFAULT_TEAMS: tuple[tuple[str, str], ...] = (("HOME", "#FF7A1A"), ("AWAY", "#2D8CFF"))
MIN_TEAMS = 2
MAX_TEAMS = 4
PRESENCE_GRACE_MS = 120_000
FEED_SIZE = 30
REEL_SIZE = 50
MAX_UPLOAD_BYTES = 200 * 1024 * 1024
MAX_VIDEO_SEC = 15


class StreakRule(CamelModel):
    count: int = Field(ge=1, le=50)
    window_min: int = Field(ge=1, le=240)


class FireRule(StreakRule):
    cool_min: int = Field(ge=1, le=240)


class PartySettings(CamelModel):
    """Host-tunable party behaviour. Stored per night in the settings table under key "party"."""

    heating_up: StreakRule = StreakRule(count=2, window_min=20)
    on_fire: FireRule = FireRule(count=3, window_min=30, cool_min=30)
    combo_window_ms: int = Field(4000, ge=0, le=20_000)
    combo_max_ms: int = Field(8000, ge=1000, le=30_000)
    undo_window_sec: int = Field(10, ge=0, le=120)
    not_me_window_sec: int = Field(60, ge=0, le=600)
    reel_item_sec: int = Field(6, ge=2, le=60)
    shot_log_per_min: int = Field(20, ge=1, le=200)
    player_milestone_every: int = Field(5, ge=0, le=100)
    party_milestones: list[int] = [25, 50]
    party_milestone_every: int = Field(100, ge=0, le=10_000)
    voice: bool = True
    sound: bool = True
```

`backend/app/party/errors.py`:
```python
class PartyError(Exception):
    """A request the party can't honour. The message is shown to the guest as-is."""
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd backend && venv/bin/python -m pytest -q`
Expected: `3 passed`.

- [ ] **Step 7: Commit**

```bash
git add .gitignore backend/requirements.txt backend/requirements-dev.txt backend/pytest.ini backend/app/database.py backend/app/models.py backend/app/party backend/tests
git commit -m "feat(party): add party tables, settings model and test harness"
```

---

### Task 2: Ledger: pure scoring rules

**Files:**
- Create: `backend/app/party/ledger.py`
- Test: `backend/tests/test_ledger.py`

**Interfaces:**
- Consumes: `PartySettings` (Task 1).
- Produces (all pure; times are epoch ms):
  - `@dataclass ShotRec(id, drinker_id, logged_by_id: str | None, request_id, source, created_at: int, voided_at: int | None = None, void_reason: str | None = None)` (mutable)
  - `live_counts(shots: Iterable[ShotRec], player_ids: Iterable[str]) -> dict[str, int]`
  - `team_totals(counts: dict[str, int], team_of: dict[str, str], team_ids: Iterable[str]) -> dict[str, int]`
  - `sole_leader(totals: dict[str, int]) -> str | None`
  - `ranked(counts: dict[str, int], last_at: dict[str, int | None]) -> list[tuple[str, int]]`
  - `streak_status(times: list[int], now: int, settings: PartySettings) -> Literal["heating", "fire"] | None`
  - `party_milestones_crossed(before: int, after: int, settings: PartySettings) -> list[int]`
  - `is_player_milestone(count: int, settings: PartySettings) -> bool`

- [ ] **Step 1: Write the failing tests**

`backend/tests/test_ledger.py`:
```python
from app.party import ledger
from app.party.config import PartySettings
from app.party.ledger import ShotRec

MIN = 60_000
S = PartySettings()


def shot(sid, drinker, at, voided=None):
    return ShotRec(id=sid, drinker_id=drinker, logged_by_id=None, request_id=sid, source="manual",
                   created_at=at, voided_at=voided)


def test_live_counts_skip_voided_shots_and_unknown_players():
    shots = [shot("a", "jess", 1), shot("b", "jess", 2, voided=3), shot("c", "sam", 4), shot("d", "gone", 5)]
    assert ledger.live_counts(shots, ["jess", "sam", "alex"]) == {"jess": 1, "sam": 1, "alex": 0}


def test_team_totals_sum_members_and_include_empty_teams():
    counts = {"jess": 3, "sam": 2, "alex": 1}
    team_of = {"jess": "home", "sam": "away", "alex": "home"}
    assert ledger.team_totals(counts, team_of, ["home", "away", "bench"]) == {"home": 4, "away": 2, "bench": 0}


def test_sole_leader_needs_a_strict_lead_above_zero():
    assert ledger.sole_leader({"home": 4, "away": 2}) == "home"
    assert ledger.sole_leader({"home": 3, "away": 3}) is None
    assert ledger.sole_leader({"home": 0, "away": 0}) is None
    assert ledger.sole_leader({}) is None


def test_ranked_uses_competition_ranking_and_first_to_reach_breaks_ties():
    counts = {"jess": 5, "sam": 3, "alex": 5, "kim": 0}
    last_at = {"jess": 200, "sam": 50, "alex": 100, "kim": None}
    assert ledger.ranked(counts, last_at) == [("alex", 1), ("jess", 1), ("sam", 3), ("kim", 4)]


def test_streak_heating_up_is_two_shots_in_twenty_minutes():
    now = 100 * MIN
    assert ledger.streak_status([now - 25 * MIN, now - 1 * MIN], now, S) is None
    assert ledger.streak_status([now - 19 * MIN, now - 1 * MIN], now, S) == "heating"


def test_streak_on_fire_is_three_in_thirty_minutes_and_cools_after_thirty():
    base = 100 * MIN
    times = [base, base + 10 * MIN, base + 29 * MIN]
    assert ledger.streak_status(times, base + 29 * MIN, S) == "fire"
    assert ledger.streak_status(times, base + 29 * MIN + 30 * MIN, S) == "fire"
    assert ledger.streak_status(times, base + 29 * MIN + 31 * MIN, S) is None
    assert ledger.streak_status([base, base + 10 * MIN, base + 31 * MIN], base + 31 * MIN, S) == "heating"


def test_streak_with_no_shots_is_none():
    assert ledger.streak_status([], 0, S) is None


def test_party_milestones_cover_the_list_then_every_hundred():
    assert ledger.party_milestones_crossed(0, 24, S) == []
    assert ledger.party_milestones_crossed(24, 25, S) == [25]
    assert ledger.party_milestones_crossed(23, 27, S) == [25]
    assert ledger.party_milestones_crossed(49, 101, S) == [50, 100]
    assert ledger.party_milestones_crossed(199, 200, S) == [200]
    assert ledger.party_milestones_crossed(200, 200, S) == []


def test_player_milestones_are_every_fifth_shot():
    assert [n for n in range(0, 21) if ledger.is_player_milestone(n, S)] == [5, 10, 15, 20]
    assert not ledger.is_player_milestone(5, PartySettings(player_milestone_every=0))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && venv/bin/python -m pytest tests/test_ledger.py -q`
Expected: FAIL with `ImportError: cannot import name 'ledger'`.

- [ ] **Step 3: Implement `ledger.py`**

`backend/app/party/ledger.py`:
```python
"""Pure scoring rules over the shot ledger. No I/O; every time is epoch milliseconds."""
from collections.abc import Iterable
from dataclasses import dataclass
from typing import Literal

from .config import PartySettings

Streak = Literal["heating", "fire"]
MINUTE_MS = 60_000
_NEVER = 2**62


@dataclass
class ShotRec:
    id: str
    drinker_id: str
    logged_by_id: str | None
    request_id: str
    source: str
    created_at: int
    voided_at: int | None = None
    void_reason: str | None = None


def live_counts(shots: Iterable[ShotRec], player_ids: Iterable[str]) -> dict[str, int]:
    counts = dict.fromkeys(player_ids, 0)
    for s in shots:
        if s.voided_at is None and s.drinker_id in counts:
            counts[s.drinker_id] += 1
    return counts


def team_totals(counts: dict[str, int], team_of: dict[str, str], team_ids: Iterable[str]) -> dict[str, int]:
    totals = dict.fromkeys(team_ids, 0)
    for player_id, n in counts.items():
        team_id = team_of.get(player_id)
        if team_id in totals:
            totals[team_id] += n
    return totals


def sole_leader(totals: dict[str, int]) -> str | None:
    if not totals:
        return None
    best = max(totals.values())
    leaders = [k for k, v in totals.items() if v == best]
    return leaders[0] if best > 0 and len(leaders) == 1 else None


def ranked(counts: dict[str, int], last_at: dict[str, int | None]) -> list[tuple[str, int]]:
    """Order by count desc; ties go to whoever reached the count first. Ranks are 1,1,3,..."""
    order = sorted(counts, key=lambda pid: (-counts[pid], last_at.get(pid) or _NEVER, pid))
    out: list[tuple[str, int]] = []
    rank, previous = 0, None
    for position, player_id in enumerate(order, start=1):
        if counts[player_id] != previous:
            rank, previous = position, counts[player_id]
        out.append((player_id, rank))
    return out


def streak_status(times: list[int], now: int, settings: PartySettings) -> Streak | None:
    """times: one player's live shot times, ascending."""
    if not times:
        return None
    last = times[-1]
    fire = settings.on_fire
    in_fire_window = sum(1 for t in times if last - t <= fire.window_min * MINUTE_MS)
    if in_fire_window >= fire.count and now - last <= fire.cool_min * MINUTE_MS:
        return "fire"
    heat = settings.heating_up
    if sum(1 for t in times if now - t <= heat.window_min * MINUTE_MS) >= heat.count:
        return "heating"
    return None


def party_milestones_crossed(before: int, after: int, settings: PartySettings) -> list[int]:
    hits = {m for m in settings.party_milestones if before < m <= after}
    every = settings.party_milestone_every
    if every > 0:
        hits.update(range((before // every + 1) * every, after + 1, every))
    return sorted(hits)


def is_player_milestone(count: int, settings: PartySettings) -> bool:
    every = settings.player_milestone_every
    return every > 0 and count > 0 and count % every == 0
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd backend && venv/bin/python -m pytest tests/test_ledger.py -q`
Expected: `9 passed`.

- [ ] **Step 5: Commit**

```bash
git add backend/app/party/ledger.py backend/tests/test_ledger.py
git commit -m "feat(party): add pure ledger rules for counts, ranks, streaks and milestones"
```

---

### Task 3: Wire contract and generated TypeScript types

**Files:**
- Create: `backend/app/party/contract.py`, `backend/app/party/schema.py`, `backend/scripts/export_schema.py`
- Create (generated, committed): `frontend/src/party/contract.schema.json`, `frontend/src/party/contract.gen.ts`
- Modify: `frontend/package.json` (devDependency `json-schema-to-typescript`; scripts `gen:types`, `predev`, `prebuild`)
- Test: `backend/tests/test_schema.py`

**Interfaces:**
- Consumes: `CamelModel`, `PartySettings` (Task 1).
- Produces:
  - **Inbound models** (validation):
    - Players and shots: `AvatarIn`, `JoinIn`, `ResumeIn`, `PlayerUpdateIn`, `ShotLogIn`, `ShotUndoIn`, `ShotRejectIn`
    - Actions and auth: `GameActionIn`, `HostAuthIn`, `HostActionIn`
    - Host payloads: `TeamUpsertIn`, `TeamRemoveIn`, `PlayerEditIn`, `PlayerRemoveIn`, `PlayerMergeIn`, `ShotAddIn`, `ShotVoidIn`, `SettingsIn`, `WifiIn`, `NightNewIn`, `GameEnableIn`
  - **Ack models:** `JoinOut`, `ResumeOut`, `ShotLogOut`, `MediaOut`.
  - **State models:** `AvatarView`, `TeamView`, `PlayerView`, `FeedItem`, `ReelItem`, `JoinInfo`, `PublicState`, `HostShot`, `HostState`.
  - **Moments:** `ShotDrinker`, `ShotMoment`, `MilestoneMoment`, `LeadChangeMoment`, `WavedOffMoment`, `ReplayMoment`, `GameMoment`, the `Moment` union, and `MomentEnvelope`.
  - `WIRE_MODELS`: a list of `(model, mode)` pairs.
  - `app.party.schema.contract_schema() -> dict`.
  - TS: `frontend/src/party/contract.gen.ts` exports an interface per model, named after the model. Models used both inbound and outbound get an `Input`/`Output` suffix, e.g. `PartySettingsOutput`.

- [ ] **Step 1: Write the failing test**

`backend/tests/test_schema.py`:
```python
import json
from pathlib import Path

from app.party.contract import JoinIn, PublicState
from app.party.schema import contract_schema

SCHEMA_PATH = Path(__file__).resolve().parents[2] / "frontend" / "src" / "party" / "contract.schema.json"


def test_wire_models_use_camel_case():
    payload = {"requestId": "r", "name": "  Jess ", "avatar": {"kind": "emoji", "value": "🔥"}, "teamId": "t"}
    join = JoinIn.model_validate(payload)
    assert join.name == "Jess" and join.team_id == "t"
    assert "nightId" in PublicState.model_json_schema(mode="serialization")["properties"]


def test_schema_names_every_definition_after_its_model():
    defs = contract_schema()["$defs"]
    assert {"PublicState", "PlayerView", "ShotMoment", "JoinIn", "HostState"} <= set(defs)
    assert all(d["title"] == key.replace("-", "") for key, d in defs.items())
    assert "title" not in defs["PlayerView"]["properties"]["name"]


def test_committed_schema_matches_the_models():
    committed = json.loads(SCHEMA_PATH.read_text())
    assert committed == contract_schema(), (
        "Contract changed. Run: venv/bin/python scripts/export_schema.py && (cd ../frontend && npm run gen:types)"
    )
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && venv/bin/python -m pytest tests/test_schema.py -q`
Expected: FAIL with `ModuleNotFoundError: No module named 'app.party.contract'`.

- [ ] **Step 3: Implement the contract**

`backend/app/party/contract.py`:
```python
"""Socket.IO wire contract, the single source of truth for payload shapes.

Frontend types are generated from this module:
    backend$  venv/bin/python scripts/export_schema.py
    frontend$ npm run gen:types
"""
from typing import Annotated, Any, Literal, Union

from pydantic import BaseModel, Field, StringConstraints

from .camel import CamelModel
from .config import PartySettings

Streak = Literal["heating", "fire"]
Id = Annotated[str, StringConstraints(min_length=1, max_length=64)]
Name = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=20)]
TeamName = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=16)]
Color = Annotated[str, StringConstraints(pattern=r"^#[0-9A-Fa-f]{6}$")]


# ---------- inbound: phone / host -> server ----------


class AvatarIn(CamelModel):
    kind: Literal["emoji", "photo"]
    value: Annotated[str, StringConstraints(min_length=1, max_length=64)]


class JoinIn(CamelModel):
    request_id: Id
    name: Name
    avatar: AvatarIn
    team_id: Id


class ResumeIn(CamelModel):
    token: Id


class PlayerUpdateIn(CamelModel):
    name: Name | None = None
    avatar: AvatarIn | None = None
    team_id: Id | None = None


class ShotLogIn(CamelModel):
    request_id: Id
    drinker_ids: Annotated[list[Id], Field(min_length=1, max_length=60)]


class ShotUndoIn(CamelModel):
    request_id: Id


class ShotRejectIn(CamelModel):
    shot_id: Id


class GameActionIn(CamelModel):
    request_id: Id
    game_id: Id
    action: Id
    payload: dict[str, Any] = {}


class HostAuthIn(CamelModel):
    pin: Annotated[str, StringConstraints(min_length=1, max_length=12)]


class HostActionIn(CamelModel):
    request_id: Id
    action: Id
    payload: dict[str, Any] = {}


class TeamUpsertIn(CamelModel):
    id: Id | None = None
    name: TeamName
    color: Color


class TeamRemoveIn(CamelModel):
    team_id: Id


class PlayerEditIn(CamelModel):
    player_id: Id
    name: Name | None = None
    team_id: Id | None = None


class PlayerRemoveIn(CamelModel):
    player_id: Id


class PlayerMergeIn(CamelModel):
    from_id: Id
    into_id: Id


class ShotAddIn(CamelModel):
    drinker_ids: Annotated[list[Id], Field(min_length=1, max_length=60)]


class ShotVoidIn(CamelModel):
    shot_id: Id


class SettingsIn(CamelModel):
    settings: PartySettings


class WifiIn(CamelModel):
    ssid: Annotated[str, StringConstraints(strip_whitespace=True, max_length=32)] = ""
    password: Annotated[str, StringConstraints(max_length=63)] = ""


class NightNewIn(CamelModel):
    name: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=80)] | None = None


class GameEnableIn(CamelModel):
    game_id: Id
    enabled: bool


# ---------- ack payloads ----------


class JoinOut(CamelModel):
    player_id: str
    token: str


class ResumeOut(CamelModel):
    player_id: str


class ShotLogOut(CamelModel):
    shot_ids: list[str]


class MediaOut(CamelModel):
    media_id: str
    status: Literal["processing", "ready", "failed"]


# ---------- outbound state ----------


class AvatarView(CamelModel):
    kind: Literal["emoji", "photo"]
    value: str  # emoji, or a /media/... URL


class TeamView(CamelModel):
    id: str
    name: str
    color: str
    total: int
    size: int


class PlayerView(CamelModel):
    id: str
    name: str
    avatar: AvatarView
    team_id: str
    count: int
    rank: int
    streak: Streak | None
    connected: bool
    last_shot_at: int | None


class FeedItem(CamelModel):
    shot_id: str
    request_id: str
    drinker_id: str
    logged_by_id: str | None
    source: str
    at: int


class ReelItem(CamelModel):
    media_id: str
    kind: Literal["photo", "video"]
    url: str
    poster_url: str | None
    player_id: str
    shot_id: str | None
    at: int


class JoinInfo(CamelModel):
    lan_url: str
    tunnel_url: str | None
    wifi_ssid: str | None
    wifi_password: str | None


class PublicState(CamelModel):
    night_id: str
    night_name: str
    started_at: int
    now: int
    total: int
    teams: list[TeamView]
    players: list[PlayerView]  # sorted by rank
    feed: list[FeedItem]  # newest first, live shots only
    reel: list[ReelItem]  # newest first, ready shot-cam media only
    join: JoinInfo
    settings: PartySettings
    games: dict[str, Any]  # game id -> that plugin's public state
    games_enabled: dict[str, bool]


class HostShot(CamelModel):
    id: str
    request_id: str
    drinker_id: str
    logged_by_id: str | None
    source: str
    at: int
    voided_at: int | None
    void_reason: str | None


class HostState(CamelModel):
    shots: list[HostShot]  # newest first, including voided


# ---------- moments: server -> TV and phones ----------


class ShotDrinker(CamelModel):
    player_id: str
    count: int
    streak: Streak | None


class ShotMoment(CamelModel):
    type: Literal["shot"] = "shot"
    id: str
    at: int
    request_id: str
    source: str
    logged_by_id: str | None
    drinkers: list[ShotDrinker]


class MilestoneMoment(CamelModel):
    type: Literal["milestone"] = "milestone"
    id: str
    at: int
    scope: Literal["first", "player", "party"]
    player_id: str | None
    value: int


class LeadChangeMoment(CamelModel):
    type: Literal["lead-change"] = "lead-change"
    id: str
    at: int
    team_id: str


class WavedOffMoment(CamelModel):
    type: Literal["waved-off"] = "waved-off"
    id: str
    at: int
    player_id: str
    shot_id: str
    reason: Literal["undo", "not_me", "host"]


class ReplayMoment(CamelModel):
    type: Literal["replay"] = "replay"
    id: str
    at: int
    item: ReelItem


class GameMoment(CamelModel):
    type: Literal["game"] = "game"
    id: str
    at: int
    game_id: str
    kind: str
    data: dict[str, Any]


Moment = Annotated[
    Union[ShotMoment, MilestoneMoment, LeadChangeMoment, WavedOffMoment, ReplayMoment, GameMoment],
    Field(discriminator="type"),
]


class MomentEnvelope(CamelModel):
    """Exists so the Moment union gets a named TypeScript type."""

    moment: Moment


SchemaMode = Literal["validation", "serialization"]

WIRE_MODELS: list[tuple[type[BaseModel], SchemaMode]] = [
    *[(m, "validation") for m in (
        JoinIn, ResumeIn, PlayerUpdateIn, ShotLogIn, ShotUndoIn, ShotRejectIn, GameActionIn, HostAuthIn,
        HostActionIn, TeamUpsertIn, TeamRemoveIn, PlayerEditIn, PlayerRemoveIn, PlayerMergeIn, ShotAddIn,
        ShotVoidIn, SettingsIn, WifiIn, NightNewIn, GameEnableIn,
    )],
    *[(m, "serialization") for m in (
        JoinOut, ResumeOut, ShotLogOut, MediaOut, PublicState, HostState, MomentEnvelope,
    )],
]
```

`backend/app/party/schema.py`:
```python
"""JSON Schema export of the wire contract, consumed by json-schema-to-typescript."""
from typing import Any

from pydantic.json_schema import models_json_schema

from .contract import WIRE_MODELS


def contract_schema() -> dict[str, Any]:
    _, schema = models_json_schema(WIRE_MODELS, title="HoopContract")
    _clean(schema, is_definition=True)
    for key, definition in schema.get("$defs", {}).items():
        definition["title"] = key.replace("-", "")  # PartySettings-Input -> PartySettingsInput
    return schema


def _clean(node: Any, *, is_definition: bool) -> None:
    """Drop pydantic's per-field titles; otherwise json2ts emits an alias type for every field."""
    if isinstance(node, dict):
        if not is_definition:
            node.pop("title", None)
        for key, value in node.items():
            if key == "$defs":
                for definition in value.values():
                    _clean(definition, is_definition=True)
            elif key == "properties":
                for prop in value.values():
                    _clean(prop, is_definition=False)
            else:
                _clean(value, is_definition=False)
    elif isinstance(node, list):
        for item in node:
            _clean(item, is_definition=False)
```

`backend/scripts/export_schema.py`:
```python
"""Write the wire contract as JSON Schema for frontend type generation.

Usage (from backend/):  venv/bin/python scripts/export_schema.py
Then (from frontend/):  npm run gen:types
"""
import json
import sys
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BACKEND))

from app.party.schema import contract_schema  # noqa: E402

OUT = BACKEND.parent / "frontend" / "src" / "party" / "contract.schema.json"


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(contract_schema(), indent=2, sort_keys=True, ensure_ascii=False) + "\n")
    print(f"wrote {OUT.relative_to(BACKEND.parent)}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 4: Export the schema and generate the TS types**

Run: `cd backend && venv/bin/python scripts/export_schema.py`
Expected: `wrote frontend/src/party/contract.schema.json`

Run: `cd frontend && npm install && npm install -D json-schema-to-typescript@16`

In `frontend/package.json`, set `"scripts"` to:
```json
"scripts": {
  "gen:types": "json2ts -i src/party/contract.schema.json -o src/party/contract.gen.ts --unreachableDefinitions --additionalProperties false --bannerComment '/* Generated from backend/app/party/contract.py by `npm run gen:types`. Do not edit. */'",
  "predev": "npm run gen:types",
  "dev": "vite",
  "prebuild": "npm run gen:types",
  "build": "tsc -b && vite build",
  "lint": "oxlint",
  "preview": "vite preview"
}
```

Run: `cd frontend && npm run gen:types && grep -c "export interface" src/party/contract.gen.ts && grep -n "export interface PublicState" -A 3 src/party/contract.gen.ts`
Expected: a count above 40, then:
```
export interface PublicState {
  nightId: string;
  nightName: string;
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd backend && venv/bin/python -m pytest -q && cd ../frontend && npx tsc -b`
Expected: `15 passed`, and `tsc` exits 0.

- [ ] **Step 6: Commit**

```bash
git add backend/app/party/contract.py backend/app/party/schema.py backend/scripts/export_schema.py backend/tests/test_schema.py frontend/package.json frontend/package-lock.json frontend/src/party/contract.schema.json frontend/src/party/contract.gen.ts
git commit -m "feat(party): define the socket wire contract and generate frontend types"
```

---

### Task 4: PartyService core: nights, players, presence and public state

**Files:**
- Create: `backend/app/party/service.py`
- Modify: `backend/tests/conftest.py` (add `FakeEmitter`, `FakeClock`, `service` fixture, `join` helper)
- Test: `backend/tests/test_service_players.py`

**Interfaces:**
- Consumes: the Task 1–3 modules.
- Produces:
  - Module helpers: `now_ms() -> int`, `new_id() -> str`, `hash_token(token) -> str`.
  - `Emitter` protocol: `async state(PublicState)`, `async moment(BaseModel)`, `async host_state(HostState)`.
  - Dataclasses: `TeamRec(id, name, color, sort)`, `PlayerRec(id, name, avatar_kind, avatar_value, team_id, token_hash, created_at)`, `MediaRec(id, player_id, shot_id, purpose, kind, status, path, poster_path, created_at)`.
  - `PartyService(emitter, *, session_factory=SessionLocal, clock=now_ms, lan_url=callable, game_types=(), rng=None)`.
- `PartyService` attributes:
  - Night: `night_id`, `night_name`, `started_at`.
  - Dicts `teams`, `players`, `by_token`, `shots` (`dict[str, ShotRec]` in chronological order), `by_request`, `media`.
  - `settings`, `wifi`, `games`, `games_enabled`, `lan_url`, `tunnel_url`.
- `PartyService` methods:
  - **Setup and storage:** `db()` (a context manager that commits), `load()`, `save_setting(key, value_json)`.
  - **Players:** `async join(JoinIn) -> JoinOut`, `resume(token) -> player_id`, `async update_player(player_id, PlayerUpdateIn)`.
  - **Presence:** `async attach(sid, player_id)`, `async detach(sid)`, `is_connected(player_id) -> bool`.
  - **Derived data:** `counts() -> dict`, `team_totals() -> dict`, `live_times(player_id) -> list[int]`.
  - **State out:** `public_state() -> PublicState`, `host_state() -> HostState`, `async broadcast()`, `async refresh()`.
  - **Background and nights:** `async tick_games()`, `async run_ticker()`, `async set_tunnel(url)`, `async new_night(name)`.

- [ ] **Step 1: Add shared fixtures to `backend/tests/conftest.py`**

Append:
```python


class FakeEmitter:
    """Records everything the service would push to screens."""

    def __init__(self):
        self.states = []
        self.moments = []
        self.host_states = []

    async def state(self, state):
        self.states.append(state)

    async def moment(self, moment):
        self.moments.append(moment)

    async def host_state(self, state):
        self.host_states.append(state)

    def moment_types(self):
        return [m.type for m in self.moments]


class FakeClock:
    def __init__(self, start=1_790_000_000_000):
        self.t = start

    def __call__(self):
        return self.t

    def advance(self, ms):
        self.t += ms


@pytest.fixture
def emitter():
    return FakeEmitter()


@pytest.fixture
def clock():
    return FakeClock()


@pytest.fixture
def service(db_reset, emitter, clock):
    from app.party.service import PartyService

    svc = PartyService(emitter, clock=clock, lan_url=lambda: "http://10.0.0.5:8000/play")
    svc.load()
    return svc


async def join(service, name, team=0, emoji="🏀"):
    """Join a player through the service and return their id."""
    import uuid

    from app.party.contract import AvatarIn, JoinIn

    team_id = sorted(service.teams.values(), key=lambda t: t.sort)[team].id
    out = await service.join(JoinIn(request_id=str(uuid.uuid4()), name=name,
                                    avatar=AvatarIn(kind="emoji", value=emoji), team_id=team_id))
    return out.player_id
```

- [ ] **Step 2: Write the failing tests**

`backend/tests/test_service_players.py`:
```python
import pytest

from app.party.contract import AvatarIn, JoinIn, PlayerUpdateIn
from app.party.errors import PartyError
from app.party.service import PartyService
from tests.conftest import join


def test_first_load_creates_a_night_with_home_and_away(service):
    teams = sorted(service.teams.values(), key=lambda t: t.sort)
    assert [(t.name, t.color) for t in teams] == [("HOME", "#FF7A1A"), ("AWAY", "#2D8CFF")]
    assert service.night_name == "Game Night"


def test_reload_reuses_the_open_night(service, emitter, clock):
    again = PartyService(emitter, clock=clock)
    again.load()
    assert again.night_id == service.night_id
    assert set(again.teams) == set(service.teams)


async def test_join_returns_a_token_that_resumes_the_same_player(service):
    team_id = next(iter(service.teams))
    out = await service.join(JoinIn(request_id="r1", name="Jess", avatar=AvatarIn(kind="emoji", value="🔥"), team_id=team_id))
    assert service.resume(out.token) == out.player_id
    assert service.players[out.player_id].token_hash != out.token


async def test_join_retry_with_same_request_returns_the_same_player(service):
    team_id = next(iter(service.teams))
    req = JoinIn(request_id="r1", name="Jess", avatar=AvatarIn(kind="emoji", value="🔥"), team_id=team_id)
    first, second = await service.join(req), await service.join(req)
    assert first == second and len(service.players) == 1


async def test_names_are_unique_ignoring_case(service):
    await join(service, "Jess")
    with pytest.raises(PartyError, match="taken"):
        await join(service, "jess")


async def test_unknown_token_or_team_is_rejected(service):
    with pytest.raises(PartyError):
        service.resume("nope")
    with pytest.raises(PartyError):
        await service.join(JoinIn(request_id="r", name="Sam", avatar=AvatarIn(kind="emoji", value="😎"), team_id="nope"))


async def test_players_survive_a_restart(service, emitter, clock):
    pid = await join(service, "Jess")
    again = PartyService(emitter, clock=clock)
    again.load()
    assert again.players[pid].name == "Jess"


async def test_update_player_changes_name_team_and_emoji(service):
    pid = await join(service, "Jess")
    away = sorted(service.teams.values(), key=lambda t: t.sort)[1].id
    await service.update_player(pid, PlayerUpdateIn(name="Jessie", team_id=away, avatar=AvatarIn(kind="emoji", value="🐐")))
    p = service.players[pid]
    assert (p.name, p.team_id, p.avatar_value) == ("Jessie", away, "🐐")


async def test_presence_has_a_two_minute_grace(service, clock):
    pid = await join(service, "Jess")
    assert not service.is_connected(pid)
    await service.attach("sid-1", pid)
    assert service.is_connected(pid)
    await service.detach("sid-1")
    clock.advance(119_000)
    assert service.is_connected(pid)
    clock.advance(2_000)
    assert not service.is_connected(pid)


async def test_public_state_lists_teams_players_and_join_info(service, emitter):
    await join(service, "Jess")
    state = emitter.states[-1]
    assert state.join.lan_url == "http://10.0.0.5:8000/play"
    assert [t.name for t in state.teams] == ["HOME", "AWAY"]
    assert state.players[0].name == "Jess" and state.players[0].rank == 1
    assert state.wire()["players"][0]["teamId"] == state.players[0].team_id


async def test_new_night_archives_players_and_keeps_teams_and_settings(service):
    await join(service, "Jess")
    old_night = service.night_id
    service.settings = service.settings.model_copy(update={"sound": False})
    service.save_setting("party", service.settings.model_dump_json())
    await service.new_night("Round Two")
    assert service.night_id != old_night and service.night_name == "Round Two"
    assert service.players == {}
    assert sorted(t.name for t in service.teams.values()) == ["AWAY", "HOME"]
    assert service.settings.sound is False


async def test_refresh_only_broadcasts_when_something_changed(service, emitter, clock):
    pid = await join(service, "Jess")
    await service.attach("sid-1", pid)
    await service.detach("sid-1")
    sent = len(emitter.states)
    await service.refresh()
    assert len(emitter.states) == sent
    clock.advance(121_000)
    await service.refresh()
    assert len(emitter.states) == sent + 1
    assert emitter.states[-1].players[0].connected is False
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd backend && venv/bin/python -m pytest tests/test_service_players.py -q`
Expected: FAIL with `ModuleNotFoundError: No module named 'app.party.service'`.

- [ ] **Step 4: Implement `service.py`**

`backend/app/party/service.py`:
```python
"""Authoritative state for the current party night.

Every mutation is committed to SQLite first, then applied in memory, then broadcast.
uvicorn must run with exactly one worker, because this object *is* the party.
"""
from __future__ import annotations

import asyncio
import hashlib
import json
import logging
import random
import secrets
import time
import uuid
from collections import Counter, defaultdict, deque
from collections.abc import Callable, Iterator, Sequence
from contextlib import contextmanager
from dataclasses import dataclass
from typing import Any, Protocol

from pydantic import BaseModel
from sqlalchemy import select, update
from sqlalchemy.orm import Session

from .. import models
from ..database import SessionLocal
from . import ledger
from .config import DEFAULT_TEAMS, FEED_SIZE, PRESENCE_GRACE_MS, REEL_SIZE, PartySettings
from .contract import (
    AvatarView,
    FeedItem,
    HostShot,
    HostState,
    JoinIn,
    JoinInfo,
    JoinOut,
    PlayerUpdateIn,
    PlayerView,
    PublicState,
    ReelItem,
    TeamView,
    WifiIn,
)
from .errors import PartyError
from .ledger import ShotRec

log = logging.getLogger("hoopdreams.party")

FALLBACK_EMOJI = "🏀"


def now_ms() -> int:
    return time.time_ns() // 1_000_000


def new_id() -> str:
    return str(uuid.uuid4())


def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


class Emitter(Protocol):
    async def state(self, state: PublicState) -> None: ...

    async def moment(self, moment: BaseModel) -> None: ...

    async def host_state(self, state: HostState) -> None: ...


@dataclass
class TeamRec:
    id: str
    name: str
    color: str
    sort: int


@dataclass
class PlayerRec:
    id: str
    name: str
    avatar_kind: str
    avatar_value: str
    team_id: str
    token_hash: str
    created_at: int


@dataclass
class MediaRec:
    id: str
    player_id: str
    shot_id: str | None
    purpose: str
    kind: str
    status: str
    path: str
    poster_path: str | None
    created_at: int


class PartyService:
    def __init__(
        self,
        emitter: Emitter,
        *,
        session_factory: Callable[[], Session] = SessionLocal,
        clock: Callable[[], int] = now_ms,
        lan_url: Callable[[], str] = lambda: "http://localhost:8000/play",
        game_types: Sequence[type] = (),
        rng: random.Random | None = None,
    ) -> None:
        self.emitter = emitter
        self.session_factory = session_factory
        self.clock = clock
        self.lan_url_fn = lan_url
        self.game_types = list(game_types)
        self.rng = rng or random.Random()
        self.lan_url = ""
        self.tunnel_url: str | None = None
        self._reset_memory()

    def _reset_memory(self) -> None:
        self.night_id = ""
        self.night_name = ""
        self.started_at = 0
        self.teams: dict[str, TeamRec] = {}
        self.players: dict[str, PlayerRec] = {}
        self.by_token: dict[str, str] = {}
        self.shots: dict[str, ShotRec] = {}
        self.by_request: dict[str, list[str]] = {}
        self.media: dict[str, MediaRec] = {}
        self.joins: dict[str, JoinOut] = {}
        self.rate: defaultdict[str, deque[int]] = defaultdict(deque)
        self.sid_player: dict[str, str] = {}
        self.sockets: defaultdict[str, set[str]] = defaultdict(set)
        self.last_seen: dict[str, int] = {}
        self.settings = PartySettings()
        self.wifi = WifiIn()
        self.games: dict[str, Any] = {}
        self.games_enabled: dict[str, bool] = {}
        self.last_leader: str | None = None
        self._digest = ""

    # ---------- persistence ----------

    @contextmanager
    def db(self) -> Iterator[Session]:
        with self.session_factory() as session:
            yield session
            session.commit()

    def load(self) -> None:
        """Load the open night from SQLite, creating the first night if there is none."""
        with self.db() as db:
            night = db.scalar(
                select(models.Night).where(models.Night.ended_at.is_(None)).order_by(models.Night.started_at.desc())
            )
            night_id = night.id if night else self._insert_night(db, "Game Night", DEFAULT_TEAMS)
        self._load_night(night_id)
        self.lan_url = self.lan_url_fn()

    def _insert_night(self, db: Session, name: str, teams: Sequence[tuple[str, str]]) -> str:
        night_id = new_id()
        db.add(models.Night(id=night_id, name=name, started_at=self.clock()))
        db.flush()
        db.add_all(
            models.Team(id=new_id(), night_id=night_id, name=team_name, color=color, sort=i)
            for i, (team_name, color) in enumerate(teams)
        )
        return night_id

    def _load_night(self, night_id: str) -> None:
        self._reset_memory()
        with self.session_factory() as db:
            night = db.get(models.Night, night_id)
            assert night is not None
            self.night_id, self.night_name, self.started_at = night.id, night.name, night.started_at
            for t in db.scalars(select(models.Team).where(models.Team.night_id == night_id, models.Team.removed_at.is_(None))):
                self.teams[t.id] = TeamRec(t.id, t.name, t.color, t.sort)
            for p in db.scalars(
                select(models.Player).where(models.Player.night_id == night_id, models.Player.removed_at.is_(None))
            ):
                self.players[p.id] = PlayerRec(
                    p.id, p.name, p.avatar_kind, p.avatar_value, p.team_id, p.token_hash, p.created_at
                )
                self.by_token[p.token_hash] = p.id
            for s in db.scalars(
                select(models.Shot).where(models.Shot.night_id == night_id).order_by(models.Shot.created_at, models.Shot.id)
            ):
                self.shots[s.id] = ShotRec(
                    s.id, s.drinker_id, s.logged_by_id, s.request_id, s.source, s.created_at, s.voided_at, s.void_reason
                )
                self.by_request.setdefault(s.request_id, []).append(s.id)
            for m in db.scalars(
                select(models.Media).where(models.Media.night_id == night_id).order_by(models.Media.created_at)
            ):
                self.media[m.id] = MediaRec(
                    m.id, m.player_id, m.shot_id, m.purpose, m.kind, m.status, m.path, m.poster_path, m.created_at
                )
            stored = {
                row.key: row.value_json
                for row in db.scalars(select(models.Setting).where(models.Setting.night_id == night_id))
            }
        if "party" in stored:
            self.settings = PartySettings.model_validate_json(stored["party"])
        if "wifi" in stored:
            self.wifi = WifiIn.model_validate_json(stored["wifi"])
        if "games" in stored:
            self.games_enabled = json.loads(stored["games"])
        self.last_leader = ledger.sole_leader(self.team_totals())
        self._start_games()

    def save_setting(self, key: str, value_json: str) -> None:
        with self.db() as db:
            db.merge(models.Setting(night_id=self.night_id, key=key, value_json=value_json))

    async def new_night(self, name: str | None) -> None:
        teams = [(t.name, t.color) for t in sorted(self.teams.values(), key=lambda t: t.sort)] or list(DEFAULT_TEAMS)
        with self.db() as db:
            old_settings = list(db.scalars(select(models.Setting).where(models.Setting.night_id == self.night_id)))
            db.execute(update(models.Night).where(models.Night.id == self.night_id).values(ended_at=self.clock()))
            night_id = self._insert_night(db, name or "Game Night", teams)
            db.add_all(models.Setting(night_id=night_id, key=s.key, value_json=s.value_json) for s in old_settings)
        self._load_night(night_id)
        await self.broadcast()

    # ---------- games (filled in by the plugin task) ----------

    def _start_games(self) -> None:
        self.games = {}

    async def tick_games(self) -> None:
        for game_id, game in list(self.games.items()):
            if self.games_enabled.get(game_id, True):
                await game.tick()

    # ---------- players ----------

    async def join(self, req: JoinIn) -> JoinOut:
        if req.request_id in self.joins:
            return self.joins[req.request_id]
        if req.team_id not in self.teams:
            raise PartyError("Pick a team first")
        self._check_name(req.name)
        emoji = req.avatar.value if req.avatar.kind == "emoji" else FALLBACK_EMOJI
        token = secrets.token_urlsafe(16)
        rec = PlayerRec(new_id(), req.name, "emoji", emoji, req.team_id, hash_token(token), self.clock())
        with self.db() as db:
            db.add(
                models.Player(
                    id=rec.id, night_id=self.night_id, name=rec.name, avatar_kind=rec.avatar_kind,
                    avatar_value=rec.avatar_value, team_id=rec.team_id, token_hash=rec.token_hash,
                    created_at=rec.created_at,
                )
            )
        self.players[rec.id] = rec
        self.by_token[rec.token_hash] = rec.id
        out = JoinOut(player_id=rec.id, token=token)
        self.joins[req.request_id] = out
        await self.broadcast()
        return out

    def _check_name(self, name: str, *, exclude: str | None = None) -> None:
        if name.casefold() in {p.name.casefold() for p in self.players.values() if p.id != exclude}:
            raise PartyError(f"{name} is taken — add an initial")

    def resume(self, token: str) -> str:
        player_id = self.by_token.get(hash_token(token))
        if player_id is None or player_id not in self.players:
            raise PartyError("Unknown player")
        return player_id

    async def update_player(self, player_id: str, req: PlayerUpdateIn) -> None:
        rec = self.players.get(player_id)
        if rec is None:
            raise PartyError("Unknown player")
        values: dict[str, Any] = {}
        if req.name is not None and req.name != rec.name:
            self._check_name(req.name, exclude=player_id)
            values["name"] = req.name
        if req.team_id is not None and req.team_id != rec.team_id:
            if req.team_id not in self.teams:
                raise PartyError("That team doesn't exist")
            values["team_id"] = req.team_id
        if req.avatar is not None:
            if req.avatar.kind != "emoji":
                raise PartyError("Use the camera button for photos")
            values["avatar_kind"], values["avatar_value"] = "emoji", req.avatar.value
        if not values:
            return
        with self.db() as db:
            db.execute(update(models.Player).where(models.Player.id == player_id).values(**values))
        for key, value in values.items():
            setattr(rec, key, value)
        await self.broadcast()

    # ---------- presence ----------

    async def attach(self, sid: str, player_id: str) -> None:
        previous = self.sid_player.get(sid)
        if previous is not None:
            self.sockets[previous].discard(sid)
        self.sid_player[sid] = player_id
        self.sockets[player_id].add(sid)
        await self.broadcast()

    async def detach(self, sid: str) -> None:
        player_id = self.sid_player.pop(sid, None)
        if player_id is None:
            return
        self.sockets[player_id].discard(sid)
        self.last_seen[player_id] = self.clock()
        await self.broadcast()

    def is_connected(self, player_id: str) -> bool:
        if self.sockets.get(player_id):
            return True
        seen = self.last_seen.get(player_id)
        return seen is not None and self.clock() - seen <= PRESENCE_GRACE_MS

    # ---------- derived state ----------

    def counts(self) -> dict[str, int]:
        return ledger.live_counts(self.shots.values(), self.players)

    def team_totals(self) -> dict[str, int]:
        return ledger.team_totals(self.counts(), {p.id: p.team_id for p in self.players.values()}, self.teams)

    def live_times(self, player_id: str) -> list[int]:
        return [s.created_at for s in self.shots.values() if s.drinker_id == player_id and s.voided_at is None]

    def roster(self) -> tuple[list[PlayerView], list[TeamView]]:
        """Players (ranked) and teams with totals. Game plugins read this; it never includes game state."""
        now = self.clock()
        times: dict[str, list[int]] = {pid: [] for pid in self.players}
        for s in self.shots.values():
            if s.voided_at is None and s.drinker_id in times:
                times[s.drinker_id].append(s.created_at)
        counts = {pid: len(ts) for pid, ts in times.items()}
        last_at = {pid: (ts[-1] if ts else None) for pid, ts in times.items()}
        players = [
            PlayerView(
                id=pid, name=p.name, avatar=self._avatar(p), team_id=p.team_id, count=counts[pid], rank=rank,
                streak=ledger.streak_status(times[pid], now, self.settings), connected=self.is_connected(pid),
                last_shot_at=last_at[pid],
            )
            for pid, rank in ledger.ranked(counts, last_at)
            for p in (self.players[pid],)
        ]
        totals = ledger.team_totals(counts, {p.id: p.team_id for p in self.players.values()}, self.teams)
        sizes = Counter(p.team_id for p in self.players.values())
        teams = [
            TeamView(id=t.id, name=t.name, color=t.color, total=totals[t.id], size=sizes[t.id])
            for t in sorted(self.teams.values(), key=lambda t: t.sort)
        ]
        return players, teams

    def _avatar(self, p: PlayerRec) -> AvatarView:
        if p.avatar_kind == "photo":
            media = self.media.get(p.avatar_value)
            if media is not None and media.status == "ready":
                return AvatarView(kind="photo", value=f"/media/{media.path}")
            return AvatarView(kind="emoji", value=FALLBACK_EMOJI)
        return AvatarView(kind="emoji", value=p.avatar_value)

    def reel_item(self, m: MediaRec) -> ReelItem:
        return ReelItem(
            media_id=m.id, kind="video" if m.kind == "video" else "photo", url=f"/media/{m.path}",
            poster_url=f"/media/{m.poster_path}" if m.poster_path else None, player_id=m.player_id,
            shot_id=m.shot_id, at=m.created_at,
        )

    def public_state(self) -> PublicState:
        players, teams = self.roster()
        live = [s for s in self.shots.values() if s.voided_at is None and s.drinker_id in self.players]
        feed = [
            FeedItem(shot_id=s.id, request_id=s.request_id, drinker_id=s.drinker_id, logged_by_id=s.logged_by_id,
                     source=s.source, at=s.created_at)
            for s in reversed(live[-FEED_SIZE:])
        ]
        reel = [
            self.reel_item(m) for m in reversed(list(self.media.values())) if m.purpose == "shot" and m.status == "ready"
        ][:REEL_SIZE]
        return PublicState(
            night_id=self.night_id, night_name=self.night_name, started_at=self.started_at, now=self.clock(),
            total=len(live), teams=teams, players=players, feed=feed, reel=reel,
            join=JoinInfo(lan_url=self.lan_url, tunnel_url=self.tunnel_url, wifi_ssid=self.wifi.ssid or None,
                          wifi_password=self.wifi.password or None),
            settings=self.settings,
            games={gid: game.public_state().wire() for gid, game in self.games.items()},
            games_enabled={gid: self.games_enabled.get(gid, True) for gid in self.games},
        )

    def host_state(self) -> HostState:
        return HostState(
            shots=[
                HostShot(id=s.id, request_id=s.request_id, drinker_id=s.drinker_id, logged_by_id=s.logged_by_id,
                         source=s.source, at=s.created_at, voided_at=s.voided_at, void_reason=s.void_reason)
                for s in reversed(self.shots.values())
            ]
        )

    # ---------- fan-out ----------

    async def broadcast(self) -> None:
        state = self.public_state()
        self._digest = state.model_dump_json(exclude={"now"})
        await self.emitter.state(state)
        await self.emitter.host_state(self.host_state())

    async def refresh(self) -> None:
        """Re-broadcast only if time alone changed something (streaks cooling, presence, LAN address)."""
        self.lan_url = self.lan_url_fn()
        state = self.public_state()
        digest = state.model_dump_json(exclude={"now"})
        if digest != self._digest:
            self._digest = digest
            await self.emitter.state(state)

    async def set_tunnel(self, url: str | None) -> None:
        if url != self.tunnel_url:
            self.tunnel_url = url
            await self.broadcast()

    async def run_ticker(self) -> None:
        """Every second: game ticks. Every 30 s: refresh time-dependent state."""
        tick = 0
        while True:
            await asyncio.sleep(1)
            tick += 1
            try:
                await self.tick_games()
                if tick % 30 == 0:
                    await self.refresh()
            except Exception:
                log.exception("party tick failed")
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd backend && venv/bin/python -m pytest -q`
Expected: all pass (`27 passed`).

- [ ] **Step 6: Commit**

```bash
git add backend/app/party/service.py backend/tests/conftest.py backend/tests/test_service_players.py
git commit -m "feat(party): add PartyService with nights, players, presence and public state"
```

---

### Task 5: PartyService shots: log, undo, NOT ME and hype moments

**Files:**
- Modify: `backend/app/party/service.py`
- Test: `backend/tests/test_service_shots.py`

**Interfaces:**
- Consumes: `PartyService` (Task 4), `ledger` (Task 2).
- Produces these `PartyService` methods:
  - `async log_shots(logged_by_id: str | None, request_id: str, drinker_ids: Sequence[str], *, source="manual", reason="") -> list[str]`. Returns the shot IDs and is idempotent per `request_id`.
  - `async undo(logger_id, request_id)`
  - `async reject(player_id, shot_id)`
  - `async void(shots: list[ShotRec], reason: Literal["undo","not_me","host"])`
- Moments emitted:
  - `ShotMoment` first.
  - Then `MilestoneMoment`s: first, player, party.
  - Then `LeadChangeMoment` when the sole leading team changes.
  - Voiding emits one `WavedOffMoment` per shot.

- [ ] **Step 1: Write the failing tests**

`backend/tests/test_service_shots.py`:
```python
import pytest

from app.party.errors import PartyError
from app.party.service import PartyService
from tests.conftest import join

MIN = 60_000


async def test_log_writes_one_shot_per_drinker_and_announces_it(service, emitter):
    jess, sam = await join(service, "Jess"), await join(service, "Sam", team=1)
    ids = await service.log_shots(jess, "r1", [jess, sam, jess])
    assert len(ids) == 2
    assert service.counts() == {jess: 1, sam: 1}
    shot = next(m for m in emitter.moments if m.type == "shot")
    assert shot.logged_by_id == jess and [d.player_id for d in shot.drinkers] == [jess, sam]
    assert emitter.states[-1].total == 2


async def test_retrying_a_request_does_not_double_count(service, emitter):
    jess = await join(service, "Jess")
    first = await service.log_shots(jess, "r1", [jess])
    moments = len(emitter.moments)
    assert await service.log_shots(jess, "r1", [jess]) == first
    assert service.counts()[jess] == 1 and len(emitter.moments) == moments


async def test_shots_survive_a_restart(service, emitter, clock):
    jess = await join(service, "Jess")
    await service.log_shots(jess, "r1", [jess])
    again = PartyService(emitter, clock=clock)
    again.load()
    assert again.counts()[jess] == 1
    assert await again.log_shots(jess, "r1", [jess]) == service.by_request["r1"]


async def test_unknown_drinkers_are_rejected(service):
    jess = await join(service, "Jess")
    with pytest.raises(PartyError):
        await service.log_shots(jess, "r1", [jess, "ghost"])
    assert service.counts()[jess] == 0


async def test_first_shot_is_first_bucket_and_team_takes_the_lead(service, emitter):
    jess = await join(service, "Jess")
    await service.log_shots(jess, "r1", [jess])
    assert emitter.moment_types() == ["shot", "milestone", "lead-change"]
    assert emitter.moments[1].scope == "first"


async def test_lead_change_only_fires_when_the_sole_leader_changes(service, emitter):
    jess, sam = await join(service, "Jess"), await join(service, "Sam", team=1)
    await service.log_shots(jess, "a", [jess])
    await service.log_shots(sam, "b", [sam])  # tie: no change
    await service.log_shots(jess, "c", [jess])  # home again: same leader, no change
    await service.log_shots(sam, "d", [sam])
    await service.log_shots(sam, "e", [sam])  # away takes it
    leads = [m.team_id for m in emitter.moments if m.type == "lead-change"]
    assert leads == [service.players[jess].team_id, service.players[sam].team_id]


async def test_every_fifth_shot_is_a_player_milestone(service, emitter, clock):
    jess = await join(service, "Jess")
    for i in range(5):
        clock.advance(MIN)
        await service.log_shots(jess, f"r{i}", [jess])
    player = [m for m in emitter.moments if m.type == "milestone" and m.scope == "player"]
    assert [(m.player_id, m.value) for m in player] == [(jess, 5)]


async def test_shot_moment_carries_streak_status(service, emitter, clock):
    jess = await join(service, "Jess")
    for i in range(3):
        clock.advance(5 * MIN)
        await service.log_shots(jess, f"r{i}", [jess])
    streaks = [m.drinkers[0].streak for m in emitter.moments if m.type == "shot"]
    assert streaks == [None, "heating", "fire"]


async def test_rate_limit_applies_per_logger_per_minute(service, clock):
    jess = await join(service, "Jess")
    service.settings = service.settings.model_copy(update={"shot_log_per_min": 2})
    await service.log_shots(jess, "a", [jess])
    await service.log_shots(jess, "b", [jess])
    with pytest.raises(PartyError, match="too many"):
        await service.log_shots(jess, "c", [jess])
    clock.advance(61_000)
    await service.log_shots(jess, "d", [jess])


async def test_logger_can_undo_within_the_window(service, emitter, clock):
    jess, sam = await join(service, "Jess"), await join(service, "Sam")
    await service.log_shots(jess, "r1", [jess, sam])
    clock.advance(9_000)
    await service.undo(jess, "r1")
    assert service.counts() == {jess: 0, sam: 0}
    assert [m.reason for m in emitter.moments if m.type == "waved-off"] == ["undo", "undo"]


async def test_undo_rules(service, clock):
    jess, sam = await join(service, "Jess"), await join(service, "Sam")
    await service.log_shots(jess, "r1", [jess])
    with pytest.raises(PartyError):
        await service.undo(sam, "r1")
    clock.advance(11_000)
    with pytest.raises(PartyError, match="late"):
        await service.undo(jess, "r1")
    with pytest.raises(PartyError):
        await service.undo(jess, "missing")


async def test_drinker_can_say_not_me_within_a_minute(service, emitter, clock):
    jess, sam = await join(service, "Jess"), await join(service, "Sam")
    [shot_id] = await service.log_shots(sam, "r1", [jess])
    clock.advance(59_000)
    await service.reject(jess, shot_id)
    assert service.counts()[jess] == 0
    assert emitter.moments[-1].type == "waved-off" and emitter.moments[-1].reason == "not_me"


async def test_not_me_rules(service, clock):
    jess, sam = await join(service, "Jess"), await join(service, "Sam")
    [own] = await service.log_shots(jess, "r1", [jess])
    [other] = await service.log_shots(sam, "r2", [jess])
    with pytest.raises(PartyError):
        await service.reject(jess, own)  # self-logged: use undo instead
    with pytest.raises(PartyError):
        await service.reject(sam, other)  # not the drinker
    clock.advance(61_000)
    with pytest.raises(PartyError, match="late"):
        await service.reject(jess, other)


async def test_voided_shots_leave_the_feed_but_stay_in_host_state(service, emitter):
    jess = await join(service, "Jess")
    [shot_id] = await service.log_shots(jess, "r1", [jess])
    await service.undo(jess, "r1")
    assert emitter.states[-1].feed == []
    assert emitter.host_states[-1].shots[0].id == shot_id
    assert emitter.host_states[-1].shots[0].void_reason == "undo"
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && venv/bin/python -m pytest tests/test_service_shots.py -q`
Expected: FAIL with `AttributeError: 'PartyService' object has no attribute 'log_shots'`.

- [ ] **Step 3: Implement shots in `service.py`**

Add these imports to the `from .contract import (...)` block in `backend/app/party/service.py`: `LeadChangeMoment`, `MilestoneMoment`, `ShotDrinker`, `ShotMoment`, `WavedOffMoment`. Also add `from typing import Any, Literal, Protocol`, replacing the existing `typing` import.

Then add this section to `PartyService`, directly after `is_connected`:
```python
    # ---------- shots ----------

    async def log_shots(
        self,
        logged_by_id: str | None,
        request_id: str,
        drinker_ids: Sequence[str],
        *,
        source: str = "manual",
        reason: str = "",
    ) -> list[str]:
        if request_id in self.by_request:
            return list(self.by_request[request_id])
        ids = list(dict.fromkeys(drinker_ids))
        if not ids or any(pid not in self.players for pid in ids):
            raise PartyError("Someone in that shot isn't in the game")
        if source == "manual" and logged_by_id is not None:
            self._check_rate(logged_by_id)
        now = self.clock()
        before_total = sum(self.counts().values())
        recs = [ShotRec(new_id(), pid, logged_by_id, request_id, source, now) for pid in ids]
        with self.db() as db:
            db.add_all(
                models.Shot(id=r.id, night_id=self.night_id, drinker_id=r.drinker_id, logged_by_id=r.logged_by_id,
                            request_id=r.request_id, source=r.source, reason=reason, created_at=r.created_at)
                for r in recs
            )
        for r in recs:
            self.shots[r.id] = r
        self.by_request[request_id] = [r.id for r in recs]
        await self._announce(recs, before_total)
        return [r.id for r in recs]

    def _check_rate(self, player_id: str) -> None:
        window, now = self.rate[player_id], self.clock()
        while window and now - window[0] > 60_000:
            window.popleft()
        if len(window) >= self.settings.shot_log_per_min:
            raise PartyError("Easy there — too many shots logged this minute")
        window.append(now)

    async def _announce(self, recs: list[ShotRec], before_total: int) -> None:
        now, counts, first = self.clock(), self.counts(), recs[0]
        moments: list[BaseModel] = [
            ShotMoment(
                id=new_id(), at=now, request_id=first.request_id, source=first.source, logged_by_id=first.logged_by_id,
                drinkers=[
                    ShotDrinker(player_id=r.drinker_id, count=counts[r.drinker_id],
                                streak=ledger.streak_status(self.live_times(r.drinker_id), now, self.settings))
                    for r in recs
                ],
            )
        ]
        if before_total == 0:
            moments.append(MilestoneMoment(id=new_id(), at=now, scope="first", player_id=first.drinker_id, value=1))
        moments += [
            MilestoneMoment(id=new_id(), at=now, scope="player", player_id=r.drinker_id, value=counts[r.drinker_id])
            for r in recs
            if ledger.is_player_milestone(counts[r.drinker_id], self.settings)
        ]
        moments += [
            MilestoneMoment(id=new_id(), at=now, scope="party", player_id=None, value=value)
            for value in ledger.party_milestones_crossed(before_total, sum(counts.values()), self.settings)
        ]
        leader = ledger.sole_leader(self.team_totals())
        if leader is not None and leader != self.last_leader:
            self.last_leader = leader
            moments.append(LeadChangeMoment(id=new_id(), at=now, team_id=leader))
        await self.broadcast()
        for moment in moments:
            await self.emitter.moment(moment)

    async def undo(self, logger_id: str, request_id: str) -> None:
        shots = [self.shots[i] for i in self.by_request.get(request_id, [])]
        if not shots:
            raise PartyError("Nothing to undo")
        if any(s.logged_by_id != logger_id for s in shots):
            raise PartyError("You can only undo your own logs")
        if self.clock() - shots[0].created_at > self.settings.undo_window_sec * 1000:
            raise PartyError("Too late to undo — ask the host")
        await self.void([s for s in shots if s.voided_at is None], "undo")

    async def reject(self, player_id: str, shot_id: str) -> None:
        shot = self.shots.get(shot_id)
        if shot is None or shot.drinker_id != player_id:
            raise PartyError("That shot isn't yours")
        if shot.logged_by_id is None or shot.logged_by_id == player_id:
            raise PartyError("Use undo for shots you logged")
        if shot.voided_at is not None:
            return
        if self.clock() - shot.created_at > self.settings.not_me_window_sec * 1000:
            raise PartyError("Too late — take it up with the host")
        await self.void([shot], "not_me")

    async def void(self, shots: list[ShotRec], reason: Literal["undo", "not_me", "host"]) -> None:
        if not shots:
            return
        now = self.clock()
        with self.db() as db:
            db.execute(
                update(models.Shot).where(models.Shot.id.in_([s.id for s in shots])).values(voided_at=now, void_reason=reason)
            )
        for s in shots:
            s.voided_at, s.void_reason = now, reason
        await self.broadcast()
        for s in shots:
            await self.emitter.moment(
                WavedOffMoment(id=new_id(), at=now, player_id=s.drinker_id, shot_id=s.id, reason=reason)
            )
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd backend && venv/bin/python -m pytest -q`
Expected: all pass (`41 passed`).

- [ ] **Step 5: Commit**

```bash
git add backend/app/party/service.py backend/tests/test_service_shots.py
git commit -m "feat(party): log shots idempotently with undo, NOT ME, streaks and milestones"
```

---

### Task 6: Realtime: Socket.IO inside Steven's FastAPI app

**Files:**
- Create: `backend/app/party/realtime.py`, `backend/app/party/net.py`, `backend/app/party/web.py`
- Modify: `backend/app/main.py` (two edits)
- Modify: `backend/tests/conftest.py` (add `server_url`, `SocketClient`, `connect`, `socket_join`)
- Test: `backend/tests/test_realtime.py`

**Interfaces:**
- Consumes: `PartyService` (Tasks 4–5), the contract models (Task 3).
- Produces:
  - `realtime.SioEmitter(sio)`.
  - `realtime.Session(player_id: str | None, host: bool, pin_attempts: list[float])`.
  - `realtime.register_handlers(sio, service, *, host_pin: str, admin=None) -> dict[str, Session]`. It registers `connect`, `disconnect`, `player:join`, `player:resume`, `player:update`, `shot:log`, `shot:undo` and `shot:reject`, and returns the sid → Session map.
  - `realtime.handler(sio, sessions, service, event, model, *, player=False, host=False)`: a decorator factory that later tasks reuse.
  - `net.lan_ip() -> str`, `net.join_url() -> str`.
  - `web`: the module-level `sio`, `service`, `sessions`, `party_router`, `HOST_PIN`, `party_lifespan(app)` and `mount_party(app)`.
  - Server → client events: `state`, `moment`, `host_state` (room `host`).

- [ ] **Step 1: Add integration fixtures to `backend/tests/conftest.py`**

Append:
```python


import asyncio  # noqa: E402
import socket  # noqa: E402
import subprocess  # noqa: E402
import sys  # noqa: E402
import time  # noqa: E402
import uuid  # noqa: E402
from pathlib import Path  # noqa: E402

import httpx  # noqa: E402
import socketio  # noqa: E402

BACKEND_DIR = Path(__file__).resolve().parents[1]


def _free_port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


@pytest.fixture(scope="session")
def server_url(tmp_path_factory):
    """A real uvicorn running app.main:app with its own database, shared by the integration tests."""
    tmp = tmp_path_factory.mktemp("server")
    port = _free_port()
    env = {**os.environ, "HOOP_DB": str(tmp / "server.db"), "HOOP_MEDIA_DIR": str(tmp / "media"),
           "HOOP_HOST_PIN": "4242", "HOOP_PUBLIC_PORT": str(port)}
    proc = subprocess.Popen(
        [sys.executable, "-m", "uvicorn", "app.main:app", "--port", str(port), "--log-level", "warning"],
        cwd=BACKEND_DIR, env=env,
    )
    url = f"http://127.0.0.1:{port}"
    deadline = time.monotonic() + 20
    while True:
        try:
            if httpx.get(f"{url}/api/health", timeout=0.5).status_code == 200:
                break
        except httpx.HTTPError:
            pass
        if proc.poll() is not None or time.monotonic() > deadline:
            proc.kill()
            raise RuntimeError("test server did not start")
        time.sleep(0.2)
    yield url
    proc.terminate()
    proc.wait(timeout=10)


class SocketClient:
    """Stands in for a phone, the TV or the host panel, and records everything the server pushes."""

    def __init__(self, url):
        self.url = url
        self.sio = socketio.AsyncClient(reconnection=False)
        self.states, self.moments, self.host_states = [], [], []
        self.sio.on("state", self._on_state)
        self.sio.on("moment", self._on_moment)
        self.sio.on("host_state", self._on_host_state)

    async def _on_state(self, data):
        self.states.append(data)

    async def _on_moment(self, data):
        self.moments.append(data)

    async def _on_host_state(self, data):
        self.host_states.append(data)

    async def connect(self):
        await self.sio.connect(self.url, transports=["websocket"])
        await self.wait_for(lambda: self.states, "initial state")

    async def call(self, event, data=None):
        return await self.sio.call(event, data if data is not None else {}, timeout=5)

    @property
    def state(self):
        return self.states[-1]

    async def wait_for(self, predicate, what="condition", timeout=5.0):
        loop = asyncio.get_running_loop()
        deadline = loop.time() + timeout
        while True:
            found = predicate()
            if found:
                return found
            if loop.time() > deadline:
                raise AssertionError(f"timed out waiting for {what}")
            await asyncio.sleep(0.02)

    async def close(self):
        await self.sio.disconnect()


@pytest.fixture
async def connect(server_url):
    clients = []

    async def factory():
        client = SocketClient(server_url)
        await client.connect()
        clients.append(client)
        return client

    yield factory
    for client in clients:
        await client.close()


async def socket_join(client, name=None, team=0):
    """Join through the socket with a unique name. Returns the ack: {ok, playerId, token}."""
    name = name or f"P{uuid.uuid4().hex[:6]}"
    team_id = client.state["teams"][team]["id"]
    ack = await client.call("player:join", {"requestId": str(uuid.uuid4()), "name": name,
                                            "avatar": {"kind": "emoji", "value": "🏀"}, "teamId": team_id})
    assert ack["ok"], ack
    return ack
```

- [ ] **Step 2: Write the failing tests**

`backend/tests/test_realtime.py`:
```python
import uuid

import httpx

from tests.conftest import socket_join


def rid():
    return str(uuid.uuid4())


async def test_new_sockets_get_state_immediately(connect):
    tv = await connect()
    assert {"nightId", "teams", "players", "feed", "join", "settings"} <= set(tv.state)
    assert tv.state["join"]["lanUrl"].endswith("/play")


async def test_join_then_resume_from_a_new_socket(connect):
    phone = await connect()
    ack = await socket_join(phone)
    await phone.close()
    again = await connect()
    resumed = await again.call("player:resume", {"token": ack["token"]})
    assert resumed == {"ok": True, "playerId": ack["playerId"]}


async def test_a_logged_shot_reaches_every_screen(connect):
    tv, phone = await connect(), await connect()
    me = await socket_join(phone)
    ack = await phone.call("shot:log", {"requestId": rid(), "drinkerIds": [me["playerId"]]})
    assert ack["ok"] and len(ack["shotIds"]) == 1
    moment = await tv.wait_for(
        lambda: next((m for m in tv.moments if m["type"] == "shot" and m["loggedById"] == me["playerId"]), None),
        "shot moment",
    )
    assert moment["drinkers"][0]["count"] == 1
    row = next(p for p in tv.state["players"] if p["id"] == me["playerId"])
    assert row["count"] == 1 and row["connected"] is True


async def test_retrying_the_same_request_is_idempotent(connect):
    phone = await connect()
    me = await socket_join(phone)
    payload = {"requestId": rid(), "drinkerIds": [me["playerId"]]}
    first, second = await phone.call("shot:log", payload), await phone.call("shot:log", payload)
    assert first["shotIds"] == second["shotIds"]


async def test_drinker_can_wave_off_a_shot_someone_else_logged(connect):
    jess_phone, sam_phone = await connect(), await connect()
    jess, sam = await socket_join(jess_phone), await socket_join(sam_phone)
    logged = await sam_phone.call("shot:log", {"requestId": rid(), "drinkerIds": [jess["playerId"]]})
    rejected = await jess_phone.call("shot:reject", {"shotId": logged["shotIds"][0]})
    assert rejected == {"ok": True}
    await sam_phone.wait_for(
        lambda: any(m["type"] == "waved-off" and m["playerId"] == jess["playerId"] for m in sam_phone.moments),
        "waved-off moment",
    )


async def test_logging_before_joining_is_refused(connect):
    stranger = await connect()
    ack = await stranger.call("shot:log", {"requestId": rid(), "drinkerIds": ["x"]})
    assert ack == {"ok": False, "error": "Join the game first"}


async def test_malformed_payloads_get_an_error_ack(connect):
    phone = await connect()
    await socket_join(phone)
    ack = await phone.call("shot:log", {"requestId": rid(), "drinkerIds": []})
    assert ack == {"ok": False, "error": "Invalid request"}


def test_hat_api_still_works(server_url):
    games = httpx.get(f"{server_url}/api/games").json()
    assert any(g["name"] == "Trivia" for g in games)
    assert httpx.get(f"{server_url}/api/party/health").json() == {"ok": True}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd backend && venv/bin/python -m pytest tests/test_realtime.py -q`
Expected: FAIL. The socket tests fail to connect (`socketio.exceptions.ConnectionError`), and `/api/party/health` returns 404.

- [ ] **Step 4: Implement `net.py`, `realtime.py` and `web.py`, and wire them into `main.py`**

`backend/app/party/net.py`:
```python
import os
import socket


def lan_ip() -> str:
    """The Mac's address on the local network (a UDP connect sends no packets)."""
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect(("10.254.254.254", 1))
        return probe.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        probe.close()


def join_url() -> str:
    port = os.environ.get("HOOP_PUBLIC_PORT", "8000")
    return f"http://{lan_ip()}:{port}/play"
```

`backend/app/party/realtime.py`:
```python
"""Socket.IO transport: validate intents, route them to PartyService, fan state out to screens."""
import logging
from collections.abc import Awaitable, Callable
from dataclasses import dataclass, field
from typing import Any

import socketio
from pydantic import BaseModel, ValidationError

from .contract import JoinIn, PlayerUpdateIn, ResumeIn, ResumeOut, ShotLogIn, ShotLogOut, ShotRejectIn, ShotUndoIn
from .errors import PartyError
from .service import PartyService

log = logging.getLogger("hoopdreams.realtime")


class SioEmitter:
    def __init__(self, sio: socketio.AsyncServer) -> None:
        self.sio = sio

    async def state(self, state) -> None:
        await self.sio.emit("state", state.wire())

    async def moment(self, moment) -> None:
        await self.sio.emit("moment", moment.wire())

    async def host_state(self, state) -> None:
        await self.sio.emit("host_state", state.wire(), room="host")


@dataclass
class Session:
    player_id: str | None = None
    host: bool = False
    pin_attempts: list[float] = field(default_factory=list)


Handler = Callable[[str, Session, Any], Awaitable[BaseModel | dict | None]]


def handler(
    sio: socketio.AsyncServer,
    sessions: dict[str, Session],
    service: PartyService,
    event: str,
    model: type[BaseModel] | None = None,
    *,
    player: bool = False,
    host: bool = False,
) -> Callable[[Handler], Handler]:
    """Register `event`: check auth, validate the payload, and turn the result into an {ok, ...} ack."""

    def decorate(fn: Handler) -> Handler:
        async def wrapped(sid: str, data: Any = None) -> dict:
            session = sessions.setdefault(sid, Session())
            try:
                if player and (session.player_id is None or session.player_id not in service.players):
                    raise PartyError("Join the game first")
                if host and not session.host:
                    raise PartyError("Host PIN required")
                payload = model.model_validate(data if data is not None else {}) if model else None
                result = await fn(sid, session, payload)
            except ValidationError as exc:
                log.warning("%s: invalid payload: %s", event, exc.errors(include_url=False))
                return {"ok": False, "error": "Invalid request"}
            except PartyError as exc:
                return {"ok": False, "error": str(exc)}
            except Exception:
                log.exception("%s failed", event)
                return {"ok": False, "error": "Server error"}
            if isinstance(result, BaseModel):
                return {"ok": True, **result.wire()}
            return {"ok": True, **(result or {})}

        sio.on(event, handler=wrapped)
        return fn

    return decorate


def register_handlers(sio: socketio.AsyncServer, service: PartyService, *, host_pin: str, admin: Any = None) -> dict[str, Session]:
    sessions: dict[str, Session] = {}

    def on(event: str, model: type[BaseModel] | None = None, **kw: bool) -> Callable[[Handler], Handler]:
        return handler(sio, sessions, service, event, model, **kw)

    @sio.event
    async def connect(sid, environ, auth=None):
        sessions[sid] = Session()
        await sio.emit("state", service.public_state().wire(), to=sid)

    @sio.event
    async def disconnect(sid, reason=None):
        sessions.pop(sid, None)
        await service.detach(sid)

    @on("player:join", JoinIn)
    async def join(sid, session, payload):
        out = await service.join(payload)
        session.player_id = out.player_id
        await service.attach(sid, out.player_id)
        return out

    @on("player:resume", ResumeIn)
    async def resume(sid, session, payload):
        player_id = service.resume(payload.token)
        session.player_id = player_id
        await service.attach(sid, player_id)
        return ResumeOut(player_id=player_id)

    @on("player:update", PlayerUpdateIn, player=True)
    async def update(sid, session, payload):
        await service.update_player(session.player_id, payload)

    @on("shot:log", ShotLogIn, player=True)
    async def log_shot(sid, session, payload):
        ids = await service.log_shots(session.player_id, payload.request_id, payload.drinker_ids)
        return ShotLogOut(shot_ids=ids)

    @on("shot:undo", ShotUndoIn, player=True)
    async def undo(sid, session, payload):
        await service.undo(session.player_id, payload.request_id)

    @on("shot:reject", ShotRejectIn, player=True)
    async def reject(sid, session, payload):
        await service.reject(session.player_id, payload.shot_id)

    return sessions
```

`backend/app/party/web.py`:
```python
"""Wire the party into Steven's FastAPI app: Socket.IO, party REST routes, lifespan.

main.py calls mount_party(app) as its LAST line, so nothing else ends up behind the SPA catch-all mount.
"""
import asyncio
import os
import secrets
from contextlib import asynccontextmanager

import socketio
from fastapi import APIRouter, FastAPI

from . import net
from .realtime import SioEmitter, register_handlers
from .service import PartyService

HOST_PIN = os.environ.get("HOOP_HOST_PIN") or f"{secrets.randbelow(10_000):04d}"

sio = socketio.AsyncServer(async_mode="asgi", cors_allowed_origins="*")
service = PartyService(SioEmitter(sio), lan_url=net.join_url)
sessions = register_handlers(sio, service, host_pin=HOST_PIN)
party_router = APIRouter(prefix="/api/party")


@party_router.get("/health")
def party_health():
    return {"ok": True}


@asynccontextmanager
async def party_lifespan(_app: FastAPI):
    service.load()
    print(f"🏀 HoopDreams party is live — phones: {service.lan_url} · host PIN: {HOST_PIN}", flush=True)
    ticker = asyncio.create_task(service.run_ticker())
    try:
        yield
    finally:
        ticker.cancel()


def mount_party(app: FastAPI) -> None:
    app.include_router(party_router)
    app.mount("/socket.io", socketio.ASGIApp(sio, socketio_path=""))
```

In `backend/app/main.py`, make two edits:

1. Add this import after `from .seed_data import SEED_GAMES`:
```python
from .party.web import mount_party, party_lifespan
```
and change `app = FastAPI(title="HoopDreams API")` to:
```python
app = FastAPI(title="HoopDreams API", lifespan=party_lifespan)
```

2. Append at the very end of the file:
```python


# Keep this last: it adds the Socket.IO mount and, in party mode, the catch-all SPA mount.
mount_party(app)
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd backend && venv/bin/python -m pytest -q`
Expected: all pass (`49 passed`).

- [ ] **Step 6: Smoke-test Steven's dev command**

Run: `cd backend && (HOOP_DB=/tmp/hoop-smoke.db venv/bin/uvicorn app.main:app --port 8123 > /tmp/hoop-smoke.log 2>&1 &) && for i in $(seq 1 40); do curl -sf localhost:8123/api/health && break; python3 -c "import time; time.sleep(.25)"; done; curl -s localhost:8123/api/party/health; pkill -f "port 8123"; grep "party is live" /tmp/hoop-smoke.log`
Expected: `{"status":"ok"}{"ok":true}`, then the `🏀 HoopDreams party is live — phones: http://…:8000/play · host PIN: ####` line.

- [ ] **Step 7: Commit**

```bash
git add backend/app/party/net.py backend/app/party/realtime.py backend/app/party/web.py backend/app/main.py backend/tests/conftest.py backend/tests/test_realtime.py
git commit -m "feat(party): serve Socket.IO from the FastAPI app with typed, acked intents"
```

---
### Task 7: Frontend foundation: router, dev proxy, party client and offline outbox

**Files:**
- Modify: `frontend/package.json` (dependencies, `test` script)
- Modify: `frontend/vite.config.ts`, `frontend/src/main.tsx`, `frontend/index.html`
- Create: `frontend/vitest.config.ts`
- Create: `frontend/src/party/ids.ts`, `socket.ts`, `store.ts`, `outbox.ts`, `identity.ts`, `time.ts`
- Test: `frontend/src/party/outbox.test.ts`, `frontend/src/party/time.test.ts`, `frontend/src/party/ids.test.ts`

**Interfaces:**
- Consumes: `contract.gen.ts` (Task 3).
- Produces:
  - `ids.uuid(): string`.
  - `socket`:
    - `type Ack<T> = ({ok: true} & T) | {ok: false; error: string; retryable?: boolean}`
    - `getSocket(): Socket`
    - `call<T>(event, payload, timeoutMs?) => Promise<Ack<T>>`
  - `store`:
    - `type Moment`
    - Hooks: `usePartyState()`, `useHostState()`, `useConnected()`, `useNow(intervalMs?)`
    - `onMoment(listener) => unsubscribe`
    - `serverNow()`
  - `outbox`: `class Outbox({store, key, send, onResult, retryMs?})` with the methods `push`, `pause`, `resume`, `flush`, `dispose`, `subscribe` and `snapshot`, plus the types `OutboxItem`, `OutboxResult`, `KeyValueStore` and `Sender`.
  - `identity`: `type Identity`, `loadIdentity()`, `saveIdentity(identity | null)`.
  - `time`: `formatClock(ms)`, `ago(ms)`.

- [ ] **Step 1: Install dependencies and add configuration**

Run: `cd frontend && npm install react-router@8 socket.io-client@4 motion@13 qrcode@1 @fontsource/bungee@5 @fontsource/press-start-2p@5 && npm install -D vitest@5 @types/qrcode@1`

In `frontend/package.json` `"scripts"`, add after `"preview"`:
```json
"test": "vitest run"
```

`frontend/vite.config.ts` (full file):
```ts
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const backend = 'http://127.0.0.1:8000'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Party traffic goes to FastAPI. Steven's hat API is still called directly via VITE_API_URL.
    proxy: {
      '/api/party': backend,
      '/socket.io': { target: backend, ws: true },
      '/media': backend,
    },
  },
})
```

`frontend/vitest.config.ts`:
```ts
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: { include: ["src/**/*.test.ts"], environment: "node" },
});
```

In `frontend/index.html`, the Google Fonts stylesheet is render-blocking. Offline, that would stall every screen. Make it non-blocking by replacing the `<link href="https://fonts.googleapis.com/css2?…" rel="stylesheet" />` element with:
```html
    <link
      href="https://fonts.googleapis.com/css2?family=Fredoka:wght@500;600;700&family=Nunito:wght@400;600;700&display=swap"
      rel="stylesheet"
      media="print"
      onload="this.media='all'"
    />
```

`frontend/src/main.tsx` (full file; this adds the router only, and the pages come in later tasks):
```tsx
import { StrictMode, Suspense } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router'
import './index.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <Suspense fallback={null}>
        <Routes>
          <Route path="/" element={<App />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  </StrictMode>,
)
```

- [ ] **Step 2: Write the failing tests**

`frontend/src/party/ids.test.ts`:
```ts
import { describe, expect, it } from "vitest";
import { uuid } from "./ids";

describe("uuid", () => {
  it("makes distinct RFC 4122 v4 ids without crypto.randomUUID", () => {
    const ids = new Set(Array.from({ length: 200 }, uuid));
    expect(ids.size).toBe(200);
    for (const id of ids) expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  });
});
```

`frontend/src/party/time.test.ts`:
```ts
import { describe, expect, it } from "vitest";
import { ago, formatClock } from "./time";

describe("formatClock", () => {
  it("shows m:ss under an hour and h:mm:ss after", () => {
    expect(formatClock(0)).toBe("0:00");
    expect(formatClock(83_000)).toBe("1:23");
    expect(formatClock(3_723_000)).toBe("1:02:03");
    expect(formatClock(-5)).toBe("0:00");
  });
});

describe("ago", () => {
  it("rounds down to now, minutes or hours", () => {
    expect(ago(59_000)).toBe("now");
    expect(ago(125_000)).toBe("2m");
    expect(ago(7_300_000)).toBe("2h");
  });
});
```

`frontend/src/party/outbox.test.ts`:
```ts
import { describe, expect, it, vi } from "vitest";
import { Outbox, type KeyValueStore, type OutboxResult, type Sender } from "./outbox";

class MemoryStore implements KeyValueStore {
  data = new Map<string, string>();
  getItem(key: string) {
    return this.data.get(key) ?? null;
  }
  setItem(key: string, value: string) {
    this.data.set(key, value);
  }
}

function setup(send: Sender) {
  const store = new MemoryStore();
  const results: OutboxResult[] = [];
  const outbox = new Outbox({ store, key: "k", send, onResult: (r) => results.push(r), retryMs: 60_000 });
  return { store, results, outbox };
}

const shot = (requestId: string) => ({ requestId, drinkerIds: ["p1"] });

describe("Outbox", () => {
  it("persists an intent before sending it", async () => {
    const send = vi.fn<Sender>();
    const { store, outbox } = setup(send);
    await outbox.push("shot:log", shot("r1"));
    expect(send).not.toHaveBeenCalled(); // starts paused until the phone has resumed its session
    expect(JSON.parse(store.getItem("k")!)).toHaveLength(1);
  });

  it("sends once resumed and drops the item on an ok ack", async () => {
    const send = vi.fn<Sender>().mockResolvedValue({ ok: true, shotIds: ["s1"] });
    const { outbox, results } = setup(send);
    await outbox.push("shot:log", shot("r1"));
    await outbox.resume();
    expect(send).toHaveBeenCalledWith("shot:log", shot("r1"));
    expect(outbox.snapshot()).toEqual([]);
    expect(results[0].ack.ok).toBe(true);
  });

  it("keeps an unanswered intent and resends the same requestId", async () => {
    const send = vi
      .fn<Sender>()
      .mockResolvedValueOnce({ ok: false, error: "No answer", retryable: true })
      .mockResolvedValueOnce({ ok: true });
    const { outbox, results } = setup(send);
    await outbox.resume();
    await outbox.push("shot:log", shot("r1"));
    expect(outbox.snapshot()).toHaveLength(1);
    expect(results).toEqual([]);
    await outbox.flush();
    expect(send).toHaveBeenNthCalledWith(2, "shot:log", shot("r1"));
    expect(outbox.snapshot()).toEqual([]);
    outbox.dispose();
  });

  it("reports a refused intent and moves on to the next", async () => {
    const send = vi.fn<Sender>().mockResolvedValueOnce({ ok: false, error: "Too late" }).mockResolvedValueOnce({ ok: true });
    const { outbox, results } = setup(send);
    await outbox.push("shot:log", shot("r1"));
    await outbox.push("shot:log", shot("r2"));
    await outbox.resume();
    expect(results.map((r) => r.ack.ok)).toEqual([false, true]);
    expect(outbox.snapshot()).toEqual([]);
  });

  it("restores unsent intents after a reload", async () => {
    const first = setup(vi.fn<Sender>());
    await first.outbox.push("shot:log", shot("r1"));
    const reloaded = new Outbox({ store: first.store, key: "k", send: vi.fn<Sender>(), onResult: () => {} });
    expect(reloaded.snapshot().map((i) => i.requestId)).toEqual(["r1"]);
  });

  it("notifies subscribers when the queue changes", async () => {
    const { outbox } = setup(vi.fn<Sender>());
    const listener = vi.fn();
    outbox.subscribe(listener);
    await outbox.push("shot:log", shot("r1"));
    expect(listener).toHaveBeenCalledTimes(1);
  });
});
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd frontend && npx vitest run`
Expected: FAIL with `Failed to resolve import "./outbox"` (and the same for `./ids` and `./time`).

- [ ] **Step 4: Implement the party client modules**

`frontend/src/party/ids.ts`:
```ts
/** RFC 4122 v4 id. crypto.randomUUID needs a secure context, and phones reach the Mac over plain-HTTP LAN. */
export function uuid(): string {
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("");
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}
```

`frontend/src/party/time.ts`:
```ts
/** 83000 -> "1:23"; 3723000 -> "1:02:03". */
export function formatClock(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const ss = String(total % 60).padStart(2, "0");
  return h > 0 ? `${h}:${String(m).padStart(2, "0")}:${ss}` : `${m}:${ss}`;
}

/** Short "time ago" for the play-by-play feed. */
export function ago(ms: number): string {
  if (ms < 60_000) return "now";
  if (ms < 3_600_000) return `${Math.floor(ms / 60_000)}m`;
  return `${Math.floor(ms / 3_600_000)}h`;
}
```

`frontend/src/party/socket.ts`:
```ts
import { io, type Socket } from "socket.io-client";

export type Ack<T extends object = object> = ({ ok: true } & T) | { ok: false; error: string; retryable?: boolean };

let socket: Socket | null = null;

/** One Socket.IO connection per tab, on the page's own origin (Vite proxies it in dev). */
export function getSocket(): Socket {
  socket ??= io({ reconnectionDelayMax: 2000 });
  return socket;
}

/** Emit and wait for the server's {ok, ...} ack. No answer comes back as a retryable failure. */
export async function call<T extends object = object>(event: string, payload: unknown, timeoutMs = 5000): Promise<Ack<T>> {
  try {
    return (await getSocket().timeout(timeoutMs).emitWithAck(event, payload)) as Ack<T>;
  } catch {
    return { ok: false, error: "No answer from the party server", retryable: true };
  }
}
```

`frontend/src/party/store.ts`:
```ts
import { useEffect, useState, useSyncExternalStore } from "react";
import type { HostState, MomentEnvelope, PublicState } from "./contract.gen";
import { getSocket } from "./socket";

export type Moment = MomentEnvelope["moment"];

let state: PublicState | null = null;
let hostState: HostState | null = null;
let offsetMs = 0;
let connected = false;
let wired = false;
const listeners = new Set<() => void>();
const momentListeners = new Set<(moment: Moment) => void>();

function notify(): void {
  for (const listener of listeners) listener();
}

function wire(): void {
  if (wired) return;
  wired = true;
  const socket = getSocket();
  connected = socket.connected;
  socket.on("connect", () => {
    connected = true;
    notify();
  });
  socket.on("disconnect", () => {
    connected = false;
    notify();
  });
  socket.on("state", (next: PublicState) => {
    state = next;
    offsetMs = next.now - Date.now();
    notify();
  });
  socket.on("host_state", (next: HostState) => {
    hostState = next;
    notify();
  });
  socket.on("moment", (moment: Moment) => {
    for (const listener of momentListeners) listener(moment);
  });
}

function subscribe(listener: () => void): () => void {
  wire();
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function usePartyState(): PublicState | null {
  return useSyncExternalStore(subscribe, () => state);
}

export function useHostState(): HostState | null {
  return useSyncExternalStore(subscribe, () => hostState);
}

export function useConnected(): boolean {
  return useSyncExternalStore(subscribe, () => connected);
}

export function onMoment(listener: (moment: Moment) => void): () => void {
  wire();
  momentListeners.add(listener);
  return () => {
    momentListeners.delete(listener);
  };
}

/** The server's clock, so countdowns agree across phones and the TV. */
export function serverNow(): number {
  return Date.now() + offsetMs;
}

export function useNow(intervalMs = 1000): number {
  const [now, setNow] = useState(serverNow);
  useEffect(() => {
    const id = setInterval(() => setNow(serverNow()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs]);
  return now;
}
```

`frontend/src/party/identity.ts`:
```ts
export interface Identity {
  nightId: string;
  playerId: string;
  token: string;
}

const KEY = "hoop.identity";

export function loadIdentity(): Identity | null {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as Identity) : null;
  } catch {
    return null;
  }
}

export function saveIdentity(identity: Identity | null): void {
  try {
    if (identity) localStorage.setItem(KEY, JSON.stringify(identity));
    else localStorage.removeItem(KEY);
  } catch {
    // Storage blocked (private mode): the identity lasts for this tab only.
  }
}
```

`frontend/src/party/outbox.ts`:
```ts
import type { Ack } from "./socket";

export interface OutboxItem {
  requestId: string;
  event: string;
  payload: { requestId: string } & Record<string, unknown>;
  createdAt: number;
}

export interface KeyValueStore {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}

export type Sender = (event: string, payload: unknown) => Promise<Ack<Record<string, unknown>>>;

export interface OutboxResult {
  item: OutboxItem;
  ack: Ack<Record<string, unknown>>;
}

interface OutboxOptions {
  store: KeyValueStore;
  key: string;
  send: Sender;
  onResult: (result: OutboxResult) => void;
  retryMs?: number;
}

/**
 * Intents that must never be lost (shot logs). An item is persisted before it is sent and leaves the
 * queue only once the server answers. Resends reuse the requestId, which the server dedupes.
 * It starts paused: the phone resumes it once the server knows who this socket belongs to.
 */
export class Outbox {
  private readonly store: KeyValueStore;
  private readonly key: string;
  private readonly send: Sender;
  private readonly onResult: (result: OutboxResult) => void;
  private readonly retryMs: number;
  private readonly listeners = new Set<() => void>();
  private items: readonly OutboxItem[];
  private paused = true;
  private flushing = false;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(options: OutboxOptions) {
    this.store = options.store;
    this.key = options.key;
    this.send = options.send;
    this.onResult = options.onResult;
    this.retryMs = options.retryMs ?? 2000;
    this.items = this.read();
  }

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  snapshot = (): readonly OutboxItem[] => this.items;

  async push(event: string, payload: OutboxItem["payload"]): Promise<void> {
    this.write([...this.items, { requestId: payload.requestId, event, payload, createdAt: Date.now() }]);
    await this.flush();
  }

  pause(): void {
    this.paused = true;
  }

  async resume(): Promise<void> {
    this.paused = false;
    await this.flush();
  }

  async flush(): Promise<void> {
    if (this.paused || this.flushing) return;
    this.flushing = true;
    try {
      while (!this.paused && this.items.length > 0) {
        const item = this.items[0];
        const ack = await this.send(item.event, item.payload);
        if (!ack.ok && ack.retryable) {
          this.scheduleRetry();
          return;
        }
        this.write(this.items.filter((i) => i.requestId !== item.requestId));
        this.onResult({ item, ack });
      }
    } finally {
      this.flushing = false;
    }
  }

  dispose(): void {
    if (this.retryTimer) clearTimeout(this.retryTimer);
    this.retryTimer = null;
    this.paused = true;
  }

  private scheduleRetry(): void {
    if (this.retryTimer) return;
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null;
      void this.flush();
    }, this.retryMs);
  }

  private write(items: readonly OutboxItem[]): void {
    this.items = items;
    try {
      this.store.setItem(this.key, JSON.stringify(items));
    } catch {
      // Storage blocked or full: keep the queue in memory.
    }
    for (const listener of this.listeners) listener();
  }

  private read(): OutboxItem[] {
    try {
      const raw = this.store.getItem(this.key);
      return raw ? (JSON.parse(raw) as OutboxItem[]) : [];
    } catch {
      return [];
    }
  }
}
```

- [ ] **Step 5: Run the tests and the build to verify they pass**

Run: `cd frontend && npx vitest run && npm run build`
Expected: `3` test files and `9` tests pass, then the Vite build finishes with no TypeScript errors.

- [ ] **Step 6: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/vite.config.ts frontend/vitest.config.ts frontend/index.html frontend/src/main.tsx frontend/src/party
git commit -m "feat(web): add router, dev proxy, socket store and persistent shot outbox"
```

---

### Task 8: Arcade UI kit: surface, avatar, LED digits, sounds and announcer voice

**Files:**
- Create: `frontend/src/arcade/arcade.css`, `Surface.tsx`, `Avatar.tsx`, `LedDigits.tsx`, `sound.ts`, `voice.ts`, `lines.ts`
- Test: `frontend/src/arcade/lines.test.ts`

**Interfaces:**
- Consumes: `AvatarView` (from `contract.gen`).
- Produces:
  - `<ArcadeSurface>{children}</ArcadeSurface>`: sets `body[data-surface="arcade"]` while mounted.
  - `<Avatar avatar size? color? fire? />`, where `size` is a CSS length.
  - `<LedDigits value digits? size? color? />`: supports `0-9 - : space`.
  - `sound`: `unlockAudio(): Promise<boolean>`, `setSoundEnabled(on)`, and `sfx` with `swish`, `bucket`, `horn`, `buzzer`, `fire`, `whistle`, `tick` and `spin`.
  - `voice`: `announce(text)`, `setVoiceEnabled(on)`.
  - `lines`: `LINES`, `fill(line, vars)`, `pick(lines, vars?, random?)`.

- [ ] **Step 1: Write the failing test**

`frontend/src/arcade/lines.test.ts`:
```ts
import { describe, expect, it } from "vitest";
import { LINES, fill, pick } from "./lines";

describe("announcer lines", () => {
  it("fills known placeholders and leaves unknown ones", () => {
    expect(fill("{name} hits {value}! {nope}", { name: "Jess", value: 10 })).toBe("Jess hits 10! {nope}");
  });

  it("picks with the given random source", () => {
    expect(pick(["a {name}", "b {name}"], { name: "Sam" }, () => 0.99)).toBe("b Sam");
    expect(pick(["a {name}", "b {name}"], { name: "Sam" }, () => 0)).toBe("a Sam");
  });

  it("has a line for every stage moment", () => {
    for (const key of ["shot", "heating", "fire", "group", "first", "playerMilestone", "partyMilestone", "century", "lead", "wavedOff", "replay"] as const) {
      expect(LINES[key].length).toBeGreaterThan(0);
    }
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend && npx vitest run src/arcade`
Expected: FAIL with `Failed to resolve import "./lines"`.

- [ ] **Step 3: Implement the kit**

`frontend/src/arcade/lines.ts`:
```ts
export const LINES = {
  shot: [
    "{name}… from downtown!",
    "{name}, nothing but net!",
    "Boomshakalaka! {name}!",
    "{name} with the jam!",
    "Is it the shoes? {name}!",
    "Razzle dazzle, {name}!",
    "{name} for three!",
    "Count it! {name}!",
  ],
  heating: ["{name} is heating up!", "{name} is getting warm!"],
  fire: ["{name} is on fire!", "{name} can't miss!", "Somebody stop {name}!"],
  group: ["{n} shots down! Team effort!", "Combo! {n} at once!", "Everybody eats! {n} shots!"],
  first: ["And we're underway! First bucket, {name}!"],
  playerMilestone: ["{name} hits {value}!", "That's {value} for {name}!"],
  partyMilestone: ["{value} shots tonight! What a game!"],
  century: ["Welcome to the century club!"],
  lead: ["{team} takes the lead!", "Lead change! {team} in front!"],
  wavedOff: ["No good! Waved off!", "Overturned on review!", "The ref says no!"],
  replay: ["Let's go to the replay!", "Instant replay!"],
} as const;

export function fill(line: string, vars: Record<string, string | number>): string {
  return line.replace(/\{(\w+)\}/g, (match, key: string) => (key in vars ? String(vars[key]) : match));
}

export function pick(lines: readonly string[], vars: Record<string, string | number> = {}, random = Math.random): string {
  return fill(lines[Math.min(lines.length - 1, Math.floor(random() * lines.length))], vars);
}
```

`frontend/src/arcade/sound.ts`:
```ts
/** Arcade sound effects synthesised with Web Audio: no files, works offline. */
let ctx: AudioContext | null = null;
let enabled = true;

export function setSoundEnabled(on: boolean): void {
  enabled = on;
}

/** Call from a click handler (browser autoplay rules). Resolves true once audio can play. */
export async function unlockAudio(): Promise<boolean> {
  ctx ??= new AudioContext();
  try {
    await Promise.race([ctx.resume(), new Promise((resolve) => setTimeout(resolve, 300))]);
  } catch {
    // resume() rejects when the context is closed; report "not running" below.
  }
  return ctx.state === "running";
}

function audio(): AudioContext | null {
  return enabled && ctx && ctx.state === "running" ? ctx : null;
}

function tone(freq: number, start: number, dur: number, type: OscillatorType = "square", gain = 0.2, slideTo?: number): void {
  const a = audio();
  if (!a) return;
  const t = a.currentTime + start;
  const osc = a.createOscillator();
  const env = a.createGain();
  osc.type = type;
  osc.frequency.setValueAtTime(freq, t);
  if (slideTo) osc.frequency.exponentialRampToValueAtTime(slideTo, t + dur);
  env.gain.setValueAtTime(gain, t);
  env.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  osc.connect(env).connect(a.destination);
  osc.start(t);
  osc.stop(t + dur + 0.05);
}

function noise(start: number, dur: number, gain = 0.3, freq = 1200): void {
  const a = audio();
  if (!a) return;
  const t = a.currentTime + start;
  const buffer = a.createBuffer(1, Math.ceil(a.sampleRate * dur), a.sampleRate);
  const data = buffer.getChannelData(0);
  for (let i = 0; i < data.length; i++) data[i] = Math.random() * 2 - 1;
  const src = a.createBufferSource();
  src.buffer = buffer;
  const filter = a.createBiquadFilter();
  filter.type = "bandpass";
  filter.frequency.value = freq;
  const env = a.createGain();
  env.gain.setValueAtTime(gain, t);
  env.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  src.connect(filter).connect(env).connect(a.destination);
  src.start(t);
}

export const sfx = {
  swish() {
    noise(0, 0.35, 0.35, 2600);
    tone(900, 0.05, 0.15, "sine", 0.06, 1800);
  },
  bucket() {
    tone(523, 0, 0.1);
    tone(659, 0.1, 0.1);
    tone(784, 0.2, 0.3);
  },
  horn() {
    tone(233, 0, 0.9, "sawtooth", 0.18);
    tone(294, 0, 0.9, "sawtooth", 0.12);
    tone(349, 0, 0.9, "sawtooth", 0.1);
  },
  buzzer() {
    tone(98, 0, 1.3, "square", 0.25);
    tone(104, 0, 1.3, "square", 0.2);
  },
  fire() {
    noise(0, 0.9, 0.3, 500);
    tone(180, 0, 0.9, "sawtooth", 0.12, 900);
  },
  whistle() {
    tone(2900, 0, 0.22, "sine", 0.18);
    tone(2900, 0.3, 0.5, "sine", 0.18);
  },
  tick() {
    tone(1400, 0, 0.05, "square", 0.08);
  },
  spin() {
    for (let i = 0; i < 18; i++) tone(600 + i * 25, i * i * 0.012, 0.04, "square", 0.06);
  },
};
```

`frontend/src/arcade/voice.ts`:
```ts
/** Announcer voice using the Mac's built-in speech synthesis (local voices, works offline). */
let enabled = true;
let voice: SpeechSynthesisVoice | null = null;
const PREFERRED = ["Daniel", "Ralph", "Fred", "Alex", "Samantha"];

export function setVoiceEnabled(on: boolean): void {
  enabled = on;
  if (!on && "speechSynthesis" in window) speechSynthesis.cancel();
}

function pickVoice(): SpeechSynthesisVoice | null {
  const english = speechSynthesis.getVoices().filter((v) => v.lang.startsWith("en"));
  const local = english.filter((v) => v.localService);
  for (const name of PREFERRED) {
    const found = local.find((v) => v.name.startsWith(name));
    if (found) return found;
  }
  return local[0] ?? english[0] ?? null;
}

/** Newest call wins: a backlog of stale announcements is worse than a skipped one. */
export function announce(text: string): void {
  if (!enabled || !("speechSynthesis" in window)) return;
  voice ??= pickVoice();
  if (speechSynthesis.speaking || speechSynthesis.pending) speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  if (voice) utterance.voice = voice;
  utterance.rate = 1.08;
  utterance.pitch = 0.9;
  speechSynthesis.speak(utterance);
}
```

`frontend/src/arcade/Surface.tsx`:
```tsx
import "@fontsource/bungee";
import "@fontsource/press-start-2p";
import "./arcade.css";
import { useEffect, type ReactNode } from "react";

/** Switches the page to the arcade look while mounted, leaving Steven's pastel hat page alone. */
export function ArcadeSurface({ children }: { children: ReactNode }) {
  useEffect(() => {
    document.body.dataset.surface = "arcade";
    return () => {
      delete document.body.dataset.surface;
    };
  }, []);
  return <div className="arcade">{children}</div>;
}
```

`frontend/src/arcade/Avatar.tsx`:
```tsx
import type { CSSProperties } from "react";
import type { AvatarView } from "../party/contract.gen";

interface AvatarProps {
  avatar: AvatarView;
  size?: string;
  color?: string;
  fire?: boolean;
}

export function Avatar({ avatar, size = "48px", color, fire = false }: AvatarProps) {
  const style: CSSProperties = { width: size, height: size, fontSize: `calc(${size} * 0.55)`, borderColor: color };
  return (
    <span className={`avatar${fire ? " avatar--fire" : ""}`} style={style}>
      {avatar.kind === "photo" ? <img src={avatar.value} alt="" /> : avatar.value}
    </span>
  );
}
```

`frontend/src/arcade/LedDigits.tsx`:
```tsx
type Segment = "a" | "b" | "c" | "d" | "e" | "f" | "g";

// x, y, width, height inside a 12x20 cell: a=top, b/c=right, d=bottom, e/f=left, g=middle
const RECTS: Record<Segment, [number, number, number, number]> = {
  a: [2, 0, 8, 2],
  b: [10, 2, 2, 7],
  c: [10, 11, 2, 7],
  d: [2, 18, 8, 2],
  e: [0, 11, 2, 7],
  f: [0, 2, 2, 7],
  g: [2, 9, 8, 2],
};
const SEGMENTS = Object.keys(RECTS) as Segment[];
const LIT: Record<string, string> = {
  "0": "abcdef", "1": "bc", "2": "abdeg", "3": "abcdg", "4": "bcfg", "5": "acdfg",
  "6": "acdefg", "7": "abc", "8": "abcdefg", "9": "abcdfg", "-": "g", " ": "",
};

interface LedDigitsProps {
  value: string | number;
  digits?: number;
  size?: string;
  color?: string;
}

/** Seven-segment scoreboard digits. Supports 0-9, "-", ":" and spaces. */
export function LedDigits({ value, digits, size = "1.5em", color = "var(--led)" }: LedDigitsProps) {
  const raw = String(value);
  const text = digits ? raw.padStart(digits, " ").slice(-digits) : raw;
  let x = 0;
  const cells = [...text].map((ch, i) => {
    const left = x;
    x += ch === ":" ? 6 : 15;
    if (ch === ":") {
      return (
        <g key={i} fill={color}>
          <rect x={left + 1} y={5} width={2.2} height={2.2} />
          <rect x={left + 1} y={12.8} width={2.2} height={2.2} />
        </g>
      );
    }
    const lit = LIT[ch] ?? "";
    return (
      <g key={i} transform={`translate(${left} 0)`}>
        {SEGMENTS.map((seg) => {
          const [rx, ry, w, h] = RECTS[seg];
          const on = lit.includes(seg);
          return <rect key={seg} x={rx} y={ry} width={w} height={h} rx={0.9} fill={on ? color : "var(--led-dim)"} opacity={on ? 1 : 0.5} />;
        })}
      </g>
    );
  });
  const width = Math.max(x - 3, 1);
  return (
    <svg className="led" viewBox={`0 0 ${width} 20`} style={{ height: size, aspectRatio: `${width} / 20` }} role="img" aria-label={raw}>
      {cells}
    </svg>
  );
}
```

`frontend/src/arcade/arcade.css`:
```css
/* Arcade surface for /tv, /play and /host. Scoped so Steven's pastel hat page is untouched. */
body[data-surface="arcade"] {
  --arena-900: #06070d;
  --arena-800: #0d1020;
  --arena-700: #171b33;
  --arena-600: #242a4d;
  --led: #ffb000;
  --led-dim: #3b2a05;
  --hot: #ff4d00;
  --fire: #ffd000;
  --ink-hi: #f7f3ea;
  --ink-lo: #9aa0b8;
  --good: #35e07f;
  --bad: #ff3b5c;
  --font-display: "Bungee", system-ui, sans-serif;
  --font-pixel: "Press Start 2P", ui-monospace, monospace;

  margin: 0;
  color: var(--ink-hi);
  font-family: system-ui, -apple-system, "Segoe UI", sans-serif;
  background: radial-gradient(ellipse at 50% -20%, #232a55 0%, var(--arena-900) 65%) fixed;
  -webkit-tap-highlight-color: transparent;
}

.arcade {
  min-height: 100%;
}

.display {
  font-family: var(--font-display);
  letter-spacing: 0.02em;
  text-transform: uppercase;
}

.pixel {
  font-family: var(--font-pixel);
  font-size: 0.7em;
  line-height: 1.6;
}

.avatar {
  display: inline-grid;
  place-items: center;
  flex: none;
  box-sizing: border-box;
  border-radius: 50%;
  border: 3px solid var(--ink-lo);
  background: var(--arena-700);
  overflow: hidden;
  line-height: 1;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar--fire {
  animation: flicker 0.5s infinite alternate;
}

@keyframes flicker {
  from { box-shadow: 0 0 0 3px var(--fire), 0 0 18px 4px var(--hot); }
  to { box-shadow: 0 0 0 3px var(--hot), 0 0 34px 12px var(--fire); }
}

.btn {
  font-family: var(--font-display);
  font-size: 1.05rem;
  border: none;
  border-radius: 14px;
  padding: 0.8rem 1.1rem;
  min-height: 56px;
  color: var(--arena-900);
  background: var(--led);
  box-shadow: 0 5px 0 #a86f00;
  cursor: pointer;
  touch-action: manipulation;
}

.btn:active {
  transform: translateY(3px);
  box-shadow: 0 2px 0 #a86f00;
}

.btn:disabled {
  opacity: 0.45;
}

.btn--ghost {
  background: var(--arena-600);
  color: var(--ink-hi);
  box-shadow: 0 5px 0 var(--arena-800);
}

.btn--danger {
  background: var(--bad);
  color: #fff;
  box-shadow: 0 5px 0 #a3182f;
}

.btn--good {
  background: var(--good);
  box-shadow: 0 5px 0 #168a48;
}

.btn--small {
  min-height: 40px;
  padding: 0.4rem 0.8rem;
  font-size: 0.85rem;
}

.input {
  font: inherit;
  font-size: 1.2rem;
  padding: 0.8rem 1rem;
  border-radius: 12px;
  border: 2px solid var(--arena-600);
  background: var(--arena-800);
  color: var(--ink-hi);
}

.led {
  display: block;
}

.banner {
  position: fixed;
  inset: 0 0 auto;
  z-index: 60;
  padding: 0.5rem;
  text-align: center;
  font-weight: 700;
  background: var(--bad);
  color: #fff;
}

.toast-stack {
  position: fixed;
  left: 12px;
  right: 12px;
  bottom: 12px;
  z-index: 40;
  display: grid;
  gap: 8px;
}

.toast {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 14px;
  background: var(--arena-600);
  box-shadow: 0 8px 24px rgb(0 0 0 / 50%);
}

.toast .btn {
  min-height: 44px;
  padding: 0.5rem 0.8rem;
}

.toast__text {
  flex: 1;
}

.toast--error {
  background: #4a1020;
}
```

- [ ] **Step 4: Run the tests and the build to verify they pass**

Run: `cd frontend && npx vitest run && npm run build`
Expected: all tests pass (`12`), and the build succeeds.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/arcade
git commit -m "feat(web): add arcade UI kit with LED digits, synth sounds and announcer voice"
```

---

### Task 9: Phone controller (`/play`)

**Files:**
- Create: `frontend/src/play/PlayPage.tsx`, `usePlayer.ts`, `useOutbox.ts`, `JoinFlow.tsx`, `RosterGrid.tsx`, `Toasts.tsx`, `play.css`
- Modify: `frontend/src/main.tsx`

**Interfaces:**
- Consumes:
  - `call`, `Ack` (socket); `usePartyState`, `useConnected`, `useNow`, `serverNow` (store); `Outbox`, `OutboxResult`; `loadIdentity`, `saveIdentity`; `uuid`.
  - `ArcadeSurface`, `Avatar`.
  - Contract types: `PublicState`, `PlayerView`, `TeamView`, `JoinIn`, `JoinOut`, `ResumeOut`.
- Produces:
  - `usePlayer(state)` returns `{ status: "joining" | "resuming" | "ready", identity, me: PlayerView | null, resumed: boolean, join(input: JoinInput): Promise<Ack<JoinOut>>, forget() }`.
  - `JoinInput = { requestId, name, emoji, teamId }`.
  - `useOutbox(playerId, onResult)` returns `{ outbox, pending }`.
  - The `PlayScreen` layout contains a `<div className="play__extras">` slot; Tasks 16 and 19 add to it.
- Visible text that later tests rely on:
  - `Your name`: the input placeholder.
  - Buttons `NEXT`, `I TOOK ONE`, `LOG +1 (n)`, `NOT ME` and `UNDO`.
  - Team buttons labelled with the team name.

- [ ] **Step 1: Write `useOutbox.ts` and `usePlayer.ts`**

`frontend/src/play/useOutbox.ts`:
```ts
import { useEffect, useMemo, useRef, useSyncExternalStore } from "react";
import { Outbox, type KeyValueStore, type OutboxResult } from "../party/outbox";
import { call } from "../party/socket";

const memory = new Map<string, string>();
const safeStore: KeyValueStore = {
  getItem(key) {
    try {
      return localStorage.getItem(key);
    } catch {
      return memory.get(key) ?? null;
    }
  },
  setItem(key, value) {
    try {
      localStorage.setItem(key, value);
    } catch {
      memory.set(key, value);
    }
  },
};

export function useOutbox(playerId: string, onResult: (result: OutboxResult) => void) {
  const onResultRef = useRef(onResult);
  useEffect(() => {
    onResultRef.current = onResult;
  }, [onResult]);
  const outbox = useMemo(
    () =>
      new Outbox({
        store: safeStore,
        key: `hoop.outbox.${playerId}`,
        send: (event, payload) => call<Record<string, unknown>>(event, payload),
        onResult: (result) => onResultRef.current(result),
      }),
    [playerId],
  );
  useEffect(() => () => outbox.dispose(), [outbox]);
  const pending = useSyncExternalStore(outbox.subscribe, outbox.snapshot);
  return { outbox, pending };
}
```

`frontend/src/play/usePlayer.ts`:
```ts
import { useCallback, useEffect, useState } from "react";
import type { JoinIn, JoinOut, PublicState, ResumeOut } from "../party/contract.gen";
import { loadIdentity, saveIdentity, type Identity } from "../party/identity";
import { call, type Ack } from "../party/socket";
import { useConnected } from "../party/store";

export interface JoinInput {
  requestId: string;
  name: string;
  emoji: string;
  teamId: string;
}

export type PlayerStatus = "joining" | "resuming" | "ready";

/** Who this phone is. Re-identifies on every (re)connect and forgets itself when the night changes. */
export function usePlayer(state: PublicState | null) {
  const connected = useConnected();
  const [identity, setIdentity] = useState<Identity | null>(loadIdentity);
  const [resumed, setResumed] = useState(false);

  const forget = useCallback(() => {
    saveIdentity(null);
    setIdentity(null);
    setResumed(false);
  }, []);

  useEffect(() => {
    if (!connected || !identity) {
      setResumed(false);
      return;
    }
    let live = true;
    void call<ResumeOut>("player:resume", { token: identity.token }).then((ack) => {
      if (!live) return;
      if (ack.ok) setResumed(true);
      else if (!ack.retryable) forget();
    });
    return () => {
      live = false;
    };
  }, [connected, identity, forget]);

  const nightId = state?.nightId;
  useEffect(() => {
    if (nightId && identity && nightId !== identity.nightId) forget();
  }, [nightId, identity, forget]);

  const join = useCallback(
    async (input: JoinInput): Promise<Ack<JoinOut>> => {
      if (!nightId) return { ok: false, error: "Still connecting — try again" };
      const payload: JoinIn = {
        requestId: input.requestId,
        name: input.name,
        avatar: { kind: "emoji", value: input.emoji },
        teamId: input.teamId,
      };
      const ack = await call<JoinOut>("player:join", payload);
      if (ack.ok) {
        const next = { nightId, playerId: ack.playerId, token: ack.token };
        saveIdentity(next);
        setIdentity(next);
        setResumed(true);
      }
      return ack;
    },
    [nightId],
  );

  const me = identity ? (state?.players.find((p) => p.id === identity.playerId) ?? null) : null;
  const status: PlayerStatus = !identity ? "joining" : resumed ? "ready" : "resuming";
  return { status, identity, me, resumed, join, forget };
}
```

- [ ] **Step 2: Write the join flow, roster grid and toasts**

`frontend/src/play/JoinFlow.tsx`:
```tsx
import { useState } from "react";
import type { JoinOut, PublicState } from "../party/contract.gen";
import { uuid } from "../party/ids";
import type { Ack } from "../party/socket";
import type { JoinInput } from "./usePlayer";

const EMOJIS = ["🏀", "🔥", "😎", "🐐", "👑", "🚀", "🦄", "🎯", "🌶️", "🍋", "🐻", "🦈", "👽", "🤠", "💎", "🧃"];

interface JoinFlowProps {
  state: PublicState;
  onJoin: (input: JoinInput) => Promise<Ack<JoinOut>>;
}

export function JoinFlow({ state, onJoin }: JoinFlowProps) {
  const [step, setStep] = useState<"name" | "avatar" | "team">("name");
  const [name, setName] = useState("");
  const [emoji, setEmoji] = useState(EMOJIS[0]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // Stable across retries: if an ack is lost, retrying returns the same player instead of a twin.
  const [requestId] = useState(uuid);

  async function pickTeam(teamId: string) {
    setBusy(true);
    setError(null);
    const ack = await onJoin({ requestId, name: name.trim(), emoji, teamId });
    setBusy(false);
    if (!ack.ok) {
      setError(ack.error);
      if (ack.error.includes("taken")) setStep("name");
    }
  }

  return (
    <div className="join-flow">
      <h1 className="display join-flow__title">
        HOOP
        <br />
        DREAMS
      </h1>
      {step === "name" && (
        <form
          className="join-flow__step"
          onSubmit={(e) => {
            e.preventDefault();
            if (name.trim()) setStep("avatar");
          }}
        >
          <label className="pixel" htmlFor="join-name">
            WHO'S CHECKING IN?
          </label>
          <input
            id="join-name"
            className="input"
            placeholder="Your name"
            autoComplete="nickname"
            maxLength={20}
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <button className="btn" disabled={!name.trim()}>
            NEXT
          </button>
        </form>
      )}
      {step === "avatar" && (
        <div className="join-flow__step">
          <p className="pixel">PICK YOUR JERSEY</p>
          <div className="emoji-grid">
            {EMOJIS.map((e) => (
              <button key={e} className={`emoji-grid__item${e === emoji ? " emoji-grid__item--on" : ""}`} onClick={() => setEmoji(e)}>
                {e}
              </button>
            ))}
          </div>
          <div className="join-flow__extras" />
          <button className="btn" onClick={() => setStep("team")}>
            NEXT
          </button>
          <button className="btn btn--ghost" onClick={() => setStep("name")}>
            BACK
          </button>
        </div>
      )}
      {step === "team" && (
        <div className="join-flow__step">
          <p className="pixel">PICK YOUR TEAM</p>
          {state.teams.map((t) => (
            <button key={t.id} className="team-pick display" style={{ background: t.color }} disabled={busy} onClick={() => void pickTeam(t.id)}>
              {t.name}
              <span className="team-pick__size">
                {t.size} {t.size === 1 ? "player" : "players"}
              </span>
            </button>
          ))}
          <button className="btn btn--ghost" onClick={() => setStep("avatar")}>
            BACK
          </button>
        </div>
      )}
      {error && (
        <p className="join-flow__error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
```

`frontend/src/play/RosterGrid.tsx`:
```tsx
import type { CSSProperties } from "react";
import { Avatar } from "../arcade/Avatar";
import type { PlayerView, TeamView } from "../party/contract.gen";

interface RosterGridProps {
  players: PlayerView[];
  teams: TeamView[];
  selected: string[];
  onToggle: (playerId: string) => void;
}

export function RosterGrid({ players, teams, selected, onToggle }: RosterGridProps) {
  const color = new Map(teams.map((t) => [t.id, t.color]));
  const sorted = [...players].sort((a, b) => a.name.localeCompare(b.name));
  if (sorted.length === 0) return <p className="squad__empty">Nobody else is here yet. Get them to scan the TV.</p>;
  return (
    <ul className="roster">
      {sorted.map((p) => {
        const on = selected.includes(p.id);
        return (
          <li key={p.id}>
            <button
              className={`roster__item${on ? " roster__item--on" : ""}`}
              aria-pressed={on}
              onClick={() => onToggle(p.id)}
              style={{ "--team": color.get(p.teamId) } as CSSProperties}
            >
              <Avatar avatar={p.avatar} size="52px" color={color.get(p.teamId)} />
              <span className="roster__name">{p.name}</span>
              {on && <span className="roster__check">✓</span>}
            </button>
          </li>
        );
      })}
    </ul>
  );
}
```

`frontend/src/play/Toasts.tsx`:
```tsx
export interface UndoToast {
  requestId: string;
  label: string;
  until: number;
}

export interface NotMeToast {
  shotId: string;
  by: string;
}

interface ToastsProps {
  undo: UndoToast | null;
  onUndo: () => void;
  notMe: NotMeToast | null;
  onNotMe: (shotId: string) => void;
  onFair: (shotId: string) => void;
  error: string | null;
}

export function Toasts({ undo, onUndo, notMe, onNotMe, onFair, error }: ToastsProps) {
  return (
    <div className="toast-stack">
      {error && (
        <div className="toast toast--error" role="alert">
          <span className="toast__text">{error}</span>
        </div>
      )}
      {notMe && (
        <div className="toast">
          <span className="toast__text">
            <b>{notMe.by}</b> logged a shot for you
          </span>
          <button className="btn btn--danger" onClick={() => onNotMe(notMe.shotId)}>
            NOT ME
          </button>
          <button className="btn btn--ghost" aria-label="Fair" onClick={() => onFair(notMe.shotId)}>
            👍
          </button>
        </div>
      )}
      {undo && (
        <div className="toast">
          <span className="toast__text">+1 for {undo.label}</span>
          <button className="btn btn--ghost" onClick={onUndo}>
            UNDO
          </button>
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 3: Write the page**

`frontend/src/play/PlayPage.tsx`:
```tsx
import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import { Avatar } from "../arcade/Avatar";
import { ArcadeSurface } from "../arcade/Surface";
import type { PlayerView, PublicState } from "../party/contract.gen";
import { uuid } from "../party/ids";
import type { OutboxResult } from "../party/outbox";
import { call } from "../party/socket";
import { useConnected, useNow, usePartyState } from "../party/store";
import { JoinFlow } from "./JoinFlow";
import { RosterGrid } from "./RosterGrid";
import { Toasts, type UndoToast } from "./Toasts";
import { useOutbox } from "./useOutbox";
import { usePlayer } from "./usePlayer";
import "./play.css";

export default function PlayPage() {
  const state = usePartyState();
  const connected = useConnected();
  const player = usePlayer(state);

  let body;
  if (!state) body = <p className="play__center display">Finding the party…</p>;
  else if (player.status === "joining") body = <JoinFlow state={state} onJoin={player.join} />;
  else if (player.me) body = <PlayScreen state={state} me={player.me} resumed={player.resumed} />;
  else if (player.status === "resuming") body = <p className="play__center display">Checking in…</p>;
  else body = <Gone onRejoin={player.forget} />;

  return (
    <ArcadeSurface>
      {!connected && <div className="banner">Reconnecting…</div>}
      <div className="play">{body}</div>
    </ArcadeSurface>
  );
}

function Gone({ onRejoin }: { onRejoin: () => void }) {
  return (
    <div className="play__center">
      <p className="display">You're off the roster</p>
      <p>The host removed or merged your player.</p>
      <button className="btn" onClick={onRejoin}>
        JOIN AGAIN
      </button>
    </div>
  );
}

function PlayScreen({ state, me, resumed }: { state: PublicState; me: PlayerView; resumed: boolean }) {
  const now = useNow(1000);
  const [selected, setSelected] = useState<string[]>([]);
  const [locked, setLocked] = useState(false);
  const [undo, setUndo] = useState<UndoToast | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [dismissed, setDismissed] = useState<string[]>([]);
  const names = useMemo(() => new Map(state.players.map((p) => [p.id, p.name])), [state.players]);
  const namesRef = useRef(names);
  useEffect(() => {
    namesRef.current = names;
  }, [names]);
  const undoMs = state.settings.undoWindowSec * 1000;

  const onResult = useCallback(
    (result: OutboxResult) => {
      if (result.item.event !== "shot:log") return;
      if (!result.ack.ok) {
        setError(result.ack.error);
        return;
      }
      const ids = result.item.payload.drinkerIds as string[];
      const label = ids.map((id) => namesRef.current.get(id) ?? "someone").join(", ");
      setUndo({ requestId: result.item.requestId, label, until: Date.now() + undoMs - 1000 });
    },
    [undoMs],
  );

  const { outbox, pending } = useOutbox(me.id, onResult);
  useEffect(() => {
    if (resumed) void outbox.resume();
    else outbox.pause();
  }, [outbox, resumed]);

  useEffect(() => {
    if (!error) return;
    const id = setTimeout(() => setError(null), 4000);
    return () => clearTimeout(id);
  }, [error]);

  const inFeed = new Set(state.feed.map((f) => f.requestId));
  const unsent = pending.filter((i) => !inFeed.has(i.requestId));
  const mine = unsent.filter((i) => i.event === "shot:log" && (i.payload.drinkerIds as string[]).includes(me.id)).length;

  async function log(drinkerIds: string[]) {
    if (locked) return;
    setLocked(true);
    setTimeout(() => setLocked(false), 2000);
    if ("vibrate" in navigator) navigator.vibrate(40);
    setSelected([]);
    await outbox.push("shot:log", { requestId: uuid(), drinkerIds });
  }

  async function doUndo() {
    if (!undo) return;
    const ack = await call("shot:undo", { requestId: undo.requestId });
    setUndo(null);
    if (!ack.ok) setError(ack.error);
  }

  async function doNotMe(shotId: string) {
    setDismissed((d) => [...d, shotId]);
    const ack = await call("shot:reject", { shotId });
    if (!ack.ok) setError(ack.error);
  }

  const notMeItem = state.feed.find(
    (f) =>
      f.drinkerId === me.id &&
      f.loggedById !== null &&
      f.loggedById !== me.id &&
      now - f.at < state.settings.notMeWindowSec * 1000 &&
      !dismissed.includes(f.shotId),
  );
  const team = state.teams.find((t) => t.id === me.teamId);

  return (
    <>
      <header className="me" style={{ "--team": team?.color } as CSSProperties}>
        <Avatar avatar={me.avatar} size="56px" color={team?.color} fire={me.streak === "fire"} />
        <div className="me__who">
          <span className="display">{me.name}</span>
          <span className="me__team">{team?.name}</span>
        </div>
        <div className="me__score">
          <span className="display me__count">{me.count + mine}</span>
          <span className="pixel">#{me.rank}</span>
        </div>
      </header>
      {me.streak && <p className={`streak streak--${me.streak} pixel`}>{me.streak === "fire" ? "🔥 ON FIRE 🔥" : "HEATING UP"}</p>}

      <button className="shot-button display" disabled={locked} onClick={() => void log([me.id])}>
        <span className="shot-button__glass" aria-hidden>
          🥃
        </span>
        I TOOK ONE
      </button>
      {unsent.length > 0 && <p className="sending">sending {unsent.length}…</p>}

      <div className="play__extras" />

      <section className="squad">
        <h2 className="pixel squad__title">LOG FOR THE SQUAD</h2>
        <RosterGrid
          players={state.players.filter((p) => p.id !== me.id)}
          teams={state.teams}
          selected={selected}
          onToggle={(id) => setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]))}
        />
      </section>

      <Standings state={state} me={me} />

      {selected.length > 0 && (
        <div className="log-bar">
          <button className="btn btn--ghost" onClick={() => setSelected([])}>
            CLEAR
          </button>
          <button className="btn log-bar__go" disabled={locked} onClick={() => void log(selected)}>
            LOG +1 ({selected.length})
          </button>
        </div>
      )}

      <Toasts
        undo={undo && Date.now() < undo.until ? undo : null}
        onUndo={() => void doUndo()}
        notMe={notMeItem ? { shotId: notMeItem.shotId, by: names.get(notMeItem.loggedById ?? "") ?? "Someone" } : null}
        onNotMe={(shotId) => void doNotMe(shotId)}
        onFair={(shotId) => setDismissed((d) => [...d, shotId])}
        error={error}
      />
    </>
  );
}

function Standings({ state, me }: { state: PublicState; me: PlayerView }) {
  const top = state.players.slice(0, 5);
  return (
    <section className="standings">
      <h2 className="pixel squad__title">STANDINGS</h2>
      <ol>
        {top.map((p) => (
          <li key={p.id} className={p.id === me.id ? "standings__me" : undefined}>
            <span className="standings__rank">{p.rank}</span>
            <span className="standings__name">
              {p.name}
              {p.streak === "fire" ? " 🔥" : ""}
            </span>
            <span className="display">{p.count}</span>
          </li>
        ))}
      </ol>
    </section>
  );
}
```

`frontend/src/play/play.css`:
```css
.play {
  max-width: 520px;
  margin: 0 auto;
  padding: 16px 16px 120px;
  display: grid;
  gap: 16px;
}

.play__center {
  min-height: 70vh;
  display: grid;
  place-content: center;
  gap: 12px;
  text-align: center;
}

.me {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 18px;
  background: var(--arena-700);
  border-left: 8px solid var(--team, var(--ink-lo));
}

.me__who {
  flex: 1;
  display: grid;
}

.me__team {
  color: var(--ink-lo);
  font-size: 0.85rem;
}

.me__score {
  display: grid;
  justify-items: end;
}

.me__count {
  font-size: 2.4rem;
  color: var(--led);
  line-height: 1;
}

.streak {
  margin: 0;
  text-align: center;
  padding: 8px;
  border-radius: 12px;
}

.streak--heating {
  background: #4a2a00;
  color: var(--led);
}

.streak--fire {
  background: linear-gradient(90deg, var(--hot), var(--fire), var(--hot));
  color: var(--arena-900);
  animation: flicker 0.5s infinite alternate;
}

.shot-button {
  min-height: 38vh;
  border: none;
  border-radius: 28px;
  font-size: 2.2rem;
  color: var(--arena-900);
  background: radial-gradient(circle at 50% 30%, #ffd46b, var(--led) 45%, #d47a00);
  box-shadow: 0 10px 0 #8a5200, 0 20px 40px rgb(255 176 0 / 25%);
  display: grid;
  place-content: center;
  gap: 8px;
  touch-action: manipulation;
  cursor: pointer;
}

.shot-button:active {
  transform: translateY(6px);
  box-shadow: 0 4px 0 #8a5200;
}

.shot-button:disabled {
  filter: saturate(0.4);
}

.shot-button__glass {
  font-size: 4.5rem;
}

.sending {
  margin: -8px 0 0;
  text-align: center;
  color: var(--ink-lo);
}

.squad__title {
  margin: 0 0 8px;
  color: var(--ink-lo);
}

.squad__empty {
  color: var(--ink-lo);
}

.roster {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(92px, 1fr));
  gap: 10px;
}

.roster__item {
  position: relative;
  width: 100%;
  display: grid;
  justify-items: center;
  gap: 6px;
  padding: 10px 6px;
  border-radius: 16px;
  border: 2px solid transparent;
  background: var(--arena-700);
  color: var(--ink-hi);
  font: inherit;
}

.roster__item--on {
  border-color: var(--team, var(--led));
  background: var(--arena-600);
}

.roster__name {
  font-size: 0.85rem;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.roster__check {
  position: absolute;
  top: 6px;
  right: 8px;
  color: var(--good);
  font-weight: 900;
}

.log-bar {
  position: fixed;
  left: 12px;
  right: 12px;
  bottom: 12px;
  z-index: 30;
  display: flex;
  gap: 10px;
}

.log-bar__go {
  flex: 1;
}

.standings ol {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 6px;
}

.standings li {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 8px 12px;
  border-radius: 12px;
  background: var(--arena-800);
}

.standings__me {
  outline: 2px solid var(--led);
}

.standings__rank {
  width: 1.5em;
  color: var(--ink-lo);
}

.standings__name {
  flex: 1;
}

.join-flow {
  display: grid;
  gap: 18px;
  padding-top: 4vh;
}

.join-flow__title {
  margin: 0;
  text-align: center;
  font-size: 3rem;
  line-height: 0.95;
  color: var(--led);
  text-shadow: 0 4px 0 #8a5200;
}

.join-flow__step {
  display: grid;
  gap: 12px;
}

.join-flow__error {
  color: var(--bad);
  text-align: center;
}

.emoji-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.emoji-grid__item {
  font-size: 2rem;
  aspect-ratio: 1;
  border-radius: 14px;
  border: 2px solid transparent;
  background: var(--arena-700);
}

.emoji-grid__item--on {
  border-color: var(--led);
  background: var(--arena-600);
}

.team-pick {
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 72px;
  padding: 0 20px;
  border: none;
  border-radius: 18px;
  font-size: 1.6rem;
  color: #fff;
  text-shadow: 0 2px 0 rgb(0 0 0 / 35%);
}

.team-pick__size {
  font-family: system-ui, sans-serif;
  font-size: 0.9rem;
  text-transform: none;
}
```

`frontend/src/main.tsx` (full file):
```tsx
import { StrictMode, Suspense, lazy } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router'
import './index.css'
import App from './App.tsx'

const PlayPage = lazy(() => import('./play/PlayPage.tsx'))

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <Suspense fallback={null}>
        <Routes>
          <Route path="/" element={<App />} />
          <Route path="/play" element={<PlayPage />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  </StrictMode>,
)
```

- [ ] **Step 4: Verify build, lint and behaviour**

Run: `cd frontend && npm run build && npx oxlint src/play src/party src/arcade && npx vitest run`
Expected: the build succeeds, oxlint reports `0 errors`, and the tests pass.

Then run a manual smoke check with two terminals:
- Backend: `cd backend && venv/bin/uvicorn app.main:app --port 8000`
- Frontend: `cd frontend && npm run dev -- --host`

Open `http://localhost:5173/play` in a browser at a phone-sized viewport and check:
- Typing a name, then NEXT, NEXT and HOME shows the play screen.
- **I TOOK ONE** increments the count and shows an **UNDO** toast.
- A second browser profile that joins and taps the first player in LOG FOR THE SQUAD makes a **NOT ME** toast appear on the first.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/play frontend/src/main.tsx
git commit -m "feat(web): add phone controller with join flow, shot button, squad logging and toasts"
```

---

### Task 10: TV stage: moment queue and combo rules

**Files:**
- Create: `frontend/src/tv/stage/stage.ts`, `frontend/src/tv/stage/headline.ts`
- Test: `frontend/src/tv/stage/stage.test.ts`, `frontend/src/tv/stage/headline.test.ts`

**Interfaces:**
- Consumes: `Moment` (store).
- Produces:
  - Types: `ShotMoment`, `StageKind`, `StageItem {key, kind, moments, shownAt, until}`, `StageState {current, queue}`, `StageTiming {comboWindowMs, comboMaxMs}`.
  - `DURATION_MS`, `emptyStage`, `isShot(m)`, `stageKind(m) -> StageKind | null`.
  - `enqueue(stage, m, now, timing)`, `advance(stage, now)`, `finish(stage, key, now)`.
  - `shotHeadline(shots, teamOf) -> string`, `latestDrinkers(shots) -> ShotDrinker[]`.
- Rules:
  - **Ignored moments:** shots from games (`source` starting with `game:`) and `game` moments. Game plugins render those themselves.
  - **Combos:** a shot merges into the current shot takeover if it arrives within `comboWindowMs` of the takeover's last shot and `comboMaxMs` hasn't passed since it appeared.
  - **Queued shots:** otherwise, a shot merges into the queue's last item if that item is a shot within the window.
  - **Queue cap:** at most 20 items are kept.

- [ ] **Step 1: Write the failing tests**

`frontend/src/tv/stage/stage.test.ts`:
```ts
import { describe, expect, it } from "vitest";
import type { Moment } from "../../party/store";
import { advance, emptyStage, enqueue, finish, type StageState } from "./stage";

const timing = { comboWindowMs: 4000, comboMaxMs: 8000 };

function shot(id: string, at: number, source = "manual"): Moment {
  return { type: "shot", id, at, requestId: id, source, loggedById: null, drinkers: [{ playerId: `p-${id}`, count: 1, streak: null }] };
}

function milestone(id: string, at: number): Moment {
  return { type: "milestone", id, at, scope: "party", playerId: null, value: 25 };
}

function play(moments: [Moment, number][]): StageState {
  return moments.reduce((s, [m, now]) => advance(enqueue(s, m, now, timing), now), emptyStage);
}

describe("stage", () => {
  it("shows the first moment right away with its duration", () => {
    const s = play([[shot("a", 1000), 1000]]);
    expect(s.current?.key).toBe("a");
    expect(s.current?.until).toBe(1000 + 3200);
  });

  it("merges shots arriving during a shot takeover into a combo", () => {
    const s = play([
      [shot("a", 1000), 1000],
      [shot("b", 2000), 2000],
      [shot("c", 3500), 3500],
    ]);
    expect(s.current?.moments.map((m) => m.id)).toEqual(["a", "b", "c"]);
    expect(s.current?.until).toBe(3500 + 4000);
    expect(s.queue).toEqual([]);
  });

  it("caps a combo at comboMaxMs after it appeared", () => {
    let s = play([[shot("a", 0), 0]]);
    for (const t of [3000, 6000]) s = advance(enqueue(s, shot(`s${t}`, t), t, timing), t);
    expect(s.current?.until).toBe(8000);
    s = advance(enqueue(s, shot("late", 8500), 8500, timing), 8500);
    expect(s.current?.key).toBe("late");
  });

  it("queues other moments first-in first-out", () => {
    let s = play([
      [milestone("m1", 0), 0],
      [milestone("m2", 100), 100],
    ]);
    expect(s.current?.key).toBe("m1");
    s = advance(s, 3499);
    expect(s.current?.key).toBe("m1");
    s = advance(s, 3500);
    expect(s.current?.key).toBe("m2");
    s = advance(s, 7000);
    expect(s.current).toBeNull();
  });

  it("merges shots that queue up behind another moment", () => {
    const s = play([
      [milestone("m", 0), 0],
      [shot("a", 100), 100],
      [shot("b", 900), 900],
    ]);
    expect(s.queue).toHaveLength(1);
    expect(s.queue[0].moments.map((m) => m.id)).toEqual(["a", "b"]);
  });

  it("leaves game shots and game moments to the game overlay", () => {
    const game: Moment = { type: "game", id: "g", at: 0, gameId: "challenges", kind: "violation", data: {} };
    expect(play([[shot("a", 0, "game:challenges"), 0], [game, 0]])).toEqual(emptyStage);
  });

  it("finish ends the current item early", () => {
    let s = play([[milestone("m1", 0), 0], [milestone("m2", 0), 0]]);
    s = advance(finish(s, "m1", 500), 500);
    expect(s.current?.key).toBe("m2");
    expect(finish(s, "nope", 600)).toBe(s);
  });
});
```

`frontend/src/tv/stage/headline.test.ts`:
```ts
import { describe, expect, it } from "vitest";
import { latestDrinkers, shotHeadline } from "./headline";
import type { ShotMoment } from "./stage";

function shot(drinkers: [string, number, "heating" | "fire" | null][]): ShotMoment {
  return {
    type: "shot", id: drinkers.map((d) => d[0]).join(), at: 0, requestId: "r", source: "manual", loggedById: null,
    drinkers: drinkers.map(([playerId, count, streak]) => ({ playerId, count, streak })),
  };
}

const teams: Record<string, string> = { jess: "home", alex: "home", kim: "home", sam: "away" };
const teamOf = (id: string) => teams[id];

describe("shotHeadline", () => {
  it("uses the streak for a single shot", () => {
    expect(shotHeadline([shot([["jess", 1, null]])], teamOf)).toBe("BUCKET!");
    expect(shotHeadline([shot([["jess", 2, "heating"]])], teamOf)).toBe("HEATING UP!");
    expect(shotHeadline([shot([["jess", 3, "fire"]])], teamOf)).toBe("ON FIRE!");
  });

  it("calls two shots a double and bigger groups team shots or combos", () => {
    expect(shotHeadline([shot([["jess", 1, null]]), shot([["sam", 1, null]])], teamOf)).toBe("DOUBLE!");
    expect(shotHeadline([shot([["jess", 1, null], ["alex", 1, null], ["kim", 1, null]])], teamOf)).toBe("TEAM SHOT ×3");
    expect(shotHeadline([shot([["jess", 1, null], ["sam", 1, null], ["kim", 1, null]])], teamOf)).toBe("COMBO ×3");
  });
});

describe("latestDrinkers", () => {
  it("keeps one entry per player with their newest count", () => {
    const merged = latestDrinkers([shot([["jess", 1, null]]), shot([["jess", 2, "heating"], ["sam", 1, null]])]);
    expect(merged).toEqual([
      { playerId: "jess", count: 2, streak: "heating" },
      { playerId: "sam", count: 1, streak: null },
    ]);
  });
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd frontend && npx vitest run src/tv`
Expected: FAIL with `Failed to resolve import "./stage"`.

- [ ] **Step 3: Implement the stage and headlines**

`frontend/src/tv/stage/stage.ts`:
```ts
import type { Moment } from "../../party/store";

export type ShotMoment = Extract<Moment, { type: "shot" }>;
export type StageKind = "shot" | "milestone" | "lead-change" | "waved-off" | "replay";

export interface StageItem {
  key: string;
  kind: StageKind;
  moments: Moment[];
  shownAt: number | null;
  until: number | null;
}

export interface StageState {
  current: StageItem | null;
  queue: StageItem[];
}

export interface StageTiming {
  comboWindowMs: number;
  comboMaxMs: number;
}

/** Replays end early via finish() when the clip ends; 16 s is only a safety cap. */
export const DURATION_MS: Record<StageKind, number> = {
  shot: 3200,
  milestone: 3500,
  "lead-change": 3200,
  "waved-off": 2600,
  replay: 16_000,
};

const MAX_QUEUE = 20;

export const emptyStage: StageState = { current: null, queue: [] };

export function isShot(m: Moment): m is ShotMoment {
  return m.type === "shot";
}

/** Game plugins render their own shots and moments; the stage only runs party moments. */
export function stageKind(m: Moment): StageKind | null {
  if (m.type === "game") return null;
  if (m.type === "shot" && m.source.startsWith("game:")) return null;
  return m.type;
}

function lastAt(item: StageItem): number {
  return item.moments[item.moments.length - 1].at;
}

export function enqueue(stage: StageState, m: Moment, now: number, timing: StageTiming): StageState {
  const kind = stageKind(m);
  if (kind === null) return stage;
  if (kind === "shot") {
    const cur = stage.current;
    if (
      cur?.kind === "shot" &&
      cur.shownAt !== null &&
      now - cur.shownAt < timing.comboMaxMs &&
      m.at - lastAt(cur) <= timing.comboWindowMs
    ) {
      const until = Math.min(cur.shownAt + timing.comboMaxMs, Math.max(cur.until ?? now, now + timing.comboWindowMs));
      return { ...stage, current: { ...cur, moments: [...cur.moments, m], until } };
    }
    const tail = stage.queue[stage.queue.length - 1];
    if (tail?.kind === "shot" && m.at - lastAt(tail) <= timing.comboWindowMs) {
      return { ...stage, queue: [...stage.queue.slice(0, -1), { ...tail, moments: [...tail.moments, m] }] };
    }
  }
  const item: StageItem = { key: m.id, kind, moments: [m], shownAt: null, until: null };
  return { ...stage, queue: [...stage.queue, item].slice(-MAX_QUEUE) };
}

export function advance(stage: StageState, now: number): StageState {
  const cur = stage.current;
  if (cur && cur.until !== null && now < cur.until) return stage;
  if (stage.queue.length === 0) return cur ? { current: null, queue: stage.queue } : stage;
  const [next, ...rest] = stage.queue;
  return { current: { ...next, shownAt: now, until: now + DURATION_MS[next.kind] }, queue: rest };
}

export function finish(stage: StageState, key: string, now: number): StageState {
  if (stage.current?.key !== key) return stage;
  return { ...stage, current: { ...stage.current, until: now } };
}
```

`frontend/src/tv/stage/headline.ts`:
```ts
import type { ShotMoment } from "./stage";

type Drinker = ShotMoment["drinkers"][number];

export function shotHeadline(shots: ShotMoment[], teamOf: (playerId: string) => string | undefined): string {
  const drinkers = shots.flatMap((s) => s.drinkers);
  if (drinkers.length <= 1) {
    const streak = drinkers[0]?.streak;
    return streak === "fire" ? "ON FIRE!" : streak === "heating" ? "HEATING UP!" : "BUCKET!";
  }
  if (drinkers.length === 2) return "DOUBLE!";
  const teams = new Set(drinkers.map((d) => teamOf(d.playerId)));
  return teams.size === 1 ? `TEAM SHOT ×${drinkers.length}` : `COMBO ×${drinkers.length}`;
}

/** One entry per player, with the count and streak from their newest shot. */
export function latestDrinkers(shots: ShotMoment[]): Drinker[] {
  const byPlayer = new Map<string, Drinker>();
  for (const s of shots) for (const d of s.drinkers) byPlayer.set(d.playerId, d);
  return [...byPlayer.values()];
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd frontend && npx vitest run`
Expected: all pass (`22` tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/tv/stage
git commit -m "feat(tv): add stage queue with combo merging and headline rules"
```

---

### Task 11: TV jumbotron (`/tv`)

**Files:**
- Create: `frontend/src/tv/TvPage.tsx`, `Scoreboard.tsx`, `Leaderboard.tsx`, `Feed.tsx`, `Reel.tsx`, `JoinPanel.tsx`, `wifi.ts`, `tv.css`
- Create: `frontend/src/tv/stage/useStage.ts`, `frontend/src/tv/stage/Stage.tsx`, `frontend/src/tv/stage/cue.ts`
- Modify: `frontend/src/main.tsx`
- Test: `frontend/src/tv/wifi.test.ts` (Wi-Fi QR escaping)

**Interfaces:**
- Consumes:
  - The Task 10 stage functions.
  - `sfx`, `unlockAudio`, `setSoundEnabled`; `announce`, `setVoiceEnabled`; `LINES`, `pick`.
  - `Avatar`, `LedDigits`, `ArcadeSurface`.
  - `usePartyState`, `onMoment`, `useNow`; `formatClock`, `ago`.
- Produces:
  - `wifiQr(ssid, password) -> string`.
  - `TvPage` (default export) with the structure: the `.tv` grid, then `Scoreboard`, then `Leaderboard`, then aside `.tv__side` (`Reel`, `Feed`), then footer `.tv__foot` (`.tv__strips` slot, `JoinPanel`), then `.tv__games` (the overlay slot for Task 19), then `Stage`.
- Visible text: `TIP OFF`; takeover headlines; `logged by <name>`; `NO GOOD!`; `INSTANT REPLAY`.

- [ ] **Step 1: Write the failing test**

`frontend/src/tv/wifi.test.ts`:
```ts
import { describe, expect, it } from "vitest";
import { wifiQr } from "./wifi";

describe("wifiQr", () => {
  it("builds a WPA join string and escapes special characters", () => {
    expect(wifiQr("Hoop;House", 'pa:ss"1')).toBe('WIFI:T:WPA;S:Hoop\\;House;P:pa\\:ss\\"1;;');
  });

  it("uses nopass for open networks", () => {
    expect(wifiQr("Open Court", null)).toBe("WIFI:T:nopass;S:Open Court;;");
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend && npx vitest run src/tv/wifi.test.ts`
Expected: FAIL with `Failed to resolve import "./wifi"`.

- [ ] **Step 3: Implement the TV**

`frontend/src/tv/wifi.ts`:
```ts
/** Contents of a "join this Wi-Fi" QR code (the format phone cameras understand). */
export function wifiQr(ssid: string, password: string | null): string {
  const esc = (s: string) => s.replace(/([\\;,:"])/g, "\\$1");
  return password ? `WIFI:T:WPA;S:${esc(ssid)};P:${esc(password)};;` : `WIFI:T:nopass;S:${esc(ssid)};;`;
}
```

`frontend/src/tv/JoinPanel.tsx`:
```tsx
import QRCode from "qrcode";
import { useEffect, useState } from "react";
import type { JoinInfo } from "../party/contract.gen";
import { wifiQr } from "./wifi";

interface Code {
  label: string;
  sub: string;
  text: string;
}

export function JoinPanel({ join }: { join: JoinInfo }) {
  const codes: Code[] = [];
  if (join.wifiSsid) codes.push({ label: "1 · JOIN WI-FI", sub: join.wifiSsid, text: wifiQr(join.wifiSsid, join.wifiPassword) });
  codes.push({ label: join.wifiSsid ? "2 · SCAN TO PLAY" : "SCAN TO PLAY", sub: join.lanUrl.replace(/^https?:\/\//, ""), text: join.lanUrl });
  if (join.tunnelUrl) codes.push({ label: "NOT ON WI-FI?", sub: "cell data works here", text: `${join.tunnelUrl}/play` });
  return (
    <div className="join">
      {codes.map((c) => (
        <QrCard key={c.label} {...c} />
      ))}
    </div>
  );
}

function QrCard({ label, sub, text }: Code) {
  const [src, setSrc] = useState("");
  useEffect(() => {
    let live = true;
    void QRCode.toDataURL(text, { margin: 1, width: 320, color: { dark: "#06070dff", light: "#f7f3eaff" } }).then((url) => {
      if (live) setSrc(url);
    });
    return () => {
      live = false;
    };
  }, [text]);
  return (
    <figure className="qr">
      {src && <img src={src} alt={`QR code for ${text}`} />}
      <figcaption>
        <span className="pixel">{label}</span>
        <span className="qr__sub">{sub}</span>
      </figcaption>
    </figure>
  );
}
```

`frontend/src/tv/Scoreboard.tsx`:
```tsx
import { Fragment, type CSSProperties } from "react";
import { LedDigits } from "../arcade/LedDigits";
import type { PublicState } from "../party/contract.gen";
import { useNow } from "../party/store";
import { formatClock } from "../party/time";

export function Scoreboard({ state }: { state: PublicState }) {
  const now = useNow(1000);
  const middle = Math.ceil(state.teams.length / 2);
  return (
    <header className="scoreboard">
      <div className="scoreboard__logo display">
        HOOP
        <br />
        DREAMS
      </div>
      <div className="scoreboard__teams">
        {state.teams.map((t, i) => (
          <Fragment key={t.id}>
            {i === middle && <Clock now={now} startedAt={state.startedAt} total={state.total} />}
            <div className="scoreboard__team" style={{ "--team": t.color } as CSSProperties}>
              <span className="scoreboard__name display">{t.name}</span>
              <LedDigits value={t.total} digits={3} size="5.2vw" />
            </div>
          </Fragment>
        ))}
      </div>
    </header>
  );
}

function Clock({ now, startedAt, total }: { now: number; startedAt: number; total: number }) {
  return (
    <div className="scoreboard__clock">
      <LedDigits value={formatClock(now - startedAt)} size="2.6vw" color="var(--bad)" />
      <span className="pixel">TOTAL {total}</span>
    </div>
  );
}
```

`frontend/src/tv/Leaderboard.tsx`:
```tsx
import { AnimatePresence, motion } from "motion/react";
import { useEffect, useState, type CSSProperties } from "react";
import { Avatar } from "../arcade/Avatar";
import { LedDigits } from "../arcade/LedDigits";
import type { PublicState } from "../party/contract.gen";

const PAGE = 10;

export function Leaderboard({ state }: { state: PublicState }) {
  const pages = Math.max(1, Math.ceil(state.players.length / PAGE));
  const [tick, setTick] = useState(0);
  useEffect(() => {
    if (pages <= 1) return;
    const id = setInterval(() => setTick((t) => t + 1), 8000);
    return () => clearInterval(id);
  }, [pages]);
  const page = tick % pages;
  const teams = new Map(state.teams.map((t) => [t.id, t]));

  if (state.players.length === 0) {
    return (
      <main className="board board--empty">
        <p className="display">Scan the code to get in the game →</p>
      </main>
    );
  }
  return (
    <main className="board">
      <h2 className="board__title pixel">
        LEADERBOARD{pages > 1 ? ` · ${page + 1}/${pages}` : ""}
      </h2>
      <ol className="board__list">
        <AnimatePresence initial={false}>
          {state.players.slice(page * PAGE, page * PAGE + PAGE).map((p) => {
            const team = teams.get(p.teamId);
            return (
              <motion.li
                key={p.id}
                layout
                initial={{ opacity: 0, x: -40 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0 }}
                className={`board__row${p.streak ? ` board__row--${p.streak}` : ""}${p.connected ? "" : " board__row--away"}`}
                style={{ "--team": team?.color } as CSSProperties}
              >
                <span className="board__rank display">{p.rank}</span>
                <Avatar avatar={p.avatar} size="3.4vw" color={team?.color} fire={p.streak === "fire"} />
                <span className="board__name display">
                  {p.name}
                  {p.streak === "fire" && " 🔥"}
                </span>
                {p.streak === "heating" && <span className="board__tag pixel">HEATING UP</span>}
                <LedDigits value={p.count} digits={2} size="2.8vw" />
              </motion.li>
            );
          })}
        </AnimatePresence>
      </ol>
    </main>
  );
}
```

`frontend/src/tv/Feed.tsx`:
```tsx
import type { PublicState } from "../party/contract.gen";
import { useNow } from "../party/store";
import { ago } from "../party/time";

export function Feed({ state }: { state: PublicState }) {
  const now = useNow(5000);
  const names = new Map(state.players.map((p) => [p.id, p.name]));
  const name = (id: string) => names.get(id) ?? "?";
  return (
    <section className="feed">
      <h2 className="pixel feed__title">PLAY-BY-PLAY</h2>
      <ul>
        {state.feed.slice(0, 7).map((f) => (
          <li key={f.shotId} className="feed__item">
            <span className="feed__who">
              {f.source.startsWith("game:") ? "🎯 " : ""}
              {f.loggedById && f.loggedById !== f.drinkerId ? `${name(f.loggedById)} → ${name(f.drinkerId)}` : name(f.drinkerId)}
            </span>
            <span className="feed__plus display">+1</span>
            <span className="feed__ago">{ago(now - f.at)}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}
```

`frontend/src/tv/Reel.tsx`:
```tsx
import { AnimatePresence, motion } from "motion/react";
import { useEffect, useState } from "react";
import type { PublicState } from "../party/contract.gen";

export function Reel({ state }: { state: PublicState }) {
  const items = state.reel;
  const [tick, setTick] = useState(0);
  const seconds = state.settings.reelItemSec;
  useEffect(() => {
    if (items.length <= 1) return;
    const id = setInterval(() => setTick((t) => t + 1), seconds * 1000);
    return () => clearInterval(id);
  }, [items.length, seconds]);

  if (items.length === 0) {
    return (
      <section className="reel reel--empty">
        <p className="pixel">📸 SHOT-CAM HIGHLIGHTS LAND HERE</p>
      </section>
    );
  }
  const item = items[tick % items.length];
  return (
    <section className="reel">
      <AnimatePresence mode="wait">
        <motion.div key={item.mediaId} className="reel__frame" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
          {item.kind === "video" ? (
            <video src={item.url} poster={item.posterUrl ?? undefined} autoPlay muted loop playsInline />
          ) : (
            <img src={item.url} alt="" />
          )}
        </motion.div>
      </AnimatePresence>
      <span className="reel__label pixel">HIGHLIGHTS</span>
    </section>
  );
}
```

`frontend/src/tv/stage/useStage.ts`:
```ts
import { useCallback, useEffect, useRef, useState } from "react";
import { onMoment } from "../../party/store";
import { advance, emptyStage, enqueue, finish, type StageItem, type StageState, type StageTiming } from "./stage";

interface StageCallbacks {
  onShow: (item: StageItem) => void;
  onMerge: (item: StageItem) => void;
}

export function useStage(timing: StageTiming, callbacks: StageCallbacks) {
  const [stage, setStage] = useState<StageState>(emptyStage);
  const timingRef = useRef(timing);
  const callbacksRef = useRef(callbacks);
  useEffect(() => {
    timingRef.current = timing;
    callbacksRef.current = callbacks;
  });

  useEffect(
    () =>
      onMoment((m) =>
        setStage((s) => {
          const now = Date.now();
          return advance(enqueue(s, m, now, timingRef.current), now);
        }),
      ),
    [],
  );
  useEffect(() => {
    const id = setInterval(() => setStage((s) => advance(s, Date.now())), 200);
    return () => clearInterval(id);
  }, []);

  // Cue sound and voice outside the reducer: once per new item, plus a swish per merged shot.
  const current = stage.current;
  const seen = useRef<{ key: string | null; size: number }>({ key: null, size: 0 });
  useEffect(() => {
    const previous = seen.current;
    seen.current = { key: current?.key ?? null, size: current?.moments.length ?? 0 };
    if (!current) return;
    if (current.key !== previous.key) callbacksRef.current.onShow(current);
    else if (current.moments.length > previous.size) callbacksRef.current.onMerge(current);
  }, [current]);

  const done = useCallback((key: string) => setStage((s) => advance(finish(s, key, Date.now()), Date.now())), []);
  return { current, done };
}
```

`frontend/src/tv/stage/cue.ts`:
```ts
import { LINES, pick } from "../../arcade/lines";
import { sfx } from "../../arcade/sound";
import { announce } from "../../arcade/voice";
import type { PublicState } from "../../party/contract.gen";
import { latestDrinkers } from "./headline";
import { isShot, type StageItem } from "./stage";

/** Sound plus announcer line for a stage item as it takes the screen. */
export function cue(item: StageItem, state: PublicState): void {
  const name = (id: string | null | undefined) => state.players.find((p) => p.id === id)?.name ?? "Somebody";
  const m = item.moments[0];
  switch (m.type) {
    case "shot": {
      const drinkers = latestDrinkers(item.moments.filter(isShot));
      const lead = drinkers[0];
      if (drinkers.length > 1) {
        sfx.bucket();
        announce(pick(LINES.group, { n: drinkers.length }));
      } else if (lead?.streak === "fire") {
        sfx.fire();
        announce(pick(LINES.fire, { name: name(lead.playerId) }));
      } else {
        sfx.swish();
        announce(pick(lead?.streak === "heating" ? LINES.heating : LINES.shot, { name: name(lead?.playerId) }));
      }
      return;
    }
    case "milestone":
      sfx.horn();
      if (m.scope === "first") announce(pick(LINES.first, { name: name(m.playerId) }));
      else if (m.scope === "player") announce(pick(LINES.playerMilestone, { name: name(m.playerId), value: m.value }));
      else announce(pick(m.value === 100 ? LINES.century : LINES.partyMilestone, { value: m.value }));
      return;
    case "lead-change":
      sfx.horn();
      announce(pick(LINES.lead, { team: state.teams.find((t) => t.id === m.teamId)?.name ?? "The visitors" }));
      return;
    case "waved-off":
      sfx.whistle();
      announce(pick(LINES.wavedOff));
      return;
    case "replay":
      sfx.bucket();
      announce(pick(LINES.replay));
      return;
    default:
      return;
  }
}
```

`frontend/src/tv/stage/Stage.tsx`:
```tsx
import { AnimatePresence, motion } from "motion/react";
import { useCallback, useEffect, useRef, type CSSProperties } from "react";
import { Avatar } from "../../arcade/Avatar";
import { LedDigits } from "../../arcade/LedDigits";
import { sfx } from "../../arcade/sound";
import type { PublicState } from "../../party/contract.gen";
import { cue } from "./cue";
import { latestDrinkers, shotHeadline } from "./headline";
import { isShot, type StageItem } from "./stage";
import { useStage } from "./useStage";

export function Stage({ state }: { state: PublicState }) {
  const stateRef = useRef(state);
  useEffect(() => {
    stateRef.current = state;
  });
  const onShow = useCallback((item: StageItem) => cue(item, stateRef.current), []);
  const onMerge = useCallback(() => sfx.swish(), []);
  const { current, done } = useStage(
    { comboWindowMs: state.settings.comboWindowMs, comboMaxMs: state.settings.comboMaxMs },
    { onShow, onMerge },
  );
  const currentKey = current?.key;
  const finishCurrent = useCallback(() => {
    if (currentKey) done(currentKey);
  }, [currentKey, done]);
  return (
    <AnimatePresence>
      {current && (
        <motion.div
          key={current.key}
          className="takeover"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2 }}
        >
          <Takeover item={current} state={state} onDone={finishCurrent} />
        </motion.div>
      )}
    </AnimatePresence>
  );
}

function Headline({ children, color }: { children: string; color?: string }) {
  return (
    <motion.h1
      className="takeover__headline display"
      style={{ color }}
      initial={{ scale: 3, rotate: -12, opacity: 0 }}
      animate={{ scale: 1, rotate: -4, opacity: 1 }}
      transition={{ type: "spring", stiffness: 420, damping: 18 }}
    >
      {children}
    </motion.h1>
  );
}

function Takeover({ item, state, onDone }: { item: StageItem; state: PublicState; onDone: () => void }) {
  const players = new Map(state.players.map((p) => [p.id, p]));
  const teams = new Map(state.teams.map((t) => [t.id, t]));
  const m = item.moments[0];

  // Photo replays hold for 5 s; video replays end when the clip does (the stage's 16 s cap is a backstop).
  const isPhotoReplay = m.type === "replay" && m.item.kind === "photo";
  useEffect(() => {
    if (!isPhotoReplay) return;
    const id = setTimeout(onDone, 5000);
    return () => clearTimeout(id);
  }, [isPhotoReplay, onDone]);

  if (m.type === "shot") {
    const shots = item.moments.filter(isShot);
    const drinkers = latestDrinkers(shots).filter((d) => players.has(d.playerId));
    const drinkerIds = new Set(drinkers.map((d) => d.playerId));
    const loggers = [...new Set(shots.map((s) => s.loggedById).filter((id): id is string => !!id && !drinkerIds.has(id)))];
    const big = drinkers.length <= 3;
    return (
      <>
        <Headline>{shotHeadline(shots, (id) => players.get(id)?.teamId)}</Headline>
        <div className="takeover__people">
          {drinkers.map((d, i) => {
            const p = players.get(d.playerId)!;
            const color = teams.get(p.teamId)?.color;
            return (
              <motion.div
                key={d.playerId}
                className="takeover__person"
                style={{ "--team": color } as CSSProperties}
                initial={{ y: 80, opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                transition={{ delay: 0.08 + i * 0.06 }}
              >
                <Avatar avatar={p.avatar} size={big ? "13vw" : "7vw"} color={color} fire={d.streak === "fire"} />
                <span className="takeover__name display">{p.name}</span>
                <LedDigits value={d.count} size={big ? "5vw" : "3vw"} />
              </motion.div>
            );
          })}
        </div>
        {loggers.length > 0 && <p className="takeover__by">logged by {loggers.map((id) => players.get(id)?.name ?? "?").join(", ")}</p>}
      </>
    );
  }

  if (m.type === "milestone") {
    const p = m.playerId ? players.get(m.playerId) : undefined;
    const title =
      m.scope === "first"
        ? "FIRST BUCKET"
        : m.scope === "player"
          ? `${p?.name ?? "?"} HITS ${m.value}`
          : m.value === 100
            ? "CENTURY CLUB"
            : `${m.value} SHOTS TONIGHT`;
    return (
      <>
        <Headline color="var(--fire)">{title}</Headline>
        {p && <Avatar avatar={p.avatar} size="14vw" color={teams.get(p.teamId)?.color} fire />}
        {m.scope === "first" && p && <p className="takeover__sub display">{p.name}</p>}
      </>
    );
  }

  if (m.type === "lead-change") {
    const team = teams.get(m.teamId);
    return (
      <div className="takeover__lead" style={{ "--team": team?.color } as CSSProperties}>
        <Headline>LEAD CHANGE!</Headline>
        <p className="takeover__sub display">{team?.name ?? "?"} TAKES THE LEAD</p>
      </div>
    );
  }

  if (m.type === "waved-off") {
    const p = players.get(m.playerId);
    const why = m.reason === "not_me" ? "overturned on review" : m.reason === "host" ? "ref's call" : "waved off";
    return (
      <>
        <Headline color="var(--bad)">NO GOOD!</Headline>
        <p className="takeover__sub display">
          {p?.name ?? "?"} · {why}
        </p>
      </>
    );
  }

  if (m.type === "replay") {
    const by = players.get(m.item.playerId);
    return (
      <div className="replay">
        <p className="replay__label pixel">INSTANT REPLAY</p>
        {m.item.kind === "video" ? (
          <video className="replay__media" src={m.item.url} autoPlay playsInline onEnded={onDone} onError={onDone} />
        ) : (
          <img className="replay__media" src={m.item.url} alt="" onError={onDone} />
        )}
        {by && <p className="replay__by display">📸 {by.name}</p>}
      </div>
    );
  }
  return null;
}
```

`frontend/src/tv/TvPage.tsx`:
```tsx
import { useEffect, useState } from "react";
import { setSoundEnabled, unlockAudio } from "../arcade/sound";
import { ArcadeSurface } from "../arcade/Surface";
import { announce, setVoiceEnabled } from "../arcade/voice";
import { usePartyState } from "../party/store";
import { Feed } from "./Feed";
import { JoinPanel } from "./JoinPanel";
import { Leaderboard } from "./Leaderboard";
import { Reel } from "./Reel";
import { Scoreboard } from "./Scoreboard";
import { Stage } from "./stage/Stage";
import "./tv.css";

export default function TvPage() {
  const state = usePartyState();
  const [tippedOff, setTippedOff] = useState(false);
  const sound = state?.settings.sound ?? true;
  const voice = state?.settings.voice ?? true;
  useEffect(() => setSoundEnabled(sound), [sound]);
  useEffect(() => setVoiceEnabled(voice), [voice]);

  // Browsers only allow sound and speech after a click, so the host tips off once per page load.
  async function tipOff() {
    await unlockAudio();
    announce("Welcome to Hoop Dreams! Let's get it!");
    setTippedOff(true);
  }

  if (!state) {
    return (
      <ArcadeSurface>
        <div className="tv tv--loading display">Warming up…</div>
      </ArcadeSurface>
    );
  }
  return (
    <ArcadeSurface>
      <div className="tv">
        <Scoreboard state={state} />
        <Leaderboard state={state} />
        <aside className="tv__side">
          <Reel state={state} />
          <Feed state={state} />
        </aside>
        <footer className="tv__foot">
          <div className="tv__strips" />
          <JoinPanel join={state.join} />
        </footer>
        <div className="tv__games" />
        <Stage state={state} />
        {!tippedOff && (
          <button className="tipoff" onClick={() => void tipOff()}>
            <span className="display">🏀 TIP OFF</span>
            <span className="pixel">click once to turn on sound</span>
          </button>
        )}
      </div>
    </ArcadeSurface>
  );
}
```

`frontend/src/tv/tv.css`:
```css
.tv {
  position: relative;
  box-sizing: border-box;
  height: 100vh;
  overflow: hidden;
  display: grid;
  grid-template-columns: 1fr 30vw;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: 1.2vw;
  padding: 1.2vw;
  font-size: 1.2vw;
}

.tv--loading {
  place-content: center;
  font-size: 4vw;
}

.scoreboard {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: 2vw;
  padding: 0.8vw 1.5vw;
  border-radius: 1vw;
  background: linear-gradient(180deg, #151935, #0a0c18);
  border: 0.25vw solid #2b3160;
  box-shadow: inset 0 0 3vw rgb(0 0 0 / 60%);
}

.scoreboard__logo {
  font-size: 2.2vw;
  line-height: 0.95;
  color: var(--led);
  text-shadow: 0 0.3vw 0 #8a5200;
}

.scoreboard__teams {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 3vw;
}

.scoreboard__team {
  display: grid;
  justify-items: center;
  gap: 0.4vw;
  padding: 0.5vw 1.5vw;
  border-radius: 0.8vw;
  border-bottom: 0.5vw solid var(--team);
  background: rgb(255 255 255 / 4%);
}

.scoreboard__name {
  font-size: 1.6vw;
  color: var(--team);
}

.scoreboard__clock {
  display: grid;
  justify-items: center;
  gap: 0.5vw;
  color: var(--ink-lo);
}

.board {
  grid-column: 1;
  grid-row: 2;
  min-height: 0;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
}

.board--empty {
  place-content: center;
  font-size: 2.4vw;
  color: var(--ink-lo);
}

.board__title {
  margin: 0 0 0.6vw;
  color: var(--ink-lo);
}

.board__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.5vw;
  align-content: start;
}

.board__row {
  display: flex;
  align-items: center;
  gap: 1.2vw;
  padding: 0.5vw 1.2vw;
  border-radius: 0.8vw;
  background: var(--arena-700);
  border-left: 0.5vw solid var(--team);
}

.board__row--heating {
  background: linear-gradient(90deg, #3a2200, var(--arena-700) 60%);
}

.board__row--fire {
  background: linear-gradient(90deg, #7a1c00, #3a1200 40%, var(--arena-700));
  background-size: 200% 100%;
  animation: blaze 1.2s ease-in-out infinite alternate;
}

@keyframes blaze {
  from { background-position: 0% 0; box-shadow: 0 0 1vw rgb(255 77 0 / 40%); }
  to { background-position: 100% 0; box-shadow: 0 0 2.5vw rgb(255 208 0 / 55%); }
}

.board__row--away {
  opacity: 0.55;
}

.board__rank {
  width: 2.5vw;
  font-size: 2vw;
  color: var(--ink-lo);
}

.board__name {
  flex: 1;
  font-size: 1.9vw;
}

.board__tag {
  color: var(--led);
}

.tv__side {
  grid-column: 2;
  grid-row: 2;
  min-height: 0;
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  gap: 1vw;
}

.reel {
  position: relative;
  min-height: 0;
  border-radius: 1vw;
  overflow: hidden;
  background: var(--arena-800);
  border: 0.2vw solid var(--arena-600);
}

.reel--empty {
  display: grid;
  place-content: center;
  text-align: center;
  color: var(--ink-lo);
  padding: 2vw;
}

.reel__frame,
.reel__frame img,
.reel__frame video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.reel__label {
  position: absolute;
  top: 0.8vw;
  left: 0.8vw;
  padding: 0.3vw 0.6vw;
  background: var(--bad);
  border-radius: 0.3vw;
}

.feed__title {
  margin: 0 0 0.4vw;
  color: var(--ink-lo);
}

.feed ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.3vw;
}

.feed__item {
  display: flex;
  gap: 0.8vw;
  align-items: baseline;
}

.feed__who {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.feed__plus {
  color: var(--good);
}

.feed__ago {
  color: var(--ink-lo);
  width: 3vw;
  text-align: right;
}

.tv__foot {
  grid-column: 1 / -1;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 1.5vw;
}

.tv__strips {
  flex: 1;
}

.join {
  display: flex;
  gap: 1vw;
}

.qr {
  margin: 0;
  display: flex;
  gap: 0.8vw;
  align-items: center;
  padding: 0.6vw;
  border-radius: 0.8vw;
  background: var(--arena-700);
}

.qr img {
  width: 7.5vw;
  height: 7.5vw;
  border-radius: 0.4vw;
}

.qr figcaption {
  display: grid;
  gap: 0.4vw;
  max-width: 12vw;
}

.qr__sub {
  color: var(--ink-lo);
  font-size: 0.9vw;
  word-break: break-all;
}

.takeover {
  position: absolute;
  inset: 0;
  z-index: 20;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 2vw;
  text-align: center;
  background: radial-gradient(circle at 50% 45%, rgb(35 42 85 / 92%), rgb(6 7 13 / 96%) 70%);
}

.takeover__headline {
  margin: 0;
  font-size: 9vw;
  line-height: 1;
  color: var(--led);
  text-shadow: 0 0.6vw 0 #8a5200, 0 0 4vw rgb(255 176 0 / 50%);
}

.takeover__people {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 3vw;
}

.takeover__person {
  display: grid;
  justify-items: center;
  gap: 0.8vw;
}

.takeover__name {
  font-size: 3vw;
  color: var(--team, var(--ink-hi));
}

.takeover__by,
.takeover__sub {
  margin: 0;
  font-size: 2.2vw;
  color: var(--ink-lo);
}

.takeover__lead {
  display: grid;
  gap: 2vw;
  padding: 3vw 6vw;
  border-radius: 2vw;
  background: linear-gradient(135deg, var(--team), transparent 80%);
}

.replay {
  display: grid;
  justify-items: center;
  gap: 1vw;
}

.replay__label {
  margin: 0;
  padding: 0.4vw 1vw;
  background: var(--bad);
  font-size: 1.4vw;
}

.replay__media {
  max-width: 80vw;
  max-height: 70vh;
  border-radius: 1vw;
  border: 0.4vw solid var(--ink-hi);
}

.replay__by {
  margin: 0;
  font-size: 2.4vw;
}

.tipoff {
  position: absolute;
  inset: 0;
  z-index: 50;
  display: grid;
  place-content: center;
  gap: 1.5vw;
  border: none;
  color: var(--ink-hi);
  background: rgb(6 7 13 / 88%);
  cursor: pointer;
}

.tipoff .display {
  font-size: 7vw;
  color: var(--led);
}
```

`frontend/src/main.tsx` (full file):
```tsx
import { StrictMode, Suspense, lazy } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router'
import './index.css'
import App from './App.tsx'

const PlayPage = lazy(() => import('./play/PlayPage.tsx'))
const TvPage = lazy(() => import('./tv/TvPage.tsx'))

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <Suspense fallback={null}>
        <Routes>
          <Route path="/" element={<App />} />
          <Route path="/play" element={<PlayPage />} />
          <Route path="/tv" element={<TvPage />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  </StrictMode>,
)
```

- [ ] **Step 4: Verify build, tests and behaviour**

Run: `cd frontend && npx vitest run && npm run build && npx oxlint src/tv`
Expected: `24` tests pass, the build succeeds, and oxlint reports `0 errors`.

Then run a manual smoke check with the two dev servers from Task 9 running:
- Open `http://localhost:5173/tv` at 1920×1080 and click **TIP OFF**. You should hear the welcome line.
- Log a shot from `/play` in another window. The TV should show the **BUCKET!** takeover with the player's avatar, name and LED count, a swish sound and an announcer line.
- The first shot of the night should be followed by **FIRST BUCKET** and **LEAD CHANGE!**.
- The leaderboard should update, and the QR code should show the LAN URL.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/tv frontend/src/main.tsx
git commit -m "feat(tv): add jumbotron with scoreboard, leaderboard, feed, QR join and hype takeovers"
```

---

### Task 12: Party mode: serve the build, tunnel endpoint, launcher script

**Files:**
- Modify: `backend/app/party/web.py`
- Create: `backend/scripts/party.py`
- Modify: `README.md` (add a "Party mode" section)
- Test: `backend/tests/test_web.py`

**Interfaces:**
- Consumes: `service`, `HOST_PIN`, `party_router` (Task 6).
- Produces:
  - `web.SPAStaticFiles`.
  - `web.DIST_DIR`.
  - `POST /api/party/tunnel`, with body `{pin, url|null}` and response `{ok: true}`, or 403 for a wrong PIN.
  - `scripts/party.py` with the flags `--tunnel`, `--demo`, `--no-build` and `--no-open`.

- [ ] **Step 1: Write the failing tests**

`backend/tests/test_web.py`:
```python
import httpx
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.party.web import SPAStaticFiles
from tests.conftest import SocketClient


def test_spa_serves_app_routes_but_not_unknown_api_or_media(tmp_path):
    (tmp_path / "index.html").write_text("<html>app</html>")
    (tmp_path / "app.js").write_text("js")
    app = FastAPI()
    app.mount("/", SPAStaticFiles(directory=tmp_path, html=True))
    client = TestClient(app)
    assert client.get("/tv").text == "<html>app</html>"
    assert client.get("/play").text == "<html>app</html>"
    assert client.get("/app.js").text == "js"
    assert client.get("/api/nope").status_code == 404
    assert client.get("/media/nope.jpg").status_code == 404


async def test_tunnel_url_reaches_the_tv_with_the_right_pin(server_url):
    tv = SocketClient(server_url)
    await tv.connect()
    try:
        async with httpx.AsyncClient() as http:
            bad = await http.post(f"{server_url}/api/party/tunnel", json={"pin": "0000", "url": "https://x.trycloudflare.com"})
            assert bad.status_code == 403
            ok = await http.post(f"{server_url}/api/party/tunnel", json={"pin": "4242", "url": "https://x.trycloudflare.com"})
            assert ok.json() == {"ok": True}
        await tv.wait_for(lambda: tv.state["join"]["tunnelUrl"] == "https://x.trycloudflare.com", "tunnel url")
    finally:
        await tv.close()
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && venv/bin/python -m pytest tests/test_web.py -q`
Expected: FAIL with `ImportError: cannot import name 'SPAStaticFiles'`.

- [ ] **Step 3: Implement SPA serving and the tunnel endpoint**

In `backend/app/party/web.py`, replace the import block with:
```python
import asyncio
import os
import secrets
from contextlib import asynccontextmanager
from pathlib import Path

import socketio
from fastapi import APIRouter, FastAPI, HTTPException
from pydantic import BaseModel
from starlette.exceptions import HTTPException as StarletteHTTPException
from starlette.staticfiles import StaticFiles

from . import net
from .realtime import SioEmitter, register_handlers
from .service import PartyService
```
After the `HOST_PIN = ...` line add:
```python
DIST_DIR = Path(os.environ.get("HOOP_DIST", Path(__file__).resolve().parents[3] / "frontend" / "dist"))
```
After `party_health` add:
```python


class TunnelIn(BaseModel):
    pin: str
    url: str | None


@party_router.post("/tunnel")
async def set_tunnel(body: TunnelIn):
    """Called by scripts/party.py once cloudflared prints its public URL."""
    if not secrets.compare_digest(body.pin.encode(), HOST_PIN.encode()):
        raise HTTPException(status_code=403, detail="Wrong PIN")
    await service.set_tunnel(body.url)
    return {"ok": True}


class SPAStaticFiles(StaticFiles):
    """The built frontend. App routes (/tv, /play, /host) fall back to index.html; API and media 404s stay 404s."""

    async def get_response(self, path: str, scope):
        try:
            return await super().get_response(path, scope)
        except StarletteHTTPException as exc:
            if exc.status_code != 404 or path.split(os.sep, 1)[0] in {"api", "media", "socket.io"}:
                raise
            return await super().get_response("index.html", scope)
```
Replace `mount_party` with:
```python
def mount_party(app: FastAPI) -> None:
    app.include_router(party_router)
    app.mount("/socket.io", socketio.ASGIApp(sio, socketio_path=""))
    if os.environ.get("HOOP_PARTY") == "1":
        if not (DIST_DIR / "index.html").exists():
            raise RuntimeError(f"HOOP_PARTY=1 but {DIST_DIR} has no build. Run `npm run build` in frontend/.")
        app.mount("/", SPAStaticFiles(directory=DIST_DIR, html=True), name="spa")
```

- [ ] **Step 4: Write the launcher**

`backend/scripts/party.py`:
```python
"""Party mode: build the frontend, serve everything on :8000, keep the Mac awake, open the TV.

Usage (from the repo root):
    backend/venv/bin/python backend/scripts/party.py [--tunnel] [--demo] [--no-build] [--no-open]

The host PIN is printed below (set HOOP_HOST_PIN to choose it). Stop with Ctrl+C.
"""
import argparse
import json
import os
import re
import secrets
import signal
import subprocess
import sys
import threading
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BACKEND = ROOT / "backend"
FRONTEND = ROOT / "frontend"
PORT = 8000
CHROME = Path("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
TUNNEL_RE = re.compile(r"https://[a-z0-9-]+\.trycloudflare\.com")

sys.path.insert(0, str(BACKEND))
from app.party.net import join_url  # noqa: E402


class Supervisor:
    """Runs a command and restarts it whenever it exits, until stop()."""

    def __init__(self, name, cmd, *, cwd=None, env=None, on_line=None):
        self.name, self.cmd, self.cwd, self.env, self.on_line = name, cmd, cwd, env, on_line
        self.proc = None
        self.stopping = False

    def start(self):
        threading.Thread(target=self._loop, daemon=True).start()
        return self

    def _loop(self):
        while not self.stopping:
            self.proc = subprocess.Popen(
                self.cmd, cwd=self.cwd, env=self.env, text=True,
                stdout=subprocess.PIPE if self.on_line else None,
                stderr=subprocess.STDOUT if self.on_line else None,
            )
            if self.on_line:
                for line in self.proc.stdout:
                    self.on_line(line)
            code = self.proc.wait()
            if self.stopping:
                return
            print(f"⚠️  {self.name} exited ({code}); restarting in 1s", flush=True)
            time.sleep(1)

    def stop(self):
        self.stopping = True
        if self.proc and self.proc.poll() is None:
            self.proc.terminate()
            try:
                self.proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.proc.kill()


def wait_for_server(timeout=30):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/api/party/health", timeout=1):
                return
        except OSError:
            time.sleep(0.3)
    raise SystemExit("server did not come up. Check the output above.")


def post_tunnel(pin, url):
    body = json.dumps({"pin": pin, "url": url}).encode()
    request = urllib.request.Request(
        f"http://127.0.0.1:{PORT}/api/party/tunnel", data=body, headers={"Content-Type": "application/json"}
    )
    try:
        urllib.request.urlopen(request, timeout=2).close()
    except OSError:
        pass  # server restarting; the heartbeat retries


def start_tunnel(pin):
    latest = {"url": None}

    def on_line(line):
        match = TUNNEL_RE.search(line)
        if match and match.group(0) != latest["url"]:
            latest["url"] = match.group(0)
            print(f"🌐 Tunnel: {latest['url']}/play", flush=True)
            post_tunnel(pin, latest["url"])

    def heartbeat():  # re-announce so a restarted server learns the URL again
        while True:
            time.sleep(15)
            if latest["url"]:
                post_tunnel(pin, latest["url"])

    threading.Thread(target=heartbeat, daemon=True).start()
    return Supervisor("cloudflared", ["cloudflared", "tunnel", "--no-autoupdate", "--url", f"http://localhost:{PORT}"],
                      on_line=on_line).start()


def open_tv():
    url = f"http://localhost:{PORT}/tv"
    if CHROME.exists():
        profile = Path.home() / ".hoopdreams" / "chrome-tv"
        subprocess.Popen(
            [str(CHROME), f"--user-data-dir={profile}", "--kiosk", "--no-first-run",
             "--disable-session-crashed-bubble", url],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
    else:
        subprocess.run(["open", url], check=False)


def print_banner(url, pin):
    import qrcode

    print("\n🏀  HOOP DREAMS PARTY MODE\n", flush=True)
    qr = qrcode.QRCode(border=1)
    qr.add_data(url)
    qr.make(fit=True)
    qr.print_ascii(invert=True)
    print(f"\n  Phones:   {url}\n  TV:       http://localhost:{PORT}/tv\n  Host:     http://localhost:{PORT}/host   PIN {pin}\n")
    print("  Tip: mirror the Mac onto the TV (Control Center → Screen Mirroring), then click TIP OFF.\n", flush=True)


def _interrupt(*_):
    raise KeyboardInterrupt


def main():
    signal.signal(signal.SIGTERM, _interrupt)  # `kill` shuts down as cleanly as Ctrl+C
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--tunnel", action="store_true", help="also expose a public link for guests on cell data")
    parser.add_argument("--demo", action="store_true", help="add 8 simulated guests (rehearsal)")
    parser.add_argument("--no-build", action="store_true", help="reuse the existing frontend/dist build")
    parser.add_argument("--no-open", action="store_true", help="don't open the TV page in Chrome")
    args = parser.parse_args()

    pin = os.environ.get("HOOP_HOST_PIN") or f"{secrets.randbelow(10_000):04d}"
    if not args.no_build:
        subprocess.run(["npm", "run", "build"], cwd=FRONTEND, check=True)

    env = {**os.environ, "HOOP_PARTY": "1", "HOOP_HOST_PIN": pin, "HOOP_PUBLIC_PORT": str(PORT)}
    children = [Supervisor("server", [sys.executable, "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0",
                                      "--port", str(PORT), "--workers", "1", "--log-level", "warning"],
                           cwd=BACKEND, env=env).start()]
    caffeinate = subprocess.Popen(["caffeinate", "-dimsu", "-w", str(os.getpid())])
    wait_for_server()
    print_banner(join_url(), pin)

    if args.tunnel:
        children.append(start_tunnel(pin))
    if args.demo:
        children.append(Supervisor("demo", [sys.executable, str(BACKEND / "scripts" / "demo.py"),
                                            "--url", f"http://127.0.0.1:{PORT}"]).start())
    if not args.no_open:
        open_tv()

    try:
        while True:
            time.sleep(3600)
    except KeyboardInterrupt:
        print("\n🏁 Final buzzer. Shutting down.")
    finally:
        for child in reversed(children):
            child.stop()
        caffeinate.terminate()


if __name__ == "__main__":
    main()
```

In `README.md`, append after the "How it works" section:
````markdown

## Party mode (shot tracker)

Run it on the MacBook that is plugged into the TV:

```bash
backend/venv/bin/python backend/scripts/party.py            # add --tunnel for guests on cell data
```

This builds the frontend, serves everything on port 8000 and keeps the Mac awake. It prints a QR code and a host PIN, then opens the TV page in Chrome.

- **TV**: `http://localhost:8000/tv`. Mirror the Mac onto the TV and click **TIP OFF** once to turn on sound.
- **Phones**: scan the QR code on the TV (same Wi-Fi), or the "not on Wi-Fi?" code when `--tunnel` is on.
- **Host panel**: `http://localhost:8000/host` with the printed PIN (set `HOOP_HOST_PIN` to choose one).
- **Rehearsal**: add `--demo` for 8 simulated guests.

Development: run the backend as above, then `npm run dev -- --host` in `frontend/`. Open `/tv`, `/play` or `/host` on the Vite port, and set `HOOP_PUBLIC_PORT=5173` for the backend so the TV's QR code points at Vite.
Tests: `cd backend && venv/bin/python -m pytest` and `cd frontend && npm test`.
````

- [ ] **Step 5: Run the tests and a party-mode smoke check**

Run: `cd backend && venv/bin/python -m pytest -q`
Expected: all pass (`51 passed`).

Run: `cd frontend && npm run build && cd .. && (HOOP_HOST_PIN=1234 HOOP_DB=/tmp/hoop-party-smoke.db backend/venv/bin/python backend/scripts/party.py --no-build --no-open > /tmp/party.log 2>&1 &) ; for i in $(seq 1 60); do curl -sf localhost:8000/api/party/health >/dev/null && break; python3 -c "import time; time.sleep(.5)"; done; curl -s localhost:8000/tv | grep -c '<div id="root">'; curl -s -o /dev/null -w '%{http_code}\n' localhost:8000/api/nope; grep -E "Phones|PIN" /tmp/party.log; pkill -f "scripts/party.py"`
Expected: `1`, then `404`, then the Phones line and the Host line with `PIN 1234`.

- [ ] **Step 6: Commit**

```bash
git add backend/app/party/web.py backend/scripts/party.py backend/tests/test_web.py README.md
git commit -m "feat(party): add party-mode launcher, SPA serving and tunnel announcement"
```

---
### Task 13: Host backend: PIN auth and admin actions

**Files:**
- Create: `backend/app/party/admin.py`
- Modify: `backend/app/party/realtime.py` (host handlers; `admin` parameter)
- Modify: `backend/app/party/web.py` (construct `PartyAdmin`, pass it to `register_handlers`)
- Test: `backend/tests/test_admin.py`, `backend/tests/test_host_socket.py`

**Interfaces:**
- Consumes:
  - `PartyService` (Tasks 4–5), including `db()`, `teams`, `players`, `by_token`, `shots`, `media`, `save_setting`, `log_shots`, `void`, `update_player`, `new_night` and `broadcast`.
  - The host payload models from `contract`.
- Produces:
  - `PartyAdmin(service)`.
  - `async PartyAdmin.dispatch(action: str, payload: dict)`, which raises `PartyError` or `ValidationError`.
  - The `PartyAdmin.handlers` dict, which Task 17 extends with `game.enable`.
  - Socket events:
    - `host:auth {pin}`: 5 attempts per minute per socket. On success it joins room `host` and emits `host_state`.
    - `host:action {requestId, action, payload}`.
  - Actions: `team.upsert`, `team.remove`, `player.edit`, `player.remove`, `player.merge`, `shot.add`, `shot.void`, `settings.update`, `wifi.set`, `night.new`.

- [ ] **Step 1: Write the failing tests**

`backend/tests/test_admin.py`:
```python
import pytest
from pydantic import ValidationError

from app.party.admin import PartyAdmin
from app.party.errors import PartyError
from app.party.service import PartyService
from tests.conftest import join


@pytest.fixture
def admin(service):
    return PartyAdmin(service)


def teams_in_order(service):
    return sorted(service.teams.values(), key=lambda t: t.sort)


async def test_teams_can_be_added_and_renamed_up_to_four(admin, service):
    await admin.dispatch("team.upsert", {"name": "BENCH", "color": "#35E07F"})
    bench = teams_in_order(service)[-1]
    assert (bench.name, bench.sort) == ("BENCH", 2)
    await admin.dispatch("team.upsert", {"id": bench.id, "name": "BALLERS", "color": "#FFFFFF"})
    assert service.teams[bench.id].name == "BALLERS"
    await admin.dispatch("team.upsert", {"name": "FOURTH", "color": "#000000"})
    with pytest.raises(PartyError, match="4 teams max"):
        await admin.dispatch("team.upsert", {"name": "FIFTH", "color": "#000000"})
    with pytest.raises(PartyError, match="share a name"):
        await admin.dispatch("team.upsert", {"name": "home", "color": "#000000"})


async def test_team_removal_rules(admin, service):
    home, away = teams_in_order(service)
    with pytest.raises(PartyError, match="at least 2"):
        await admin.dispatch("team.remove", {"teamId": away.id})
    await join(service, "Jess")
    await admin.dispatch("team.upsert", {"name": "BENCH", "color": "#35E07F"})
    with pytest.raises(PartyError, match="Move everyone"):
        await admin.dispatch("team.remove", {"teamId": home.id})
    await admin.dispatch("team.remove", {"teamId": away.id})
    assert away.id not in service.teams


async def test_player_edit_and_remove(admin, service):
    jess = await join(service, "Jess")
    away = teams_in_order(service)[1].id
    await admin.dispatch("player.edit", {"playerId": jess, "name": "Jessica", "teamId": away})
    assert (service.players[jess].name, service.players[jess].team_id) == ("Jessica", away)
    token_hash = service.players[jess].token_hash
    await admin.dispatch("player.remove", {"playerId": jess})
    assert jess not in service.players and token_hash not in service.by_token


async def test_merge_moves_shots_and_skips_group_duplicates(admin, service, emitter, clock):
    jess, dup, sam = await join(service, "Jess"), await join(service, "Jess2"), await join(service, "Sam")
    await service.log_shots(sam, "group", [jess, dup])
    await service.log_shots(dup, "solo", [dup])
    await admin.dispatch("player.merge", {"fromId": dup, "intoId": jess})
    assert dup not in service.players
    assert service.counts()[jess] == 2
    assert all(s.logged_by_id != dup for s in service.shots.values())
    again = PartyService(emitter, clock=clock)
    again.load()
    assert again.counts()[jess] == 2 and dup not in again.players


async def test_host_can_add_and_void_shots(admin, service, emitter):
    jess = await join(service, "Jess")
    await admin.dispatch("shot.add", {"drinkerIds": [jess]})
    [shot] = service.shots.values()
    assert shot.source == "host" and shot.logged_by_id is None
    await admin.dispatch("shot.void", {"shotId": shot.id})
    assert service.counts()[jess] == 0
    assert emitter.moments[-1].type == "waved-off" and emitter.moments[-1].reason == "host"


async def test_settings_and_wifi_persist_across_restart(admin, service, emitter, clock):
    await admin.dispatch("settings.update", {"settings": {**service.settings.wire(), "voice": False}})
    await admin.dispatch("wifi.set", {"ssid": "Hoop House", "password": "buckets"})
    again = PartyService(emitter, clock=clock)
    again.load()
    assert again.settings.voice is False
    assert again.public_state().join.wifi_ssid == "Hoop House"


async def test_new_night_action(admin, service):
    await join(service, "Jess")
    await admin.dispatch("night.new", {"name": "Finals"})
    assert service.night_name == "Finals" and service.players == {}


async def test_unknown_actions_and_bad_payloads(admin):
    with pytest.raises(PartyError, match="Unknown host action"):
        await admin.dispatch("nope", {})
    with pytest.raises(ValidationError):
        await admin.dispatch("team.upsert", {"name": "X", "color": "red"})
```

`backend/tests/test_host_socket.py`:
```python
async def test_host_actions_need_the_pin(connect):
    host = await connect()
    denied = await host.call("host:action", {"requestId": "r", "action": "wifi.set", "payload": {}})
    assert denied == {"ok": False, "error": "Host PIN required"}
    assert (await host.call("host:auth", {"pin": "0000"})) == {"ok": False, "error": "Wrong PIN"}
    assert (await host.call("host:auth", {"pin": "4242"})) == {"ok": True}
    await host.wait_for(lambda: host.host_states, "host state")


async def test_pin_guessing_is_rate_limited(connect):
    host = await connect()
    for _ in range(5):
        await host.call("host:auth", {"pin": "0000"})
    ack = await host.call("host:auth", {"pin": "4242"})
    assert ack["ok"] is False and ack["error"].startswith("Too many")


async def test_host_actions_reach_every_screen(connect):
    host, tv = await connect(), await connect()
    await host.call("host:auth", {"pin": "4242"})
    ack = await host.call("host:action", {"requestId": "r1", "action": "wifi.set", "payload": {"ssid": "Hoop House", "password": "x"}})
    assert ack == {"ok": True}
    await tv.wait_for(lambda: tv.state["join"]["wifiSsid"] == "Hoop House", "wifi on the TV")
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && venv/bin/python -m pytest tests/test_admin.py tests/test_host_socket.py -q`
Expected: FAIL. `test_admin.py` fails with `ModuleNotFoundError: No module named 'app.party.admin'`, and the socket tests get `Unknown`/`None` acks.

- [ ] **Step 3: Implement `admin.py` and the host handlers**

`backend/app/party/admin.py`:
```python
"""Host-only operations, dispatched from the `host:action` socket event."""
from collections.abc import Awaitable, Callable
from typing import Any

from pydantic import BaseModel
from sqlalchemy import update

from .. import models
from .config import MAX_TEAMS, MIN_TEAMS
from .contract import (
    NightNewIn,
    PlayerEditIn,
    PlayerMergeIn,
    PlayerRemoveIn,
    PlayerUpdateIn,
    SettingsIn,
    ShotAddIn,
    ShotVoidIn,
    TeamRemoveIn,
    TeamUpsertIn,
    WifiIn,
)
from .errors import PartyError
from .service import PartyService, TeamRec, new_id

Handler = Callable[[Any], Awaitable[None]]


class PartyAdmin:
    def __init__(self, service: PartyService) -> None:
        self.s = service
        self.handlers: dict[str, tuple[type[BaseModel], Handler]] = {
            "team.upsert": (TeamUpsertIn, self.upsert_team),
            "team.remove": (TeamRemoveIn, self.remove_team),
            "player.edit": (PlayerEditIn, self.edit_player),
            "player.remove": (PlayerRemoveIn, self.remove_player),
            "player.merge": (PlayerMergeIn, self.merge_players),
            "shot.add": (ShotAddIn, self.add_shots),
            "shot.void": (ShotVoidIn, self.void_shot),
            "settings.update": (SettingsIn, self.update_settings),
            "wifi.set": (WifiIn, self.set_wifi),
            "night.new": (NightNewIn, self.new_night),
        }

    async def dispatch(self, action: str, payload: dict[str, Any]) -> None:
        entry = self.handlers.get(action)
        if entry is None:
            raise PartyError(f"Unknown host action: {action}")
        model, handler = entry
        await handler(model.model_validate(payload))

    async def upsert_team(self, req: TeamUpsertIn) -> None:
        s = self.s
        if any(t.name.casefold() == req.name.casefold() and t.id != req.id for t in s.teams.values()):
            raise PartyError("Two teams can't share a name")
        if req.id is None:
            if len(s.teams) >= MAX_TEAMS:
                raise PartyError(f"{MAX_TEAMS} teams max")
            team = TeamRec(new_id(), req.name, req.color, max((t.sort for t in s.teams.values()), default=-1) + 1)
            with s.db() as db:
                db.add(models.Team(id=team.id, night_id=s.night_id, name=team.name, color=team.color, sort=team.sort))
            s.teams[team.id] = team
        else:
            team = s.teams.get(req.id)
            if team is None:
                raise PartyError("That team doesn't exist")
            with s.db() as db:
                db.execute(update(models.Team).where(models.Team.id == team.id).values(name=req.name, color=req.color))
            team.name, team.color = req.name, req.color
        await s.broadcast()

    async def remove_team(self, req: TeamRemoveIn) -> None:
        s = self.s
        if req.team_id not in s.teams:
            raise PartyError("That team doesn't exist")
        if len(s.teams) <= MIN_TEAMS:
            raise PartyError(f"Keep at least {MIN_TEAMS} teams")
        if any(p.team_id == req.team_id for p in s.players.values()):
            raise PartyError("Move everyone off that team first")
        with s.db() as db:
            db.execute(update(models.Team).where(models.Team.id == req.team_id).values(removed_at=s.clock()))
        del s.teams[req.team_id]
        await s.broadcast()

    async def edit_player(self, req: PlayerEditIn) -> None:
        await self.s.update_player(req.player_id, PlayerUpdateIn(name=req.name, team_id=req.team_id))

    async def remove_player(self, req: PlayerRemoveIn) -> None:
        s = self.s
        rec = s.players.get(req.player_id)
        if rec is None:
            raise PartyError("Unknown player")
        with s.db() as db:
            db.execute(update(models.Player).where(models.Player.id == rec.id).values(removed_at=s.clock()))
        del s.players[rec.id]
        s.by_token.pop(rec.token_hash, None)
        await s.broadcast()

    async def merge_players(self, req: PlayerMergeIn) -> None:
        """Fold a duplicate join into the real player. Group shots already counted for both are voided."""
        s = self.s
        if req.from_id == req.into_id:
            raise PartyError("Pick two different players")
        src, dst = s.players.get(req.from_id), s.players.get(req.into_id)
        if src is None or dst is None:
            raise PartyError("Unknown player")
        dst_requests = {sh.request_id for sh in s.shots.values() if sh.drinker_id == dst.id}
        to_void = [sh for sh in s.shots.values() if sh.drinker_id == src.id and sh.request_id in dst_requests and sh.voided_at is None]
        to_move = [sh for sh in s.shots.values() if sh.drinker_id == src.id and sh.request_id not in dst_requests]
        now = s.clock()
        with s.db() as db:
            if to_void:
                db.execute(update(models.Shot).where(models.Shot.id.in_([sh.id for sh in to_void])).values(voided_at=now, void_reason="host"))
            if to_move:
                db.execute(update(models.Shot).where(models.Shot.id.in_([sh.id for sh in to_move])).values(drinker_id=dst.id))
            db.execute(update(models.Shot).where(models.Shot.logged_by_id == src.id).values(logged_by_id=dst.id))
            db.execute(update(models.Media).where(models.Media.player_id == src.id).values(player_id=dst.id))
            db.execute(update(models.Player).where(models.Player.id == src.id).values(removed_at=now))
        for sh in to_void:
            sh.voided_at, sh.void_reason = now, "host"
        for sh in to_move:
            sh.drinker_id = dst.id
        for sh in s.shots.values():
            if sh.logged_by_id == src.id:
                sh.logged_by_id = dst.id
        for m in s.media.values():
            if m.player_id == src.id:
                m.player_id = dst.id
        del s.players[src.id]
        s.by_token.pop(src.token_hash, None)
        await s.broadcast()

    async def add_shots(self, req: ShotAddIn) -> None:
        await self.s.log_shots(None, new_id(), req.drinker_ids, source="host", reason="host")

    async def void_shot(self, req: ShotVoidIn) -> None:
        shot = self.s.shots.get(req.shot_id)
        if shot is None:
            raise PartyError("Unknown shot")
        if shot.voided_at is None:
            await self.s.void([shot], "host")

    async def update_settings(self, req: SettingsIn) -> None:
        self.s.settings = req.settings
        self.s.save_setting("party", req.settings.model_dump_json())
        await self.s.broadcast()

    async def set_wifi(self, req: WifiIn) -> None:
        self.s.wifi = req
        self.s.save_setting("wifi", req.model_dump_json())
        await self.s.broadcast()

    async def new_night(self, req: NightNewIn) -> None:
        await self.s.new_night(req.name)
```

In `backend/app/party/realtime.py`:

1. Replace the import block with:
```python
import logging
import secrets
import time
from collections.abc import Awaitable, Callable
from dataclasses import dataclass, field
from typing import Any

import socketio
from pydantic import BaseModel, ValidationError

from .admin import PartyAdmin
from .contract import (
    HostActionIn,
    HostAuthIn,
    JoinIn,
    PlayerUpdateIn,
    ResumeIn,
    ResumeOut,
    ShotLogIn,
    ShotLogOut,
    ShotRejectIn,
    ShotUndoIn,
)
from .errors import PartyError
from .service import PartyService
```

2. Change the `register_handlers` signature to:
```python
def register_handlers(
    sio: socketio.AsyncServer, service: PartyService, *, host_pin: str, admin: PartyAdmin
) -> dict[str, Session]:
```

3. Insert these handlers just before `return sessions`:
```python
    @on("host:auth", HostAuthIn)
    async def host_auth(sid, session, payload):
        now = time.monotonic()
        session.pin_attempts = [t for t in session.pin_attempts if now - t < 60]
        if len(session.pin_attempts) >= 5:
            raise PartyError("Too many tries — wait a minute")
        session.pin_attempts.append(now)
        if not secrets.compare_digest(payload.pin.encode(), host_pin.encode()):
            raise PartyError("Wrong PIN")
        session.host = True
        await sio.enter_room(sid, "host")
        await sio.emit("host_state", service.host_state().wire(), to=sid)

    @on("host:action", HostActionIn, host=True)
    async def host_action(sid, session, payload):
        await admin.dispatch(payload.action, payload.payload)
```

In `backend/app/party/web.py`:
- Add `from .admin import PartyAdmin` to the imports.
- Replace `sessions = register_handlers(sio, service, host_pin=HOST_PIN)` with:
```python
admin = PartyAdmin(service)
sessions = register_handlers(sio, service, host_pin=HOST_PIN, admin=admin)
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd backend && venv/bin/python -m pytest -q`
Expected: all pass (`62 passed`).

- [ ] **Step 5: Commit**

```bash
git add backend/app/party/admin.py backend/app/party/realtime.py backend/app/party/web.py backend/tests/test_admin.py backend/tests/test_host_socket.py
git commit -m "feat(party): add PIN-gated host actions for teams, players, shots and settings"
```

---

### Task 14: Host panel (`/host`)

**Files:**
- Create: `frontend/src/host/HostPage.tsx`, `actions.ts`, `NumberField.tsx`, `ShotsPanel.tsx`, `PlayersPanel.tsx`, `TeamsPanel.tsx`, `SettingsPanel.tsx`, `host.css`
- Modify: `frontend/src/main.tsx`

**Interfaces:**
- Consumes:
  - `call` (socket), `useHostState`, `usePartyState`, `useConnected` (store), `uuid`.
  - `ArcadeSurface`, `Avatar`.
  - Contract types: `PublicState`, `HostState`, `PlayerView`, `TeamView`.
- Produces:
  - `hostAction(action, payload?) => Promise<Ack>`, `savedPin()`, `savePin(pin | null)`.
  - `type Run = (action: string, payload?: Record<string, unknown>) => Promise<boolean>`.
  - `<NumberField label value onChange />`.
  - The HostPage tabs are `SHOTS`, `PLAYERS`, `TEAMS` and `SETTINGS`; Task 17 adds `GAMES`.
- Visible text: placeholder `PIN`; buttons `ENTER`, `ADD +1 (n)`, `VOID`, `SAVE SETTINGS`, `SAVE WI-FI` and `START NEW NIGHT`.

- [ ] **Step 1: Write the helpers**

`frontend/src/host/actions.ts`:
```ts
import { uuid } from "../party/ids";
import { call, type Ack } from "../party/socket";

export type Run = (action: string, payload?: Record<string, unknown>) => Promise<boolean>;

export function hostAction(action: string, payload: Record<string, unknown> = {}): Promise<Ack> {
  return call("host:action", { requestId: uuid(), action, payload });
}

const PIN_KEY = "hoop.hostPin";

/** The PIN lives in sessionStorage so a reconnect can re-authenticate without asking again. */
export function savedPin(): string | null {
  try {
    return sessionStorage.getItem(PIN_KEY);
  } catch {
    return null;
  }
}

export function savePin(pin: string | null): void {
  try {
    if (pin) sessionStorage.setItem(PIN_KEY, pin);
    else sessionStorage.removeItem(PIN_KEY);
  } catch {
    // Storage blocked: the host re-enters the PIN after a reconnect.
  }
}
```

`frontend/src/host/NumberField.tsx`:
```tsx
interface NumberFieldProps {
  label: string;
  value: number;
  onChange: (value: number) => void;
}

export function NumberField({ label, value, onChange }: NumberFieldProps) {
  return (
    <label className="field">
      <span>{label}</span>
      <input
        className="input input--small"
        type="number"
        min={0}
        value={value}
        onChange={(e) => onChange(Math.max(0, Math.round(Number(e.target.value) || 0)))}
      />
    </label>
  );
}
```

- [ ] **Step 2: Write the panels**

`frontend/src/host/ShotsPanel.tsx`:
```tsx
import { useState } from "react";
import type { HostState, PublicState } from "../party/contract.gen";
import type { Run } from "./actions";

export function ShotsPanel({ state, hostState, run }: { state: PublicState; hostState: HostState | null; run: Run }) {
  const [adding, setAdding] = useState<string[]>([]);
  const names = new Map(state.players.map((p) => [p.id, p.name]));
  const name = (id: string) => names.get(id) ?? "(removed)";
  const toggle = (id: string) => setAdding((a) => (a.includes(id) ? a.filter((x) => x !== id) : [...a, id]));
  return (
    <section className="panel">
      <h2 className="pixel">ADD A SHOT</h2>
      <div className="chips">
        {state.players.map((p) => (
          <button key={p.id} className={`chip${adding.includes(p.id) ? " chip--on" : ""}`} onClick={() => toggle(p.id)}>
            {p.avatar.kind === "emoji" ? p.avatar.value : "📷"} {p.name}
          </button>
        ))}
      </div>
      <button
        className="btn"
        disabled={adding.length === 0}
        onClick={async () => {
          if (await run("shot.add", { drinkerIds: adding })) setAdding([]);
        }}
      >
        ADD +1 ({adding.length})
      </button>

      <h2 className="pixel">SHOT LOG</h2>
      <table className="table">
        <thead>
          <tr>
            <th>When</th>
            <th>Drinker</th>
            <th>Logged by</th>
            <th>Source</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {(hostState?.shots ?? []).map((s) => (
            <tr key={s.id} className={s.voidedAt ? "table__void" : undefined}>
              <td>{new Date(s.at).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}</td>
              <td>{name(s.drinkerId)}</td>
              <td>{s.loggedById ? name(s.loggedById) : "host"}</td>
              <td>{s.source}</td>
              <td>
                {s.voidedAt ? (
                  <span className="table__tag">{s.voidReason}</span>
                ) : (
                  <button className="btn btn--danger btn--small" onClick={() => void run("shot.void", { shotId: s.id })}>
                    VOID
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
```

`frontend/src/host/PlayersPanel.tsx`:
```tsx
import { useState } from "react";
import { Avatar } from "../arcade/Avatar";
import type { PlayerView, PublicState } from "../party/contract.gen";
import type { Run } from "./actions";

export function PlayersPanel({ state, run }: { state: PublicState; run: Run }) {
  if (state.players.length === 0) return <section className="panel">Nobody has joined yet.</section>;
  return (
    <section className="panel">
      <h2 className="pixel">PLAYERS ({state.players.length})</h2>
      <ul className="rows">
        {state.players.map((p) => (
          <PlayerRow key={`${p.id}-${p.name}`} player={p} state={state} run={run} />
        ))}
      </ul>
    </section>
  );
}

function PlayerRow({ player, state, run }: { player: PlayerView; state: PublicState; run: Run }) {
  const [name, setName] = useState(player.name);
  const [mergeInto, setMergeInto] = useState("");
  const others = state.players.filter((p) => p.id !== player.id);
  const color = state.teams.find((t) => t.id === player.teamId)?.color;
  return (
    <li className="row">
      <Avatar avatar={player.avatar} size="40px" color={color} />
      <input
        className="input input--small"
        aria-label={`Name for ${player.name}`}
        value={name}
        maxLength={20}
        onChange={(e) => setName(e.target.value)}
        onBlur={() => {
          if (name.trim() && name.trim() !== player.name) void run("player.edit", { playerId: player.id, name: name.trim() });
        }}
      />
      <select
        className="input input--small"
        aria-label={`Team for ${player.name}`}
        value={player.teamId}
        onChange={(e) => void run("player.edit", { playerId: player.id, teamId: e.target.value })}
      >
        {state.teams.map((t) => (
          <option key={t.id} value={t.id}>
            {t.name}
          </option>
        ))}
      </select>
      <span className="row__count display">{player.count}</span>
      <select className="input input--small" aria-label={`Merge ${player.name} into`} value={mergeInto} onChange={(e) => setMergeInto(e.target.value)}>
        <option value="">merge into…</option>
        {others.map((p) => (
          <option key={p.id} value={p.id}>
            {p.name}
          </option>
        ))}
      </select>
      <button
        className="btn btn--ghost btn--small"
        disabled={!mergeInto}
        onClick={() => {
          const into = others.find((p) => p.id === mergeInto)?.name;
          if (confirm(`Merge ${player.name} into ${into}? Their shots move over.`)) void run("player.merge", { fromId: player.id, intoId: mergeInto });
        }}
      >
        MERGE
      </button>
      <button
        className="btn btn--danger btn--small"
        onClick={() => {
          if (confirm(`Remove ${player.name}? Their shots stop counting.`)) void run("player.remove", { playerId: player.id });
        }}
      >
        REMOVE
      </button>
    </li>
  );
}
```

`frontend/src/host/TeamsPanel.tsx`:
```tsx
import { useState } from "react";
import type { PublicState, TeamView } from "../party/contract.gen";
import type { Run } from "./actions";

export function TeamsPanel({ state, run }: { state: PublicState; run: Run }) {
  return (
    <section className="panel">
      <h2 className="pixel">TEAMS</h2>
      <ul className="rows">
        {state.teams.map((t) => (
          <TeamRow key={`${t.id}-${t.name}-${t.color}`} team={t} canRemove={state.teams.length > 2 && t.size === 0} run={run} />
        ))}
      </ul>
      {state.teams.length < 4 && <NewTeam run={run} />}
    </section>
  );
}

function TeamRow({ team, canRemove, run }: { team: TeamView; canRemove: boolean; run: Run }) {
  const [name, setName] = useState(team.name);
  const [color, setColor] = useState(team.color.toLowerCase());
  const dirty = name !== team.name || color !== team.color.toLowerCase();
  return (
    <li className="row">
      <input type="color" aria-label={`${team.name} colour`} value={color} onChange={(e) => setColor(e.target.value)} />
      <input className="input input--small" aria-label={`${team.name} name`} value={name} maxLength={16} onChange={(e) => setName(e.target.value)} />
      <span className="row__meta">
        {team.size} players · {team.total} shots
      </span>
      <button className="btn btn--small" disabled={!dirty || !name.trim()} onClick={() => void run("team.upsert", { id: team.id, name: name.trim(), color })}>
        SAVE
      </button>
      {canRemove && (
        <button className="btn btn--danger btn--small" onClick={() => void run("team.remove", { teamId: team.id })}>
          REMOVE
        </button>
      )}
    </li>
  );
}

function NewTeam({ run }: { run: Run }) {
  const [name, setName] = useState("");
  const [color, setColor] = useState("#35e07f");
  return (
    <form
      className="row"
      onSubmit={async (e) => {
        e.preventDefault();
        if (await run("team.upsert", { name: name.trim(), color })) setName("");
      }}
    >
      <input type="color" aria-label="New team colour" value={color} onChange={(e) => setColor(e.target.value)} />
      <input className="input input--small" placeholder="New team name" value={name} maxLength={16} onChange={(e) => setName(e.target.value)} />
      <button className="btn btn--small" disabled={!name.trim()}>
        ADD TEAM
      </button>
    </form>
  );
}
```

`frontend/src/host/SettingsPanel.tsx`:
```tsx
import { useState } from "react";
import type { PublicState } from "../party/contract.gen";
import type { Run } from "./actions";
import { NumberField } from "./NumberField";

type Settings = PublicState["settings"];

export function SettingsPanel({ state, run }: { state: PublicState; run: Run }) {
  const [draft, setDraft] = useState<Settings>(state.settings);
  const [ssid, setSsid] = useState(state.join.wifiSsid ?? "");
  const [password, setPassword] = useState(state.join.wifiPassword ?? "");
  const [nightName, setNightName] = useState("");
  const set = <K extends keyof Settings>(key: K, value: Settings[K]) => setDraft((d) => ({ ...d, [key]: value }));

  return (
    <section className="panel">
      <h2 className="pixel">SOUND</h2>
      <label className="toggle">
        <input type="checkbox" checked={draft.sound} onChange={(e) => set("sound", e.target.checked)} /> Sound effects on the TV
      </label>
      <label className="toggle">
        <input type="checkbox" checked={draft.voice} onChange={(e) => set("voice", e.target.checked)} /> Announcer voice
      </label>

      <h2 className="pixel">HYPE RULES</h2>
      <div className="fields">
        <NumberField label="Heating up: shots" value={draft.heatingUp.count} onChange={(n) => set("heatingUp", { ...draft.heatingUp, count: n })} />
        <NumberField label="…within minutes" value={draft.heatingUp.windowMin} onChange={(n) => set("heatingUp", { ...draft.heatingUp, windowMin: n })} />
        <NumberField label="On fire: shots" value={draft.onFire.count} onChange={(n) => set("onFire", { ...draft.onFire, count: n })} />
        <NumberField label="…within minutes" value={draft.onFire.windowMin} onChange={(n) => set("onFire", { ...draft.onFire, windowMin: n })} />
        <NumberField label="Fire goes out after (min)" value={draft.onFire.coolMin} onChange={(n) => set("onFire", { ...draft.onFire, coolMin: n })} />
        <NumberField label="Player milestone every" value={draft.playerMilestoneEvery} onChange={(n) => set("playerMilestoneEvery", n)} />
        <NumberField label="Undo window (s)" value={draft.undoWindowSec} onChange={(n) => set("undoWindowSec", n)} />
        <NumberField label="NOT ME window (s)" value={draft.notMeWindowSec} onChange={(n) => set("notMeWindowSec", n)} />
        <NumberField label="Max logs per player per minute" value={draft.shotLogPerMin} onChange={(n) => set("shotLogPerMin", n)} />
      </div>
      <button className="btn" onClick={() => void run("settings.update", { settings: draft })}>
        SAVE SETTINGS
      </button>

      <h2 className="pixel">WI-FI ON THE TV</h2>
      <p className="panel__hint">Shown as a QR code guests can scan to join the network.</p>
      <div className="fields">
        <label className="field">
          <span>Network name</span>
          <input className="input input--small" value={ssid} onChange={(e) => setSsid(e.target.value)} />
        </label>
        <label className="field">
          <span>Password</span>
          <input className="input input--small" value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>
      </div>
      <button className="btn" onClick={() => void run("wifi.set", { ssid, password })}>
        SAVE WI-FI
      </button>

      <h2 className="pixel">LINKS</h2>
      <p className="panel__hint">
        Phones: {state.join.lanUrl}
        {state.join.tunnelUrl ? ` · Tunnel: ${state.join.tunnelUrl}/play` : " · Tunnel off (start party.py with --tunnel)"}
      </p>

      <h2 className="pixel">NEW NIGHT</h2>
      <p className="panel__hint">Archives tonight's players and shots. Everyone re-joins. Teams and settings carry over.</p>
      <div className="row">
        <input className="input input--small" placeholder="Name (optional)" value={nightName} onChange={(e) => setNightName(e.target.value)} />
        <button
          className="btn btn--danger"
          onClick={() => {
            if (confirm("Start a new night? Everyone will need to re-join.")) void run("night.new", nightName.trim() ? { name: nightName.trim() } : {});
          }}
        >
          START NEW NIGHT
        </button>
      </div>
    </section>
  );
}
```

- [ ] **Step 3: Write the page, styles and route**

`frontend/src/host/HostPage.tsx`:
```tsx
import { useEffect, useState, type FormEvent } from "react";
import { ArcadeSurface } from "../arcade/Surface";
import { call } from "../party/socket";
import { useConnected, useHostState, usePartyState } from "../party/store";
import { hostAction, savePin, savedPin } from "./actions";
import { PlayersPanel } from "./PlayersPanel";
import { SettingsPanel } from "./SettingsPanel";
import { ShotsPanel } from "./ShotsPanel";
import { TeamsPanel } from "./TeamsPanel";
import "./host.css";

type Tab = "shots" | "players" | "teams" | "settings";
const TABS: [Tab, string][] = [
  ["shots", "SHOTS"],
  ["players", "PLAYERS"],
  ["teams", "TEAMS"],
  ["settings", "SETTINGS"],
];

export default function HostPage() {
  const state = usePartyState();
  const hostState = useHostState();
  const connected = useConnected();
  const [authed, setAuthed] = useState(false);
  const [tab, setTab] = useState<Tab>("shots");
  const [message, setMessage] = useState<string | null>(null);

  // Re-authenticate on every (re)connect with the PIN remembered for this tab.
  useEffect(() => {
    if (!connected) {
      setAuthed(false);
      return;
    }
    const pin = savedPin();
    if (!pin) return;
    void call("host:auth", { pin }).then((ack) => {
      if (ack.ok) setAuthed(true);
      else savePin(null);
    });
  }, [connected]);

  async function run(action: string, payload?: Record<string, unknown>): Promise<boolean> {
    const ack = await hostAction(action, payload);
    setMessage(ack.ok ? null : ack.error);
    return ack.ok;
  }

  let body;
  if (!state) body = <p className="host__center display">Connecting…</p>;
  else if (!authed) body = <PinGate onAuthed={() => setAuthed(true)} />;
  else
    body = (
      <div className="host">
        <header className="host__head">
          <h1 className="display">HOST · {state.nightName}</h1>
          <nav className="host__tabs">
            {TABS.map(([id, label]) => (
              <button key={id} className={`host__tab${tab === id ? " host__tab--on" : ""}`} onClick={() => setTab(id)}>
                {label}
              </button>
            ))}
          </nav>
        </header>
        {message && (
          <p className="host__error" role="alert">
            {message}
          </p>
        )}
        {tab === "shots" && <ShotsPanel state={state} hostState={hostState} run={run} />}
        {tab === "players" && <PlayersPanel state={state} run={run} />}
        {tab === "teams" && <TeamsPanel state={state} run={run} />}
        {tab === "settings" && <SettingsPanel state={state} run={run} />}
      </div>
    );

  return (
    <ArcadeSurface>
      {!connected && <div className="banner">Reconnecting…</div>}
      {body}
    </ArcadeSurface>
  );
}

function PinGate({ onAuthed }: { onAuthed: () => void }) {
  const [pin, setPin] = useState("");
  const [error, setError] = useState<string | null>(null);
  async function submit(e: FormEvent) {
    e.preventDefault();
    const ack = await call("host:auth", { pin });
    if (ack.ok) {
      savePin(pin);
      onAuthed();
    } else setError(ack.error);
  }
  return (
    <form className="host__center host__pin" onSubmit={(e) => void submit(e)}>
      <h1 className="display">HOST PANEL</h1>
      <label className="pixel" htmlFor="host-pin">
        PIN FROM THE MAC TERMINAL
      </label>
      <input id="host-pin" className="input" inputMode="numeric" autoComplete="off" placeholder="PIN" value={pin} onChange={(e) => setPin(e.target.value)} />
      <button className="btn" disabled={!pin}>
        ENTER
      </button>
      {error && (
        <p className="host__error" role="alert">
          {error}
        </p>
      )}
    </form>
  );
}
```

`frontend/src/host/host.css`:
```css
.host {
  max-width: 1100px;
  margin: 0 auto;
  padding: 20px 16px 60px;
  display: grid;
  gap: 16px;
}

.host__center {
  min-height: 80vh;
  display: grid;
  place-content: center;
  gap: 12px;
  text-align: center;
}

.host__pin {
  width: min(320px, 100%);
  margin: 0 auto;
}

.host__head {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: space-between;
  align-items: center;
}

.host__head h1 {
  margin: 0;
  color: var(--led);
}

.host__tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.host__tab {
  font-family: var(--font-display);
  padding: 10px 14px;
  border: none;
  border-radius: 10px;
  color: var(--ink-hi);
  background: var(--arena-700);
}

.host__tab--on {
  background: var(--led);
  color: var(--arena-900);
}

.host__error {
  margin: 0;
  padding: 10px 14px;
  border-radius: 10px;
  background: #4a1020;
}

.panel {
  display: grid;
  gap: 14px;
  align-content: start;
}

.panel h2 {
  margin: 12px 0 0;
  color: var(--ink-lo);
}

.panel__hint {
  margin: 0;
  color: var(--ink-lo);
}

.panel__game {
  display: grid;
  gap: 12px;
  padding: 16px;
  border-radius: 16px;
  background: var(--arena-800);
}

.input--small {
  font-size: 1rem;
  padding: 0.5rem 0.7rem;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  padding: 8px 12px;
  border-radius: 999px;
  border: 2px solid var(--arena-600);
  color: var(--ink-hi);
  background: var(--arena-800);
}

.chip--on {
  border-color: var(--led);
  background: var(--arena-600);
}

.rows {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 10px;
  border-radius: 12px;
  background: var(--arena-800);
}

.row__count {
  min-width: 2em;
  color: var(--led);
  text-align: center;
}

.row__meta {
  color: var(--ink-lo);
}

.table {
  width: 100%;
  border-collapse: collapse;
}

.table th,
.table td {
  padding: 8px;
  text-align: left;
  border-bottom: 1px solid var(--arena-600);
}

.table__void td {
  color: var(--ink-lo);
  text-decoration: line-through;
}

.table__tag {
  font-size: 0.8rem;
  text-decoration: none;
}

.fields {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
}

.field {
  display: grid;
  gap: 4px;
  color: var(--ink-lo);
}

.field--wide {
  grid-column: 1 / -1;
}

.toggle {
  display: flex;
  gap: 10px;
  align-items: center;
}

.toggle input {
  width: 22px;
  height: 22px;
}
```

`frontend/src/main.tsx` (full file):
```tsx
import { StrictMode, Suspense, lazy } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router'
import './index.css'
import App from './App.tsx'

const PlayPage = lazy(() => import('./play/PlayPage.tsx'))
const TvPage = lazy(() => import('./tv/TvPage.tsx'))
const HostPage = lazy(() => import('./host/HostPage.tsx'))

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <Suspense fallback={null}>
        <Routes>
          <Route path="/" element={<App />} />
          <Route path="/play" element={<PlayPage />} />
          <Route path="/tv" element={<TvPage />} />
          <Route path="/host" element={<HostPage />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  </StrictMode>,
)
```

- [ ] **Step 4: Verify build, lint and behaviour**

Run: `cd frontend && npm run build && npx oxlint src/host && npx vitest run`
Expected: the build succeeds, oxlint reports `0 errors`, and the tests pass.

Then run a manual smoke check with the dev servers running and the backend's PIN from its startup line:
- Open `http://localhost:5173/host` and enter the PIN.
- Add a shot for a player; the TV should show the takeover.
- VOID it; the TV should show **NO GOOD!**.
- Rename a team; the TV scoreboard should update.
- Set the Wi-Fi name and password; a Wi-Fi QR code should appear on the TV.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/host frontend/src/main.tsx
git commit -m "feat(host): add PIN-gated host panel for shots, players, teams and settings"
```

---

### Task 15: Shot-cam media backend (upload, ffmpeg, replay)

**Files:**
- Create: `backend/app/party/media.py`
- Modify: `backend/app/party/service.py` (`add_media`, `media_done`, and a `Processed` protocol)
- Modify: `backend/app/party/web.py` (processor, upload route, `/media` static mount, lifespan)
- Test: `backend/tests/test_media.py`

**Interfaces:**
- Consumes: `PartyService`, `MediaRec`, `reel_item`, `ReplayMoment`, `MediaOut`, `MAX_UPLOAD_BYTES`, `MAX_VIDEO_SEC`.
- Produces:
  - `media.MEDIA_DIR`.
  - Dataclasses `MediaJob(media_id, raw: Path, kind: "photo"|"video", purpose: "shot"|"avatar")` and `MediaResult(path, poster_path, duration_ms)`.
  - `async process(job, media_dir) -> MediaResult | None`.
  - `MediaProcessor(media_dir, on_done)`, with the methods `start()`, `async stop()` and `submit(job)`.
  - `media_router(service, processor) -> APIRouter`.
  - `PartyService.add_media(media_id, player_id, *, purpose, kind, shot_id)` and `async PartyService.media_done(media_id, result)`.
  - HTTP:
    - `POST /api/party/media`, multipart with the fields `file`, `token`, `purpose` (`shot`|`avatar`) and optional `shotId`. Responses: 200 `{mediaId, status: "processing"}`, 401 for a bad token, 413 when the upload is too big, 415 when it isn't media, 400 when it isn't your shot.
    - `GET /media/<file>`.

- [ ] **Step 1: Write the failing tests**

`backend/tests/test_media.py`:
```python
import shutil
import subprocess

import httpx
import pytest

from app.party.errors import PartyError
from app.party.media import MediaJob, MediaResult, process
from tests.conftest import join, socket_join

needs_ffmpeg = pytest.mark.skipif(shutil.which("ffmpeg") is None, reason="ffmpeg not installed")


def make_video(path, width=1080, height=1920, seconds=3):
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i", f"testsrc2=size={width}x{height}:rate=30:duration={seconds}",
         "-f", "lavfi", "-i", f"sine=frequency=440:duration={seconds}", "-c:v", "libx264", "-preset", "ultrafast",
         "-c:a", "aac", "-shortest", str(path)],
        check=True,
    )


def make_photo(path, width=3000, height=2000):
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i", f"testsrc2=size={width}x{height}",
                    "-frames:v", "1", str(path)], check=True)


def video_stream(path):
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=codec_name,width,height",
         "-of", "csv=p=0", str(path)],
        capture_output=True, text=True, check=True,
    ).stdout.strip()
    codec, width, height = out.split(",")
    return codec, int(width), int(height)


@needs_ffmpeg
async def test_portrait_video_becomes_720p_h264_with_a_poster(tmp_path):
    raw = tmp_path / "raw.mov"
    make_video(raw)
    result = await process(MediaJob("m1", raw, "video", "shot"), tmp_path)
    assert result is not None and result.poster_path == "m1.jpg"
    assert video_stream(tmp_path / result.path) == ("h264", 720, 1280)
    assert 2500 <= result.duration_ms <= 3500


@needs_ffmpeg
async def test_long_videos_are_trimmed_and_small_ones_are_not_upscaled(tmp_path):
    raw = tmp_path / "long.mov"
    make_video(raw, 640, 360, seconds=20)
    result = await process(MediaJob("m2", raw, "video", "shot"), tmp_path)
    assert result.duration_ms <= 15_100
    assert video_stream(tmp_path / result.path)[1:] == (640, 360)


@needs_ffmpeg
async def test_photos_shrink_to_1600_and_avatars_crop_square(tmp_path):
    raw = tmp_path / "p.png"
    make_photo(raw)
    shot = await process(MediaJob("p1", raw, "photo", "shot"), tmp_path)
    assert video_stream(tmp_path / shot.path)[1:] == (1600, 1066)
    avatar = await process(MediaJob("p2", raw, "photo", "avatar"), tmp_path)
    assert video_stream(tmp_path / avatar.path)[1:] == (512, 512)


@needs_ffmpeg
async def test_garbage_fails_cleanly(tmp_path):
    raw = tmp_path / "junk.mov"
    raw.write_bytes(b"not a video")
    assert await process(MediaJob("j", raw, "video", "shot"), tmp_path) is None


async def test_ready_shot_media_joins_the_reel_and_triggers_a_replay(service, emitter):
    jess = await join(service, "Jess")
    [shot_id] = await service.log_shots(jess, "r1", [jess])
    await service.add_media("m1", jess, purpose="shot", kind="video", shot_id=shot_id)
    await service.media_done("m1", MediaResult("m1.mp4", "m1.jpg", 3000))
    replay = emitter.moments[-1]
    assert replay.type == "replay" and replay.item.url == "/media/m1.mp4" and replay.item.poster_url == "/media/m1.jpg"
    assert emitter.states[-1].reel[0].media_id == "m1"


async def test_avatar_media_becomes_the_players_photo(service, emitter):
    jess = await join(service, "Jess")
    await service.add_media("a1", jess, purpose="avatar", kind="photo", shot_id=None)
    await service.media_done("a1", MediaResult("a1.jpg", None, None))
    me = next(p for p in emitter.states[-1].players if p.id == jess)
    assert (me.avatar.kind, me.avatar.value) == ("photo", "/media/a1.jpg")
    assert emitter.states[-1].reel == []


async def test_media_can_only_attach_to_your_own_shots(service):
    jess, sam, kim = await join(service, "Jess"), await join(service, "Sam"), await join(service, "Kim")
    [shot_id] = await service.log_shots(sam, "r1", [jess])
    await service.add_media("ok1", jess, purpose="shot", kind="photo", shot_id=shot_id)
    await service.add_media("ok2", sam, purpose="shot", kind="photo", shot_id=shot_id)
    with pytest.raises(PartyError):
        await service.add_media("bad", kim, purpose="shot", kind="photo", shot_id=shot_id)


async def test_failed_media_stays_out_of_the_reel(service, emitter):
    jess = await join(service, "Jess")
    await service.add_media("f1", jess, purpose="shot", kind="video", shot_id=None)
    await service.media_done("f1", None)
    assert service.media["f1"].status == "failed"
    assert not any(m.type == "replay" for m in emitter.moments)


@needs_ffmpeg
async def test_http_upload_ends_in_an_instant_replay(connect, server_url, tmp_path):
    phone, tv = await connect(), await connect()
    me = await socket_join(phone)
    photo = tmp_path / "shot.jpg"
    make_photo(photo, 800, 600)
    async with httpx.AsyncClient() as http:
        response = await http.post(
            f"{server_url}/api/party/media",
            data={"token": me["token"], "purpose": "shot"},
            files={"file": ("shot.jpg", photo.read_bytes(), "image/jpeg")},
        )
        assert response.status_code == 200, response.text
        media_id = response.json()["mediaId"]
        replay = await tv.wait_for(
            lambda: next((m for m in tv.moments if m["type"] == "replay" and m["item"]["mediaId"] == media_id), None),
            "replay", timeout=15,
        )
        served = await http.get(f"{server_url}{replay['item']['url']}")
        assert served.status_code == 200 and served.headers["content-type"] == "image/jpeg"


async def test_upload_needs_a_valid_token_and_media(server_url, connect):
    async with httpx.AsyncClient() as http:
        bad_token = await http.post(f"{server_url}/api/party/media", data={"token": "nope"},
                                    files={"file": ("x.jpg", b"x", "image/jpeg")})
        assert bad_token.status_code == 401
        phone = await connect()
        me = await socket_join(phone)
        not_media = await http.post(f"{server_url}/api/party/media", data={"token": me["token"]},
                                    files={"file": ("x.txt", b"x", "text/plain")})
        assert not_media.status_code == 415
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && venv/bin/python -m pytest tests/test_media.py -q`
Expected: FAIL with `ModuleNotFoundError: No module named 'app.party.media'`.

- [ ] **Step 3: Implement `media.py`**

`backend/app/party/media.py`:
```python
"""Shot-cam uploads: store the raw file, normalise it with ffmpeg one job at a time
(the Mac is a fanless Air), then tell the party it's ready."""
import asyncio
import logging
import os
import shutil
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from pathlib import Path
from typing import Literal

from fastapi import APIRouter, File, Form, HTTPException, Request, UploadFile

from .config import MAX_UPLOAD_BYTES, MAX_VIDEO_SEC
from .contract import MediaOut
from .errors import PartyError
from .service import PartyService, new_id

log = logging.getLogger("hoopdreams.media")

MEDIA_DIR = Path(os.environ.get("HOOP_MEDIA_DIR", Path(__file__).resolve().parents[2] / "media"))
# Short side to 720 (portrait or landscape), never upscaled; even dimensions for H.264.
VIDEO_SCALE = "scale='if(gte(iw,ih),-2,min(720,iw))':'if(gte(iw,ih),min(720,ih),-2)'"
PHOTO_SCALE = "scale='if(gte(iw,ih),min(1600,iw),-2)':'if(gte(iw,ih),-2,min(1600,ih))'"
AVATAR_CROP = "crop='min(iw,ih)':'min(iw,ih)',scale=512:512"


@dataclass
class MediaJob:
    media_id: str
    raw: Path
    kind: Literal["photo", "video"]
    purpose: Literal["shot", "avatar"]


@dataclass
class MediaResult:
    path: str
    poster_path: str | None
    duration_ms: int | None


async def run_ffmpeg(*args: str) -> bool:
    proc = await asyncio.create_subprocess_exec(
        "ffmpeg", "-y", "-hide_banner", "-loglevel", "error", *args,
        stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.PIPE,
    )
    _, err = await proc.communicate()
    if proc.returncode != 0:
        log.warning("ffmpeg failed: %s", err.decode(errors="replace")[-400:])
    return proc.returncode == 0


async def probe_duration_ms(path: Path) -> int | None:
    proc = await asyncio.create_subprocess_exec(
        "ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path),
        stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL,
    )
    out, _ = await proc.communicate()
    try:
        return int(float(out.decode().strip()) * 1000)
    except ValueError:
        return None


async def process(job: MediaJob, media_dir: Path) -> MediaResult | None:
    if job.kind == "photo":
        out = f"{job.media_id}.jpg"
        vf = AVATAR_CROP if job.purpose == "avatar" else PHOTO_SCALE
        ok = await run_ffmpeg("-i", str(job.raw), "-vf", vf, "-frames:v", "1", "-q:v", "3", str(media_dir / out))
        return MediaResult(out, None, None) if ok else None

    out, poster = f"{job.media_id}.mp4", f"{job.media_id}.jpg"
    common = ["-i", str(job.raw), "-t", str(MAX_VIDEO_SEC), "-vf", VIDEO_SCALE, "-pix_fmt", "yuv420p",
              "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart"]
    ok = await run_ffmpeg(*common, "-c:v", "h264_videotoolbox", "-b:v", "3M", str(media_dir / out))
    if not ok:  # no hardware encoder (or it refused the input): software fallback
        ok = await run_ffmpeg(*common, "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", str(media_dir / out))
    if not ok:
        return None
    await run_ffmpeg("-ss", "0.5", "-i", str(media_dir / out), "-frames:v", "1", "-vf", "scale=-2:360", str(media_dir / poster))
    return MediaResult(out, poster if (media_dir / poster).exists() else None, await probe_duration_ms(media_dir / out))


class MediaProcessor:
    """Runs ffmpeg jobs one at a time and reports each result to `on_done`."""

    def __init__(self, media_dir: Path, on_done: Callable[[str, MediaResult | None], Awaitable[None]]) -> None:
        self.media_dir = media_dir
        self.on_done = on_done
        self.queue: asyncio.Queue[MediaJob] = asyncio.Queue()
        self.task: asyncio.Task | None = None

    def start(self) -> None:
        (self.media_dir / "raw").mkdir(parents=True, exist_ok=True)
        self.task = asyncio.create_task(self._run())

    async def stop(self) -> None:
        if self.task:
            self.task.cancel()

    def submit(self, job: MediaJob) -> None:
        self.queue.put_nowait(job)

    async def _run(self) -> None:
        while True:
            job = await self.queue.get()
            try:
                result = await process(job, self.media_dir)
            except Exception:
                log.exception("processing %s failed", job.media_id)
                result = None
            if result is not None:
                job.raw.unlink(missing_ok=True)
            try:
                await self.on_done(job.media_id, result)
            except Exception:
                log.exception("announcing %s failed", job.media_id)


def _save(upload: UploadFile, dest: Path) -> None:
    with dest.open("wb") as out:
        shutil.copyfileobj(upload.file, out, length=1024 * 1024)


def media_router(service: PartyService, processor: MediaProcessor) -> APIRouter:
    router = APIRouter(prefix="/api/party")

    @router.post("/media", response_model=MediaOut)
    async def upload_media(
        request: Request,
        file: UploadFile = File(...),
        token: str = Form(...),
        purpose: Literal["shot", "avatar"] = Form("shot"),
        shot_id: str | None = Form(None, alias="shotId"),
    ):
        if int(request.headers.get("content-length") or 0) > MAX_UPLOAD_BYTES:
            raise HTTPException(413, "That clip is too big (200 MB max)")
        try:
            player_id = service.resume(token)
        except PartyError as exc:
            raise HTTPException(401, str(exc)) from exc
        content_type = file.content_type or ""
        if not content_type.startswith(("image/", "video/")):
            raise HTTPException(415, "Photos and videos only")
        kind: Literal["photo", "video"] = "video" if content_type.startswith("video/") else "photo"
        media_id = new_id()
        suffix = Path(file.filename or "").suffix[:8] or (".mp4" if kind == "video" else ".jpg")
        raw = processor.media_dir / "raw" / f"{media_id}{suffix}"
        raw.parent.mkdir(parents=True, exist_ok=True)
        await asyncio.to_thread(_save, file, raw)
        try:
            await service.add_media(media_id, player_id, purpose=purpose, kind=kind, shot_id=shot_id)
        except PartyError as exc:
            raw.unlink(missing_ok=True)
            raise HTTPException(400, str(exc)) from exc
        processor.submit(MediaJob(media_id, raw, kind, purpose))
        return MediaOut(media_id=media_id, status="processing")

    return router
```

- [ ] **Step 4: Add media handling to `service.py`**

Add `ReplayMoment` to the `.contract` import block in `backend/app/party/service.py`. Then add this protocol after the `Emitter` protocol:
```python
class Processed(Protocol):
    """A finished media job (media.MediaResult)."""

    path: str
    poster_path: str | None
    duration_ms: int | None
```
Add this section to `PartyService`, directly after `void`:
```python
    # ---------- media ----------

    async def add_media(self, media_id: str, player_id: str, *, purpose: str, kind: str, shot_id: str | None) -> None:
        if shot_id is not None:
            shot = self.shots.get(shot_id)
            if shot is None or player_id not in (shot.drinker_id, shot.logged_by_id):
                raise PartyError("That shot isn't yours to film")
        rec = MediaRec(media_id, player_id, shot_id, purpose, kind, "processing", "", None, self.clock())
        with self.db() as db:
            db.add(models.Media(id=rec.id, night_id=self.night_id, player_id=player_id, shot_id=shot_id, purpose=purpose,
                                kind=kind, status="processing", created_at=rec.created_at))
        self.media[media_id] = rec

    async def media_done(self, media_id: str, result: Processed | None) -> None:
        rec = self.media.get(media_id)
        if rec is None:  # a new night started while it was processing
            return
        if result is None:
            values: dict[str, Any] = {"status": "failed"}
        else:
            values = {"status": "ready", "path": result.path, "poster_path": result.poster_path,
                      "duration_ms": result.duration_ms}
        with self.db() as db:
            db.execute(update(models.Media).where(models.Media.id == media_id).values(**values))
        if result is None:
            rec.status = "failed"
            return
        rec.status, rec.path, rec.poster_path = "ready", result.path, result.poster_path
        if rec.purpose == "avatar":
            player = self.players.get(rec.player_id)
            if player is not None:
                with self.db() as db:
                    db.execute(update(models.Player).where(models.Player.id == player.id).values(avatar_kind="photo", avatar_value=media_id))
                player.avatar_kind, player.avatar_value = "photo", media_id
            await self.broadcast()
            return
        await self.broadcast()
        await self.emitter.moment(ReplayMoment(id=new_id(), at=self.clock(), item=self.reel_item(rec)))
```

- [ ] **Step 5: Wire media into `web.py`**

In `backend/app/party/web.py`:
- Add `from .media import MEDIA_DIR, MediaProcessor, media_router` to the imports.
- After the `sessions = ...` line add:
```python
processor = MediaProcessor(MEDIA_DIR, service.media_done)
```
- Replace `party_lifespan` and `mount_party` with:
```python
@asynccontextmanager
async def party_lifespan(_app: FastAPI):
    service.load()
    processor.start()
    print(f"🏀 HoopDreams party is live — phones: {service.lan_url} · host PIN: {HOST_PIN}", flush=True)
    ticker = asyncio.create_task(service.run_ticker())
    try:
        yield
    finally:
        ticker.cancel()
        await processor.stop()


def mount_party(app: FastAPI) -> None:
    app.include_router(party_router)
    app.include_router(media_router(service, processor))
    app.mount("/socket.io", socketio.ASGIApp(sio, socketio_path=""))
    MEDIA_DIR.mkdir(parents=True, exist_ok=True)
    app.mount("/media", StaticFiles(directory=MEDIA_DIR), name="media")
    if os.environ.get("HOOP_PARTY") == "1":
        if not (DIST_DIR / "index.html").exists():
            raise RuntimeError(f"HOOP_PARTY=1 but {DIST_DIR} has no build. Run `npm run build` in frontend/.")
        app.mount("/", SPAStaticFiles(directory=DIST_DIR, html=True), name="spa")
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd backend && venv/bin/python -m pytest -q`
Expected: all pass (`72 passed`).

- [ ] **Step 7: Commit**

```bash
git add backend/app/party/media.py backend/app/party/service.py backend/app/party/web.py backend/tests/test_media.py
git commit -m "feat(party): add shot-cam uploads with ffmpeg normalisation and instant replays"
```

---

### Task 16: Shot-cam on phones (photo/video capture, selfie avatars)

**Files:**
- Create: `frontend/src/play/ShotCam.tsx`, `frontend/src/play/image.ts`, `frontend/src/play/upload.ts`
- Modify: `frontend/src/play/PlayPage.tsx`, `JoinFlow.tsx`, `usePlayer.ts`, `play.css`

**Interfaces:**
- Consumes: `POST /api/party/media` (Task 15); the TV's `Replay` overlay and `Reel` (Task 11) already render the results.
- Produces:
  - `downscaleImage(file: File, maxEdge = 1600, square = false): Promise<Blob>`.
  - `uploadMedia(blob, {token, purpose, shotId?, filename}, onProgress) => Promise<{ok, mediaId?, error?}>`.
  - `<ShotCam token recentShotId />`, whose hidden input has `data-testid="shotcam-input"`.
  - `JoinInput` gains `photo: Blob | null`.
- Visible text: `📸 SHOT-CAM`, `On its way to the jumbotron!` and `📸 USE A SELFIE`.

- [ ] **Step 1: Write the upload helpers**

`frontend/src/play/image.ts`:
```ts
/**
 * Shrink a photo on the phone before upload (a 12 MP HEIC becomes a ~300 KB JPEG).
 * If the browser can't decode it, the original goes up and the server's ffmpeg normalises it.
 */
export async function downscaleImage(file: File, maxEdge = 1600, square = false): Promise<Blob> {
  try {
    const bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
    const side = Math.min(bitmap.width, bitmap.height);
    const sw = square ? side : bitmap.width;
    const sh = square ? side : bitmap.height;
    const sx = square ? (bitmap.width - side) / 2 : 0;
    const sy = square ? (bitmap.height - side) / 2 : 0;
    const scale = Math.min(1, maxEdge / Math.max(sw, sh));
    const canvas = document.createElement("canvas");
    canvas.width = Math.round(sw * scale);
    canvas.height = Math.round(sh * scale);
    canvas.getContext("2d")!.drawImage(bitmap, sx, sy, sw, sh, 0, 0, canvas.width, canvas.height);
    bitmap.close();
    return await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("encode failed"))), "image/jpeg", 0.85),
    );
  } catch {
    return file;
  }
}
```

`frontend/src/play/upload.ts`:
```ts
export interface UploadResult {
  ok: boolean;
  mediaId?: string;
  error?: string;
}

interface UploadFields {
  token: string;
  purpose: "shot" | "avatar";
  shotId?: string | null;
  filename: string;
}

/** POST a photo or clip with progress reporting (fetch can't report upload progress). */
export function uploadMedia(file: Blob, fields: UploadFields, onProgress: (fraction: number) => void): Promise<UploadResult> {
  return new Promise((resolve) => {
    const form = new FormData();
    form.append("token", fields.token);
    form.append("purpose", fields.purpose);
    if (fields.shotId) form.append("shotId", fields.shotId);
    form.append("file", file, fields.filename);
    const xhr = new XMLHttpRequest();
    xhr.open("POST", "/api/party/media");
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress(e.loaded / e.total);
    };
    xhr.onload = () => {
      if (xhr.status === 200) {
        resolve({ ok: true, mediaId: (JSON.parse(xhr.responseText) as { mediaId: string }).mediaId });
        return;
      }
      let error = `Upload failed (${xhr.status})`;
      try {
        error = (JSON.parse(xhr.responseText) as { detail?: string }).detail ?? error;
      } catch {
        // not JSON: keep the generic message
      }
      resolve({ ok: false, error });
    };
    xhr.onerror = () => resolve({ ok: false, error: "Upload failed. Check the Wi-Fi." });
    xhr.send(form);
  });
}
```

- [ ] **Step 2: Write the ShotCam component**

`frontend/src/play/ShotCam.tsx`:
```tsx
import { useRef, useState } from "react";
import { downscaleImage } from "./image";
import { uploadMedia } from "./upload";

type Status =
  | { phase: "idle" }
  | { phase: "uploading"; progress: number }
  | { phase: "done" }
  | { phase: "error"; message: string; file: File };

interface ShotCamProps {
  token: string;
  /** Attach to the shot this phone just logged or was logged for, if any. */
  recentShotId: string | null;
}

export function ShotCam({ token, recentShotId }: ShotCamProps) {
  const input = useRef<HTMLInputElement>(null);
  const [status, setStatus] = useState<Status>({ phase: "idle" });

  async function send(file: File) {
    const isVideo = file.type.startsWith("video/");
    const blob = isVideo ? file : await downscaleImage(file);
    const filename = blob === file ? file.name || (isVideo ? "clip.mp4" : "photo.jpg") : "photo.jpg";
    setStatus({ phase: "uploading", progress: 0 });
    const result = await uploadMedia(blob, { token, purpose: "shot", shotId: recentShotId, filename }, (progress) =>
      setStatus({ phase: "uploading", progress }),
    );
    if (result.ok) {
      setStatus({ phase: "done" });
      setTimeout(() => setStatus({ phase: "idle" }), 3000);
    } else {
      setStatus({ phase: "error", message: result.error ?? "Upload failed", file });
    }
  }

  return (
    <div className="shotcam">
      <input
        ref={input}
        type="file"
        accept="image/*,video/*"
        hidden
        data-testid="shotcam-input"
        onChange={(e) => {
          const file = e.target.files?.[0];
          e.target.value = "";
          if (file) void send(file);
        }}
      />
      {status.phase === "idle" && (
        <button className="btn btn--ghost shotcam__button" onClick={() => input.current?.click()}>
          📸 SHOT-CAM <span className="shotcam__hint">photo or clip → TV replay</span>
        </button>
      )}
      {status.phase === "uploading" && (
        <div className="shotcam__progress" aria-live="polite">
          <span className="shotcam__bar" style={{ width: `${Math.round(status.progress * 100)}%` }} />
          <p>Sending to the TV… {Math.round(status.progress * 100)}%</p>
        </div>
      )}
      {status.phase === "done" && <p className="shotcam__done">✅ On its way to the jumbotron!</p>}
      {status.phase === "error" && (
        <div className="shotcam__error" role="alert">
          <span>{status.message}</span>
          <button className="btn btn--small" onClick={() => void send(status.file)}>
            RETRY
          </button>
          <button className="btn btn--ghost btn--small" aria-label="Dismiss" onClick={() => setStatus({ phase: "idle" })}>
            ✕
          </button>
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 3: Wire ShotCam into the play screen**

In `frontend/src/play/PlayPage.tsx`:
1. Add `import { ShotCam } from "./ShotCam";` to the imports.
2. In `PlayPage`, change `<PlayScreen state={state} me={player.me} resumed={player.resumed} />` to:
```tsx
<PlayScreen state={state} me={player.me} resumed={player.resumed} token={player.identity?.token ?? ""} />
```
3. Change the `PlayScreen` signature to:
```tsx
function PlayScreen({ state, me, resumed, token }: { state: PublicState; me: PlayerView; resumed: boolean; token: string }) {
```
4. Just before `const team = state.teams.find((t) => t.id === me.teamId);` add:
```tsx
  const recentShotId =
    state.feed.find((f) => (f.drinkerId === me.id || f.loggedById === me.id) && now - f.at < 120_000)?.shotId ?? null;
```
5. Replace `<div className="play__extras" />` with:
```tsx
      <div className="play__extras">
        <ShotCam token={token} recentShotId={recentShotId} />
      </div>
```

- [ ] **Step 4: Add selfie avatars to the join flow**

In `frontend/src/play/usePlayer.ts`:
- Add `import { uploadMedia } from "./upload";`.
- Add `photo: Blob | null;` to `JoinInput`.
- In `join`, inside `if (ack.ok) { ... }` after `setResumed(true);`, add:
```ts
        if (input.photo) void uploadMedia(input.photo, { token: ack.token, purpose: "avatar", filename: "avatar.jpg" }, () => {});
```

In `frontend/src/play/JoinFlow.tsx`:
- Change the react import to `import { useEffect, useRef, useState } from "react";`.
- Add `import { downscaleImage } from "./image";`.
- Add this state after the `requestId` line:
```tsx
  const selfieInput = useRef<HTMLInputElement>(null);
  const [photo, setPhoto] = useState<Blob | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  useEffect(
    () => () => {
      if (preview) URL.revokeObjectURL(preview);
    },
    [preview],
  );

  async function takeSelfie(file: File) {
    const square = await downscaleImage(file, 512, true);
    setPhoto(square);
    setPreview(URL.createObjectURL(square));
  }
```
- In `pickTeam`, change the `onJoin` call to `await onJoin({ requestId, name: name.trim(), emoji, teamId, photo })`.
- Replace `<div className="join-flow__extras" />` with:
```tsx
          <div className="join-flow__extras">
            <input
              ref={selfieInput}
              type="file"
              accept="image/*"
              capture="user"
              hidden
              onChange={(e) => {
                const file = e.target.files?.[0];
                e.target.value = "";
                if (file) void takeSelfie(file);
              }}
            />
            {preview ? (
              <div className="selfie">
                <img src={preview} alt="Your selfie" />
                <button
                  className="btn btn--ghost"
                  onClick={() => {
                    setPhoto(null);
                    setPreview(null);
                  }}
                >
                  USE EMOJI INSTEAD
                </button>
              </div>
            ) : (
              <button className="btn btn--ghost" onClick={() => selfieInput.current?.click()}>
                📸 USE A SELFIE
              </button>
            )}
          </div>
```

Append to `frontend/src/play/play.css`:
```css
.shotcam__button {
  width: 100%;
  display: flex;
  justify-content: center;
  align-items: baseline;
  gap: 10px;
}

.shotcam__hint {
  font-family: system-ui, sans-serif;
  font-size: 0.8rem;
  color: var(--ink-lo);
  text-transform: none;
}

.shotcam__progress {
  position: relative;
  overflow: hidden;
  border-radius: 14px;
  background: var(--arena-700);
  text-align: center;
}

.shotcam__progress p {
  position: relative;
  margin: 0;
  padding: 16px;
}

.shotcam__bar {
  position: absolute;
  inset: 0 auto 0 0;
  background: rgb(255 176 0 / 35%);
  transition: width 0.2s;
}

.shotcam__done {
  margin: 0;
  padding: 16px;
  text-align: center;
  border-radius: 14px;
  background: #0f3a22;
}

.shotcam__error {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 10px;
  border-radius: 14px;
  background: #4a1020;
}

.shotcam__error span {
  flex: 1;
}

.selfie {
  display: grid;
  justify-items: center;
  gap: 10px;
}

.selfie img {
  width: 140px;
  height: 140px;
  border-radius: 50%;
  object-fit: cover;
  border: 4px solid var(--led);
}
```

- [ ] **Step 5: Verify build, lint and behaviour**

Run: `cd frontend && npm run build && npx oxlint src/play && npx vitest run`
Expected: the build succeeds, oxlint reports `0 errors`, and the tests pass.

Then run a manual smoke check with the dev servers running:
- On `/play`, tap **📸 SHOT-CAM** and pick a photo. The phone should show `✅ On its way to the jumbotron!`.
- The TV should show **INSTANT REPLAY** with the photo, and it should then appear in the HIGHLIGHTS panel.
- Upload a phone video. The replay should play with sound, and the reel should loop it muted.
- Join as a new player with **📸 USE A SELFIE**. The TV leaderboard should show the photo avatar within a few seconds.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/play
git commit -m "feat(play): add shot-cam photo/video uploads and selfie avatars"
```

---
### Task 17: Game plugin framework

**Files:**
- Create: `backend/app/party/games/__init__.py`, `backend/app/party/games/base.py`
- Modify:
  - `backend/app/party/service.py`: `ServiceGameCtx`, `_start_games`, `game_action`, `set_game_enabled`.
  - `backend/app/party/realtime.py`: the `game:action` handler.
  - `backend/app/party/admin.py`: `game.enable`.
  - `backend/app/party/schema.py`: plugin wire models.
  - `backend/app/party/web.py`: `game_types=GAME_TYPES`.
- Create: `frontend/src/games/types.ts`, `frontend/src/games/act.ts`, `frontend/src/games/index.ts`, `frontend/src/host/GamesPanel.tsx`
- Modify: `frontend/src/tv/TvPage.tsx`, `frontend/src/play/PlayPage.tsx`, `frontend/src/host/HostPage.tsx`
- Test: `backend/tests/test_games.py`

**Interfaces:**
- Consumes: `PartyService`, `PartyAdmin.handlers`, `GameActionIn`, `GameEnableIn`, `GameMoment`.
- Produces:
  - **Backend, `games.base`:**
    - `PlayerCaller(player_id)`, `HostCaller()`, `Caller`.
    - The `GameCtx` protocol:
      - Attributes `game_id` and `rng`.
      - Methods `now_ms()`, `players() -> list[PlayerView]`, `teams() -> list[TeamView]`, `load_settings()`, `save_settings(data)` and `record_event(type, data)`.
      - Async methods `award_shots(drinker_ids, *, reason, logged_by_id=None)`, `push_moment(kind, data)` and `broadcast()`.
    - The `GamePlugin` protocol: class attributes `id`, `name`, `actions`, `host_actions` and `wire_models`; `__init__(ctx)`; `public_state()`; `async on_action(caller, action, payload)`; `async tick()`.
  - **Backend, registry:** `games.GAME_TYPES: list[type]`.
  - **Backend, service:** `PartyService.game_action(caller, GameActionIn)` and `PartyService.set_game_enabled(game_id, enabled)`.
  - **Frontend:**
    - Types `GameViewProps {state: unknown; party; act}`, `GamePhoneProps` (adds `me`) and `GameModule {id, name, Tv?, TvStrip?, Phone?, Host?}`.
    - `gameAct(gameId) => GameAct` and `GAMES: GameModule[]`.
    - `GamesPanel`.

- [ ] **Step 1: Write the failing tests**

`backend/tests/test_games.py`:
```python
import pytest
from pydantic import ValidationError
from sqlalchemy import func, select

from app import models
from app.database import SessionLocal
from app.party.admin import PartyAdmin
from app.party.camel import CamelModel
from app.party.contract import GameActionIn
from app.party.errors import PartyError
from app.party.games.base import HostCaller, PlayerCaller
from app.party.service import PartyService
from tests.conftest import join


class BumpIn(CamelModel):
    by: int = 1


class CounterState(CamelModel):
    total: int
    last_caller: str | None


class CounterGame:
    """A tiny plugin that exercises every GameCtx capability."""

    id = "counter"
    name = "Counter"
    actions = {"bump": BumpIn, "reset": BumpIn}
    host_actions = frozenset({"reset"})
    wire_models = []

    def __init__(self, ctx):
        self.ctx = ctx
        self.total = (ctx.load_settings() or {}).get("start", 0)
        self.last = None
        self.ticks = 0

    def public_state(self):
        return CounterState(total=self.total, last_caller=self.last)

    async def on_action(self, caller, action, payload):
        if action == "reset":
            self.total = 0
        else:
            self.total += payload.by
            self.last = caller.player_id
            if self.total >= 3:
                await self.ctx.award_shots([caller.player_id], reason="counter hit 3")
            await self.ctx.push_moment("bumped", {"total": self.total})
        self.ctx.record_event(action, {"total": self.total})
        await self.ctx.broadcast()

    async def tick(self):
        self.ticks += 1


@pytest.fixture
def games(db_reset, emitter, clock):
    svc = PartyService(emitter, clock=clock, game_types=[CounterGame])
    svc.load()
    return svc


def act(action, payload=None):
    return GameActionIn(request_id="r", game_id="counter", action=action, payload=payload or {})


async def test_game_state_rides_along_in_public_state(games, emitter):
    jess = await join(games, "Jess")
    await games.game_action(PlayerCaller(jess), act("bump", {"by": 2}))
    assert emitter.states[-1].games["counter"] == {"total": 2, "lastCaller": jess}
    assert emitter.states[-1].games_enabled == {"counter": True}


async def test_games_award_shots_and_push_their_own_moments(games, emitter):
    jess = await join(games, "Jess")
    await games.game_action(PlayerCaller(jess), act("bump", {"by": 3}))
    shot = next(m for m in emitter.moments if m.type == "shot")
    assert shot.source == "game:counter" and shot.drinkers[0].player_id == jess
    assert (emitter.moments[-1].type, emitter.moments[-1].kind, emitter.moments[-1].data) == ("game", "bumped", {"total": 3})


async def test_host_only_actions_and_payload_validation(games):
    jess = await join(games, "Jess")
    with pytest.raises(PartyError, match="Only the host"):
        await games.game_action(PlayerCaller(jess), act("reset"))
    await games.game_action(HostCaller(), act("reset"))
    with pytest.raises(PartyError, match="Unknown game action"):
        await games.game_action(PlayerCaller(jess), act("nope"))
    with pytest.raises(ValidationError):
        await games.game_action(PlayerCaller(jess), act("bump", {"by": "lots"}))


async def test_disabling_a_game_resets_it_and_stops_ticks(games):
    jess = await join(games, "Jess")
    await games.game_action(PlayerCaller(jess), act("bump"))
    await PartyAdmin(games).dispatch("game.enable", {"gameId": "counter", "enabled": False})
    assert games.games["counter"].total == 0
    with pytest.raises(PartyError, match="isn't on"):
        await games.game_action(PlayerCaller(jess), act("bump"))
    await games.tick_games()
    assert games.games["counter"].ticks == 0
    await PartyAdmin(games).dispatch("game.enable", {"gameId": "counter", "enabled": True})
    await games.tick_games()
    assert games.games["counter"].ticks == 1


async def test_game_settings_and_events_persist(games, emitter, clock):
    ctx = games.games["counter"].ctx
    ctx.save_settings({"start": 7})
    ctx.record_event("hello", {"x": 1})
    again = PartyService(emitter, clock=clock, game_types=[CounterGame])
    again.load()
    assert again.games["counter"].total == 7
    with SessionLocal() as db:
        assert db.scalar(select(func.count()).select_from(models.GameEvent)) == 1


async def test_unknown_game_is_refused(games):
    with pytest.raises(PartyError, match="isn't on"):
        await games.game_action(HostCaller(), GameActionIn(request_id="r", game_id="nope", action="x"))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && venv/bin/python -m pytest tests/test_games.py -q`
Expected: FAIL with `ModuleNotFoundError: No module named 'app.party.games'`.

- [ ] **Step 3: Implement the plugin interface**

`backend/app/party/games/base.py`:
```python
"""The game plugin interface.

A game is a class with the members of GamePlugin. The party core constructs it with a GameCtx,
validates action payloads against `actions`, calls `tick()` every second while the game is enabled,
and ships `public_state()` to every screen. The frontend half lives in frontend/src/games/<id>/.
"""
import random
from dataclasses import dataclass
from typing import Any, ClassVar, Protocol

from pydantic import BaseModel

from ..contract import PlayerView, TeamView


@dataclass(frozen=True)
class PlayerCaller:
    player_id: str


@dataclass(frozen=True)
class HostCaller:
    pass


Caller = PlayerCaller | HostCaller


class GameCtx(Protocol):
    """What the party core offers a game."""

    game_id: str
    rng: random.Random

    def now_ms(self) -> int: ...

    def players(self) -> list[PlayerView]: ...  # ranked, with `connected`

    def teams(self) -> list[TeamView]: ...

    def load_settings(self) -> dict[str, Any] | None: ...

    def save_settings(self, data: dict[str, Any]) -> None: ...

    def record_event(self, type: str, data: dict[str, Any]) -> None: ...

    async def award_shots(self, drinker_ids: list[str], *, reason: str, logged_by_id: str | None = None) -> list[str]: ...

    async def push_moment(self, kind: str, data: dict[str, Any]) -> None: ...

    async def broadcast(self) -> None: ...


class GamePlugin(Protocol):
    id: ClassVar[str]
    name: ClassVar[str]
    actions: ClassVar[dict[str, type[BaseModel]]]  # payload model per action name
    host_actions: ClassVar[frozenset[str]]  # actions only the host may call
    wire_models: ClassVar[list[tuple[type[BaseModel], str]]]  # exported to the TypeScript contract

    def public_state(self) -> BaseModel: ...

    async def on_action(self, caller: Caller, action: str, payload: Any) -> None: ...

    async def tick(self) -> None: ...
```

`backend/app/party/games/__init__.py`:
```python
"""Registry of game plugins. Add a game: write games/<id>.py and list its class here."""

GAME_TYPES: list[type] = []
```

In `backend/app/party/service.py`:
- Add `GameActionIn` and `GameMoment` to the `.contract` import block.
- Add `from .games.base import Caller, HostCaller`.
- Add this class after `MediaRec`:
```python
class ServiceGameCtx:
    """GameCtx for one game, backed by the PartyService."""

    def __init__(self, service: "PartyService", game_id: str) -> None:
        self.service = service
        self.game_id = game_id
        self.rng = service.rng

    def now_ms(self) -> int:
        return self.service.clock()

    def players(self) -> list[PlayerView]:
        return self.service.roster()[0]

    def teams(self) -> list[TeamView]:
        return self.service.roster()[1]

    def load_settings(self) -> dict[str, Any] | None:
        with self.service.session_factory() as db:
            row = db.get(models.Setting, (self.service.night_id, f"game:{self.game_id}"))
            return json.loads(row.value_json) if row else None

    def save_settings(self, data: dict[str, Any]) -> None:
        self.service.save_setting(f"game:{self.game_id}", json.dumps(data))

    def record_event(self, type: str, data: dict[str, Any]) -> None:
        with self.service.db() as db:
            db.add(models.GameEvent(id=new_id(), night_id=self.service.night_id, game_id=self.game_id, type=type,
                                    data_json=json.dumps(data), created_at=self.service.clock()))

    async def award_shots(self, drinker_ids: list[str], *, reason: str, logged_by_id: str | None = None) -> list[str]:
        return await self.service.log_shots(logged_by_id, new_id(), drinker_ids, source=f"game:{self.game_id}", reason=reason)

    async def push_moment(self, kind: str, data: dict[str, Any]) -> None:
        await self.service.emitter.moment(
            GameMoment(id=new_id(), at=self.service.clock(), game_id=self.game_id, kind=kind, data=data)
        )

    async def broadcast(self) -> None:
        await self.service.broadcast()
```
- Replace the `_start_games` method with the following, and add the two new methods after `tick_games`:
```python
    def _start_games(self) -> None:
        self.games = {cls.id: cls(ServiceGameCtx(self, cls.id)) for cls in self.game_types}

    async def game_action(self, caller: Caller, req: GameActionIn) -> None:
        game = self.games.get(req.game_id)
        if game is None or not self.games_enabled.get(req.game_id, True):
            raise PartyError("That game isn't on right now")
        model = game.actions.get(req.action)
        if model is None:
            raise PartyError("Unknown game action")
        if req.action in game.host_actions and not isinstance(caller, HostCaller):
            raise PartyError("Only the host can do that")
        await game.on_action(caller, req.action, model.model_validate(req.payload))

    async def set_game_enabled(self, game_id: str, enabled: bool) -> None:
        game = self.games.get(game_id)
        if game is None:
            raise PartyError("Unknown game")
        self.games_enabled[game_id] = enabled
        self.save_setting("games", json.dumps(self.games_enabled))
        if not enabled:  # a switched-off game starts fresh when it comes back
            self.games[game_id] = type(game)(ServiceGameCtx(self, game_id))
        await self.broadcast()
```

In `backend/app/party/realtime.py`:
- Add `GameActionIn` to the `.contract` import.
- Add `from .games.base import HostCaller, PlayerCaller`.
- Add this handler before `return sessions`:
```python
    @on("game:action", GameActionIn)
    async def game_action(sid, session, payload):
        if session.host:
            caller = HostCaller()
        elif session.player_id is not None and session.player_id in service.players:
            caller = PlayerCaller(session.player_id)
        else:
            raise PartyError("Join the game first")
        await service.game_action(caller, payload)
```

In `backend/app/party/admin.py`:
- Add `GameEnableIn` to the `.contract` import.
- Add the entry `"game.enable": (GameEnableIn, self.enable_game),` to `self.handlers`.
- Add the method:
```python
    async def enable_game(self, req: GameEnableIn) -> None:
        await self.s.set_game_enabled(req.game_id, req.enabled)
```

In `backend/app/party/schema.py`:
- Add `from .games import GAME_TYPES`.
- Change the first line of `contract_schema` to:
```python
    models = [*WIRE_MODELS, *(model for game in GAME_TYPES for model in game.wire_models)]
    _, schema = models_json_schema(models, title="HoopContract")
```

In `backend/app/party/web.py`:
- Add `from .games import GAME_TYPES`.
- Change the service line to:
```python
service = PartyService(SioEmitter(sio), lan_url=net.join_url, game_types=GAME_TYPES)
```

- [ ] **Step 4: Run the backend tests to verify they pass**

Run: `cd backend && venv/bin/python -m pytest -q`
Expected: all pass (`78 passed`).

- [ ] **Step 5: Add the frontend plugin slots**

`frontend/src/games/types.ts`:
```ts
import type { ComponentType } from "react";
import type { PlayerView, PublicState } from "../party/contract.gen";
import type { Ack } from "../party/socket";

export type GameAct = (action: string, payload?: Record<string, unknown>) => Promise<Ack>;

export interface GameViewProps {
  /** This game's public state (PublicState.games[id]); each game casts it to its generated type. */
  state: unknown;
  party: PublicState;
  act: GameAct;
}

export interface GamePhoneProps extends GameViewProps {
  me: PlayerView;
}

/** The frontend half of a game plugin. Every view renders null while the game is idle. */
export interface GameModule {
  id: string;
  name: string;
  Tv?: ComponentType<GameViewProps>; // full-screen overlay on the jumbotron
  TvStrip?: ComponentType<GameViewProps>; // small widget in the TV footer
  Phone?: ComponentType<GamePhoneProps>; // overlay on phones
  Host?: ComponentType<GameViewProps>; // controls in the host panel's GAMES tab
}
```

`frontend/src/games/act.ts`:
```ts
import { uuid } from "../party/ids";
import { call } from "../party/socket";
import type { GameAct } from "./types";

export function gameAct(gameId: string): GameAct {
  return (action, payload = {}) => call("game:action", { requestId: uuid(), gameId, action, payload });
}
```

`frontend/src/games/index.ts`:
```ts
import type { GameModule } from "./types";

/** Frontend game registry. Mirrors backend/app/party/games/__init__.py. */
export const GAMES: GameModule[] = [];
```

`frontend/src/host/GamesPanel.tsx`:
```tsx
import { gameAct } from "../games/act";
import { GAMES } from "../games";
import type { PublicState } from "../party/contract.gen";
import type { Run } from "./actions";

export function GamesPanel({ state, run }: { state: PublicState; run: Run }) {
  if (GAMES.length === 0) return <section className="panel">No games installed.</section>;
  return (
    <section className="panel">
      {GAMES.map((game) => {
        const Host = game.Host;
        const enabled = state.gamesEnabled[game.id] ?? true;
        return (
          <div key={game.id} className="panel__game">
            <label className="toggle display">
              <input type="checkbox" checked={enabled} onChange={(e) => void run("game.enable", { gameId: game.id, enabled: e.target.checked })} />
              {game.name}
            </label>
            {Host && enabled && <Host state={state.games[game.id]} party={state} act={gameAct(game.id)} />}
          </div>
        );
      })}
    </section>
  );
}
```

In `frontend/src/host/HostPage.tsx`:
- Add `import { GamesPanel } from "./GamesPanel";`.
- Change `type Tab` to `"shots" | "players" | "teams" | "games" | "settings"`.
- Add `["games", "GAMES"],` to `TABS` before `["settings", "SETTINGS"]`.
- Add `{tab === "games" && <GamesPanel state={state} run={run} />}` before the settings line.

In `frontend/src/tv/TvPage.tsx`:
- Add the imports `import { gameAct } from "../games/act";` and `import { GAMES } from "../games";`.
- Replace `<div className="tv__strips" />` and `<div className="tv__games" />` with:
```tsx
          <div className="tv__strips">
            {GAMES.map((game) => {
              const Strip = game.TvStrip;
              return Strip && state.gamesEnabled[game.id] ? <Strip key={game.id} state={state.games[game.id]} party={state} act={gameAct(game.id)} /> : null;
            })}
          </div>
```
and
```tsx
        <div className="tv__games">
          {GAMES.map((game) => {
            const View = game.Tv;
            return View && state.gamesEnabled[game.id] ? <View key={game.id} state={state.games[game.id]} party={state} act={gameAct(game.id)} /> : null;
          })}
        </div>
```
Note that the second block replaces the `<div className="tv__games" />` that sits after the footer.

In `frontend/src/play/PlayPage.tsx`:
- Add the imports `import { gameAct } from "../games/act";` and `import { GAMES } from "../games";`.
- Insert directly after the `<Toasts ... />` element in `PlayScreen`:
```tsx
      {GAMES.map((game) => {
        const View = game.Phone;
        return View && state.gamesEnabled[game.id] ? (
          <View key={game.id} state={state.games[game.id]} party={state} me={me} act={gameAct(game.id)} />
        ) : null;
      })}
```

Append to `frontend/src/tv/tv.css`:
```css
.tv__games {
  position: absolute;
  inset: 0;
  z-index: 15;
  pointer-events: none;
}

.tv__games > * {
  pointer-events: auto;
}
```

- [ ] **Step 6: Verify the frontend still builds**

Run: `cd frontend && npm run build && npx vitest run && npx oxlint src`
Expected: the build succeeds, `24` tests pass, and oxlint reports `0 errors`.

- [ ] **Step 7: Commit**

```bash
git add backend/app/party/games backend/app/party/service.py backend/app/party/realtime.py backend/app/party/admin.py backend/app/party/schema.py backend/app/party/web.py backend/tests/test_games.py frontend/src/games frontend/src/host frontend/src/tv frontend/src/play
git commit -m "feat(games): add the game plugin interface on both backend and frontend"
```

---

### Task 18: Challenges plugin (backend)

**Files:**
- Create: `backend/app/party/games/challenges.py`
- Modify: `backend/app/party/games/__init__.py`
- Regenerate: `frontend/src/party/contract.schema.json`, `frontend/src/party/contract.gen.ts`
- Test: `backend/tests/test_challenges.py`, `backend/tests/test_challenges_socket.py`

**Interfaces:**
- Consumes: `GameCtx`, `PlayerCaller`, `Caller` (Task 17); `PlayerView`, `TeamView`; `PartyError`.
- Produces:
  - `ChallengesGame`:
    - `id` is `"challenges"`.
    - Actions: `spin` (host), `settings` (host), `done` and `dare` (players).
    - `wire_models` exports `ChallengeState`, `ChallengeAnswerIn` and `ChallengeSettingsIn`.
  - Exported constants: `SLICES`, `SPIN_MS = 4500`, `OVER_MS = 6000`.
  - `ChallengeState` in TS: `{ nextAt, slices: {kind, label, weight}[], settings, current: {id, slice, label, teamId, dare, targets: {playerId, status}[], phase, spinEndsAt, deadline, overUntil} | null }`.
  - The moment `game/challenges/violation` with `{challengeId, playerIds}`.

- [ ] **Step 1: Write the failing tests**

`backend/tests/test_challenges.py`:
```python
import random

import pytest

from app.party.contract import AvatarView, PlayerView, TeamView
from app.party.errors import PartyError
from app.party.games.base import HostCaller, PlayerCaller
from app.party.games.challenges import (
    OVER_MS,
    SPIN_MS,
    ChallengeAnswerIn,
    ChallengesGame,
    ChallengeSettings,
    ChallengeSettingsIn,
    EmptyIn,
)

ALL_KINDS = ["player", "pair", "team", "everyone", "leader", "last-team", "dare"]


class FakeCtx:
    game_id = "challenges"

    def __init__(self, players, teams=None):
        self.rng = random.Random(7)
        self.now = 1_000_000
        self._players = players
        self._teams = teams or [team("home"), team("away")]
        self.awarded, self.moments, self.events = [], [], []
        self.saved = None

    def now_ms(self):
        return self.now

    def players(self):
        return self._players

    def teams(self):
        return self._teams

    def load_settings(self):
        return self.saved

    def save_settings(self, data):
        self.saved = data

    def record_event(self, type, data):
        self.events.append((type, data))

    async def award_shots(self, drinker_ids, *, reason, logged_by_id=None):
        self.awarded.append((list(drinker_ids), reason, logged_by_id))
        return ["s"]

    async def push_moment(self, kind, data):
        self.moments.append((kind, data))

    async def broadcast(self):
        pass


def player(pid, team_id="home", count=0, rank=1, connected=True):
    return PlayerView(id=pid, name=pid.title(), avatar=AvatarView(kind="emoji", value="🏀"), team_id=team_id,
                      count=count, rank=rank, streak=None, connected=connected, last_shot_at=None)


def team(tid, total=0):
    return TeamView(id=tid, name=tid.upper(), color="#FF7A1A", total=total, size=1)


def only(*kinds):
    weights = {k: 0 for k in ALL_KINDS}
    weights.update({k: 1 for k in kinds})
    return weights


def game_with(ctx, *kinds, **settings):
    ctx.saved = ChallengeSettings(weights=only(*kinds), **settings).wire()
    return ChallengesGame(ctx)


async def go_live(game, ctx):
    await game.on_action(HostCaller(), "spin", EmptyIn())
    ctx.now += SPIN_MS
    await game.tick()
    return game.current


def test_first_challenge_is_scheduled_after_the_interval():
    ctx = FakeCtx([player("jess")])
    assert ChallengesGame(ctx).public_state().next_at == ctx.now + 12 * 60_000
    assert game_with(FakeCtx([player("jess")]), "player", interval_min=0).public_state().next_at is None


async def test_the_timer_spins_the_wheel_and_the_shot_clock_follows():
    ctx = FakeCtx([player("jess")])
    game = game_with(ctx, "player")
    ctx.now = game.next_at
    await game.tick()
    assert game.current.phase == "spinning" and game.current.spin_ends_at == ctx.now + SPIN_MS
    assert game.public_state().next_at is None
    ctx.now += SPIN_MS
    await game.tick()
    assert game.current.phase == "live" and game.current.deadline == ctx.now + 24_000


async def test_done_awards_a_game_shot_and_ends_early_when_everyone_answered():
    ctx = FakeCtx([player("jess")])
    game = game_with(ctx, "player")
    current = await go_live(game, ctx)
    await game.on_action(PlayerCaller("jess"), "done", ChallengeAnswerIn(challenge_id=current.id))
    assert ctx.awarded == [(["jess"], "challenge:player", "jess")]
    await game.tick()
    assert game.current.phase == "over" and ctx.moments == []
    ctx.now += OVER_MS
    await game.tick()
    assert game.current is None and game.next_at == ctx.now + 12 * 60_000


async def test_timeouts_are_violations_without_shots():
    ctx = FakeCtx([player("jess"), player("sam", "away")])
    game = game_with(ctx, "everyone")
    current = await go_live(game, ctx)
    await game.on_action(PlayerCaller("jess"), "done", ChallengeAnswerIn(challenge_id=current.id))
    ctx.now += 24_000
    await game.tick()
    assert [(t.player_id, t.status) for t in game.current.targets] == [("jess", "done"), ("sam", "timeout")]
    assert ctx.moments == [("violation", {"challengeId": current.id, "playerIds": ["sam"]})]
    assert ctx.awarded == [(["jess"], "challenge:everyone", "jess")]


async def test_only_connected_players_are_called():
    ctx = FakeCtx([player("jess"), player("sam", connected=False)])
    game = game_with(ctx, "player")
    for _ in range(10):
        game.current = None
        await game.on_action(HostCaller(), "spin", EmptyIn())
        assert [t.player_id for t in game.current.targets] == ["jess"]


async def test_impossible_slices_fall_through_to_the_next_one():
    ctx = FakeCtx([player("jess")])
    game = game_with(ctx, "pair", "team")
    await game.on_action(HostCaller(), "spin", EmptyIn())
    assert game.current.slice == "team" and game.current.team_id == "home"


async def test_leader_and_last_team_slices_pick_the_right_people():
    players = [player("jess", "home", count=5, rank=1), player("sam", "away", count=3, rank=2), player("kim", "away", count=3, rank=2)]
    ctx = FakeCtx(players, [team("home", 5), team("away", 6)])
    leader = game_with(ctx, "leader")
    await leader.on_action(HostCaller(), "spin", EmptyIn())
    assert [t.player_id for t in leader.current.targets] == ["jess"]
    trailing = game_with(ctx, "last-team")
    await trailing.on_action(HostCaller(), "spin", EmptyIn())
    assert trailing.current.team_id == "home" and [t.player_id for t in trailing.current.targets] == ["jess"]


async def test_spin_needs_someone_to_call_and_one_challenge_at_a_time():
    ctx = FakeCtx([player("jess")])
    with pytest.raises(PartyError, match="Nobody"):
        await game_with(ctx, "leader").on_action(HostCaller(), "spin", EmptyIn())  # no shots yet: no leader
    game = game_with(ctx, "player")
    await game.on_action(HostCaller(), "spin", EmptyIn())
    with pytest.raises(PartyError, match="already running"):
        await game.on_action(HostCaller(), "spin", EmptyIn())


async def test_dare_slice_offers_a_dare_instead_of_a_shot():
    ctx = FakeCtx([player("jess")])
    game = game_with(ctx, "dare")
    current = await go_live(game, ctx)
    assert current.dare in game.settings.dares
    await game.on_action(PlayerCaller("jess"), "dare", ChallengeAnswerIn(challenge_id=current.id))
    assert current.targets[0].status == "dare" and ctx.awarded == []


async def test_answer_rules():
    ctx = FakeCtx([player("jess"), player("sam")])
    game = game_with(ctx, "player")
    await game.on_action(HostCaller(), "spin", EmptyIn())
    current = game.current
    target = current.targets[0].player_id
    other = "sam" if target == "jess" else "jess"
    with pytest.raises(PartyError, match="over"):  # still spinning
        await game.on_action(PlayerCaller(target), "done", ChallengeAnswerIn(challenge_id=current.id))
    ctx.now += SPIN_MS
    await game.tick()
    with pytest.raises(PartyError, match="weren't called"):
        await game.on_action(PlayerCaller(other), "done", ChallengeAnswerIn(challenge_id=current.id))
    with pytest.raises(PartyError, match="over"):
        await game.on_action(PlayerCaller(target), "done", ChallengeAnswerIn(challenge_id="old"))
    with pytest.raises(PartyError, match="No dare"):
        await game.on_action(PlayerCaller(target), "dare", ChallengeAnswerIn(challenge_id=current.id))


async def test_settings_persist_and_reschedule():
    ctx = FakeCtx([player("jess")])
    game = ChallengesGame(ctx)
    await game.on_action(HostCaller(), "settings", ChallengeSettingsIn(settings=ChallengeSettings(interval_min=5)))
    assert game.next_at == ctx.now + 5 * 60_000
    assert ChallengesGame(ctx).settings.interval_min == 5
```

`backend/tests/test_challenges_socket.py`:
```python
from tests.conftest import socket_join

KINDS = ["player", "pair", "team", "everyone", "leader", "last-team", "dare"]


async def test_host_spins_and_the_called_player_answers_over_sockets(connect):
    host, phone = await connect(), await connect()
    assert (await host.call("host:auth", {"pin": "4242"}))["ok"]
    new_night = await host.call("host:action", {"requestId": "n", "action": "night.new", "payload": {"name": "Challenge test"}})
    assert new_night["ok"]
    await phone.wait_for(lambda: phone.state["nightName"] == "Challenge test", "new night")
    me = await socket_join(phone)

    weights = {k: 0 for k in KINDS} | {"player": 1}
    settings = {**phone.state["games"]["challenges"]["settings"], "weights": weights}
    saved = await host.call("game:action", {"requestId": "s", "gameId": "challenges", "action": "settings", "payload": {"settings": settings}})
    assert saved["ok"], saved
    denied = await phone.call("game:action", {"requestId": "x", "gameId": "challenges", "action": "spin", "payload": {}})
    assert denied == {"ok": False, "error": "Only the host can do that"}
    assert (await host.call("game:action", {"requestId": "p", "gameId": "challenges", "action": "spin", "payload": {}}))["ok"]

    def live():
        current = phone.state["games"]["challenges"]["current"]
        return current if current and current["phase"] == "live" else None

    current = await phone.wait_for(live, "live challenge", timeout=8)
    assert [t["playerId"] for t in current["targets"]] == [me["playerId"]]
    done = await phone.call("game:action", {"requestId": "d", "gameId": "challenges", "action": "done", "payload": {"challengeId": current["id"]}})
    assert done == {"ok": True}
    await phone.wait_for(
        lambda: any(f["source"] == "game:challenges" and f["drinkerId"] == me["playerId"] for f in phone.state["feed"]),
        "challenge shot in the feed",
    )
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && venv/bin/python -m pytest tests/test_challenges.py tests/test_challenges_socket.py -q`
Expected: FAIL with `ModuleNotFoundError: No module named 'app.party.games.challenges'`.

- [ ] **Step 3: Implement the plugin**

`backend/app/party/games/challenges.py`:
```python
"""Challenges: every few minutes the TV spins a wheel and calls people out for a shot."""
import uuid
from typing import Annotated, Any, Literal

from pydantic import Field, StringConstraints

from ..camel import CamelModel
from ..contract import PlayerView
from ..errors import PartyError
from .base import Caller, GameCtx, PlayerCaller

SliceKind = Literal["player", "pair", "team", "everyone", "leader", "last-team", "dare"]

SLICES: list[tuple[SliceKind, str]] = [
    ("player", "SPOTLIGHT"),
    ("pair", "DOUBLE TEAM"),
    ("team", "TEAM SHOT"),
    ("everyone", "ALL-STAR SHOT"),
    ("leader", "DEFEND THE CROWN"),
    ("last-team", "COMEBACK SHOT"),
    ("dare", "DARE OR DRINK"),
]
SPIN_MS = 4500
OVER_MS = 6000
DEFAULT_DARES = [
    "Do your best free-throw routine with no ball",
    "Give a 20-second post-game interview about tonight",
    "Show off your championship celebration",
    "Do your best impression of someone in the room",
    "Commentate the next minute like a sportscaster",
    "Moonwalk across the room",
    "Serenade the person to your right for 15 seconds",
    "Tell the room your most embarrassing sports moment",
    "Let the group pick your next song",
    "Do 10 air jump shots with full follow-through",
]

Dare = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=120)]


class ChallengeSettings(CamelModel):
    interval_min: int = Field(12, ge=0, le=120)
    shot_clock_sec: int = Field(24, ge=5, le=120)
    weights: dict[SliceKind, Annotated[int, Field(ge=0, le=100)]] = {
        "player": 3, "pair": 2, "team": 2, "everyone": 1, "leader": 2, "last-team": 1, "dare": 2,
    }
    dares: Annotated[list[Dare], Field(max_length=50)] = DEFAULT_DARES


class ChallengeTarget(CamelModel):
    player_id: str
    status: Literal["pending", "done", "dare", "timeout"]


class SliceView(CamelModel):
    kind: SliceKind
    label: str
    weight: int


class ChallengeView(CamelModel):
    id: str
    slice: SliceKind
    label: str
    team_id: str | None
    dare: str | None
    targets: list[ChallengeTarget]
    phase: Literal["spinning", "live", "over"]
    spin_ends_at: int
    deadline: int | None
    over_until: int | None


class ChallengeState(CamelModel):
    next_at: int | None
    slices: list[SliceView]
    settings: ChallengeSettings
    current: ChallengeView | None


class EmptyIn(CamelModel):
    pass


class ChallengeAnswerIn(CamelModel):
    challenge_id: str


class ChallengeSettingsIn(CamelModel):
    settings: ChallengeSettings


Pick = tuple[list[str], str | None, str | None]  # target ids, team id, dare


class ChallengesGame:
    id = "challenges"
    name = "Challenges"
    actions = {"spin": EmptyIn, "done": ChallengeAnswerIn, "dare": ChallengeAnswerIn, "settings": ChallengeSettingsIn}
    host_actions = frozenset({"spin", "settings"})
    wire_models = [(ChallengeState, "serialization"), (ChallengeAnswerIn, "validation"), (ChallengeSettingsIn, "validation")]

    def __init__(self, ctx: GameCtx) -> None:
        self.ctx = ctx
        saved = ctx.load_settings()
        self.settings = ChallengeSettings.model_validate(saved) if saved else ChallengeSettings()
        self.current: ChallengeView | None = None
        self.next_at = self._next_time()

    def _next_time(self) -> int | None:
        minutes = self.settings.interval_min
        return self.ctx.now_ms() + minutes * 60_000 if minutes > 0 else None

    def public_state(self) -> ChallengeState:
        return ChallengeState(
            next_at=None if self.current else self.next_at,
            slices=[SliceView(kind=k, label=label, weight=self.settings.weights.get(k, 0)) for k, label in SLICES],
            settings=self.settings,
            current=self.current,
        )

    async def on_action(self, caller: Caller, action: str, payload: Any) -> None:
        if action == "spin":
            if self.current is not None:
                raise PartyError("A challenge is already running")
            if not self._start():
                raise PartyError("Nobody to call out right now")
        elif action == "settings":
            self.settings = payload.settings
            self.ctx.save_settings(self.settings.wire())
            if self.current is None:
                self.next_at = self._next_time()
        else:
            await self._answer(caller, payload.challenge_id, action)
        await self.ctx.broadcast()

    async def tick(self) -> None:
        now, cur = self.ctx.now_ms(), self.current
        if cur is None:
            if self.next_at is not None and now >= self.next_at:
                if not self._start():
                    self.next_at = self._next_time()  # nobody around; try again next interval
                await self.ctx.broadcast()
            return
        if cur.phase == "spinning" and now >= cur.spin_ends_at:
            cur.phase, cur.deadline = "live", now + self.settings.shot_clock_sec * 1000
            await self.ctx.broadcast()
        elif cur.phase == "live" and (now >= (cur.deadline or 0) or all(t.status != "pending" for t in cur.targets)):
            await self._finish(now)
        elif cur.phase == "over" and now >= (cur.over_until or 0):
            self.current, self.next_at = None, self._next_time()
            await self.ctx.broadcast()

    def _start(self) -> bool:
        picked = self._pick()
        if picked is None:
            return False
        kind, (target_ids, team_id, dare) = picked
        now = self.ctx.now_ms()
        self.current = ChallengeView(
            id=str(uuid.uuid4()), slice=kind, label=dict(SLICES)[kind], team_id=team_id, dare=dare,
            targets=[ChallengeTarget(player_id=pid, status="pending") for pid in target_ids],
            phase="spinning", spin_ends_at=now + SPIN_MS, deadline=None, over_until=None,
        )
        self.next_at = None
        self.ctx.record_event("start", {"id": self.current.id, "slice": kind, "targets": target_ids})
        return True

    def _pick(self) -> tuple[SliceKind, Pick] | None:
        players = [p for p in self.ctx.players() if p.connected]
        kinds = [k for k, _ in SLICES]
        weights = [self.settings.weights.get(k, 0) for k in kinds]
        if not players or sum(weights) == 0:
            return None
        start = self.ctx.rng.choices(range(len(kinds)), weights=weights)[0]
        for offset in range(len(kinds)):  # a slice nobody can satisfy hands over to the next one
            i = (start + offset) % len(kinds)
            if weights[i] > 0 and (found := self._targets(kinds[i], players)) is not None:
                return kinds[i], found
        return None

    def _targets(self, kind: SliceKind, players: list[PlayerView]) -> Pick | None:
        rng, ids = self.ctx.rng, [p.id for p in players]
        if kind == "player":
            return [rng.choice(ids)], None, None
        if kind == "dare":
            return ([rng.choice(ids)], None, rng.choice(self.settings.dares)) if self.settings.dares else None
        if kind == "pair":
            return (rng.sample(ids, 2), None, None) if len(ids) >= 2 else None
        if kind == "everyone":
            return (ids, None, None) if len(ids) >= 2 else None
        if kind == "leader":
            scored = [p for p in players if p.count > 0]
            if not scored:
                return None
            best = min(p.rank for p in scored)
            return [rng.choice([p.id for p in scored if p.rank == best])], None, None
        by_team: dict[str, list[str]] = {}
        for p in players:
            by_team.setdefault(p.team_id, []).append(p.id)
        if kind == "team":
            team_id = rng.choice(sorted(by_team))
            return by_team[team_id], team_id, None
        if kind == "last-team":
            if len(by_team) < 2:
                return None
            totals = {t.id: t.total for t in self.ctx.teams()}
            lowest = min(totals.get(t, 0) for t in by_team)
            team_id = rng.choice(sorted(t for t in by_team if totals.get(t, 0) == lowest))
            return by_team[team_id], team_id, None
        return None

    async def _answer(self, caller: Caller, challenge_id: str, action: str) -> None:
        cur = self.current
        if not isinstance(caller, PlayerCaller):
            raise PartyError("Only players can answer a challenge")
        if cur is None or cur.id != challenge_id or cur.phase != "live":
            raise PartyError("That challenge is over")
        target = next((t for t in cur.targets if t.player_id == caller.player_id), None)
        if target is None:
            raise PartyError("You weren't called")
        if target.status != "pending":
            return
        if action == "dare":
            if cur.dare is None:
                raise PartyError("No dare this time")
            target.status = "dare"
            return
        target.status = "done"
        await self.ctx.award_shots([caller.player_id], reason=f"challenge:{cur.slice}", logged_by_id=caller.player_id)

    async def _finish(self, now: int) -> None:
        cur = self.current
        assert cur is not None
        late = [t for t in cur.targets if t.status == "pending"]
        for t in late:
            t.status = "timeout"
        cur.phase, cur.over_until = "over", now + OVER_MS
        self.ctx.record_event("finish", {"id": cur.id, "targets": {t.player_id: t.status for t in cur.targets}})
        if late:
            await self.ctx.push_moment("violation", {"challengeId": cur.id, "playerIds": [t.player_id for t in late]})
        await self.ctx.broadcast()
```

`backend/app/party/games/__init__.py` (full file):
```python
"""Registry of game plugins. Add a game: write games/<id>.py and list its class here."""
from .challenges import ChallengesGame

GAME_TYPES: list[type] = [ChallengesGame]
```

- [ ] **Step 4: Regenerate the contract and run the tests**

Run: `cd backend && venv/bin/python scripts/export_schema.py && cd ../frontend && npm run gen:types && grep -c "export interface ChallengeState" src/party/contract.gen.ts`
Expected: `1`.

Run: `cd backend && venv/bin/python -m pytest -q`
Expected: all pass (`90 passed`).

- [ ] **Step 5: Commit**

```bash
git add backend/app/party/games backend/tests/test_challenges.py backend/tests/test_challenges_socket.py frontend/src/party/contract.schema.json frontend/src/party/contract.gen.ts
git commit -m "feat(games): add the Challenges plugin with wheel slices, shot clock and violations"
```

---

### Task 19: Challenges on the TV, phones and host panel

**Files:**
- Create: `frontend/src/games/challenges/index.ts`, `wheel.ts`, `Wheel.tsx`, `ChallengeTv.tsx`, `ChallengeStrip.tsx`, `ChallengePhone.tsx`, `ChallengeHost.tsx`, `challenges.css`
- Modify: `frontend/src/games/index.ts`
- Test: `frontend/src/games/challenges/wheel.test.ts`

**Interfaces:**
- Consumes:
  - `GameModule`, `GameViewProps`, `GamePhoneProps` (Task 17).
  - `ChallengeState` (the generated contract).
  - `useNow`, `serverNow`, `onMoment`; `sfx`, `announce`; `Avatar`, `LedDigits`; `NumberField`; `formatClock`.
- Produces:
  - `wheelArcs(slices) -> WheelArc[]`, `landingRotation(arcs, kind, turns?) -> number` and `arcPath(start, end, r) -> string`.
  - The `challenges: GameModule` registry entry.
- Visible text: `CHALLENGE TIME`, `SHOT CLOCK VIOLATION`, `ALL BUCKETS!`, `YOU'VE BEEN CALLED`, `DONE 🥃`, `DID THE DARE 🎭`, `🎡 SPIN NOW` and `NEXT CHALLENGE`.

- [ ] **Step 1: Write the failing test**

`frontend/src/games/challenges/wheel.test.ts`:
```ts
import { describe, expect, it } from "vitest";
import { arcPath, landingRotation, wheelArcs } from "./wheel";

const slices = [
  { kind: "player", label: "SPOTLIGHT", weight: 1 },
  { kind: "pair", label: "DOUBLE TEAM", weight: 0 },
  { kind: "team", label: "TEAM SHOT", weight: 1 },
  { kind: "dare", label: "DARE OR DRINK", weight: 2 },
];

describe("wheel geometry", () => {
  it("sizes slices by weight and drops zero-weight slices", () => {
    expect(wheelArcs(slices).map((a) => [a.kind, a.start, a.end])).toEqual([
      ["player", 0, 90],
      ["team", 90, 180],
      ["dare", 180, 360],
    ]);
  });

  it("parks the middle of the winning slice under the top pointer", () => {
    const arcs = wheelArcs(slices);
    expect(landingRotation(arcs, "dare", 5)).toBe(5 * 360 + 90);
    expect(landingRotation(arcs, "player", 5) % 360).toBe(315);
  });

  it("draws a full circle for a single slice", () => {
    expect(arcPath(0, 360, 10)).toContain("A 10 10 0 1 1");
    expect(arcPath(0, 90, 10)).toBe("M 10 10 L 10 0 A 10 10 0 0 1 20 10 Z");
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend && npx vitest run src/games`
Expected: FAIL with `Failed to resolve import "./wheel"`.

- [ ] **Step 3: Implement the wheel and views**

`frontend/src/games/challenges/wheel.ts`:
```ts
export interface WheelSlice {
  kind: string;
  label: string;
  weight: number;
}

/** Degrees, 0 = top, clockwise. */
export interface WheelArc extends WheelSlice {
  start: number;
  end: number;
}

export function wheelArcs(slices: WheelSlice[]): WheelArc[] {
  const live = slices.filter((s) => s.weight > 0);
  const total = live.reduce((sum, s) => sum + s.weight, 0);
  let angle = 0;
  return live.map((s) => {
    const start = angle;
    angle += (s.weight / total) * 360;
    return { ...s, start, end: angle };
  });
}

/** Clockwise rotation that parks the middle of `kind` under the top pointer after `turns` full spins. */
export function landingRotation(arcs: WheelArc[], kind: string, turns = 5): number {
  const arc = arcs.find((a) => a.kind === kind);
  if (!arc) return turns * 360;
  return turns * 360 + (360 - (arc.start + arc.end) / 2);
}

function point(deg: number, r: number): string {
  const rad = ((deg - 90) * Math.PI) / 180;
  const x = Math.round((r + r * Math.cos(rad)) * 1000) / 1000;
  const y = Math.round((r + r * Math.sin(rad)) * 1000) / 1000;
  return `${x} ${y}`;
}

/** SVG path for a pie slice of a circle with radius r centred at (r, r). */
export function arcPath(start: number, end: number, r: number): string {
  if (end - start >= 359.999) return `M ${r} 0 A ${r} ${r} 0 1 1 ${r} ${2 * r} A ${r} ${r} 0 1 1 ${r} 0 Z`;
  const large = end - start > 180 ? 1 : 0;
  return `M ${r} ${r} L ${point(start, r)} A ${r} ${r} 0 ${large} 1 ${point(end, r)} Z`;
}
```

`frontend/src/games/challenges/Wheel.tsx`:
```tsx
import { motion } from "motion/react";
import { arcPath, landingRotation, wheelArcs, type WheelSlice } from "./wheel";

const COLORS = ["#FF7A1A", "#2D8CFF", "#FFB000", "#35E07F", "#FF3B5C", "#A56BFF", "#00C2D1"];
const R = 200;

export function Wheel({ slices, landOn, spinMs }: { slices: WheelSlice[]; landOn: string; spinMs: number }) {
  const arcs = wheelArcs(slices);
  return (
    <div className="wheel">
      <div className="wheel__pointer" />
      <motion.svg
        className="wheel__disc"
        viewBox={`0 0 ${2 * R} ${2 * R}`}
        initial={{ rotate: 0 }}
        animate={{ rotate: landingRotation(arcs, landOn) }}
        transition={{ duration: spinMs / 1000, ease: [0.15, 0.85, 0.25, 1] }}
      >
        {arcs.map((a, i) => (
          <g key={a.kind}>
            <path d={arcPath(a.start, a.end, R)} fill={COLORS[i % COLORS.length]} stroke="#06070d" strokeWidth={3} />
            <text
              x={R}
              y={R * 0.3}
              fill="#06070d"
              fontSize={15}
              fontFamily="Bungee, sans-serif"
              textAnchor="middle"
              transform={`rotate(${(a.start + a.end) / 2} ${R} ${R})`}
            >
              {a.label}
            </text>
          </g>
        ))}
        <circle cx={R} cy={R} r={26} fill="#06070d" stroke="#f7f3ea" strokeWidth={4} />
      </motion.svg>
    </div>
  );
}
```

`frontend/src/games/challenges/ChallengeTv.tsx`:
```tsx
import { useEffect, useState } from "react";
import { Avatar } from "../../arcade/Avatar";
import { LedDigits } from "../../arcade/LedDigits";
import { sfx } from "../../arcade/sound";
import { announce } from "../../arcade/voice";
import type { ChallengeState } from "../../party/contract.gen";
import { onMoment, serverNow, useNow } from "../../party/store";
import type { GameViewProps } from "../types";
import { Wheel } from "./Wheel";

const STATUS = { pending: "⏳ ON THE CLOCK", done: "✅ BUCKET", dare: "🎭 DARE DONE", timeout: "🚨 VIOLATION" } as const;

type Current = NonNullable<ChallengeState["current"]>;

export function ChallengeTv({ state, party }: GameViewProps) {
  const s = state as ChallengeState;
  const cur = s.current;
  const now = useNow(250);
  const phase = cur?.phase;
  const secondsLeft = cur?.deadline ? Math.max(0, Math.ceil((cur.deadline - now) / 1000)) : null;
  const names = cur?.targets.map((t) => party.players.find((p) => p.id === t.playerId)?.name).filter(Boolean).join(", ");

  useEffect(() => {
    if (phase === "spinning") sfx.spin();
    if (phase === "live" && cur) {
      sfx.horn();
      announce(`${cur.label}! ${names ?? ""}, you're on the clock!`);
    }
    // Cue once per phase change of each challenge.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [phase, cur?.id]);

  useEffect(() => {
    if (phase === "live" && secondsLeft !== null && secondsLeft > 0 && secondsLeft <= 5) sfx.tick();
  }, [phase, secondsLeft]);

  useEffect(
    () =>
      onMoment((m) => {
        if (m.type === "game" && m.gameId === "challenges" && m.kind === "violation") {
          sfx.buzzer();
          announce("Shot clock violation!");
        }
      }),
    [],
  );

  if (!cur) return null;
  return (
    <div className="challenge">
      <p className="challenge__kicker pixel">CHALLENGE TIME</p>
      {cur.phase === "spinning" ? <Spin state={s} current={cur} /> : <Called current={cur} party={party} secondsLeft={secondsLeft} />}
    </div>
  );
}

function Spin({ state, current }: { state: ChallengeState; current: Current }) {
  // Measured once when the wheel mounts, so re-renders never restart the spin.
  const [spinMs] = useState(() => Math.max(1000, current.spinEndsAt - serverNow() - 300));
  return <Wheel slices={state.slices} landOn={current.slice} spinMs={spinMs} />;
}

function Called({ current, party, secondsLeft }: { current: Current; party: GameViewProps["party"]; secondsLeft: number | null }) {
  const players = new Map(party.players.map((p) => [p.id, p]));
  const team = current.teamId ? party.teams.find((t) => t.id === current.teamId) : undefined;
  const violation = current.targets.some((t) => t.status === "timeout");
  const hot = secondsLeft !== null && secondsLeft <= 5;
  return (
    <>
      <h1 className="challenge__title display">{current.label}</h1>
      {team && (
        <p className="challenge__team display" style={{ color: team.color }}>
          {team.name}
        </p>
      )}
      {current.dare && <p className="challenge__dare">🎭 {current.dare} — or drink</p>}
      <ul className="challenge__targets">
        {current.targets.map((t) => {
          const p = players.get(t.playerId);
          if (!p) return null;
          return (
            <li key={t.playerId} className={`challenge__target challenge__target--${t.status}`}>
              <Avatar avatar={p.avatar} size="7vw" color={party.teams.find((x) => x.id === p.teamId)?.color} />
              <span className="display">{p.name}</span>
              <span className="pixel">{STATUS[t.status]}</span>
            </li>
          );
        })}
      </ul>
      {current.phase === "live" && secondsLeft !== null && (
        <div className="challenge__clock">
          <LedDigits value={secondsLeft} digits={2} size="9vw" color={hot ? "var(--bad)" : "var(--led)"} />
          <span className="pixel">SHOT CLOCK</span>
        </div>
      )}
      {current.phase === "over" && (
        <h2 className={`challenge__verdict display${violation ? " challenge__verdict--bad" : ""}`}>
          {violation ? "SHOT CLOCK VIOLATION" : "ALL BUCKETS!"}
        </h2>
      )}
    </>
  );
}
```

`frontend/src/games/challenges/ChallengeStrip.tsx`:
```tsx
import { LedDigits } from "../../arcade/LedDigits";
import type { ChallengeState } from "../../party/contract.gen";
import { useNow } from "../../party/store";
import { formatClock } from "../../party/time";
import type { GameViewProps } from "../types";

export function ChallengeStrip({ state }: GameViewProps) {
  const s = state as ChallengeState;
  const now = useNow(1000);
  if (s.current || s.nextAt === null) return null;
  return (
    <div className="challenge-strip">
      <span className="pixel">NEXT CHALLENGE</span>
      <LedDigits value={formatClock(s.nextAt - now)} size="3vw" color="var(--bad)" />
    </div>
  );
}
```

`frontend/src/games/challenges/ChallengePhone.tsx`:
```tsx
import { useState } from "react";
import { LedDigits } from "../../arcade/LedDigits";
import type { ChallengeState } from "../../party/contract.gen";
import { useNow } from "../../party/store";
import type { GamePhoneProps } from "../types";

export function ChallengePhone({ state, me, act }: GamePhoneProps) {
  const s = state as ChallengeState;
  const cur = s.current;
  const now = useNow(250);
  const [error, setError] = useState<string | null>(null);

  if (!cur) return null;
  if (cur.phase === "spinning") return <div className="challenge-banner pixel">🎡 CHALLENGE WHEEL · WATCH THE TV!</div>;
  const target = cur.targets.find((t) => t.playerId === me.id);
  if (cur.phase !== "live" || !target || target.status !== "pending") return null;

  const challengeId = cur.id;
  const secondsLeft = Math.max(0, Math.ceil(((cur.deadline ?? now) - now) / 1000));
  async function answer(action: "done" | "dare") {
    const ack = await act(action, { challengeId });
    if (!ack.ok) setError(ack.error);
  }
  return (
    <div className="challenge-phone" role="dialog" aria-label="Challenge">
      <p className="pixel">YOU'VE BEEN CALLED</p>
      <h1 className="display challenge-phone__title">{cur.label}</h1>
      {cur.dare && (
        <p className="challenge-phone__dare">
          🎭 {cur.dare}
          <br />
          <small>…or take the shot</small>
        </p>
      )}
      <LedDigits value={secondsLeft} digits={2} size="110px" color={secondsLeft <= 5 ? "var(--bad)" : "var(--led)"} />
      <button className="btn btn--good challenge-phone__done" onClick={() => void answer("done")}>
        DONE 🥃
      </button>
      {cur.dare && (
        <button className="btn" onClick={() => void answer("dare")}>
          DID THE DARE 🎭
        </button>
      )}
      {error && <p role="alert">{error}</p>}
    </div>
  );
}
```

`frontend/src/games/challenges/ChallengeHost.tsx`:
```tsx
import { useState } from "react";
import { NumberField } from "../../host/NumberField";
import type { ChallengeState } from "../../party/contract.gen";
import type { GameViewProps } from "../types";

export function ChallengeHost({ state, act }: GameViewProps) {
  const s = state as ChallengeState;
  const [draft, setDraft] = useState(s.settings);
  const [dares, setDares] = useState(s.settings.dares.join("\n"));
  const [message, setMessage] = useState<string | null>(null);

  async function run(action: string, payload: Record<string, unknown> = {}) {
    const ack = await act(action, payload);
    setMessage(ack.ok ? "Done ✓" : ack.error);
  }

  return (
    <div className="panel">
      <button className="btn" disabled={s.current !== null} onClick={() => void run("spin")}>
        🎡 SPIN NOW
      </button>
      <div className="fields">
        <NumberField label="Auto-spin every (min, 0 = off)" value={draft.intervalMin} onChange={(n) => setDraft((d) => ({ ...d, intervalMin: n }))} />
        <NumberField label="Shot clock (seconds)" value={draft.shotClockSec} onChange={(n) => setDraft((d) => ({ ...d, shotClockSec: n }))} />
        {s.slices.map((slice) => (
          <NumberField
            key={slice.kind}
            label={`Weight · ${slice.label}`}
            value={draft.weights[slice.kind] ?? 0}
            onChange={(n) => setDraft((d) => ({ ...d, weights: { ...d.weights, [slice.kind]: n } }))}
          />
        ))}
      </div>
      <label className="field field--wide">
        <span>Dares (one per line)</span>
        <textarea className="input" rows={6} value={dares} onChange={(e) => setDares(e.target.value)} />
      </label>
      <button
        className="btn"
        onClick={() =>
          void run("settings", {
            settings: {
              ...draft,
              dares: dares
                .split("\n")
                .map((d) => d.trim())
                .filter(Boolean),
            },
          })
        }
      >
        SAVE CHALLENGE SETTINGS
      </button>
      {message && <p className="panel__hint">{message}</p>}
    </div>
  );
}
```

`frontend/src/games/challenges/index.ts`:
```ts
import type { GameModule } from "../types";
import "./challenges.css";
import { ChallengeHost } from "./ChallengeHost";
import { ChallengePhone } from "./ChallengePhone";
import { ChallengeStrip } from "./ChallengeStrip";
import { ChallengeTv } from "./ChallengeTv";

export const challenges: GameModule = {
  id: "challenges",
  name: "Challenges",
  Tv: ChallengeTv,
  TvStrip: ChallengeStrip,
  Phone: ChallengePhone,
  Host: ChallengeHost,
};
```

`frontend/src/games/index.ts` (full file):
```ts
import { challenges } from "./challenges";
import type { GameModule } from "./types";

/** Frontend game registry. Mirrors backend/app/party/games/__init__.py. */
export const GAMES: GameModule[] = [challenges];
```

`frontend/src/games/challenges/challenges.css`:
```css
.challenge {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 1.4vw;
  text-align: center;
  background: radial-gradient(circle at 50% 40%, rgb(60 20 90 / 94%), rgb(6 7 13 / 97%) 70%);
}

.challenge__kicker {
  margin: 0;
  padding: 0.4vw 1vw;
  background: var(--bad);
  font-size: 1.3vw;
}

.challenge__title {
  margin: 0;
  font-size: 7vw;
  color: var(--fire);
  text-shadow: 0 0.5vw 0 #8a5200;
}

.challenge__team {
  margin: 0;
  font-size: 3vw;
}

.challenge__dare {
  margin: 0;
  max-width: 70vw;
  font-size: 2.4vw;
}

.challenge__targets {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 2vw;
}

.challenge__target {
  display: grid;
  justify-items: center;
  gap: 0.6vw;
  padding: 1vw;
  border-radius: 1vw;
  background: rgb(255 255 255 / 6%);
  font-size: 1.6vw;
}

.challenge__target--done,
.challenge__target--dare {
  background: rgb(53 224 127 / 18%);
}

.challenge__target--timeout {
  background: rgb(255 59 92 / 25%);
}

.challenge__clock {
  display: grid;
  justify-items: center;
  gap: 0.5vw;
}

.challenge__verdict {
  margin: 0;
  font-size: 5vw;
  color: var(--good);
}

.challenge__verdict--bad {
  color: var(--bad);
}

.wheel {
  position: relative;
  width: 34vw;
  height: 34vw;
}

.wheel__disc {
  width: 100%;
  height: 100%;
}

.wheel__pointer {
  position: absolute;
  top: -1.2vw;
  left: 50%;
  z-index: 1;
  transform: translateX(-50%);
  border-left: 1.6vw solid transparent;
  border-right: 1.6vw solid transparent;
  border-top: 3vw solid var(--ink-hi);
  filter: drop-shadow(0 0.3vw 0.3vw rgb(0 0 0 / 60%));
}

.challenge-strip {
  display: inline-flex;
  align-items: center;
  gap: 1vw;
  padding: 0.6vw 1vw;
  border-radius: 0.8vw;
  background: var(--arena-700);
}

.challenge-banner {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 45;
  padding: 10px;
  text-align: center;
  background: #3c145a;
}

.challenge-phone {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 16px;
  padding: 24px;
  text-align: center;
  background: radial-gradient(circle at 50% 30%, #3c145a, var(--arena-900) 75%);
}

.challenge-phone__title {
  margin: 0;
  font-size: 2.4rem;
  color: var(--fire);
}

.challenge-phone__dare {
  margin: 0;
  font-size: 1.2rem;
}

.challenge-phone__done {
  min-width: 70vw;
  min-height: 84px;
  font-size: 1.8rem;
}
```

- [ ] **Step 4: Verify tests, build, lint and behaviour**

Run: `cd frontend && npx vitest run && npm run build && npx oxlint src`
Expected: `27` tests pass, the build succeeds, and oxlint reports `0 errors`.

Then run a manual smoke check with the dev servers running:
- In `/host` → GAMES, press **🎡 SPIN NOW**. The TV should show the wheel spinning for about 4.5 seconds with ticking, landing on a slice.
- Then the TV should show the called players with a 24-second LED shot clock.
- The called phone should show **YOU'VE BEEN CALLED** with **DONE 🥃**. Tapping it should turn the TV target to **✅ BUCKET** and add the shot to the leaderboard.
- Let a clock run out. The TV should show **SHOT CLOCK VIOLATION** with a buzzer.
- The TV footer should show **NEXT CHALLENGE** counting down.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/games
git commit -m "feat(games): add the challenge wheel, shot clock and controls to TV, phones and host"
```

---

### Task 20: Rehearsal crowd (`demo.py`)

**Files:**
- Create: `backend/scripts/demo.py`
- Test: `backend/tests/test_demo.py`

**Interfaces:**
- Consumes: the socket contract (`player:join`, `player:resume`, `shot:log`, `game:action`); `party.py --demo` already launches this script (Task 12).
- Produces: `python scripts/demo.py --url URL --guests N --speed X`.

- [ ] **Step 1: Write the failing test**

`backend/tests/test_demo.py`:
```python
import asyncio
import sys

from tests.conftest import BACKEND_DIR, SocketClient

DEMO_NAMES = {"Jess", "Sam", "Alex"}


async def test_demo_guests_join_and_log_shots(server_url):
    proc = await asyncio.create_subprocess_exec(
        sys.executable, "scripts/demo.py", "--url", server_url, "--guests", "3", "--speed", "30",
        cwd=BACKEND_DIR, stdout=asyncio.subprocess.DEVNULL,
    )
    tv = SocketClient(server_url)
    await tv.connect()
    try:
        await tv.wait_for(
            lambda: any(p["name"].split(" ")[0] in DEMO_NAMES and p["count"] > 0 for p in tv.state["players"]),
            "a demo guest logging a shot",
            timeout=20,
        )
    finally:
        proc.terminate()
        await proc.wait()
        await tv.close()
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && venv/bin/python -m pytest tests/test_demo.py -q`
Expected: FAIL with `AssertionError: timed out waiting for a demo guest logging a shot`, because the script doesn't exist yet.

- [ ] **Step 3: Implement the crowd**

`backend/scripts/demo.py`:
```python
"""Rehearsal crowd: simulated guests who join, log shots and answer challenges, so you can watch the TV.

Usage: backend/venv/bin/python backend/scripts/demo.py [--url http://127.0.0.1:8000] [--guests 8] [--speed 1]
Needs the dev requirements (aiohttp): backend/venv/bin/pip install -r backend/requirements-dev.txt
"""
import argparse
import asyncio
import random
import uuid

import socketio

GUESTS = [("Jess", "🔥"), ("Sam", "😎"), ("Alex", "🐐"), ("Taylor", "👑"), ("Jordan", "🚀"),
          ("Riley", "🦄"), ("Casey", "🎯"), ("Morgan", "🌶️"), ("Quinn", "🦈"), ("Avery", "💎")]


def rid() -> str:
    return str(uuid.uuid4())


class Guest:
    def __init__(self, url: str, name: str, emoji: str, speed: float) -> None:
        self.url, self.name, self.emoji, self.speed = url, name, emoji, speed
        self.sio = socketio.AsyncClient()
        self.state: dict | None = None
        self.night_id: str | None = None
        self.player_id: str | None = None
        self.token: str | None = None
        self.answered: set[str] = set()
        self.sio.on("state", self.on_state)
        self.sio.on("connect", self.on_connect)

    async def on_connect(self) -> None:
        if self.token:  # the server restarted or the Wi-Fi blipped: become ourselves again
            asyncio.create_task(self.sio.call("player:resume", {"token": self.token}))

    async def on_state(self, state: dict) -> None:
        self.state = state
        current = (state.get("games", {}).get("challenges") or {}).get("current")
        if current and current["phase"] == "live" and current["id"] not in self.answered:
            target = next((t for t in current["targets"] if t["playerId"] == self.player_id), None)
            if target and target["status"] == "pending":
                self.answered.add(current["id"])
                asyncio.create_task(self.answer(current["id"]))

    async def answer(self, challenge_id: str) -> None:
        await asyncio.sleep(random.uniform(2, 20) / self.speed)
        if random.random() < 0.85:  # sometimes a bot chokes, for the SHOT CLOCK VIOLATION
            await self.sio.call("game:action", {"requestId": rid(), "gameId": "challenges", "action": "done",
                                                "payload": {"challengeId": challenge_id}})

    async def join(self) -> bool:
        assert self.state is not None
        team = random.choice(self.state["teams"])["id"]
        for suffix in ("", " 2", " 3"):
            ack = await self.sio.call("player:join", {"requestId": rid(), "name": f"{self.name}{suffix}",
                                                      "avatar": {"kind": "emoji", "value": self.emoji}, "teamId": team})
            if ack["ok"]:
                self.player_id, self.token, self.night_id = ack["playerId"], ack["token"], self.state["nightId"]
                print(f"🤖 {self.name}{suffix} joined", flush=True)
                return True
        print(f"🤖 {self.name} could not join: {ack['error']}", flush=True)
        return False

    async def run(self) -> None:
        await self.sio.connect(self.url)
        while self.state is None:
            await asyncio.sleep(0.1)
        if not await self.join():
            return
        while True:
            await asyncio.sleep(random.uniform(8, 45) / self.speed)
            if self.state["nightId"] != self.night_id and not await self.join():  # host started a new night
                return
            others = [p["id"] for p in self.state["players"] if p["id"] != self.player_id]
            if others and random.random() < 0.35:
                drinkers = random.sample(others, k=min(len(others), random.choice([1, 1, 2, 3])))
            else:
                drinkers = [self.player_id]
            ack = await self.sio.call("shot:log", {"requestId": rid(), "drinkerIds": drinkers})
            if not ack["ok"]:
                print(f"🤖 {self.name}: {ack['error']}", flush=True)


async def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--url", default="http://127.0.0.1:8000")
    parser.add_argument("--guests", type=int, default=8)
    parser.add_argument("--speed", type=float, default=1.0, help="2 = twice as thirsty")
    args = parser.parse_args()
    guests = [Guest(args.url, name, emoji, args.speed) for name, emoji in GUESTS[: args.guests]]
    await asyncio.gather(*(g.run() for g in guests))


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd backend && venv/bin/python -m pytest -q`
Expected: all pass (`91 passed`).

- [ ] **Step 5: Commit**

```bash
git add backend/scripts/demo.py backend/tests/test_demo.py
git commit -m "feat(party): add a simulated rehearsal crowd for party.py --demo"
```

---

### Task 21: End-to-end tests (Playwright)

**Files:**
- Create: `frontend/playwright.config.ts`, `frontend/e2e/party.spec.ts`
- Modify: `frontend/package.json` (devDependency `@playwright/test`; script `e2e`)

**Interfaces:**
- Consumes: the whole app. Visible text from Tasks 9, 11, 14, 16 and 19; `data-testid="shotcam-input"`.
- Produces: `npm run e2e`, which builds the frontend, then starts party mode on `localhost:8000` with a throwaway database and runs 4 scenarios.

- [ ] **Step 1: Install Playwright**

Run: `cd frontend && npm install -D @playwright/test@1 && npx playwright install chromium`

In `frontend/package.json` `"scripts"`, add:
```json
"e2e": "npm run build && playwright test"
```

`frontend/playwright.config.ts`:
```ts
import { defineConfig, devices } from "@playwright/test";

// Party mode on :8000 (the hat page's API URL is http://localhost:8000). Stop any dev backend first.
const PORT = 8000;

export default defineConfig({
  testDir: "./e2e",
  timeout: 90_000,
  workers: 1,
  use: { baseURL: `http://localhost:${PORT}`, trace: "retain-on-failure" },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: [
      "rm -rf e2e/.tmp && mkdir -p e2e/.tmp &&",
      `HOOP_PARTY=1 HOOP_DB=e2e/.tmp/e2e.db HOOP_MEDIA_DIR=e2e/.tmp/media HOOP_HOST_PIN=4242 HOOP_PUBLIC_PORT=${PORT}`,
      `../backend/venv/bin/python -m uvicorn app.main:app --app-dir ../backend --port ${PORT}`,
    ].join(" "),
    url: `http://localhost:${PORT}/api/party/health`,
    reuseExistingServer: false,
    timeout: 30_000,
  },
});
```

- [ ] **Step 2: Write the scenarios**

`frontend/e2e/party.spec.ts`:
```ts
import { expect, test, type Browser, type BrowserContext, type Page } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { mkdirSync } from "node:fs";

const PHONE = { viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true };
let contexts: BrowserContext[] = [];

test.afterEach(async () => {
  await Promise.all(contexts.map((c) => c.close()));
  contexts = [];
});

async function open(browser: Browser, path: string, options = {}): Promise<Page> {
  const context = await browser.newContext(options);
  contexts.push(context);
  const page = await context.newPage();
  await page.goto(path);
  return page;
}

async function openTv(browser: Browser): Promise<Page> {
  const tv = await open(browser, "/tv", { viewport: { width: 1920, height: 1080 } });
  await tv.getByRole("button", { name: /TIP OFF/ }).click();
  return tv;
}

async function joinPhone(browser: Browser, name: string, team: "HOME" | "AWAY"): Promise<Page> {
  const phone = await open(browser, "/play", PHONE);
  await phone.getByPlaceholder("Your name").fill(name);
  await phone.getByRole("button", { name: "NEXT" }).click();
  await phone.getByRole("button", { name: "NEXT" }).click();
  await phone.getByRole("button", { name: new RegExp(`^${team}`) }).click();
  await expect(phone.getByRole("button", { name: /I TOOK ONE/ })).toBeVisible();
  return phone;
}

function boardCount(tv: Page, name: string, count: number) {
  return tv.locator(".board__row", { hasText: name }).getByRole("img", { name: String(count), exact: true });
}

test("a phone shot hypes the TV and NOT ME waves off a bad log", async ({ browser }) => {
  const tv = await openTv(browser);
  const ava = await joinPhone(browser, "Ava", "HOME");
  const ben = await joinPhone(browser, "Ben", "AWAY");

  await ava.getByRole("button", { name: /I TOOK ONE/ }).click();
  await expect(tv.locator(".takeover")).toContainText("Ava");
  await expect(ava.getByRole("button", { name: "UNDO" })).toBeVisible();
  await expect(boardCount(tv, "Ava", 1)).toBeVisible();

  await ben.getByRole("button", { name: /Ava/ }).click();
  await ben.getByRole("button", { name: /LOG \+1 \(1\)/ }).click();
  await expect(boardCount(tv, "Ava", 2)).toBeVisible();
  await expect(tv.locator(".takeover")).toContainText("logged by Ben", { timeout: 15_000 });

  await ava.getByRole("button", { name: "NOT ME" }).click();
  await expect(tv.locator(".takeover")).toContainText("NO GOOD!", { timeout: 20_000 });
  await expect(boardCount(tv, "Ava", 1)).toBeVisible();
});

test("a shot-cam photo lands on the TV as an instant replay", async ({ browser }) => {
  mkdirSync("e2e/.tmp", { recursive: true });
  execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-f", "lavfi", "-i", "testsrc2=size=1200x900", "-frames:v", "1", "e2e/.tmp/shot.jpg"]);
  const tv = await openTv(browser);
  const dee = await joinPhone(browser, "Dee", "AWAY");
  await dee.getByRole("button", { name: /I TOOK ONE/ }).click();
  await dee.getByTestId("shotcam-input").setInputFiles("e2e/.tmp/shot.jpg");
  await expect(dee.getByText("On its way to the jumbotron!")).toBeVisible();
  await expect(tv.getByText("INSTANT REPLAY")).toBeVisible({ timeout: 30_000 });
  await expect(tv.locator(".reel img")).toBeVisible({ timeout: 30_000 });
});

test("the host spins a challenge and the called player answers it", async ({ browser }) => {
  const tv = await openTv(browser);
  const host = await open(browser, "/host");
  await host.getByPlaceholder("PIN").fill("4242");
  await host.getByRole("button", { name: "ENTER" }).click();

  // A fresh night makes the only connected player the only possible target.
  await host.getByRole("button", { name: "SETTINGS", exact: true }).click();
  host.once("dialog", (dialog) => void dialog.accept());
  await host.getByRole("button", { name: "START NEW NIGHT" }).click();
  await expect(tv.locator(".board--empty")).toBeVisible();

  const cam = await joinPhone(browser, "Cam", "HOME");
  await host.getByRole("button", { name: "GAMES", exact: true }).click();
  await host.getByRole("button", { name: /SPIN NOW/ }).click();
  await expect(tv.locator(".wheel")).toBeVisible();

  await expect(cam.getByText("YOU'VE BEEN CALLED")).toBeVisible({ timeout: 10_000 });
  await cam.getByRole("button", { name: /DONE/ }).click();
  await expect(tv.locator(".challenge")).toContainText("BUCKET");
  await expect(boardCount(tv, "Cam", 1)).toBeVisible();
});

test("Steven's hat page still draws a game", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Draw a game from the hat" }).click();
  await expect(page.locator(".game-card")).toBeVisible({ timeout: 5_000 });
});
```

- [ ] **Step 3: Run the E2E suite**

Make sure nothing is listening on port 8000 (stop any dev backend), then run:

Run: `cd frontend && npm run e2e`
Expected: `4 passed`.

Then close any stray browsers to free memory on the 8 GB Air: `pkill -f "ms-playwright" || true`.

- [ ] **Step 4: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/playwright.config.ts frontend/e2e/party.spec.ts
git commit -m "test(e2e): cover phone→TV hype, NOT ME, shot-cam replay, challenges and the hat"
```

---

## Plan Self-Review

**Spec coverage:**

| Spec section | Task(s) |
|---|---|
| §1 success: TV report < 250 ms, idempotent, offline, plugin-friendly, hat intact | 5–6 (acked intents, idempotency), 7 (outbox), 8 (bundled fonts/synth), 17 (plugins), 21 (hat e2e) |
| §2 Wi-Fi first + tunnel | 6 (`join_url`), 11 (QR codes incl. Wi-Fi QR), 12 (`--tunnel`, `/api/party/tunnel`) |
| §3.1–3.2 surfaces and layout | 6, 9, 11, 12, 14 |
| §3.3 party core (nights, players, teams, presence, ledger, stage, media) | 1, 2, 4, 5, 10, 13, 15 |
| §3.4 plugin interface | 17 |
| §3.5–3.6 realtime + events + codegen | 3, 6, 13, 17 |
| §3.7 data model | 1 |
| §3.8 scripts (`party.py`, caffeinate, kiosk, watchdog, `--demo`) | 12, 20 |
| §4.1 logging, NOT ME, undo, rate limit | 5, 9 |
| §4.2 shot-cam photos/videos, avatars | 15, 16 |
| §4.3 stage, combos, streaks, milestones, lead change, reel | 2, 5, 10, 11 |
| §4.4 challenges | 18, 19 |
| §4.5 teams | 4, 13, 14 |
| §5 screens | 9, 11, 14, 16, 19 |
| §6 error handling | 6 (acks), 7 (outbox), 9 (reconnect banner), 12 (watchdog), 15 (upload/transcode failures) |
| §7 testing | every task; e2e in 21; rehearsal in 20 |
| §8 defaults | 1 (`PartySettings`), 18 (`ChallengeSettings`) |

**Placeholder scan:** there are no TBD, TODO or "similar to" references. Every code step contains its full code. Three empty slot `<div>`s (`play__extras`, `join-flow__extras`, `tv__strips`/`tv__games`) are replaced by exact snippets in Tasks 16 and 17.

**Type consistency:**
- `log_shots(logged_by_id, request_id, drinker_ids, *, source, reason)` is used the same way in Tasks 5, 13, 17 and 18.
- `void(shots, reason)`: Tasks 5 and 13. `roster()`: Tasks 4 and 17. `reel_item()`: Tasks 4 and 15.
- `register_handlers(..., admin=)`: Tasks 6, 13 and 17. `GameViewProps.state: unknown`, cast per game: Tasks 17 and 19.
- Frontend `Ack`/`call` signatures: Tasks 7, 9, 14, 17 and 19.
- Generated names are always taken via indexed access (`PublicState["settings"]`, `ChallengeState["current"]`), which avoids depending on the `Input`/`Output` suffixes.
