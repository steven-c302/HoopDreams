"""Pure scoring rules over the shot ledger. No I/O; every time is epoch milliseconds."""
from collections.abc import Iterable
from dataclasses import dataclass
from typing import Literal

from .config import PartySettings

Streak = Literal["heating", "fire"]
MINUTE_MS = 60_000
_NEVER = 2**62


@dataclass
class ShotRec:
    id: str
    drinker_id: str
    logged_by_id: str | None
    request_id: str
    source: str
    created_at: int
    voided_at: int | None = None
    void_reason: str | None = None


def live_counts(shots: Iterable[ShotRec], player_ids: Iterable[str]) -> dict[str, int]:
    counts = dict.fromkeys(player_ids, 0)
    for s in shots:
        if s.voided_at is None and s.drinker_id in counts:
            counts[s.drinker_id] += 1
    return counts


def team_totals(counts: dict[str, int], team_of: dict[str, str], team_ids: Iterable[str]) -> dict[str, int]:
    totals = dict.fromkeys(team_ids, 0)
    for player_id, n in counts.items():
        team_id = team_of.get(player_id)
        if team_id in totals:
            totals[team_id] += n
    return totals


def sole_leader(totals: dict[str, int]) -> str | None:
    if not totals:
        return None
    best = max(totals.values())
    leaders = [k for k, v in totals.items() if v == best]
    return leaders[0] if best > 0 and len(leaders) == 1 else None


def ranked(counts: dict[str, int], last_at: dict[str, int | None]) -> list[tuple[str, int]]:
    """Order by count desc; ties go to whoever reached the count first. Ranks are 1,1,3,..."""
    order = sorted(counts, key=lambda pid: (-counts[pid], last_at.get(pid) or _NEVER, pid))
    out: list[tuple[str, int]] = []
    rank, previous = 0, None
    for position, player_id in enumerate(order, start=1):
        if counts[player_id] != previous:
            rank, previous = position, counts[player_id]
        out.append((player_id, rank))
    return out


def streak_status(times: list[int], now: int, settings: PartySettings) -> Streak | None:
    """times: one player's live shot times, ascending."""
    if not times:
        return None
    last = times[-1]
    fire = settings.on_fire
    in_fire_window = sum(1 for t in times if last - t <= fire.window_min * MINUTE_MS)
    if in_fire_window >= fire.count and now - last <= fire.cool_min * MINUTE_MS:
        return "fire"
    heat = settings.heating_up
    if sum(1 for t in times if now - t <= heat.window_min * MINUTE_MS) >= heat.count:
        return "heating"
    return None


def party_milestones_crossed(before: int, after: int, settings: PartySettings) -> list[int]:
    hits = {m for m in settings.party_milestones if before < m <= after}
    every = settings.party_milestone_every
    if every > 0:
        hits.update(range((before // every + 1) * every, after + 1, every))
    return sorted(hits)


def is_player_milestone(count: int, settings: PartySettings) -> bool:
    every = settings.player_milestone_every
    return every > 0 and count > 0 and count % every == 0
