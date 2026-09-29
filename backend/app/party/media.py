"""Shot-cam uploads: store the raw file, normalise it with ffmpeg one job at a time
(the Mac is a fanless Air), then tell the party it's ready."""
import asyncio
import logging
import os
import shutil
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from pathlib import Path
from typing import Literal

from fastapi import APIRouter, File, Form, HTTPException, Request, UploadFile

from .config import MAX_UPLOAD_BYTES, MAX_VIDEO_SEC
from .contract import MediaOut
from .errors import PartyError
from .service import PartyService, new_id

log = logging.getLogger("hoopdreams.media")

MEDIA_DIR = Path(os.environ.get("HOOP_MEDIA_DIR", Path(__file__).resolve().parents[2] / "media"))
# Short side to 720 (portrait or landscape), never upscaled; even dimensions for H.264.
VIDEO_SCALE = "scale='if(gte(iw,ih),-2,min(720,iw))':'if(gte(iw,ih),min(720,ih),-2)'"
PHOTO_SCALE = "scale='if(gte(iw,ih),min(1600,iw),-2)':'if(gte(iw,ih),-2,min(1600,ih))'"
AVATAR_CROP = "crop='min(iw,ih)':'min(iw,ih)',scale=512:512"


@dataclass
class MediaJob:
    media_id: str
    raw: Path
    kind: Literal["photo", "video"]
    purpose: Literal["shot", "avatar"]


@dataclass
class MediaResult:
    path: str
    poster_path: str | None
    duration_ms: int | None


async def run_ffmpeg(*args: str) -> bool:
    proc = await asyncio.create_subprocess_exec(
        "ffmpeg", "-y", "-hide_banner", "-loglevel", "error", *args,
        stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.PIPE,
    )
    _, err = await proc.communicate()
    if proc.returncode != 0:
        log.warning("ffmpeg failed: %s", err.decode(errors="replace")[-400:])
    return proc.returncode == 0


async def probe_duration_ms(path: Path) -> int | None:
    proc = await asyncio.create_subprocess_exec(
        "ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path),
        stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL,
    )
    out, _ = await proc.communicate()
    try:
        return int(float(out.decode().strip()) * 1000)
    except ValueError:
        return None


async def process(job: MediaJob, media_dir: Path) -> MediaResult | None:
    if job.kind == "photo":
        out = f"{job.media_id}.jpg"
        vf = AVATAR_CROP if job.purpose == "avatar" else PHOTO_SCALE
        ok = await run_ffmpeg("-i", str(job.raw), "-vf", vf, "-frames:v", "1", "-q:v", "3", str(media_dir / out))
        return MediaResult(out, None, None) if ok else None

    out, poster = f"{job.media_id}.mp4", f"{job.media_id}.jpg"
    common = ["-i", str(job.raw), "-t", str(MAX_VIDEO_SEC), "-vf", VIDEO_SCALE, "-pix_fmt", "yuv420p",
              "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart"]
    ok = await run_ffmpeg(*common, "-c:v", "h264_videotoolbox", "-b:v", "3M", str(media_dir / out))
    if not ok:  # no hardware encoder (or it refused the input): software fallback
        ok = await run_ffmpeg(*common, "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", str(media_dir / out))
    if not ok:
        return None
    await run_ffmpeg("-ss", "0.5", "-i", str(media_dir / out), "-frames:v", "1", "-vf", "scale=-2:360", str(media_dir / poster))
    return MediaResult(out, poster if (media_dir / poster).exists() else None, await probe_duration_ms(media_dir / out))


class MediaProcessor:
    """Runs ffmpeg jobs one at a time and reports each result to `on_done`."""

    def __init__(self, media_dir: Path, on_done: Callable[[str, MediaResult | None], Awaitable[None]]) -> None:
        self.media_dir = media_dir
        self.on_done = on_done
        self.queue: asyncio.Queue[MediaJob] = asyncio.Queue()
        self.task: asyncio.Task | None = None

    def start(self) -> None:
        (self.media_dir / "raw").mkdir(parents=True, exist_ok=True)
        self.task = asyncio.create_task(self._run())

    async def stop(self) -> None:
        if self.task:
            self.task.cancel()

    def submit(self, job: MediaJob) -> None:
        self.queue.put_nowait(job)

    async def _run(self) -> None:
        while True:
            job = await self.queue.get()
            try:
                result = await process(job, self.media_dir)
            except Exception:
                log.exception("processing %s failed", job.media_id)
                result = None
            if result is not None:
                job.raw.unlink(missing_ok=True)
            try:
                await self.on_done(job.media_id, result)
            except Exception:
                log.exception("announcing %s failed", job.media_id)


def _save(upload: UploadFile, dest: Path) -> None:
    with dest.open("wb") as out:
        shutil.copyfileobj(upload.file, out, length=1024 * 1024)


def media_router(service: PartyService, processor: MediaProcessor) -> APIRouter:
    router = APIRouter(prefix="/api/party")

    @router.post("/media", response_model=MediaOut)
    async def upload_media(
        request: Request,
        file: UploadFile = File(...),
        token: str = Form(...),
        purpose: Literal["shot", "avatar"] = Form("shot"),
        shot_id: str | None = Form(None, alias="shotId"),
    ):
        if int(request.headers.get("content-length") or 0) > MAX_UPLOAD_BYTES:
            raise HTTPException(413, "That clip is too big (200 MB max)")
        try:
            player_id = service.resume(token)
        except PartyError as exc:
            raise HTTPException(401, str(exc)) from exc
        content_type = file.content_type or ""
        if not content_type.startswith(("image/", "video/")):
            raise HTTPException(415, "Photos and videos only")
        kind: Literal["photo", "video"] = "video" if content_type.startswith("video/") else "photo"
        media_id = new_id()
        suffix = Path(file.filename or "").suffix[:8] or (".mp4" if kind == "video" else ".jpg")
        raw = processor.media_dir / "raw" / f"{media_id}{suffix}"
        raw.parent.mkdir(parents=True, exist_ok=True)
        await asyncio.to_thread(_save, file, raw)
        try:
            await service.add_media(media_id, player_id, purpose=purpose, kind=kind, shot_id=shot_id)
        except PartyError as exc:
            raw.unlink(missing_ok=True)
            raise HTTPException(400, str(exc)) from exc
        processor.submit(MediaJob(media_id, raw, kind, purpose))
        return MediaOut(media_id=media_id, status="processing")

    return router
