# Making Party OS better: research and recommendations

Research date: 27 September 2026.

**Built since:** phones stay awake during games (NoSleep: the Wake Lock API needs HTTPS, the phones use http, so it
falls back to a silent video); a Timers setting (normal, relaxed 1.5×, no rush 2×) that stretches only decision
timers; ("Water tonight" was built and then removed on request); and a host reset for typed team names (TV → Esc → Team names). The Mac now saves the party (roster, scores,
the game on screen) and photo faces under `~/Library/Application Support/PartyOS/`, so a restart within six hours
comes back with the same room code, the game paused, and phones rejoining on their own (`PARTYOS_FRESH=1` starts over). A veto for Bluff lies is still open: any
way for the captain to hide one tells them which options are lies.

## How this was done

- **Outside:** player complaints about Jackbox on Steam, Jackbox's own posts on accessibility, streaming and
  moderation, AirConsole's developer guidelines for phone-as-controller games, write-ups on why Gartic Phone spread,
  board-game writing on downtime, and responsible-drinking guidance for drinking-game design (sources at the end).
- **Inside:** the current code on `party-os-2`: six games (Brain Drain, Write It Down, Bluff Battle, Drunk Blackjack,
  Home Turf, Sprawl), the captain phone, reconnects and pauses, drink calls, played-question memory, per-game themes
  and photo avatars.

## What Party OS already gets right

Most of the lessons the research turns up are already built in:

- **Your own crew is the content.** Home Turf and Sprawl run on the crew's places, faces are drawn or photos, and
  teams carry over between games. Generic packs go stale; the Jackbox complaints are mostly that jokes lost
  their surprise.
- **Colour plus shape plus text** on every answer covers most of what Jackbox's colour-blind mode does.
- **Reconnects, pauses, a captain phone, late joins and autopilot for absent players** cover the "someone's
  phone died" moments that sink most phone-controller games.
- **Pacing drinks** is already part of the design: water counts, Sober Hand, drink calls can be switched off.

## The gaps, in order of what they'd do for a real night

### 1. Don't lose the night (reliability)

| Gap | Evidence | Fix |
| --- | --- | --- |
| Phones fall asleep mid-game | No Screen Wake Lock anywhere in the controller. Home Turf and Sprawl turns leave phones idle for minutes, and asleep phones drop their sockets. The reconnect copes, but the host sees pauses and waiting-for-player banners. | Request `navigator.wakeLock` while a game runs; re-request on `visibilitychange`. Small. |
| A Mac restart loses the party | The devserver (how parties are actually run) keeps only played questions. The Android app restores the whole party from Room; the Mac can't. Photos live in memory. | Save the party snapshot and photos to disk the way PlayedStore does, and restore on launch. Medium. |
| No latency check | AirConsole's bar is under 100 ms of input lag on real devices. We've never measured it on the party Wi-Fi. | Add a ping/latency readout to the host overlay and log it. Small. |

### 2. Nobody should be bored (downtime)

The board-game writing is blunt: waiting with nothing to do is where people reach for their phones. The fix is
simultaneous play, or giving waiting players something small but real to do.

- **Home Turf and Sprawl downtime.** Between your turns you can only trade. Add quick side action for everyone
  else: a bet on the next roll ("over/under 7", a sip if wrong), heckle stamps that show on the TV, and "call it"
  predictions on auctions. Medium.
- **Spectators do nothing.** "Just watch" phones show a static "Watching" screen. Jackbox's audience votes count
  toward the result. Give watchers a vote in Bluff Battle ("best lie" bonus), a side in Pick a Side, and a
  crowd-favourite award. Medium.

### 3. Make the night last past the night (recap and sharing)

Gartic Phone spread because its end-of-game album is fun to watch again and easy to share. Party OS keeps one line
of highlights per game and forgets the rest.

- **End-of-night recap:** one page after the last game with the winners, awards, best bluffs, the Home Turf
  landlord of the night, and the photos. Downloadable as an image or GIF for the group chat. Medium.
- **A season across nights:** a running crew leaderboard and "rivalries" (most rent paid to one person, most times
  robbed), kept in the same file as played questions. Small once the recap exists.

### 4. Let the crew make the content

- **Rename the boards from the captain's phone.** The crew's places live in `turf/board.json` and
  `sprawl/board.json`; changing them means editing a file. A "Places" editor in the captain lobby makes every party's
  board theirs. Medium.
