"""Socket.IO transport: validate intents, route them to PartyService, fan state out to screens."""
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


def register_handlers(
    sio: socketio.AsyncServer, service: PartyService, *, host_pin: str, admin: PartyAdmin
) -> dict[str, Session]:
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

    return sessions
