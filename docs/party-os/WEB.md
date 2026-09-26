# PARTY OS on the web (Mac + any TV)

The same show as the Android TV app (the same engine, games, rules and phone controller), with the TV screen drawn
in a browser. Plug a Mac into the TV (HDMI or AirPlay mirroring) and go.

## Start

Double-click **`Start Party OS.command`** in the repo root. It:

1. builds the phone + TV screens (`controller/`) and the party server (`tv/devserver`) if needed,
2. starts the server on port 8080 and keeps the Mac awake,
3. opens Chrome in kiosk mode (full screen, sound allowed) at `http://127.0.0.1:8080/tv`.

Phones scan the QR code on the TV (same Wi-Fi). Needs Java 17 (`brew install openjdk@17`), Node and Chrome.
Without Chrome it opens your default browser; click **GO LIVE** once for full screen and sound.

## On the TV

| Key | Does |
| --- | --- |
| ← → and Enter | pick a game and start it (lobby) |
| ↑ ↓ | rounds (3–8) |
| Esc | host controls: pause, skip, end, remove players, sound and music volume |
| P | pause / resume |
| M | mute |
| F | full screen |

The TV page gets host rights from `/api/tv/session`, which only answers requests from the machine itself, so
phones on the Wi-Fi never can. Co-hosts can still use `/host` with the PIN printed in the terminal.

## Rehearse without friends

```bash
node controller/scripts/bots.mjs 6
```

Six bots join, ready up, bluff, pick, bet and play blackjack like people would.

## How it's built

- `controller/src/tv/` is the TV: a fixed 1920×1080 stage scaled to the screen (2px per Android dp), Motion for
  animation, canvas-confetti, and `audio.ts`, a Web Audio engine that synthesizes every sound effect and the house
  band live. There are no audio files.
- `director.ts` turns state changes into cues (joins climb a scale, bluffs and bets clink higher, stingers per
  phase, music per phase with a hurry-up in the last 10 seconds, ticks at 5-4-3-2-1). Card deals, reveals and
  payouts play their sounds from the components, in time with the animation.
- Games live once, in `tv/engine`. Both TVs render the same `TvState`.
