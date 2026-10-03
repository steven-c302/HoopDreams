# Song Drop: design

Date: 2026-10-02. Game id `songdrop`. A guess-the-song game for Party OS: the TV plays a clip from a YouTube video, the clip
grows longer each time nobody has it, and phones race to pick the right song.

## What was asked, and what is assumed

Asked: "a guess that song game, use youtubes, do extensive research and make the best version possible." The audio source was
chosen as **YouTube with the video covered** on the TV.

Assumed (say if wrong): played tonight and on later nights at a party with Wi-Fi; 3 to 16 solo players; same house style as
the other games (per-game theme, colour + shape + text answers, drunk-readable at TV size).

## Research findings that shape the design

- YouTube's [required-functionality policy](https://developers.google.com/youtube/terms/required-minimum-functionality) forbids
  overlays in front of any part of an embedded player, requires at least 200×200, and allows autoplay only when more than half
  the player is visible. **Covering the video breaks that rule.** This was explained and the cover was chosen anyway, so the
  design makes the cover a single, removable layer (see TV) and keeps the player 480×270, on screen, never `display:none`.
- The [IFrame API](https://developers.google.com/youtube/iframe_api_reference) reports `onError` 101/150 when the owner blocks
  embedding, 100 when a video is gone, and `onAutoplayBlocked` when the browser refuses. `loadVideoById` takes
  `startSeconds`/`endSeconds`.
- **oEmbed cannot pre-check embeddability.** It answered 200 for four popular videos that the real player then rejected with
  150 (inside the app's browser pane; not yet tried in the party's Chrome). So playability is only known by trying: the game
  must survive a song that will not play.
- Heardle-style games ([Heardle](https://en.wikipedia.org/wiki/Heardle)) hold attention with escalating clips, fewer attempts
  and a shorter clip being worth more.

## The game

Per game: N songs (3 to 12, the `rounds` option, default 8). Per song, up to four stages with clip lengths 2 s, 4 s, 8 s, 15 s.

1. **Load.** The TV loads the video and seeks to the song's hook. The clock does not start until the TV reports the player is
   really playing. This keeps the timing fair despite YouTube's load delay.
2. **Stage.** The TV plays the clip; phones show four answers (each "Title, Artist", with colour, shape and text). A player
   may lock in an answer any time during the stage. A **wrong answer locks that player out for the rest of the song**, so
   nobody can spam four taps.
3. If nobody (still in) has it by the end of the stage, the next stage replays from the same start, longer. After the last
   stage the song is revealed.
4. **Reveal.** Title, artist, year and cover art on the TV; who got it and how fast; scores update.

Scoring, per correct answer: stage 1 = 1000, stage 2 = 700, stage 3 = 450, stage 4 = 250, plus up to 300 for speed inside the
stage (linear on time since the stage started playing). The song ends early once everyone has answered or is locked out.
Last song counts double. Drink calls (option `drinks`, like the other games): players who did not get it drink 1 sip; if
nobody got it, everyone drinks 1.

Lobby options: songs (`rounds`), **era** (new option `era`: 0 any, 1 60s/70s, 2 80s, 3 90s, 4 00s, 5 10s/20s), drink calls.
TV keys: ↑/↓ songs, **E** era, D drinks. The captain's phone gets the same settings.

## Architecture

New engine module `games/songdrop/SongDrop.kt` (a `GameModule<SongState>`), registered in the devserver registry beside the
other games. State is plain serialisable data so a restart resumes it (a restored game restarts the current song at Load).

**Clip handshake (the part that must not go wrong).** The engine is the only authority on time. Flow:

- Engine enters phase `load` with fields `{seq, videoId, startSec, stage, clipMs}` and a 10 s deadline.
- The TV player loads and, once the player reports PLAYING at the start offset, sends `gameAction("ready:<seq>")` over the host
  socket. The engine then enters phase `stage` with deadline `clipMs` and records `stageStartedAt`. Speed bonuses use the
  engine clock from that moment, so every phone is judged equally.
- At the deadline the TV stops the player (it also has a local `endSeconds` as a safety net). The engine moves to the next
  stage (back to `load`, no reload needed: a seek) or to `reveal`.
- TV reports `gameAction("bad:<seq>")` on any player error or if `ready` is not reached. The engine skips the song, marks the
  video bad (remembered, so it is dropped from later nights), and draws a replacement. After 3 bad songs in a row it ends the
  game with a plain message ("YouTube isn't playing on this TV"), so a dead connection can never loop.
- `seq` makes late or duplicate messages from an earlier song harmless; the engine ignores any `ready`/`bad` whose seq is not
  the current one.
- Pause, skip and end work as in the other games; the TV pauses the player on pause.

**Data.** `engine/src/main/resources/packs/songdrop-core.json`:
`{id, title, artist, year, genre, videoId, startSec}`; era is derived from `year`. Wrong answers are drawn from songs in the
same era and genre where possible (never the same title or artist as the answer). Played songs are remembered across nights
through the existing `Effect.UseContent` mechanism, and the pack recycles when exhausted like the others.
**Building the pack.** About 150 well-known songs, spread over eras and genres. Video IDs come from a dev-time script that
resolves "artist title" to a video (preferring auto-generated "Topic" uploads, which have no intro or outro and often allow
embedding), checks that the title matches, and records the hook offset from YouTube's "most replayed" data when available,
else a fixed offset. Every entry is checked by the real player in the song check below before it is kept. Dev-time use of
yt-dlp needs permission to install it.

**TV.** `SongDropStage.tsx` in its own theme (`data-game-theme="songdrop"`, class prefix `sd-`). A `YouTubeClip` module wraps
the IFrame API: loads the API once, owns one 480×270 player, exposes `play(videoId, start, lengthMs)` and reports
ready/error. The **cover** is one removable element (a flag turns it off for a quick test of whether the player works). The
cover shows the stage ticker (four steps filling as the clip grows), who has locked in (faces), and, on reveal, the cover art
(YouTube thumbnail) and the answer. The music bed and Spotify are paused while a clip plays.
**Song check:** when Song Drop is the focused game in the lobby, the TV quietly loads (muted) the first song of the deck and
shows "YouTube OK" or "YouTube not working here" next to the cover, so a problem shows before anyone starts.

**Phone.** No new screen type: reuses the existing choice list in "shapes" style, with the song title and artist in each
option, plus a locked-out state.

**Protocol.** One new TV view type (`SongDropTv`) and the `gameAction` strings above; fixtures regenerate in the usual way.

## Testing

- Engine (Kotlin, seeded): staging and clip growth; scoring by stage and speed; lockout after a wrong answer; early end;
  late `ready`/`bad` ignored; skip on `bad`; three bad songs end the game; replacement draw; options never include the same
  title or artist; era filter; restore mid-song resumes at Load; drink calls.
- Protocol fixtures for the new TV view.
- Web: the TV `YouTubeClip` takes an injectable player so the end-to-end test runs with a fake that "plays" after 100 ms (no
  network), covering: four phones, a wrong answer locks out, a correct answer scores, reveal text, a `bad` song is skipped.
- Visual: gallery page for each beat at 1920×1080 and 16 players.
- **Real player:** the song check, run on the party Mac's Chrome before the first song, is the only test that can tell whether
  YouTube plays there. If it reports not working, the cover flag shows the player so the cause (usually error 150 or an
  autoplay block) is visible.

## Risks

- YouTube may refuse to play in the party browser (embedding errors, autoplay, ads). Mitigated by the song check, skip and
  early-end rules, but not removable: it cannot be known without trying on that Mac.
- Hook offsets are estimates until heard; some clips will start in a quiet part. A "fix this song" host key (mark bad) is in scope.
- The cover contradicts YouTube's rules (decided by the owner); it is one flag so it can be removed.

## Out of scope for the first version

Typed answers, team play, player-chosen playlists, new ElevenLabs cues, Android TV support.
