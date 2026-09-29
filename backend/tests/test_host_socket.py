async def test_host_actions_need_the_pin(connect):
    host = await connect()
    denied = await host.call("host:action", {"requestId": "r", "action": "wifi.set", "payload": {}})
    assert denied == {"ok": False, "error": "Host PIN required"}
    assert (await host.call("host:auth", {"pin": "0000"})) == {"ok": False, "error": "Wrong PIN"}
    assert (await host.call("host:auth", {"pin": "4242"})) == {"ok": True}
    await host.wait_for(lambda: host.host_states, "host state")


async def test_pin_guessing_is_rate_limited(connect):
    host = await connect()
    for _ in range(5):
        await host.call("host:auth", {"pin": "0000"})
    ack = await host.call("host:auth", {"pin": "4242"})
    assert ack["ok"] is False and ack["error"].startswith("Too many")


async def test_host_actions_reach_every_screen(connect):
    host, tv = await connect(), await connect()
    await host.call("host:auth", {"pin": "4242"})
    ack = await host.call("host:action", {"requestId": "r1", "action": "wifi.set", "payload": {"ssid": "Hoop House", "password": "x"}})
    assert ack == {"ok": True}
    await tv.wait_for(lambda: tv.state["join"]["wifiSsid"] == "Hoop House", "wifi on the TV")
