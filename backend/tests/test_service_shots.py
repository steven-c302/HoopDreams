import pytest

from app.party.errors import PartyError
from app.party.service import PartyService
from tests.conftest import join

MIN = 60_000


async def test_log_writes_one_shot_per_drinker_and_announces_it(service, emitter):
    jess, sam = await join(service, "Jess"), await join(service, "Sam", team=1)
    ids = await service.log_shots(jess, "r1", [jess, sam, jess])
    assert len(ids) == 2
    assert service.counts() == {jess: 1, sam: 1}
    shot = next(m for m in emitter.moments if m.type == "shot")
    assert shot.logged_by_id == jess and [d.player_id for d in shot.drinkers] == [jess, sam]
    assert emitter.states[-1].total == 2


async def test_retrying_a_request_does_not_double_count(service, emitter):
    jess = await join(service, "Jess")
    first = await service.log_shots(jess, "r1", [jess])
    moments = len(emitter.moments)
    assert await service.log_shots(jess, "r1", [jess]) == first
    assert service.counts()[jess] == 1 and len(emitter.moments) == moments


async def test_shots_survive_a_restart(service, emitter, clock):
    jess = await join(service, "Jess")
    await service.log_shots(jess, "r1", [jess])
    again = PartyService(emitter, clock=clock)
    again.load()
    assert again.counts()[jess] == 1
    assert await again.log_shots(jess, "r1", [jess]) == service.by_request["r1"]


async def test_unknown_drinkers_are_rejected(service):
    jess = await join(service, "Jess")
    with pytest.raises(PartyError):
        await service.log_shots(jess, "r1", [jess, "ghost"])
    assert service.counts()[jess] == 0


async def test_first_shot_is_first_bucket_and_team_takes_the_lead(service, emitter):
    jess = await join(service, "Jess")
    await service.log_shots(jess, "r1", [jess])
    assert emitter.moment_types() == ["shot", "milestone", "lead-change"]
    assert emitter.moments[1].scope == "first"


async def test_lead_change_only_fires_when_the_sole_leader_changes(service, emitter):
    jess, sam = await join(service, "Jess"), await join(service, "Sam", team=1)
    await service.log_shots(jess, "a", [jess])
    await service.log_shots(sam, "b", [sam])  # tie: no change
    await service.log_shots(jess, "c", [jess])  # home again: same leader, no change
    await service.log_shots(sam, "d", [sam])
    await service.log_shots(sam, "e", [sam])  # away takes it
    leads = [m.team_id for m in emitter.moments if m.type == "lead-change"]
    assert leads == [service.players[jess].team_id, service.players[sam].team_id]


async def test_every_fifth_shot_is_a_player_milestone(service, emitter, clock):
    jess = await join(service, "Jess")
    for i in range(5):
        clock.advance(MIN)
        await service.log_shots(jess, f"r{i}", [jess])
    player = [m for m in emitter.moments if m.type == "milestone" and m.scope == "player"]
    assert [(m.player_id, m.value) for m in player] == [(jess, 5)]


async def test_shot_moment_carries_streak_status(service, emitter, clock):
    jess = await join(service, "Jess")
    for i in range(3):
        clock.advance(5 * MIN)
        await service.log_shots(jess, f"r{i}", [jess])
    streaks = [m.drinkers[0].streak for m in emitter.moments if m.type == "shot"]
    assert streaks == [None, "heating", "fire"]


async def test_rate_limit_applies_per_logger_per_minute(service, clock):
    jess = await join(service, "Jess")
    service.settings = service.settings.model_copy(update={"shot_log_per_min": 2})
    await service.log_shots(jess, "a", [jess])
    await service.log_shots(jess, "b", [jess])
    with pytest.raises(PartyError, match="too many"):
        await service.log_shots(jess, "c", [jess])
    clock.advance(61_000)
    await service.log_shots(jess, "d", [jess])


async def test_logger_can_undo_within_the_window(service, emitter, clock):
    jess, sam = await join(service, "Jess"), await join(service, "Sam")
    await service.log_shots(jess, "r1", [jess, sam])
    clock.advance(9_000)
    await service.undo(jess, "r1")
    assert service.counts() == {jess: 0, sam: 0}
    assert [m.reason for m in emitter.moments if m.type == "waved-off"] == ["undo", "undo"]


async def test_undo_rules(service, clock):
    jess, sam = await join(service, "Jess"), await join(service, "Sam")
    await service.log_shots(jess, "r1", [jess])
    with pytest.raises(PartyError):
        await service.undo(sam, "r1")
    clock.advance(11_000)
    with pytest.raises(PartyError, match="late"):
        await service.undo(jess, "r1")
    with pytest.raises(PartyError):
        await service.undo(jess, "missing")


async def test_drinker_can_say_not_me_within_a_minute(service, emitter, clock):
    jess, sam = await join(service, "Jess"), await join(service, "Sam")
    [shot_id] = await service.log_shots(sam, "r1", [jess])
    clock.advance(59_000)
    await service.reject(jess, shot_id)
    assert service.counts()[jess] == 0
    assert emitter.moments[-1].type == "waved-off" and emitter.moments[-1].reason == "not_me"


async def test_not_me_rules(service, clock):
    jess, sam = await join(service, "Jess"), await join(service, "Sam")
    [own] = await service.log_shots(jess, "r1", [jess])
    [other] = await service.log_shots(sam, "r2", [jess])
    with pytest.raises(PartyError):
        await service.reject(jess, own)  # self-logged: use undo instead
    with pytest.raises(PartyError):
        await service.reject(sam, other)  # not the drinker
    clock.advance(61_000)
    with pytest.raises(PartyError, match="late"):
        await service.reject(jess, other)


async def test_voided_shots_leave_the_feed_but_stay_in_host_state(service, emitter):
    jess = await join(service, "Jess")
    [shot_id] = await service.log_shots(jess, "r1", [jess])
    await service.undo(jess, "r1")
    assert emitter.states[-1].feed == []
    assert emitter.host_states[-1].shots[0].id == shot_id
    assert emitter.host_states[-1].shots[0].void_reason == "undo"
