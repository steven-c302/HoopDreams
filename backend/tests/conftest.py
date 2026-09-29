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
