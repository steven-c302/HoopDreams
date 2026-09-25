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
