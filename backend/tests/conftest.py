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
