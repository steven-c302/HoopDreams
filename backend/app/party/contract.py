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
