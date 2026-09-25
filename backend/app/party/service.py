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
