from datetime import datetime

from pydantic import BaseModel, ConfigDict


class GameOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    emoji: str
    description: str
    color: str


class GameCreate(BaseModel):
    name: str
    emoji: str = "🎲"
    description: str = ""
    color: str = "blue"


class DrawOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    drawn_at: datetime
    game: GameOut


class TriviaAnswer(BaseModel):
    text: str
    correct: bool


class TriviaQuestionOut(BaseModel):
    category: str
    difficulty: str
    question: str
    answers: list[TriviaAnswer]
