"""Wire the party into Steven's FastAPI app: Socket.IO, party REST routes, lifespan.

main.py calls mount_party(app) as its LAST line, so nothing else ends up behind the SPA catch-all mount.
"""
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
from .admin import PartyAdmin
from .realtime import SioEmitter, register_handlers
from .service import PartyService

HOST_PIN = os.environ.get("HOOP_HOST_PIN") or f"{secrets.randbelow(10_000):04d}"
DIST_DIR = Path(os.environ.get("HOOP_DIST", Path(__file__).resolve().parents[3] / "frontend" / "dist"))

sio = socketio.AsyncServer(async_mode="asgi", cors_allowed_origins="*")
service = PartyService(SioEmitter(sio), lan_url=net.join_url)
admin = PartyAdmin(service)
sessions = register_handlers(sio, service, host_pin=HOST_PIN, admin=admin)
party_router = APIRouter(prefix="/api/party")


@party_router.get("/health")
def party_health():
    return {"ok": True}


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
    if os.environ.get("HOOP_PARTY") == "1":
        if not (DIST_DIR / "index.html").exists():
            raise RuntimeError(f"HOOP_PARTY=1 but {DIST_DIR} has no build. Run `npm run build` in frontend/.")
        app.mount("/", SPAStaticFiles(directory=DIST_DIR, html=True), name="spa")
