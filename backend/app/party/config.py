from pydantic import Field

from .camel import CamelModel

DEFAULT_TEAMS: tuple[tuple[str, str], ...] = (("HOME", "#FF7A1A"), ("AWAY", "#2D8CFF"))
MIN_TEAMS = 2
MAX_TEAMS = 4
PRESENCE_GRACE_MS = 120_000
FEED_SIZE = 30
REEL_SIZE = 50
MAX_UPLOAD_BYTES = 200 * 1024 * 1024
MAX_VIDEO_SEC = 15


class StreakRule(CamelModel):
    count: int = Field(ge=1, le=50)
    window_min: int = Field(ge=1, le=240)


class FireRule(StreakRule):
    cool_min: int = Field(ge=1, le=240)


class PartySettings(CamelModel):
    """Host-tunable party behaviour. Stored per night in the settings table under key "party"."""

    heating_up: StreakRule = StreakRule(count=2, window_min=20)
    on_fire: FireRule = FireRule(count=3, window_min=30, cool_min=30)
    combo_window_ms: int = Field(4000, ge=0, le=20_000)
    combo_max_ms: int = Field(8000, ge=1000, le=30_000)
    undo_window_sec: int = Field(10, ge=0, le=120)
    not_me_window_sec: int = Field(60, ge=0, le=600)
    reel_item_sec: int = Field(6, ge=2, le=60)
    shot_log_per_min: int = Field(20, ge=1, le=200)
    player_milestone_every: int = Field(5, ge=0, le=100)
    party_milestones: list[int] = [25, 50]
    party_milestone_every: int = Field(100, ge=0, le=10_000)
    voice: bool = True
    sound: bool = True
