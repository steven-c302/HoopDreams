import random

from fastapi import Depends, FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.orm import Session

from . import models, schemas
from .database import Base, SessionLocal, engine, get_db
from .seed_data import SEED_GAMES
from .party.web import mount_party, party_lifespan

Base.metadata.create_all(bind=engine)


def seed_games_if_empty() -> None:
    db = SessionLocal()
    try:
        if db.scalar(select(models.Game).limit(1)) is None:
            db.add_all(models.Game(**game) for game in SEED_GAMES)
            db.commit()
    finally:
        db.close()


seed_games_if_empty()

app = FastAPI(title="HoopDreams API", lifespan=party_lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://localhost:5180",
        "http://127.0.0.1:5180",
    ],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/api/games", response_model=list[schemas.GameOut])
def list_games(db: Session = Depends(get_db)):
    return db.scalars(
        select(models.Game).where(models.Game.active.is_(True)).order_by(models.Game.id)
    ).all()


class DrawRequest(BaseModel):
    exclude: list[int] = []


class DrawResponse(BaseModel):
    game: schemas.GameOut
    reshuffled: bool


@app.post("/api/draw", response_model=DrawResponse)
def draw_game(payload: DrawRequest, db: Session = Depends(get_db)):
    games = db.scalars(
        select(models.Game).where(models.Game.active.is_(True)).order_by(models.Game.id)
    ).all()
    if not games:
        raise HTTPException(status_code=404, detail="No games in the hat yet.")

    pool = [g for g in games if g.id not in payload.exclude]
    reshuffled = False
    if not pool:
        pool = games
        reshuffled = True

    chosen = random.choice(pool)

    draw = models.Draw(game_id=chosen.id)
    db.add(draw)
    db.commit()

    return DrawResponse(game=schemas.GameOut.model_validate(chosen), reshuffled=reshuffled)


@app.get("/api/history", response_model=list[schemas.DrawOut])
def draw_history(limit: int = 10, db: Session = Depends(get_db)):
    draws = db.scalars(
        select(models.Draw).order_by(models.Draw.drawn_at.desc()).limit(limit)
    ).all()
    return draws


@app.get("/api/health")
def health():
    return {"status": "ok"}


# Keep this last: it adds the Socket.IO mount and, in party mode, the catch-all SPA mount.
mount_party(app)
