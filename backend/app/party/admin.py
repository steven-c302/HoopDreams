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
