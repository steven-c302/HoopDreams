import shutil
import subprocess

import httpx
import pytest

from app.party.errors import PartyError
from app.party.media import MediaJob, MediaResult, process
from tests.conftest import join, socket_join

needs_ffmpeg = pytest.mark.skipif(shutil.which("ffmpeg") is None, reason="ffmpeg not installed")


def make_video(path, width=1080, height=1920, seconds=3):
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i", f"testsrc2=size={width}x{height}:rate=30:duration={seconds}",
         "-f", "lavfi", "-i", f"sine=frequency=440:duration={seconds}", "-c:v", "libx264", "-preset", "ultrafast",
         "-c:a", "aac", "-shortest", str(path)],
        check=True,
    )


def make_photo(path, width=3000, height=2000):
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i", f"testsrc2=size={width}x{height}",
                    "-frames:v", "1", str(path)], check=True)


def video_stream(path):
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=codec_name,width,height",
         "-of", "csv=p=0", str(path)],
        capture_output=True, text=True, check=True,
    ).stdout.strip()
    codec, width, height = out.split(",")
    return codec, int(width), int(height)


@needs_ffmpeg
async def test_portrait_video_becomes_720p_h264_with_a_poster(tmp_path):
    raw = tmp_path / "raw.mov"
    make_video(raw)
    result = await process(MediaJob("m1", raw, "video", "shot"), tmp_path)
    assert result is not None and result.poster_path == "m1.jpg"
    assert video_stream(tmp_path / result.path) == ("h264", 720, 1280)
    assert 2500 <= result.duration_ms <= 3500


@needs_ffmpeg
async def test_long_videos_are_trimmed_and_small_ones_are_not_upscaled(tmp_path):
    raw = tmp_path / "long.mov"
    make_video(raw, 640, 360, seconds=20)
    result = await process(MediaJob("m2", raw, "video", "shot"), tmp_path)
    assert result.duration_ms <= 15_100
    assert video_stream(tmp_path / result.path)[1:] == (640, 360)


@needs_ffmpeg
async def test_photos_shrink_to_1600_and_avatars_crop_square(tmp_path):
    raw = tmp_path / "p.png"
    make_photo(raw)
    shot = await process(MediaJob("p1", raw, "photo", "shot"), tmp_path)
    assert video_stream(tmp_path / shot.path)[1:] == (1600, 1066)
    avatar = await process(MediaJob("p2", raw, "photo", "avatar"), tmp_path)
    assert video_stream(tmp_path / avatar.path)[1:] == (512, 512)


@needs_ffmpeg
async def test_garbage_fails_cleanly(tmp_path):
    raw = tmp_path / "junk.mov"
    raw.write_bytes(b"not a video")
    assert await process(MediaJob("j", raw, "video", "shot"), tmp_path) is None


async def test_ready_shot_media_joins_the_reel_and_triggers_a_replay(service, emitter):
    jess = await join(service, "Jess")
    [shot_id] = await service.log_shots(jess, "r1", [jess])
    await service.add_media("m1", jess, purpose="shot", kind="video", shot_id=shot_id)
    await service.media_done("m1", MediaResult("m1.mp4", "m1.jpg", 3000))
    replay = emitter.moments[-1]
    assert replay.type == "replay" and replay.item.url == "/media/m1.mp4" and replay.item.poster_url == "/media/m1.jpg"
    assert emitter.states[-1].reel[0].media_id == "m1"


async def test_avatar_media_becomes_the_players_photo(service, emitter):
    jess = await join(service, "Jess")
    await service.add_media("a1", jess, purpose="avatar", kind="photo", shot_id=None)
    await service.media_done("a1", MediaResult("a1.jpg", None, None))
    me = next(p for p in emitter.states[-1].players if p.id == jess)
    assert (me.avatar.kind, me.avatar.value) == ("photo", "/media/a1.jpg")
    assert emitter.states[-1].reel == []


async def test_media_can_only_attach_to_your_own_shots(service):
    jess, sam, kim = await join(service, "Jess"), await join(service, "Sam"), await join(service, "Kim")
    [shot_id] = await service.log_shots(sam, "r1", [jess])
    await service.add_media("ok1", jess, purpose="shot", kind="photo", shot_id=shot_id)
    await service.add_media("ok2", sam, purpose="shot", kind="photo", shot_id=shot_id)
    with pytest.raises(PartyError):
        await service.add_media("bad", kim, purpose="shot", kind="photo", shot_id=shot_id)


async def test_failed_media_stays_out_of_the_reel(service, emitter):
    jess = await join(service, "Jess")
    await service.add_media("f1", jess, purpose="shot", kind="video", shot_id=None)
    await service.media_done("f1", None)
    assert service.media["f1"].status == "failed"
    assert not any(m.type == "replay" for m in emitter.moments)


@needs_ffmpeg
async def test_http_upload_ends_in_an_instant_replay(connect, server_url, tmp_path):
    phone, tv = await connect(), await connect()
    me = await socket_join(phone)
    photo = tmp_path / "shot.jpg"
    make_photo(photo, 800, 600)
    async with httpx.AsyncClient() as http:
        response = await http.post(
            f"{server_url}/api/party/media",
            data={"token": me["token"], "purpose": "shot"},
            files={"file": ("shot.jpg", photo.read_bytes(), "image/jpeg")},
        )
        assert response.status_code == 200, response.text
        media_id = response.json()["mediaId"]
        replay = await tv.wait_for(
            lambda: next((m for m in tv.moments if m["type"] == "replay" and m["item"]["mediaId"] == media_id), None),
            "replay", timeout=15,
        )
        served = await http.get(f"{server_url}{replay['item']['url']}")
        assert served.status_code == 200 and served.headers["content-type"] == "image/jpeg"


async def test_upload_needs_a_valid_token_and_media(server_url, connect):
    async with httpx.AsyncClient() as http:
        bad_token = await http.post(f"{server_url}/api/party/media", data={"token": "nope"},
                                    files={"file": ("x.jpg", b"x", "image/jpeg")})
        assert bad_token.status_code == 401
        phone = await connect()
        me = await socket_join(phone)
        not_media = await http.post(f"{server_url}/api/party/media", data={"token": me["token"]},
                                    files={"file": ("x.txt", b"x", "text/plain")})
        assert not_media.status_code == 415
