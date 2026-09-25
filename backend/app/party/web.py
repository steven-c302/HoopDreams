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
