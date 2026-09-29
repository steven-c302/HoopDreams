import httpx
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.party.web import SPAStaticFiles
from tests.conftest import SocketClient


def test_spa_serves_app_routes_but_not_unknown_api_or_media(tmp_path):
    (tmp_path / "index.html").write_text("<html>app</html>")
    (tmp_path / "app.js").write_text("js")
    app = FastAPI()
    app.mount("/", SPAStaticFiles(directory=tmp_path, html=True))
    client = TestClient(app)
    assert client.get("/tv").text == "<html>app</html>"
    assert client.get("/play").text == "<html>app</html>"
    assert client.get("/app.js").text == "js"
    assert client.get("/api/nope").status_code == 404
    assert client.get("/media/nope.jpg").status_code == 404


async def test_tunnel_url_reaches_the_tv_with_the_right_pin(server_url):
    tv = SocketClient(server_url)
    await tv.connect()
    try:
        async with httpx.AsyncClient() as http:
            bad = await http.post(f"{server_url}/api/party/tunnel", json={"pin": "0000", "url": "https://x.trycloudflare.com"})
            assert bad.status_code == 403
            ok = await http.post(f"{server_url}/api/party/tunnel", json={"pin": "4242", "url": "https://x.trycloudflare.com"})
            assert ok.json() == {"ok": True}
        await tv.wait_for(lambda: tv.state["join"]["tunnelUrl"] == "https://x.trycloudflare.com", "tunnel url")
    finally:
        await tv.close()
