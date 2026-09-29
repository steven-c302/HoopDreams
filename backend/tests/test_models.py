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
