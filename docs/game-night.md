# HoopDreams: the living room league

## Launch tonight

Double-click **Start Game Night.command** in Finder (or run `./"Start Game Night.command"` from the repository). It installs missing local dependencies, builds the app, opens the TV screen, and keeps your Mac awake. Python 3 and Node/npm must be installed. Leave its Terminal window open; Ctrl+C ends the server.

1. Mirror or connect your Mac to the TV, open `/tv`, and choose **Full screen**.
2. Open `/host` on a phone using the Mac's LAN address printed in Terminal. Enter the printed PIN. Under **Games**, click **Set up four teams** once if the old tracker has only two teams. Rename them under **Teams**.
3. Everyone scans the TV QR code, enters a name, and picks a team. Aim for four players per team. No account or app download.
4. Pick a game and press **Let’s play**. Pause whenever the room needs a moment. Timers automatically close submissions; the host advances from each reveal.
5. The shot tally stays visible on TV. On phones, expand **Shot tally** to log, undo, log for the squad, or attach photos. Game scores are independent of shots.

Routes: `/` and `/tv` are the game lobby/stage; `/play` is the phone controller; `/host` manages the night; `/tracker` retains the original arcade shot board; `/hat` retains the random in-person game picker.

For a private rehearsal with 16 simulated guests, run `backend/venv/bin/python backend/scripts/party.py --demo`. It uses a temporary database and does not touch your real roster or shot history. Guests automatically answer trivia; drawing and comedy remain manually controlled. Use an ordinary phone to join as an additional player to try those controllers. Close an existing server before launching another on port 8000.

## A good first playlist

These are design recommendations for this group's size, not a claim that a study establishes a universally “most fun” format:

- **Know It All:** 8 questions, about 8 minutes. Everyone taps an answer after 30 seconds of team discussion. A team's majority answer gets 1,000 points. Tied answers use whichever was submitted first; there is no speed bonus. Easy/medium questions keep people participating.
- **Draw a Blank:** 8 turns, about 12 minutes. Each team draws twice. One player draws on their phone while a secret prompt stays on that phone; the other teams type guesses. Each correct team gets 1,000 points. The artist's team gets 500 for each correct team. Artists rotate across teammates, and each turn lasts 75 seconds. Guesses ignore case, spaces, punctuation and a trailing plural “s”; synonyms and fuzzy spelling are not supported.
- **Room Service:** 4 prompts, about 7 minutes. Each team has 60 seconds to submit one funny line (first submission locks the team answer), followed by 25 seconds of anonymous voting. Everyone votes, then each team's majority becomes one ballot worth 500 points. No self-voting; tied ballots use the earliest vote. With fewer than two answers the round skips voting and awards no points.

Use a breather between games. Keep the TV audible to conversation; the game stage is intentionally quiet. No automated drinking penalties. The host can play from a separate `/play` tab while keeping `/host` open.

## Research and choices — checked September 25, 2026

Netflix's [official party-games overview](https://www.netflix.com/tudum/articles/netflix-party-games-play-on-tv) describes phone controllers, shared TV play, and drawing on a phone that appears on TV. Its Pictionary format awards both the artist and correct guessers. The app borrows those interaction patterns, using original names, artwork, prompts and rules; it does not integrate Netflix or copy its games. Four-team scoring lets this room support 16 participants even though several listed Netflix games support fewer players.

The [official Netflix controller guide](https://www.netflix.com/tudum/articles/how-to-play-netflix-games-on-tv) reinforces the QR-to-controller pattern. Here the LAN website replaces Netflix's controller app. A private screen is useful for secret drawing words; simultaneous inputs reduce waiting for 16 people.

### Trivia sourcing

| Source | What it offers | Decision |
| --- | --- | --- |
| [The Trivia API](https://the-trivia-api.com/) | Public v2 endpoint, categories and difficulty filters, free personal/noncommercial use; paid features include image questions and managed sessions | **Implemented.** Host loads 50 easy/medium questions once, server validates and caches them, then shuffles a round deck. No signup needed for this house party. |
| [Open Trivia DB](https://opentdb.com/api_config.php) | Free without a key, up to 50 questions per request, session tokens, 5-second per-IP request limit; CC BY-SA 4.0 | Good alternative, not integrated. Requires decoding HTML entities and respecting its rate limit; tokens expire after 6 hours of inactivity. |
| Original house pack | 32 short multiple-choice questions, 32 drawing words, 12 comedy prompts | **Included.** Starts fully offline, with no API prerequisite. |

API requests happen on the server before play. The backend filters long/malformed items and retains the last successful online batch in SQLite. A failed refresh leaves that batch and the house pack intact. Online play requires choosing the **Fresh trivia** pack after loading it. House-pack play is an explicit choice, not a silent mid-round replacement. Question IDs already played this night are excluded from future trivia decks; when a pack lacks enough unseen questions, load fresh questions, choose the other pack, or start a new night.

**Signups:** None are needed today. If you later want picture rounds, translations, or commercial usage, consider a paid [The Trivia API subscription](https://the-trivia-api.com/pricing/). Those premium features are not implemented here. The current personal-use question attribution is displayed in the app; the provider uses [CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/). Pricing amounts are not quoted because the public pricing page did not expose a stable plan table during this research.

## Stack and reliability

React 19 + TypeScript, Vite 8, Socket.IO, FastAPI/Pydantic, and SQLAlchemy/SQLite. The existing modern stack already fits this job; the feature adds no production dependencies or cloud service requirements. TV, phones and API share one origin in the launch build. Use exactly **one Uvicorn worker**.

The server owns timers, round IDs, locks and scoring. Private solutions stay server-side until reveal; only the artist's authenticated socket can retrieve their word or draw. Duplicate trivia answers are idempotent. Stale round packets and stale host commands are rejected. Team membership is frozen for scoring within each round; late joiners enter the next round. Game state and points survive server restarts; active rounds resume paused so the host can give everybody a fair restart. Browser reloads recover current state automatically. Starting a new night resets game points, question history and cached questions; the prior night remains in SQLite.

The engine validates drawing coordinates and bounds stroke count/size. Drawing streams small stroke segments through Socket.IO; it targets a local party, not a public multi-room service. The browser's voice/sound controls on the legacy shot board remain available at `/tracker`.

Tests cover majority/tie scoring, answer locking, no duplicate scoring, hidden answers, drawing authorization and rotation, self-vote rejection, team-weighted ballots, 16 concurrent players, clock deadlines, pause/resume, late packets, persistence, new-night isolation, provider failure, deduplication and socket authorization.
