from datetime import datetime, timezone

from sqlalchemy import DateTime, ForeignKey, Integer, String
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
