from datetime import datetime, timezone

from sqlalchemy import BigInteger, DateTime, ForeignKey, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .database import Base


class Game(Base):
    __tablename__ = "games"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    name: Mapped[str] = mapped_column(String(80), unique=True, nullable=False)
    emoji: Mapped[str] = mapped_column(String(8), default="🎲")
    description: Mapped[str] = mapped_column(String(280), default="")
    color: Mapped[str] = mapped_column(String(20), default="blue")
    active: Mapped[bool] = mapped_column(default=True)

    draws: Mapped[list["Draw"]] = relationship(back_populates="game")


class Draw(Base):
    __tablename__ = "draws"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, index=True)
    game_id: Mapped[int] = mapped_column(ForeignKey("games.id"))
    drawn_at: Mapped[datetime] = mapped_column(
        DateTime, default=lambda: datetime.now(timezone.utc)
    )

    game: Mapped["Game"] = relationship(back_populates="draws")


# --- Party shot tracker (timestamps are epoch milliseconds, ids are UUID4 strings) ---


class Night(Base):
    __tablename__ = "nights"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    name: Mapped[str] = mapped_column(String(80))
    started_at: Mapped[int] = mapped_column(BigInteger)
    ended_at: Mapped[int | None] = mapped_column(BigInteger, nullable=True)


class Team(Base):
    __tablename__ = "teams"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    night_id: Mapped[str] = mapped_column(ForeignKey("nights.id"), index=True)
    name: Mapped[str] = mapped_column(String(24))
    color: Mapped[str] = mapped_column(String(7))
    sort: Mapped[int] = mapped_column(Integer, default=0)
    removed_at: Mapped[int | None] = mapped_column(BigInteger, nullable=True)


class Player(Base):
    __tablename__ = "players"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    night_id: Mapped[str] = mapped_column(ForeignKey("nights.id"), index=True)
    name: Mapped[str] = mapped_column(String(20))
    avatar_kind: Mapped[str] = mapped_column(String(8))
    avatar_value: Mapped[str] = mapped_column(String(64))
    team_id: Mapped[str] = mapped_column(ForeignKey("teams.id"))
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    created_at: Mapped[int] = mapped_column(BigInteger)
    removed_at: Mapped[int | None] = mapped_column(BigInteger, nullable=True)


class Shot(Base):
    __tablename__ = "shots"
    __table_args__ = (UniqueConstraint("request_id", "drinker_id"),)

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    night_id: Mapped[str] = mapped_column(ForeignKey("nights.id"), index=True)
    drinker_id: Mapped[str] = mapped_column(ForeignKey("players.id"), index=True)
    logged_by_id: Mapped[str | None] = mapped_column(ForeignKey("players.id"), nullable=True)
    request_id: Mapped[str] = mapped_column(String(36))
    source: Mapped[str] = mapped_column(String(32))
    reason: Mapped[str] = mapped_column(String(80), default="")
    created_at: Mapped[int] = mapped_column(BigInteger)
    voided_at: Mapped[int | None] = mapped_column(BigInteger, nullable=True)
    void_reason: Mapped[str | None] = mapped_column(String(8), nullable=True)


class Media(Base):
    __tablename__ = "media"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    night_id: Mapped[str] = mapped_column(ForeignKey("nights.id"), index=True)
    player_id: Mapped[str] = mapped_column(ForeignKey("players.id"))
    shot_id: Mapped[str | None] = mapped_column(ForeignKey("shots.id"), nullable=True)
    purpose: Mapped[str] = mapped_column(String(8))
    kind: Mapped[str] = mapped_column(String(8))
    status: Mapped[str] = mapped_column(String(12))
    path: Mapped[str] = mapped_column(String(255), default="")
    poster_path: Mapped[str | None] = mapped_column(String(255), nullable=True)
    duration_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    created_at: Mapped[int] = mapped_column(BigInteger)


class Setting(Base):
    __tablename__ = "settings"

    night_id: Mapped[str] = mapped_column(ForeignKey("nights.id"), primary_key=True)
    key: Mapped[str] = mapped_column(String(40), primary_key=True)
    value_json: Mapped[str] = mapped_column(Text)


class GameEvent(Base):
    __tablename__ = "game_events"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    night_id: Mapped[str] = mapped_column(ForeignKey("nights.id"), index=True)
    game_id: Mapped[str] = mapped_column(String(32))
    type: Mapped[str] = mapped_column(String(32))
    data_json: Mapped[str] = mapped_column(Text)
    created_at: Mapped[int] = mapped_column(BigInteger)
