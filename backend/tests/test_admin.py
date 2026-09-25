import pytest
from pydantic import ValidationError

from app.party.admin import PartyAdmin
from app.party.errors import PartyError
from app.party.service import PartyService
from tests.conftest import join


@pytest.fixture
def admin(service):
    return PartyAdmin(service)


def teams_in_order(service):
    return sorted(service.teams.values(), key=lambda t: t.sort)


async def test_teams_can_be_added_and_renamed_up_to_four(admin, service):
    await admin.dispatch("team.upsert", {"name": "BENCH", "color": "#35E07F"})
    bench = teams_in_order(service)[-1]
    assert (bench.name, bench.sort) == ("BENCH", 2)
    await admin.dispatch("team.upsert", {"id": bench.id, "name": "BALLERS", "color": "#FFFFFF"})
    assert service.teams[bench.id].name == "BALLERS"
    await admin.dispatch("team.upsert", {"name": "FOURTH", "color": "#000000"})
    with pytest.raises(PartyError, match="4 teams max"):
        await admin.dispatch("team.upsert", {"name": "FIFTH", "color": "#000000"})
    with pytest.raises(PartyError, match="share a name"):
        await admin.dispatch("team.upsert", {"name": "home", "color": "#000000"})


async def test_team_removal_rules(admin, service):
    home, away = teams_in_order(service)
    with pytest.raises(PartyError, match="at least 2"):
        await admin.dispatch("team.remove", {"teamId": away.id})
    await join(service, "Jess")
    await admin.dispatch("team.upsert", {"name": "BENCH", "color": "#35E07F"})
    with pytest.raises(PartyError, match="Move everyone"):
        await admin.dispatch("team.remove", {"teamId": home.id})
    await admin.dispatch("team.remove", {"teamId": away.id})
    assert away.id not in service.teams


async def test_player_edit_and_remove(admin, service):
    jess = await join(service, "Jess")
    away = teams_in_order(service)[1].id
    await admin.dispatch("player.edit", {"playerId": jess, "name": "Jessica", "teamId": away})
    assert (service.players[jess].name, service.players[jess].team_id) == ("Jessica", away)
    token_hash = service.players[jess].token_hash
    await admin.dispatch("player.remove", {"playerId": jess})
    assert jess not in service.players and token_hash not in service.by_token


async def test_merge_moves_shots_and_skips_group_duplicates(admin, service, emitter, clock):
    jess, dup, sam = await join(service, "Jess"), await join(service, "Jess2"), await join(service, "Sam")
    await service.log_shots(sam, "group", [jess, dup])
    await service.log_shots(dup, "solo", [dup])
    await admin.dispatch("player.merge", {"fromId": dup, "intoId": jess})
    assert dup not in service.players
    assert service.counts()[jess] == 2
    assert all(s.logged_by_id != dup for s in service.shots.values())
    again = PartyService(emitter, clock=clock)
    again.load()
    assert again.counts()[jess] == 2 and dup not in again.players


async def test_host_can_add_and_void_shots(admin, service, emitter):
    jess = await join(service, "Jess")
    await admin.dispatch("shot.add", {"drinkerIds": [jess]})
    [shot] = service.shots.values()
    assert shot.source == "host" and shot.logged_by_id is None
    await admin.dispatch("shot.void", {"shotId": shot.id})
    assert service.counts()[jess] == 0
    assert emitter.moments[-1].type == "waved-off" and emitter.moments[-1].reason == "host"


async def test_settings_and_wifi_persist_across_restart(admin, service, emitter, clock):
    await admin.dispatch("settings.update", {"settings": {**service.settings.wire(), "voice": False}})
    await admin.dispatch("wifi.set", {"ssid": "Hoop House", "password": "buckets"})
    again = PartyService(emitter, clock=clock)
    again.load()
    assert again.settings.voice is False
    assert again.public_state().join.wifi_ssid == "Hoop House"


async def test_new_night_action(admin, service):
    await join(service, "Jess")
    await admin.dispatch("night.new", {"name": "Finals"})
    assert service.night_name == "Finals" and service.players == {}


async def test_unknown_actions_and_bad_payloads(admin):
    with pytest.raises(PartyError, match="Unknown host action"):
        await admin.dispatch("nope", {})
    with pytest.raises(ValidationError):
        await admin.dispatch("team.upsert", {"name": "X", "color": "red"})
