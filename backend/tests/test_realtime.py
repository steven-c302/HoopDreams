import uuid

import httpx

from tests.conftest import socket_join


def rid():
    return str(uuid.uuid4())


async def test_new_sockets_get_state_immediately(connect):
    tv = await connect()
    assert {"nightId", "teams", "players", "feed", "join", "settings"} <= set(tv.state)
    assert tv.state["join"]["lanUrl"].endswith("/play")


async def test_join_then_resume_from_a_new_socket(connect):
    phone = await connect()
    ack = await socket_join(phone)
    await phone.close()
    again = await connect()
    resumed = await again.call("player:resume", {"token": ack["token"]})
    assert resumed == {"ok": True, "playerId": ack["playerId"]}


async def test_a_logged_shot_reaches_every_screen(connect):
    tv, phone = await connect(), await connect()
    me = await socket_join(phone)
    ack = await phone.call("shot:log", {"requestId": rid(), "drinkerIds": [me["playerId"]]})
    assert ack["ok"] and len(ack["shotIds"]) == 1
    moment = await tv.wait_for(
        lambda: next((m for m in tv.moments if m["type"] == "shot" and m["loggedById"] == me["playerId"]), None),
        "shot moment",
    )
    assert moment["drinkers"][0]["count"] == 1
    row = next(p for p in tv.state["players"] if p["id"] == me["playerId"])
    assert row["count"] == 1 and row["connected"] is True


async def test_retrying_the_same_request_is_idempotent(connect):
    phone = await connect()
    me = await socket_join(phone)
    payload = {"requestId": rid(), "drinkerIds": [me["playerId"]]}
    first, second = await phone.call("shot:log", payload), await phone.call("shot:log", payload)
    assert first["shotIds"] == second["shotIds"]


async def test_drinker_can_wave_off_a_shot_someone_else_logged(connect):
    jess_phone, sam_phone = await connect(), await connect()
    jess, sam = await socket_join(jess_phone), await socket_join(sam_phone)
    logged = await sam_phone.call("shot:log", {"requestId": rid(), "drinkerIds": [jess["playerId"]]})
    rejected = await jess_phone.call("shot:reject", {"shotId": logged["shotIds"][0]})
    assert rejected == {"ok": True}
    await sam_phone.wait_for(
        lambda: any(m["type"] == "waved-off" and m["playerId"] == jess["playerId"] for m in sam_phone.moments),
        "waved-off moment",
    )


async def test_logging_before_joining_is_refused(connect):
    stranger = await connect()
    ack = await stranger.call("shot:log", {"requestId": rid(), "drinkerIds": ["x"]})
    assert ack == {"ok": False, "error": "Join the game first"}


async def test_malformed_payloads_get_an_error_ack(connect):
    phone = await connect()
    await socket_join(phone)
    ack = await phone.call("shot:log", {"requestId": rid(), "drinkerIds": []})
    assert ack == {"ok": False, "error": "Invalid request"}


def test_hat_api_still_works(server_url):
    games = httpx.get(f"{server_url}/api/games").json()
    assert any(g["name"] == "Trivia" for g in games)
    assert httpx.get(f"{server_url}/api/party/health").json() == {"ok": True}
