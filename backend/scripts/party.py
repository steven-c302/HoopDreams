"""Party mode: build the frontend, serve everything on :8000, keep the Mac awake, open the TV.

Usage (from the repo root):
    backend/venv/bin/python backend/scripts/party.py [--tunnel] [--demo] [--no-build] [--no-open]

The host PIN is printed below (set HOOP_HOST_PIN to choose it). Stop with Ctrl+C.
"""
import argparse
import json
import os
import re
import secrets
import signal
import subprocess
import sys
import threading
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BACKEND = ROOT / "backend"
FRONTEND = ROOT / "frontend"
PORT = 8000
CHROME = Path("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
TUNNEL_RE = re.compile(r"https://[a-z0-9-]+\.trycloudflare\.com")

sys.path.insert(0, str(BACKEND))
from app.party.net import join_url  # noqa: E402


class Supervisor:
    """Runs a command and restarts it whenever it exits, until stop()."""

    def __init__(self, name, cmd, *, cwd=None, env=None, on_line=None):
        self.name, self.cmd, self.cwd, self.env, self.on_line = name, cmd, cwd, env, on_line
        self.proc = None
        self.stopping = False

    def start(self):
        threading.Thread(target=self._loop, daemon=True).start()
        return self

    def _loop(self):
        while not self.stopping:
            self.proc = subprocess.Popen(
                self.cmd, cwd=self.cwd, env=self.env, text=True,
                stdout=subprocess.PIPE if self.on_line else None,
                stderr=subprocess.STDOUT if self.on_line else None,
            )
            if self.on_line:
                for line in self.proc.stdout:
                    self.on_line(line)
            code = self.proc.wait()
            if self.stopping:
                return
            print(f"⚠️  {self.name} exited ({code}); restarting in 1s", flush=True)
            time.sleep(1)

    def stop(self):
        self.stopping = True
        if self.proc and self.proc.poll() is None:
            self.proc.terminate()
            try:
                self.proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.proc.kill()


def wait_for_server(timeout=30):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/api/party/health", timeout=1):
                return
        except OSError:
            time.sleep(0.3)
    raise SystemExit("server did not come up. Check the output above.")


def post_tunnel(pin, url):
    body = json.dumps({"pin": pin, "url": url}).encode()
    request = urllib.request.Request(
        f"http://127.0.0.1:{PORT}/api/party/tunnel", data=body, headers={"Content-Type": "application/json"}
    )
    try:
        urllib.request.urlopen(request, timeout=2).close()
    except OSError:
        pass  # server restarting; the heartbeat retries


def start_tunnel(pin):
    latest = {"url": None}

    def on_line(line):
        match = TUNNEL_RE.search(line)
        if match and match.group(0) != latest["url"]:
            latest["url"] = match.group(0)
            print(f"🌐 Tunnel: {latest['url']}/play", flush=True)
            post_tunnel(pin, latest["url"])

    def heartbeat():  # re-announce so a restarted server learns the URL again
        while True:
            time.sleep(15)
            if latest["url"]:
                post_tunnel(pin, latest["url"])

    threading.Thread(target=heartbeat, daemon=True).start()
    return Supervisor("cloudflared", ["cloudflared", "tunnel", "--no-autoupdate", "--url", f"http://localhost:{PORT}"],
                      on_line=on_line).start()


def open_tv():
    url = f"http://localhost:{PORT}/tv"
    if CHROME.exists():
        profile = Path.home() / ".hoopdreams" / "chrome-tv"
        subprocess.Popen(
            [str(CHROME), f"--user-data-dir={profile}", "--kiosk", "--no-first-run",
             "--disable-session-crashed-bubble", url],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
    else:
        subprocess.run(["open", url], check=False)


def print_banner(url, pin):
    import qrcode

    print("\n🏀  HOOP DREAMS PARTY MODE\n", flush=True)
    qr = qrcode.QRCode(border=1)
    qr.add_data(url)
    qr.make(fit=True)
    qr.print_ascii(invert=True)
    print(f"\n  Phones:   {url}\n  TV:       http://localhost:{PORT}/tv\n  Host:     http://localhost:{PORT}/host   PIN {pin}\n")
    print("  Tip: mirror the Mac onto the TV (Control Center → Screen Mirroring), then click TIP OFF.\n", flush=True)


def _interrupt(*_):
    raise KeyboardInterrupt


def main():
    signal.signal(signal.SIGTERM, _interrupt)  # `kill` shuts down as cleanly as Ctrl+C
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--tunnel", action="store_true", help="also expose a public link for guests on cell data")
    parser.add_argument("--demo", action="store_true", help="add 8 simulated guests (rehearsal)")
    parser.add_argument("--no-build", action="store_true", help="reuse the existing frontend/dist build")
    parser.add_argument("--no-open", action="store_true", help="don't open the TV page in Chrome")
    args = parser.parse_args()

    pin = os.environ.get("HOOP_HOST_PIN") or f"{secrets.randbelow(10_000):04d}"
    if not args.no_build:
        subprocess.run(["npm", "run", "build"], cwd=FRONTEND, check=True)

    env = {**os.environ, "HOOP_PARTY": "1", "HOOP_HOST_PIN": pin, "HOOP_PUBLIC_PORT": str(PORT)}
    children = [Supervisor("server", [sys.executable, "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0",
                                      "--port", str(PORT), "--workers", "1", "--log-level", "warning"],
                           cwd=BACKEND, env=env).start()]
    caffeinate = subprocess.Popen(["caffeinate", "-dimsu", "-w", str(os.getpid())])
    wait_for_server()
    print_banner(join_url(), pin)

    if args.tunnel:
        children.append(start_tunnel(pin))
    if args.demo:
        children.append(Supervisor("demo", [sys.executable, str(BACKEND / "scripts" / "demo.py"),
                                            "--url", f"http://127.0.0.1:{PORT}"]).start())
    if not args.no_open:
        open_tv()

    try:
        while True:
            time.sleep(3600)
    except KeyboardInterrupt:
        print("\n🏁 Final buzzer. Shutting down.")
    finally:
        for child in reversed(children):
            child.stop()
        caffeinate.terminate()


if __name__ == "__main__":
    main()