- **Crew questions.** Before a party (or in the lobby), guests type "most likely to" prompts and facts about each
  other; Brain Drain and Bluff Battle mix them in. The Bluff pack has 70 prompts, and played-question memory means
  it runs out after about a dozen games. Medium.
- **A host veto for typed text.** Bluff lies and team names go straight to the TV. Jackbox added human moderation and
  a family-friendly filter once people streamed and played in mixed company. A captain "hide that" tap is enough
  here. Small.

### 5. Settings people ask for (accessibility)

Jackbox's most-used switches are extended timers and no timers, plus motion sensitivity and subtitles.

- **Timer length:** normal, relaxed (1.5×) and off, from the captain lobby. Most useful late at night. Small.
- **Big text** on the phone. Small.
- Reduced motion already follows the device setting. There's no voice-over, so subtitles aren't needed yet.

### 6. Drinking that keeps people playing

The responsible-drinking guidance for these games agrees on pacing, water swaps and not making anyone chug.

- **Per-player "sober" or "water" mode**, set once at join: drink calls say "water" or turn into points for
  them, without anyone else needing to know. Small.
- **A night tally and a cooldown:** count sips per player and skip their calls for a while after a big run. Small.
- **Water break** as a scheduled card between games. Small.

### 7. New games worth making

These fill gaps in the current lineup:

- **A drawing game** (Gartic-style telephone). Everyone already draws their face; the drawing pad and the replayable
  album are the parts people share.
- **A social deduction game** ("who's the imposter", one secret word). The most popular single-phone party games
  right now are this shape, and it's all talking, so no downtime.
- **Something only a phone can do.** AirConsole's lesson is that the best phone games don't copy a gamepad: shake,
  tilt, hold. A 60-second reflex round between longer games resets the energy.

### 8. Housekeeping

- The Android TV app is still on the 1.0 look and can't show photos. Decide whether it's still needed (the Mac
  mirrored to the TV is how parties run) before spending more time keeping it at parity.

## Suggested order

1. **Before the next party (small):** wake lock, timer length, host veto for typed text, sober/water mode.
2. **Next (medium):** save the party and photos across restarts, end-of-night recap, spectator voting.
3. **Then:** the board and question editors, side bets for Home Turf and Sprawl, one new game (the drawing game,
   since the pieces already exist).

## Sources

- [Jackbox Party Pack 5 player discussion](https://steamcommunity.com/app/774461/discussions/0/1734342161851395264) ·
  [Party Pack 4 discussion](https://steamcommunity.com/app/610180/discussions/0/1697169163395366935)
- [Accessibility features in Party Pack 10](https://www.jackboxgames.com/blog/accessibility-features-in-the-jackbox-party-pack-10) ·
  [Jackbox accessibility help](https://support.jackboxgames.com/hc/en-us/articles/15794801592855-What-accessibility-features-are-available-in-your-games)
- [Jackbox streaming, moderation and accessibility (Pack 8)](https://www.jackboxgames.com/blog/streaming-moderation-accessibility-features-jackbox-party-pack-eight) ·
  [How moderation works](https://support.jackboxgames.com/hc/en-us/articles/15794773430295-How-does-Moderation-work) ·
  [Safety and moderation when streaming](https://www.jackboxgames.com/blog/safety-and-moderation-streaming-jackbox-games)
- [AirConsole best practices](https://developers.airconsole.com/best-practices-for-web-games-on-airconsole) ·
  [AirConsole: using smartphones as controllers](https://www.gamedeveloper.com/design/airconsole-using-smartphones-as-controllers)
- [How Gartic Phone uses UGC](https://www.lurkit.gg/blog/how-gartic-phone-is-utilizing-ugc-to-become-the-next-hit-party-game) ·
  [Gartic Phone album](https://gartic-phone.fandom.com/wiki/Album)
- [7 ways to reduce downtime](https://entrogames.substack.com/p/7-ways-to-reduce-downtime-in-your) ·
  [The concept of downtime](https://therewillbe.games/articles-essays/8361-the-concept-of-downtime)
- [Responsible drinking in drinking-game design](https://acelioncardgames.com/responsible-in-drinking-card-game-design/) ·
  [Safety guide for drinking games](https://bestdrinkinggame.net/safety)
- [Best party games for TV in 2026](https://playbuzzin.com/articles/best-party-games-for-tv) ·
  [Best one-phone party games 2026](https://bluffin.app/blog/best-party-games-one-phone-2026/)
