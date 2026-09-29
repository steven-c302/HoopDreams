# PARTY OS — the crew cut

A 24-second, 1920×1080 / 30 fps launch video using the brag-slim workflow from [latent-spaces/brag](https://github.com/latent-spaces/brag). Existing renders are preserved. No narration.

## Creative brief
- Product: a living-room game show hosted by your TV, controlled by friends' phones.
- Audience: friends getting together for team trivia and party games.
- Difference: custom faces, a phone captain, five trivia formats, team voting, robbery and personal awards.
- Hook: **13 friends. Two Daniels. One game show.** Both Daniels are intentional, separate players with unique IDs and faces.
- Identity: the app's Saturday Morning tokens, Rammetto One, Figtree, thick ink, sun-yellow paper, pink Brainy and comic panels.
- Real UI: current TriviaStage, AvatarFace and Brainy components, using staged fixture data for this cast. This is an illustrative product demo, not a recording of the named people playing.
- Tone: playful, punchy cartoon game-show opening with readable holds.
- Flow: meet the crew → answer with a team → rob a rival → type an answer → start your own night.
- Audio: existing Party OS lobby score and restrained game stingers, mixed and faded as one soundtrack.
- Share line: Thirteen friends. Two Daniels. One TV running the whole show. PARTY OS turns your phones into controllers for team trivia, cheeky heists, and living-room bragging rights.

## Cast, in requested order
Amanda, Steven, John, Sunhye, Alex, Anna, Charlie, Daniel, Daniel, Ethan, Junha, Kaishun, Izzy.

## Storyboard
| Time | Picture | Copy |
| --- | --- | --- |
| 0–3 | Giant comic numerals, two Daniel faces, Brainy | 13 friends. Two Daniels. One game show. |
| 3–8 | All 13 real avatar components enter a 7+6 roster | PARTY OS / Your TV hosts. Your phones play. / all names |
| 8–12 | Actual Quick Draw question → reveal, team flags | TEAM UP. LOCK IT IN. / Your team's top pick counts. |
| 12–16 | Actual Heist victim selection → robbery | ROB YOUR FRIENDS. / The fastest correct team picks the victim. |
| 16–20 | Actual Write It Down question → typed-answer reveal | WRITE IT DOWN. / Close spelling counts. |
| 20–24 | Logo, cast ribbon, Brainy, launch instruction | Your next game night, sorted. / Double-click Start Party OS / Up to 16 players · No app. No account. |

## Build and verification
Source and intermediate assets live in work/. The renderer streams frames to FFmpeg without storing a frame sequence. Key frames and transitions are inspected before the final render. A settled roster poster replaces frame zero without adding duration. Technical checks and cast validation are recorded in validation.md.
