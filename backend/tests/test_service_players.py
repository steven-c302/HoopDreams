import pytest

from app.party.contract import AvatarIn, JoinIn, PlayerUpdateIn
from app.party.errors import PartyError
from app.party.service import PartyService
from tests.conftest import join


def test_first_load_creates_a_night_with_home_and_away(service):
    teams = sorted(service.teams.values(), key=lambda t: t.sort)
    assert [(t.name, t.color) for t in teams] == [("HOME", "#FF7A1A"), ("AWAY", "#2D8CFF")]
    assert service.night_name == "Game Night"


def test_reload_reuses_the_open_night(service, emitter, clock):
    again = PartyService(emitter, clock=clock)
    again.load()
    assert again.night_id == service.night_id
    assert set(again.teams) == set(service.teams)


async def test_join_returns_a_token_that_resumes_the_same_player(service):
    team_id = next(iter(service.teams))
    out = await service.join(JoinIn(request_id="r1", name="Jess", avatar=AvatarIn(kind="emoji", value="🔥"), team_id=team_id))
    assert service.resume(out.token) == out.player_id
    assert service.players[out.player_id].token_hash != out.token


async def test_join_retry_with_same_request_returns_the_same_player(service):
    team_id = next(iter(service.teams))
    req = JoinIn(request_id="r1", name="Jess", avatar=AvatarIn(kind="emoji", value="🔥"), team_id=team_id)
    first, second = await service.join(req), await service.join(req)
    assert first == second and len(service.players) == 1


async def test_names_are_unique_ignoring_case(service):
    await join(service, "Jess")
    with pytest.raises(PartyError, match="taken"):
        await join(service, "jess")


async def test_unknown_token_or_team_is_rejected(service):
    with pytest.raises(PartyError):
        service.resume("nope")
    with pytest.raises(PartyError):
        await service.join(JoinIn(request_id="r", name="Sam", avatar=AvatarIn(kind="emoji", value="😎"), team_id="nope"))


async def test_players_survive_a_restart(service, emitter, clock):
    pid = await join(service, "Jess")
    again = PartyService(emitter, clock=clock)
    again.load()
    assert again.players[pid].name == "Jess"


async def test_update_player_changes_name_team_and_emoji(service):
    pid = await join(service, "Jess")
    away = sorted(service.teams.values(), key=lambda t: t.sort)[1].id
    await service.update_player(pid, PlayerUpdateIn(name="Jessie", team_id=away, avatar=AvatarIn(kind="emoji", value="🐐")))
    p = service.players[pid]
    assert (p.name, p.team_id, p.avatar_value) == ("Jessie", away, "🐐")


async def test_presence_has_a_two_minute_grace(service, clock):
    pid = await join(service, "Jess")
    assert not service.is_connected(pid)
    await service.attach("sid-1", pid)
    assert service.is_connected(pid)
    await service.detach("sid-1")
    clock.advance(119_000)
    assert service.is_connected(pid)
    clock.advance(2_000)
    assert not service.is_connected(pid)


async def test_public_state_lists_teams_players_and_join_info(service, emitter):
    await join(service, "Jess")
    state = emitter.states[-1]
    assert state.join.lan_url == "http://10.0.0.5:8000/play"
    assert [t.name for t in state.teams] == ["HOME", "AWAY"]
    assert state.players[0].name == "Jess" and state.players[0].rank == 1
    assert state.wire()["players"][0]["teamId"] == state.players[0].team_id


async def test_new_night_archives_players_and_keeps_teams_and_settings(service):
    await join(service, "Jess")
    old_night = service.night_id
    service.settings = service.settings.model_copy(update={"sound": False})
    service.save_setting("party", service.settings.model_dump_json())
    await service.new_night("Round Two")
    assert service.night_id != old_night and service.night_name == "Round Two"
    assert service.players == {}
    assert sorted(t.name for t in service.teams.values()) == ["AWAY", "HOME"]
    assert service.settings.sound is False


async def test_refresh_only_broadcasts_when_something_changed(service, emitter, clock):
    pid = await join(service, "Jess")
    await service.attach("sid-1", pid)
    await service.detach("sid-1")
    sent = len(emitter.states)
    await service.refresh()
    assert len(emitter.states) == sent
    clock.advance(121_000)
    await service.refresh()
    assert len(emitter.states) == sent + 1
    assert emitter.states[-1].players[0].connected is False
